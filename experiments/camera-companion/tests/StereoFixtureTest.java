package app.stereobridge.companion;
import java.util.Arrays;
public final class StereoFixtureTest {
 public static void main(String[] args) {
  byte[] whole=new byte[19200];new StereoFixture().fill(whole,0,whole.length);
  byte[] pieces=new byte[whole.length];StereoFixture split=new StereoFixture();split.fill(pieces,0,4000);split.fill(pieces,4000,pieces.length-4000);
  if(!Arrays.equals(whole,pieces))throw new AssertionError("Frame continuity across reads");
  double left440=amplitude(whole,0,440),left997=amplitude(whole,0,997),right440=amplitude(whole,1,440),right997=amplitude(whole,1,997);
  if(left440<7000||right997<7000||left997>100||right440>100)throw new AssertionError("Separate tones missing: "+left440+","+left997+","+right440+","+right997);
  byte[] guard=new byte[48];Arrays.fill(guard,(byte)0x55);new StereoFixture().fill(guard,4,40);if(guard[0]!=0x55||guard[47]!=0x55)throw new AssertionError("Overwrite outside requested buffer");
  try{new StereoFixture().fill(new byte[8],0,3);throw new AssertionError("Partial frame accepted");}catch(IllegalArgumentException expected){}
  System.out.println("Stereo fixture: continuity, channels, bounds and alignment passed");
 }
 static double amplitude(byte[] data,int channel,double hz){double re=0,im=0;int n=data.length/4;for(int i=0;i<n;i++){int o=i*4+channel*2;short v=(short)((data[o]&255)|(data[o+1]<<8));double a=2*Math.PI*hz*i/48000;re+=v*Math.cos(a);im-=v*Math.sin(a);}return 2*Math.hypot(re,im)/n;}
}
