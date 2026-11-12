package app.stereobridge.handoff;

import android.app.Activity;
import android.content.*;
import android.os.Bundle;
import android.widget.*;

/** Thin UI over app-owned session state; no external diagnostic intent actions. */
public final class HandoffActivity extends Activity implements HandoffSession.Observer {
  private HandoffSession session;
  private TextView status;
  private Button start,play,discard,original,cancel;
  private boolean resumed;
  @Override public void onCreate(Bundle state){
    boolean dark=(getResources().getConfiguration().uiMode&android.content.res.Configuration.UI_MODE_NIGHT_MASK)==android.content.res.Configuration.UI_MODE_NIGHT_YES;
    setTheme(dark?android.R.style.Theme_Material_NoActionBar:android.R.style.Theme_Material_Light_NoActionBar);
    super.onCreate(state);session=HandoffSession.get(this);
    ScrollView scroll=new ScrollView(this);LinearLayout root=new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);int padding=(int)(24*getResources().getDisplayMetrics().density);root.setPadding(padding,padding,padding,padding);scroll.addView(root);
    TextView title=new TextView(this);title.setText("Record with Pixel Camera");title.setTextSize(28);root.addView(title);
    status=new TextView(this);status.setTextSize(20);root.addView(status);
    start=new Button(this);root.addView(start);start.setOnClickListener(v->{if(session.canRetry())session.retry();else permissions();});
    cancel=new Button(this);cancel.setText("Cancel setup");root.addView(cancel);cancel.setOnClickListener(v->session.cancelSetup());
    play=new Button(this);play.setText("Play video");root.addView(play);play.setOnClickListener(v->{
      if(session.saved==null)return;
      try{startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(session.saved,"video/mp4").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));}
      catch(Exception failure){Toast.makeText(this,"Open the saved video in Photos",Toast.LENGTH_LONG).show();}
    });
    original=new Button(this);original.setText("Save original video without DJI sound");root.addView(original);original.setOnClickListener(v->session.saveOriginal());
    discard=new Button(this);discard.setText("Discard interrupted take");root.addView(discard);discard.setOnClickListener(v->new android.app.AlertDialog.Builder(this).setMessage("Discard this private original video and both microphone recordings?").setNegativeButton("Keep",null).setPositiveButton("Discard",(d,w)->session.discard()).show());
    TextView note=new TextView(this);note.setText("Experimental · two original DJI Mic Mini transmitters · takes up to two minutes. Keep the app open while it adds sound. Originals are deleted after a finished video is saved; interrupted takes stay private until you finish, save the original, or discard them.");root.addView(note);
    setContentView(scroll);changed();
  }
  @Override protected void onStart(){super.onStart();session.observe(this);}
  @Override protected void onResume(){super.onResume();resumed=true;changed();}
  @Override protected void onPause(){resumed=false;super.onPause();}
  @Override protected void onStop(){session.unobserve(this);super.onStop();}
  public void changed(){
    status.setText(session.message);start.setText(session.canRetry()?"Finish saved take":"Connect mics and open Camera");
    start.setEnabled(!session.busy()&&(!session.retained()||session.canRetry()));
    cancel.setVisibility(session.phase==HandoffSession.Phase.CONNECTING||session.phase==HandoffSession.Phase.READY?android.view.View.VISIBLE:android.view.View.GONE);
    play.setVisibility(session.saved!=null&&!session.busy()?android.view.View.VISIBLE:android.view.View.GONE);
    original.setVisibility(session.retained()&&!session.busy()?android.view.View.VISIBLE:android.view.View.GONE);
    discard.setVisibility(original.getVisibility());
    if(resumed&&session.phase==HandoffSession.Phase.READY){
      Intent intent=session.claimCamera();
      if(intent!=null)try{startActivityForResult(intent,42);}catch(Exception failure){session.cameraFailed(failure);}
    }
  }
  private void permissions(){
    if(android.os.Build.VERSION.SDK_INT>=31&&checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT)!=android.content.pm.PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{android.Manifest.permission.BLUETOOTH_CONNECT},7);return;}
    session.connect();
  }
  @Override public void onRequestPermissionsResult(int request,String[] names,int[] grants){super.onRequestPermissionsResult(request,names,grants);if(request==7&&grants.length>0&&grants[0]==android.content.pm.PackageManager.PERMISSION_GRANTED)session.connect();}
  @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==42)session.cameraReturned(result==RESULT_OK);}
}
