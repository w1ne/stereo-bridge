package app.stereobridge.handoff;

import android.media.*;
import java.io.*;
import java.nio.*;

/** Local experiment: copy compressed camera video and replace its sound with stereo AAC. */
final class StereoVideoFinisher {
  static void finish(File cameraFile, File leftFile, File rightFile,
      long leftStartSample, long rightStartSample, File output) throws IOException {
    if (leftStartSample < 0 || rightStartSample < 0) throw new IOException("Negative source offset");
    MediaExtractor video = new MediaExtractor();
    MediaCodec encoder = null;
    MediaMuxer muxer = null;
    boolean encoding = false, muxing = false, completed = false;
    try (RandomAccessFile left = new RandomAccessFile(leftFile, "r");
         RandomAccessFile right = new RandomAccessFile(rightFile, "r")) {
      video.setDataSource(cameraFile.getAbsolutePath());
      int videoIndex = -1;
      MediaFormat videoFormat = null;
      for (int i = 0; i < video.getTrackCount(); i++) {
        MediaFormat format = video.getTrackFormat(i);
        if (format.getString(MediaFormat.KEY_MIME).startsWith("video/")) {
          videoIndex = i; videoFormat = format; break;
        }
      }
      if (videoFormat == null) throw new IOException("Camera returned no video track");
      long durationUs = videoFormat.getLong(MediaFormat.KEY_DURATION);
      if (durationUs <= 0 || durationUs > 120_000_000) throw new IOException("Probe supports takes up to two minutes");
      long totalFrames = (durationUs * 48000 + 999999) / 1000000;
      if (leftStartSample > left.length()/2 - totalFrames || rightStartSample > right.length()/2 - totalFrames)
        throw new IOException("Both microphone files must cover the entire video");
      left.seek(leftStartSample * 2); right.seek(rightStartSample * 2);
      MediaFormat audio = MediaFormat.createAudioFormat("audio/mp4a-latm", 48000, 2);
      audio.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
      audio.setInteger(MediaFormat.KEY_BIT_RATE, 192000);
      audio.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384);
      // This probe deliberately uses the AOSP codec tested on this Pixel. Its AAC-LC
      // output has two priming packets (2048 samples), verified by decoded tone phase.
      // Do not extend this assumption to another encoder without calibration.
      encoder = MediaCodec.createByCodecName("c2.android.aac.encoder");
      encoder.configure(audio, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
      encoder.start(); encoding = true;
      muxer = new MediaMuxer(output.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
      if (videoFormat.containsKey(MediaFormat.KEY_ROTATION)) muxer.setOrientationHint(videoFormat.getInteger(MediaFormat.KEY_ROTATION));
      int outputVideo = muxer.addTrack(videoFormat), outputAudio = -1;
      video.selectTrack(videoIndex);
      long frames = 0;
      boolean inputEnded = false, outputEnded = false;
      long deadline = System.nanoTime() + 120_000_000_000L;
      byte[] leftBytes = new byte[8192], rightBytes = new byte[8192];
      MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
      while (!outputEnded) {
        if (System.nanoTime() > deadline) throw new IOException("Audio encoding timed out");
        if (!inputEnded) {
          int index = encoder.dequeueInputBuffer(10000);
          if (index >= 0) {
            ByteBuffer input = encoder.getInputBuffer(index);
            if (input == null) throw new IOException("No audio input buffer");
            input.clear();
            int count = (int)Math.min(totalFrames - frames, Math.min(input.capacity()/4, 1024));
            if (count > 0) {
              left.readFully(leftBytes, 0, count*2); right.readFully(rightBytes, 0, count*2);
              for (int p = 0; p < count*2; p += 2) input.put(leftBytes[p]).put(leftBytes[p+1]).put(rightBytes[p]).put(rightBytes[p+1]);
            }
            long pts = frames * 1000000 / 48000;
            frames += count;
            inputEnded = count == 0;
            encoder.queueInputBuffer(index, 0, count*4, pts, inputEnded ? MediaCodec.BUFFER_FLAG_END_OF_STREAM : 0);
          }
        }
        int index = encoder.dequeueOutputBuffer(info, 10000);
        if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
          if (muxing) throw new IOException("Audio format changed twice");
          outputAudio = muxer.addTrack(encoder.getOutputFormat());
          muxer.start(); muxing = true;
          copyVideo(video, muxer, outputVideo, videoFormat);
        } else if (index >= 0) {
          try {
            long adjustedPts = info.presentationTimeUs - 2048L * 1000000 / 48000;
            if (info.size > 0 && (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0 && adjustedPts >= 0 && adjustedPts < durationUs) {
              if (!muxing) throw new IOException("Audio data before format");
              ByteBuffer data = encoder.getOutputBuffer(index);
              if (data == null) throw new IOException("No audio output buffer");
              data.position(info.offset); data.limit(info.offset + info.size);
              info.presentationTimeUs = adjustedPts;
              muxer.writeSampleData(outputAudio, data, info);
            }
            outputEnded = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
          } finally { encoder.releaseOutputBuffer(index, false); }
        }
      }
      info.set(0, 0, durationUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
      muxer.writeSampleData(outputAudio, ByteBuffer.allocateDirect(0), info);
      muxer.stop(); muxing = false;
      completed = true;
    } finally {
      video.release();
      if (encoder != null) { if (encoding) try { encoder.stop(); } catch (RuntimeException ignored) { } encoder.release(); }
      if (muxer != null) { if (muxing) try { muxer.stop(); } catch (RuntimeException ignored) { } muxer.release(); }
      if (!completed) output.delete();
    }
  }

  private static void copyVideo(MediaExtractor input, MediaMuxer output, int track, MediaFormat format) throws IOException {
    int capacity = Math.max(4*1024*1024, format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE) ? format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE) : 0);
    ByteBuffer data = ByteBuffer.allocateDirect(capacity);
    MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
    while (input.getSampleTime() >= 0) {
      data.clear();
      int bytes = input.readSampleData(data, 0);
      if (bytes < 0) break;
      if (bytes > capacity) throw new IOException("Video sample exceeds copy buffer");
      int flags=input.getSampleFlags();
      if((flags&(MediaExtractor.SAMPLE_FLAG_ENCRYPTED|MediaExtractor.SAMPLE_FLAG_PARTIAL_FRAME))!=0)throw new IOException("Unsupported encrypted or partial video sample");
      info.set(0, bytes, input.getSampleTime(), (flags&MediaExtractor.SAMPLE_FLAG_SYNC)!=0?MediaCodec.BUFFER_FLAG_KEY_FRAME:0);
      output.writeSampleData(track, data, info);
      input.advance();
    }
  }
}
