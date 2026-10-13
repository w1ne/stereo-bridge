package app.stereobridge;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.SystemClock;
import android.view.Surface;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;

/** Owns codecs and muxer; all codec pumping and mux writes run on one worker. */
public final class OfflineVideoEncoder {
  private static final int MAX_PCM = 2 * 1024 * 1024;
  private static final int MAX_ENCODED = 8 * 1024 * 1024;
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
    try {
      MediaFormat vf = MediaFormat.createVideoFormat("video/avc", width, height);
      vf.setInteger(
          MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
      vf.setInteger(MediaFormat.KEY_BIT_RATE, 8_000_000);
      vf.setInteger(MediaFormat.KEY_FRAME_RATE, 30);
      vf.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
      vf.setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline);
      video = MediaCodec.createEncoderByType("video/avc");
      video.configure(vf, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
      surface = video.createInputSurface();
      MediaFormat af = MediaFormat.createAudioFormat("audio/mp4a-latm", 48000, 2);
      af.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
      af.setInteger(MediaFormat.KEY_BIT_RATE, 192000);
      af.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384);
      audio = MediaCodec.createEncoderByType("audio/mp4a-latm");
      audio.configure(af, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
      muxer = new MediaMuxer(output.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
      muxer.setOrientationHint(rotation);
    } catch (Throwable e) {
      release();
      throw new IOException("Cannot initialize video/audio encoder", e);
    }
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
        if (stopping && SystemClock.elapsedRealtime() > stopDeadline)
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

  private synchronized void release() {
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
