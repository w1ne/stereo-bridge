package app.stereobridge;
import org.junit.Test;
import static org.junit.Assert.*;
public class CameraPreviewGeometryTest {
 @Test public void textureAlreadyHasSensorOrientationInPortrait() {
  assertArrayEquals(new float[]{0,0,1080,0,1080,1920,0,1920},CameraGeometry.previewCorners(1080,1920,1920,1080,90,0),.01f);
 }
 @Test public void landscapeCompensatesOnlyDisplayRotation() {
  assertArrayEquals(new float[]{0,1080,0,0,1920,0,1920,1080},CameraGeometry.previewCorners(1920,1080,1920,1080,90,90),.01f);
 }
 @Test public void squareViewCropsWithoutChangingOrientedAspectRatio() {
  float[] p=CameraGeometry.previewCorners(1000,1000,1920,1080,90,0);
  assertEquals(1000,p[2]-p[0],.01f);
  assertEquals(1000*1920f/1080,p[5]-p[3],.01f);
  assertEquals(500,(p[1]+p[5])/2,.01f);
 }
}
