package app.stereobridge;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.SystemClock;
import android.view.Surface;
import java.io.File;
import java.io.FileDescriptor;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;

/** Owns codecs and muxer; all codec pumping and mux writes run on one worker. */
public final class OfflineVideoEncoder {
  private static final int MAX_PCM = 2 * 1024 * 1024;
  private static final int MAX_ENCODED = 8 * 1024 * 1024;
  private final EncoderReleaseBarrier releaseBarrier = new EncoderReleaseBarrier();
  private MediaCodec video, audio;
  private MediaMuxer muxer;
  private Surface surface;
  private Thread worker;
  private final PcmBatchQueue pcm = new PcmBatchQueue(MAX_PCM, 512);
  private final ArrayDeque<Sample> pending = new ArrayDeque<>();
  private final EncoderMuxGate muxGate = new EncoderMuxGate(MAX_ENCODED, 4096);
  private volatile boolean stopping, aborted;
  private volatile Throwable failure;
  private final EncoderTimeline timeline = new EncoderTimeline();
  private long epochNanos, lastAudioEndUs;
  private int videoTrack = -1, audioTrack = -1;
  private boolean muxStarted, videoEos, audioEos, audioInputEos;
  private int videoSamples, audioSamples;

  public OfflineVideoEncoder(File output, int width, int height, int rotation) throws IOException {
    this(output, width, height, rotation, 30);
  }

  public OfflineVideoEncoder(File output, int width, int height, int rotation, int rate) throws IOException {
    this(output.getAbsolutePath(), null, width, height, rotation, rate, CodecPolicy.AVC, false);
  }

  public OfflineVideoEncoder(FileDescriptor output, int width, int height, int rotation, int rate) throws IOException {
    this(null, java.util.Objects.requireNonNull(output), width, height, rotation, rate, CodecPolicy.AVC, false);
  }

  public OfflineVideoEncoder(FileDescriptor output, int width, int height, int rotation, int rate, String mime, boolean hdr) throws IOException {
    this(null, java.util.Objects.requireNonNull(output), width, height, rotation, rate, mime, hdr);
  }

  private OfflineVideoEncoder(String path, FileDescriptor descriptor, int width, int height, int rotation, int rate, String mime, boolean hdr) throws IOException {
    try {
      video = configuredVideo(width, height, rate, mime, hdr);
      surface = video.createInputSurface();
      audio = configuredAudio();
      muxer = descriptor == null
          ? new MediaMuxer(path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
          : new MediaMuxer(descriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
      muxer.setOrientationHint(rotation);
    } catch (Throwable e) {
      release();
      throw new IOException("Cannot initialize video/audio encoder", e);
    }
  }

  /** Select an encoder for this exact surface format, including advertised rate limits. */
  static MediaFormat videoFormat(MediaCodecInfo info, String mime, int width, int height, int requestedFps, boolean hdr) {
    try {
      MediaCodecInfo.CodecCapabilities caps = info.getCapabilitiesForType(mime);
      boolean surface = false;
      for (int color : caps.colorFormats)
        if (color == MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface) surface = true;
      if (!surface) return null;
      MediaCodecInfo.VideoCapabilities v = caps.getVideoCapabilities();
      if (!v.isSizeSupported(width, height)) return null;
      android.util.Range<Double> rates = v.getSupportedFrameRatesFor(width, height);
      int rate = VideoCapabilityPolicy.rate(requestedFps, rates.getLower(), rates.getUpper());
      if (rate != requestedFps) return null;
      MediaFormat format = MediaFormat.createVideoFormat(mime, width, height);
      format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
      format.setInteger(MediaFormat.KEY_FRAME_RATE, rate);
      format.setInteger(MediaFormat.KEY_BIT_RATE, VideoCapabilityPolicy.bitrate(width, height, rate, v.getBitrateRange().getLower(), v.getBitrateRange().getUpper()));
      format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
      int profile = 0, level = 0;
      for (MediaCodecInfo.CodecProfileLevel candidate : caps.profileLevels) {
        if (CodecPolicy.HEVC.equals(mime)) {
          boolean family = hdr
              ? candidate.profile == MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10
                  || candidate.profile == MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10
              : candidate.profile == MediaCodecInfo.CodecProfileLevel.HEVCProfileMain;
          if (family && candidate.level > level) { profile = candidate.profile; level = candidate.level; }
        } else {
          boolean family = candidate.profile == MediaCodecInfo.CodecProfileLevel.AVCProfileHigh
              || candidate.profile == MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline;
          if (family && candidate.level > level) { profile = candidate.profile; level = candidate.level; }
        }
      }
      if (profile == 0) return null;
      format.setInteger(MediaFormat.KEY_PROFILE, profile);
      // Legacy OMX requires a level whenever a profile is set (Android 10 ACodec regression).
      format.setInteger(MediaFormat.KEY_LEVEL, level);
      return caps.isFormatSupported(format) ? format : null;
    } catch (IllegalArgumentException e) { return null; }
  }

  static boolean supportsVideo(int width, int height, int rate) {
    return supportsVideo(width, height, rate, CodecPolicy.AVC);
  }

  static boolean supportsVideo(int width, int height, int rate, String mime) {
    for (MediaCodecInfo info : new MediaCodecList(MediaCodecList.REGULAR_CODECS).getCodecInfos())
      if (info.isEncoder() && videoFormat(info, mime, width, height, rate, false) != null) return true;
    return false;
  }

  static boolean supportsHlgEncode(int width, int height, int rate) {
    if (android.os.Build.VERSION.SDK_INT < 33 || rate != 30) return false;
    for (MediaCodecInfo info : new MediaCodecList(MediaCodecList.REGULAR_CODECS).getCodecInfos())
      if (info.isEncoder() && videoFormat(info, CodecPolicy.HEVC, width, height, rate, true) != null) return true;
    return false;
  }

  private static MediaCodec configuredVideo(int width, int height, int rate, String mime, boolean hdr) throws IOException {
    Throwable last = null;
    for (String candidateMime : hdr ? new String[] {CodecPolicy.HEVC} : CodecPolicy.order(mime)) {
      boolean candidateHdr = hdr;
      for (MediaCodecInfo info : new MediaCodecList(MediaCodecList.REGULAR_CODECS).getCodecInfos()) {
        if (!info.isEncoder()) continue;
        MediaFormat format = videoFormat(info, candidateMime, width, height, rate, candidateHdr);
        if (format == null) continue;
        MediaCodec codec = null;
        try {
          codec = MediaCodec.createByCodecName(info.getName());
          codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
          return codec;
        } catch (Exception e) { last = e; if (codec != null) codec.release(); }
      }
    }
    throw new IOException("No compatible surface encoder for " + mime + (hdr ? " HDR" : ""), last);
  }

  private static MediaCodec configuredAudio() throws IOException {
    Throwable last = null;
    for (MediaCodecInfo info : new MediaCodecList(MediaCodecList.REGULAR_CODECS).getCodecInfos()) {
      if (!info.isEncoder()) continue;
      MediaCodec codec = null;
      try {
        MediaCodecInfo.CodecCapabilities caps = info.getCapabilitiesForType("audio/mp4a-latm");
        MediaFormat format = MediaFormat.createAudioFormat("audio/mp4a-latm",48000,2);
        format.setInteger(MediaFormat.KEY_AAC_PROFILE,MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        format.setInteger(MediaFormat.KEY_BIT_RATE,caps.getAudioCapabilities().getBitrateRange().clamp(192000));
        format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE,16384);
        if (!caps.isFormatSupported(format)) continue;
        codec = MediaCodec.createByCodecName(info.getName());
        codec.configure(format,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE);
        return codec;
      } catch (Exception e) { last=e; if (codec != null) codec.release(); }
    }
    throw new IOException("No compatible stereo AAC encoder",last);
  }

  public Surface videoSurface() {
    return surface;
  }

  public synchronized void start(long epochElapsedNanos) throws IOException {
    if (worker != null || aborted || muxer == null) throw new IOException("Encoder cannot start");
    epochNanos = epochElapsedNanos;
    try {
      video.start();
      audio.start();
    } catch (Throwable e) {
      release();
      throw new IOException("Cannot start encoders", e);
    }
    worker = new Thread(this::pump, "OfflineMp4Encoder");
    worker.start();
  }

  public synchronized void videoStarted(long firstCaptureElapsedNanos) {
    timeline.videoStarted(Math.max(0, (firstCaptureElapsedNanos - epochNanos) / 1000));
  }

  public synchronized void audio(byte[] stereoPcm, long ptsUs) {
    if (worker == null || stopping || aborted || failure != null)
      throw new IllegalStateException("Encoder unavailable", failure);
    pcm.offer(stereoPcm, ptsUs);
  }

  public void finish() throws IOException {
    Thread t;
    synchronized (this) {
      stopping = true;
      t = worker;
    }
    if (t == null) {
      abort();
      throw new IOException("Encoder never started");
    }
    try {
      t.join(12000);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      abort();
      throw new IOException("Interrupted finalization", e);
    }
    if (t.isAlive()) {
      abort();
      throw new IOException("Encoder finalization timed out");
    }
    if (failure != null) throw new IOException("Video finalization failed", failure);
    if (aborted || videoSamples == 0 || audioSamples == 0 || !videoEos || !audioEos)
      throw new IOException("Recording lacks complete audio/video tracks");
  }

  public void abort() {
    aborted = true;
    Thread t = worker;
    if (t == null) release();
    else if (t != Thread.currentThread()) {
      t.interrupt();
      try {
        t.join(1000);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
  }

  private void pump() {
    long stopDeadline = 0;
    boolean signalled = false;
    try {
      while (!aborted && !(videoEos && audioEos)) {
        if (stopping && !signalled) {
          video.signalEndOfInputStream();
          signalled = true;
          stopDeadline = SystemClock.elapsedRealtime() + 9000;
        }
        boolean progress = feedAudio();
        progress |= drain(video, true) | drain(audio, false);
        flushPending();
        if (signalled && SystemClock.elapsedRealtime() > stopDeadline)
          throw new IOException("Codec EOS timeout");
        if (!progress) Thread.sleep(2);
      }
      flushPending();
      if (!aborted && (!pending.isEmpty() || videoSamples == 0 || audioSamples == 0))
        throw new IOException("Missing playable audio/video data");
    } catch (Throwable e) {
      if (!aborted) failure = e;
    } finally {
      release();
    }
  }

  private boolean feedAudio() throws IOException {
    if (audioInputEos || !pcm.ready(stopping)) return false;
    int index = audio.dequeueInputBuffer(0);
    if (index < 0) return false;
    ByteBuffer buffer = audio.getInputBuffer(index);
    if (buffer == null || buffer.capacity() < 4) throw new IOException("Invalid AAC input buffer");
    buffer.clear();
    PcmBatchQueue.Batch batch = pcm.take(buffer, stopping);
    if (batch == null) {
      audio.queueInputBuffer(index, 0, 0, lastAudioEndUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
      audioInputEos = true;
    } else {
      audio.queueInputBuffer(index, 0, batch.bytes, batch.ptsUs, 0);
      lastAudioEndUs = batch.endUs;
    }
    return true;
  }

  private boolean drain(MediaCodec codec, boolean isVideo) throws IOException {
    MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
    int index = codec.dequeueOutputBuffer(info, 0);
    if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
      muxGate.format(isVideo);
      if (isVideo) {
        if (videoTrack >= 0) throw new IOException("Video format changed twice");
        videoTrack = muxer.addTrack(codec.getOutputFormat());
      } else {
        if (audioTrack >= 0) throw new IOException("Audio format changed twice");
        audioTrack = muxer.addTrack(codec.getOutputFormat());
      }
      if (muxGate.ready()) {
        muxer.start();
        muxStarted = true;
      }
      return true;
    }
    if (index < 0) return false;
    try {
      if (info.size > 0 && (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
        muxGate.reserve(info.size);
        ByteBuffer buffer = codec.getOutputBuffer(index);
        if (buffer == null) throw new IOException("Missing codec output");
        buffer.position(info.offset);
        buffer.limit(info.offset + info.size);
        byte[] bytes = new byte[info.size];
        buffer.get(bytes);
        pending.add(new Sample(isVideo, bytes, info.presentationTimeUs, info.flags));
      }
      if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
        if (isVideo) videoEos = true;
        else audioEos = true;
      }
    } finally {
      codec.releaseOutputBuffer(index, false);
    }
    return true;
  }

  private void flushPending() throws IOException {
    if (!muxStarted || !timeline.ready()) return;
    while (!pending.isEmpty()) {
      Sample s = pending.remove();
      long pts = s.pts;
      if (s.video) {
        pts = timeline.videoPts(pts);
      }
      MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
      info.set(
          0, s.bytes.length, Math.max(0, pts), s.flags & ~MediaCodec.BUFFER_FLAG_END_OF_STREAM);
      muxer.writeSampleData(s.video ? videoTrack : audioTrack, ByteBuffer.wrap(s.bytes), info);
      muxGate.release(s.bytes.length);
      if (s.video) videoSamples++;
      else audioSamples++;
    }
  }

  public boolean isReleased() { return releaseBarrier.isReleased(); }
  public void awaitRelease() throws InterruptedException { releaseBarrier.await(); }

  private void release() {
    try { releaseResources(); }
    finally { releaseBarrier.markReleased(); }
  }

  private synchronized void releaseResources() {
    if (muxer != null) {
      try {
        if (muxStarted) muxer.stop();
      } catch (Throwable e) {
        if (!aborted && failure == null) failure = e;
      }
      try {
        muxer.release();
      } catch (Throwable ignored) {
      }
      muxer = null;
    }
    for (MediaCodec codec : new MediaCodec[] {video, audio})
      if (codec != null) {
        try {
          codec.stop();
        } catch (Throwable ignored) {
        }
        try {
          codec.release();
        } catch (Throwable ignored) {
        }
      }
    video = null;
    audio = null;
    if (surface != null) {
      surface.release();
      surface = null;
    }
    pcm.clear();
    pending.clear();
  }

  private static final class Sample {
    final boolean video;
    final byte[] bytes;
    final long pts;
    final int flags;

    Sample(boolean v, byte[] b, long p, int f) {
      video = v;
      bytes = b;
      pts = p;
      flags = f;
    }
  }
}
