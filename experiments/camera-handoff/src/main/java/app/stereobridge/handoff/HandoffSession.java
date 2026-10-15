package app.stereobridge.handoff;

import android.content.*;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import java.io.*;
import java.util.concurrent.*;

/** App-owned session state. Activities only observe; private take journal survives process death. */
final class HandoffSession {
  enum Phase { IDLE, CONNECTING, READY, CAMERA, FINISHING, SAVED, ERROR }
  interface Observer { void changed(); }
  private static HandoffSession instance;
  static synchronized HandoffSession get(Context context) {
    if(instance==null)instance=new HandoffSession(context.getApplicationContext());
    return instance;
  }
  private final Context context;
  private final SharedPreferences journal;
  private final Handler main=new Handler(Looper.getMainLooper());
  private final ExecutorService worker=Executors.newSingleThreadExecutor();
  private Observer observer;
  Phase phase=Phase.IDLE;
  String message="Turn on both DJI mics. Record in Camera, then tap Done. Stereo sound is added afterward.";
  Uri saved;
  private File camera,left,right;
  private boolean captureClean;
  private int generation;

  private HandoffSession(Context context) {
    this.context=context;journal=context.getSharedPreferences("handoff-session",Context.MODE_PRIVATE);
    String uri=journal.getString("saved","");
    if(!uri.isEmpty())saved=Uri.parse(uri);
    try {
      camera=restore("camera","take-[0-9]+\\.mp4");
      left=restore("left","left-[0-9]+\\.pcm");
      right=restore("right","right-[0-9]+\\.pcm");
      captureClean=journal.getBoolean("clean",false);
    } catch(IOException invalid) { camera=left=right=null; }
    if(camera==null){
      for(String key:new String[]{"staging_left","staging_right"}){
        String name=journal.getString(key,"");
        if(name.matches("(left|right)-[0-9]+\\.pcm"))new File(context.getFilesDir(),name).delete();
      }
      journal.edit().remove("staging_left").remove("staging_right").commit();
    }
    if(camera!=null) {
      phase=Phase.ERROR;
      message=captureClean?"A take needs finishing. Tap Finish saved take.":"Recording was interrupted. Original video and microphone files are retained. Save the original video or discard this take.";
    } else if(saved!=null) { phase=Phase.SAVED;message="Stereo video saved\nMovies / Stereo Bridge Test"; }
  }
  private File restore(String key,String pattern)throws IOException {
    String name=journal.getString(key,"");if(name.isEmpty())return null;
    if(!name.matches(pattern))throw new IOException("Invalid session file");
    return new File(context.getFilesDir(),name);
  }
  void observe(Observer next) { observer=next;next.changed(); }
  void unobserve(Observer old) { if(observer==old)observer=null; }
  private void notifyUi() { if(observer!=null)observer.changed(); }
  boolean busy() { return phase==Phase.CONNECTING||phase==Phase.READY||phase==Phase.CAMERA||phase==Phase.FINISHING; }
  boolean retained() { return camera!=null; }
  boolean canRetry() { return retained()&&captureClean&&!busy(); }
  void connect() {
    if(busy()||retained())return;
    int attempt=++generation;
    phase=Phase.CONNECTING;message="Connecting both mics…";notifyUi();
    MicCaptureService.ready=false;MicCaptureService.finished=false;MicCaptureService.error=null;
    MicCaptureService.leftFile=MicCaptureService.rightFile=null;
    try { context.startForegroundService(new Intent(context,MicCaptureService.class)); }
    catch(Exception failure) { fail(failure);return; }
    long deadline=SystemClock.elapsedRealtime()+25000;
    main.post(new Runnable(){ public void run(){
      if(attempt!=generation||phase!=Phase.CONNECTING)return;
      if(MicCaptureService.ready) {
        try {
          left=MicCaptureService.leftFile;right=MicCaptureService.rightFile;
          if(left==null||right==null)throw new IOException("Missing microphone files");
          camera=new File(context.getFilesDir(),"take-"+System.currentTimeMillis()+".mp4");
          if(!camera.createNewFile())throw new IOException("Could not create Camera output");
          captureClean=false;persist();phase=Phase.READY;message="Both mics ready";notifyUi();
          main.postDelayed(()->{if(attempt==generation&&phase==Phase.READY)abandonSetup("Camera was not opened. Microphones stopped; tap Connect to try again.");},15000);
        }catch(Exception failure){abandonSetup(failure.getMessage());}
      }else if(MicCaptureService.error!=null||SystemClock.elapsedRealtime()>deadline){
        abandonSetup("Could not connect both mics. Check their power and pairing.");
      }else main.postDelayed(this,200);
    }});
  }
  Intent claimCamera() {
    if(phase!=Phase.READY)return null;
    phase=Phase.CAMERA;message="Recording in Pixel Camera…";
    Uri uri=Uri.parse("content://"+context.getPackageName()+".capture/"+camera.getName());
    Intent intent=new Intent(MediaStore.ACTION_VIDEO_CAPTURE).setPackage("com.google.android.GoogleCamera")
        .putExtra(MediaStore.EXTRA_OUTPUT,uri).putExtra(MediaStore.EXTRA_VIDEO_QUALITY,1).putExtra(MediaStore.EXTRA_DURATION_LIMIT,120)
        .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_READ_URI_PERMISSION);
    intent.setClipData(ClipData.newRawUri("Camera output",uri));return intent;
  }
  void cameraFailed(Exception failure) { abandonSetup("Could not open Camera: "+failure.getMessage()); }
  void cancelSetup(){if(phase==Phase.CONNECTING||phase==Phase.READY)abandonSetup("Setup cancelled. Microphones stopped.");}
  private void abandonSetup(String reason){
    generation++;
    if(left==null)left=MicCaptureService.leftFile;if(right==null)right=MicCaptureService.rightFile;
    context.stopService(new Intent(context,MicCaptureService.class));phase=Phase.FINISHING;message="Stopping microphones…";notifyUi();
    worker.execute(()->{waitForCapture();main.post(()->{cleanup();phase=Phase.ERROR;message=reason;notifyUi();});});
  }
  void cameraReturned(boolean accepted) {
    context.stopService(new Intent(context,MicCaptureService.class));
    if(camera!=null)context.revokeUriPermission(Uri.parse("content://"+context.getPackageName()+".capture/"+camera.getName()),Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_READ_URI_PERMISSION);
    if(!accepted){phase=Phase.FINISHING;message="Discarding cancelled take…";notifyUi();worker.execute(()->{
      waitForCapture();main.post(()->{cleanup();phase=Phase.IDLE;message="Cancelled. No recording retained.";notifyUi();});
    });return;}
    finish(true);
  }
  void retry() { if(canRetry())finish(false); }
  private void finish(boolean wait) {
    if(camera==null||left==null||right==null){fail(new IOException("Interrupted capture: microphone files unavailable"));return;}
    phase=Phase.FINISHING;message="Adding stereo sound…\nKeep this screen open. Your original take is retained.";notifyUi();
    worker.execute(()->{
      File output=null;
      try {
        if(wait) {
          if(!waitForCapture()||MicCaptureService.error!=null)throw new IOException("Microphone recording was interrupted. Original video retained.");
          captureClean=true;persist();
        }
        if(!captureClean)throw new IOException("Unclean microphone capture cannot be synchronized automatically");
        long started=SystemClock.elapsedRealtime();
        AudioEnvelope reference=AudioEnvelope.camera(camera);
        float[] l=AudioEnvelope.mono(left),r=AudioEnvelope.mono(right);
        SyncEstimator.Result ls=SyncEstimator.align(reference.energy,l),rs=SyncEstimator.align(reference.energy,r);
        android.util.Log.i("StereoAlign","left="+ls.offsetBins+" score="+ls.correlation+" confident="+ls.confident+" right="+rs.offsetBins+" score="+rs.correlation+" confident="+rs.confident);
        if(!ls.confident||!rs.confident)throw new IOException("Could not confidently synchronize both mics. Original video and sound retained.");
        long correction=Math.round(reference.startUs*48000.0/1000000);
        output=new File(context.getFilesDir(),"stereo-"+System.currentTimeMillis()+".mp4");
        StereoVideoFinisher.finish(camera,left,right,ls.offsetBins*480L-correction,rs.offsetBins*480L-correction,output);
        Uri uri=publish(output);
        android.util.Log.i("StereoHandoff","Finished stereo "+uri+" processingMs="+(SystemClock.elapsedRealtime()-started));
        File completed=output;
        main.post(()->{
          saved=uri;journal.edit().putString("saved",uri.toString()).commit();
          cleanup();completed.delete();phase=Phase.SAVED;message="Stereo video saved\nMovies / Stereo Bridge Test\n\nVideo: original Pixel Camera\nSound: two DJI mics\n\nExperimental — check playback before relying on it.";notifyUi();
        });
      }catch(Exception failure){if(output!=null)output.delete();main.post(()->fail(failure));}
    });
  }
  private boolean waitForCapture(){
    long deadline=SystemClock.elapsedRealtime()+5000;
    while(!MicCaptureService.finished&&SystemClock.elapsedRealtime()<deadline)SystemClock.sleep(50);
    return MicCaptureService.finished;
  }
  void discard(){if(busy())return;cleanup();phase=Phase.IDLE;message="Take discarded.";notifyUi();}
  void saveOriginal(){
    if(busy()||camera==null)return;
    phase=Phase.FINISHING;message="Saving original Camera video…";notifyUi();
    worker.execute(()->{
      try {
        android.media.MediaExtractor check=new android.media.MediaExtractor();
        try{check.setDataSource(camera.getAbsolutePath());if(check.getTrackCount()==0)throw new IOException("Original video is incomplete");}finally{check.release();}
        Uri uri=publish(camera);main.post(()->{saved=uri;journal.edit().putString("saved",uri.toString()).commit();cleanup();phase=Phase.SAVED;message="Original Camera video saved. DJI stereo sound was not added.";notifyUi();});
      }catch(Exception failure){main.post(()->fail(failure));}
    });
  }
  private void persist()throws IOException{
    if(!journal.edit().putString("camera",camera.getName()).putString("left",left.getName()).putString("right",right.getName()).putBoolean("clean",captureClean).commit())throw new IOException("Could not save recovery information");
  }
  private void cleanup(){
    for(File file:new File[]{camera,left,right})if(file!=null)file.delete();
    camera=left=right=null;captureClean=false;
    journal.edit().remove("camera").remove("left").remove("right").remove("clean").remove("staging_left").remove("staging_right").commit();
  }
  private void fail(Exception failure){phase=Phase.ERROR;message=failure.getMessage()==null?failure.getClass().getSimpleName():failure.getMessage();android.util.Log.e("StereoHandoff",message,failure);notifyUi();}
  private Uri publish(File source)throws Exception{
    ContentValues values=new ContentValues();values.put(MediaStore.Video.Media.DISPLAY_NAME,"Stereo-Camera-"+System.currentTimeMillis()+".mp4");values.put(MediaStore.Video.Media.MIME_TYPE,"video/mp4");values.put(MediaStore.Video.Media.RELATIVE_PATH,"Movies/Stereo Bridge Test");values.put(MediaStore.Video.Media.IS_PENDING,1);
    Uri uri=context.getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,values);
    if(uri==null)throw new IOException("Could not create saved video");
    try{
      try(InputStream in=new FileInputStream(source);OutputStream out=context.getContentResolver().openOutputStream(uri)){
        if(out==null)throw new IOException("Could not write saved video");byte[] buffer=new byte[65536];for(int count;(count=in.read(buffer))!=-1;)out.write(buffer,0,count);
      }
      ContentValues published=new ContentValues();published.put(MediaStore.Video.Media.IS_PENDING,0);
      if(context.getContentResolver().update(uri,published,null,null)!=1)throw new IOException("Could not publish video");
      return uri;
    }catch(Exception failure){context.getContentResolver().delete(uri,null,null);throw failure;}
  }
}
