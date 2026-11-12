package app.stereobridge.companion;
/** Deterministic diagnostic PCM only. Never used as real microphone audio. */
public final class StereoFixture {
 private long frame;
 public void fill(byte[] data,int offset,int length) {
  if(data==null||offset<0||length<0||offset>data.length-length||length%4!=0)throw new IllegalArgumentException("Expected complete stereo PCM16 frames");
  for(int p=offset;p<offset+length;p+=4,frame++){
   short left=(short)Math.round(8000*Math.sin(2*Math.PI*440*frame/48000));
   short right=(short)Math.round(8000*Math.sin(2*Math.PI*997*frame/48000));
   data[p]=(byte)left;data[p+1]=(byte)(left>>>8);data[p+2]=(byte)right;data[p+3]=(byte)(right>>>8);
  }
 }
}
