package app.stereobridge;
/** Camera buffer geometry. Display rotation is expressed in clockwise degrees. */
public final class CameraGeometry {
 private CameraGeometry() {}
 public static int rotation(int sensor, int display, boolean front) { return Math.floorMod(sensor + (front ? display : -display),360); }
 public static float[] previewCorners(int viewWidth,int viewHeight,int bufferWidth,int bufferHeight,int sensor,int display) {
   // TextureView's producer transform already applies sensor orientation and front mirroring.
   float sw = sensor % 180 == 0 ? bufferWidth : bufferHeight;
   float sh = sensor % 180 == 0 ? bufferHeight : bufferWidth;
   float rw = display % 180 == 0 ? sw : sh, rh = display % 180 == 0 ? sh : sw;
   float scale = Math.max(viewWidth / rw,viewHeight / rh);
   double angle = Math.toRadians(-display);
   float[] corners = {-sw/2,-sh/2,sw/2,-sh/2,sw/2,sh/2,-sw/2,sh/2};
   float[] target = new float[8];
   for (int n=0;n<4;n++) {
     float x=corners[n*2],y=corners[n*2+1];
     target[n*2]=viewWidth/2f+scale*(float)(x*Math.cos(angle)-y*Math.sin(angle));
     target[n*2+1]=viewHeight/2f+scale*(float)(x*Math.sin(angle)+y*Math.cos(angle));
   }
   return target;
 }
}
