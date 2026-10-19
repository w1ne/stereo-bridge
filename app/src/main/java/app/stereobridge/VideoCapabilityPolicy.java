package app.stereobridge;
/** Arithmetic used while negotiating camera and encoder limits. */
final class VideoCapabilityPolicy {
 static int rate(int requested,double lower,double upper) {
   int selected = Math.min(requested,(int)Math.floor(upper));
   return selected >= 1 && selected >= lower ? selected : 0;
 }
 static int captureRate(int advertisedUpper,long videoDuration,long previewDuration) {
   long duration = Math.max(videoDuration,previewDuration);
   return duration > 0 ? (int)Math.min(advertisedUpper,1000000000L/duration) : advertisedUpper;
 }
 static boolean preferRange(int upper,int rate,int lower,int selectedUpper,int selectedRate,int selectedLower) {
   if ((upper <= 30) != (selectedUpper <= 30)) return upper <= 30;
   if (rate != selectedRate) return upper <= 30 ? rate > selectedRate : rate < selectedRate;
   return lower > selectedLower;
 }
 // Presets calibrated against a real Pixel Camera 4K30 HDR file (~43 Mbps).
 static int bitrate(int width,int height,int rate,int lower,int upper) {
  long desired;
  if (width >= 3840) desired = rate >= 50 ? 80_000_000L : 45_000_000L;
  else if (width >= 1920) desired = rate >= 50 ? 30_000_000L : 20_000_000L;
  else desired = Math.max(256000L,Math.min(8000000L,(long)width*height*rate/8));
  return (int)Math.max(lower,Math.min(upper,desired));
 }
}
