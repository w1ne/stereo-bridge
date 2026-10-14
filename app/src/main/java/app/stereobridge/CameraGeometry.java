package app.stereobridge;
/** Camera buffer geometry. Display rotation is expressed in clockwise degrees. */
public final class CameraGeometry {
 private CameraGeometry() {}
 public static int rotation(int sensor, int display, boolean front) { return Math.floorMod(sensor + (front ? display : -display),360); }
 public static boolean within1080(int width,int height) { return width>0 && height>0 && Math.max(width,height)<=1920 && Math.min(width,height)<=1080; }
}
