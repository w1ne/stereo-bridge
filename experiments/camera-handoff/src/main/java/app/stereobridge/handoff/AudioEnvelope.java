package app.stereobridge.handoff;

import android.media.*;
import java.io.*;
import java.nio.*;
import java.util.*;

/** Small 10 ms envelopes for local alignment; raw speech stays in app-private files. */
final class AudioEnvelope {
  final float[] energy;
  final long startUs;
  AudioEnvelope(float[] energy, long startUs) { this.energy=energy; this.startUs=startUs; }

  static float[] mono(File input) throws IOException {
    Accumulator accumulator = new Accumulator();
    try (InputStream stream = new FileInputStream(input)) {
      byte[] bytes=new byte[65536];int carry=-1;
      for(int count;(count=stream.read(bytes))!=-1;) {
        int p=0;
        if(carry>=0 && count>0) { accumulator.add((short)(carry | bytes[p++]<<8));carry=-1; }
        while(p+1<count) { accumulator.add((short)((bytes[p]&255) | bytes[p+1]<<8));p+=2; }
        if(p<count)carry=bytes[p]&255;
      }
      if(carry>=0)throw new IOException("Incomplete PCM sample");
    }
    return accumulator.finish();
  }

  static AudioEnvelope camera(File input) throws IOException {
    MediaExtractor extractor = new MediaExtractor();
    MediaCodec decoder = null;
    boolean running=false;
    try {
      extractor.setDataSource(input.getAbsolutePath());
      MediaFormat format=null;
      for(int i=0;i<extractor.getTrackCount();i++) {
        MediaFormat candidate=extractor.getTrackFormat(i);
        if(candidate.getString(MediaFormat.KEY_MIME).startsWith("audio/")) { format=candidate;extractor.selectTrack(i);break; }
      }
      if(format==null) throw new IOException("Camera audio is required for automatic synchronization");
      if(format.getInteger(MediaFormat.KEY_SAMPLE_RATE)!=48000) throw new IOException("Probe requires 48 kHz Camera audio");
      long startUs=extractor.getSampleTime();
      decoder=MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME));
      decoder.configure(format,null,null,0);decoder.start();running=true;
      int channels=format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
      boolean inputEnded=false,outputEnded=false;
      long deadline=System.nanoTime()+120_000_000_000L;
      Accumulator accumulator=new Accumulator();
      MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();
      while(!outputEnded) {
        if(System.nanoTime()>deadline) throw new IOException("Camera audio decoding timed out");
        if(!inputEnded) {
          int index=decoder.dequeueInputBuffer(10000);
          if(index>=0) {
            ByteBuffer buffer=decoder.getInputBuffer(index);
            if(buffer==null) throw new IOException("No decoder input buffer");
            buffer.clear();int count=extractor.readSampleData(buffer,0);
            if(count<0) { decoder.queueInputBuffer(index,0,0,0,MediaCodec.BUFFER_FLAG_END_OF_STREAM);inputEnded=true; }
            else { decoder.queueInputBuffer(index,0,count,extractor.getSampleTime(),0);extractor.advance(); }
          }
        }
        int index=decoder.dequeueOutputBuffer(info,10000);
        if(index==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
          MediaFormat output=decoder.getOutputFormat();channels=output.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
          if(output.getInteger(MediaFormat.KEY_SAMPLE_RATE)!=48000 || (output.containsKey(MediaFormat.KEY_PCM_ENCODING) && output.getInteger(MediaFormat.KEY_PCM_ENCODING)!=AudioFormat.ENCODING_PCM_16BIT)) throw new IOException("Unsupported decoded Camera audio format");
        } else if(index>=0) {
          try {
            ByteBuffer buffer=decoder.getOutputBuffer(index);
            if(buffer==null) throw new IOException("No decoder output buffer");
            buffer.order(ByteOrder.LITTLE_ENDIAN);buffer.position(info.offset);buffer.limit(info.offset+info.size);
            while(buffer.remaining()>=channels*2) {
              double value=0;
              for(int channel=0;channel<channels;channel++) value+=Math.abs((int)buffer.getShort());
              accumulator.add(value/channels);
            }
            outputEnded=(info.flags&MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0;
          } finally { decoder.releaseOutputBuffer(index,false); }
        }
      }
      return new AudioEnvelope(accumulator.finish(),startUs);
    } finally {
      extractor.release();
      if(decoder!=null) { if(running) try { decoder.stop(); } catch(RuntimeException ignored) {} decoder.release(); }
    }
  }

  private static final class Accumulator {
    final ArrayList<Float> bins=new ArrayList<>();int samples;double sum;
    void add(double value) throws IOException {
      sum+=Math.abs(value);
      if(++samples==480) { bins.add((float)(sum/480));samples=0;sum=0;if(bins.size()>30000)throw new IOException("Probe audio limit exceeded"); }
    }
    float[] finish() { float[] values=new float[bins.size()];for(int i=0;i<values.length;i++)values[i]=bins.get(i);return values; }
  }
}
