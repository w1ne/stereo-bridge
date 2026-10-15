package app.stereobridge.handoff;

import android.app.*;
import android.content.*;
import android.os.Bundle;
import java.io.*;
import java.nio.file.*;

/** Signed test APK only. Seeds a clean interrupted-finishing journal using private test copies. */
public final class RecoveryProbe extends Instrumentation {
  private Bundle arguments;
  @Override public void onCreate(Bundle args){super.onCreate(args);arguments=args;start();}
  @Override public void onStart(){
    Bundle result=new Bundle();
    try{
      Context context=getTargetContext();long id=System.currentTimeMillis();
      String[] keys={"camera","left","right"},patterns={"take-[0-9]+\\.mp4","left-[0-9]+\\.pcm","right-[0-9]+\\.pcm"};
      SharedPreferences.Editor journal=context.getSharedPreferences("handoff-session",Context.MODE_PRIVATE).edit();
      for(int i=0;i<keys.length;i++){
        String name=arguments.getString(keys[i]);
        if(name==null||!name.matches(patterns[i]))throw new IOException("Invalid private test file");
        File source=new File(context.getFilesDir(),name);
        if(source.length()==0)throw new IOException("Missing private test file");
        String linked=(i==0?"take-":keys[i]+"-")+id+(i==0?".mp4":".pcm");
        Files.copy(source.toPath(),new File(context.getFilesDir(),linked).toPath());
        journal.putString(keys[i],linked);
      }
      if(!journal.putBoolean("clean",true).commit())throw new IOException("Journal commit failed");
      result.putString("result","Recovery journal seeded. Open helper and tap Finish saved take.");finish(Activity.RESULT_OK,result);
    }catch(Exception failure){result.putString("error",failure.toString());finish(Activity.RESULT_CANCELED,result);}
  }
}
