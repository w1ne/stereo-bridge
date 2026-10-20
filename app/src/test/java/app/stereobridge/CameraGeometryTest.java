package app.stereobridge;
import org.junit.Test;
import static org.junit.Assert.*;
public class CameraGeometryTest {
 @Test public void rearRotationSubtractsDisplay() { assertEquals(0, CameraGeometry.rotation(90,90,false)); assertEquals(270,CameraGeometry.rotation(90,180,false)); }
 @Test public void frontRotationAddsDisplay() { assertEquals(180,CameraGeometry.rotation(90,90,true)); assertEquals(0,CameraGeometry.rotation(270,90,true)); }
}
