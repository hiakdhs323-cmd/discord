package com.hiakdhs323.localai;

import android.app.*;import android.content.*;import android.os.*;import java.io.*;import java.util.*;

public class AiServerService extends Service {
  private Process process; private final String channel="local_ai";
  @Override public void onCreate(){super.onCreate(); createChannel(); startForeground(42,notification("AI 서버 준비 중"));}
  @Override public int onStartCommand(Intent intent,int flags,int id){String model=intent.getStringExtra("model"); if(model!=null) startNative(model); return START_STICKY;}
  private void createChannel(){if(Build.VERSION.SDK_INT>=26){NotificationChannel c=new NotificationChannel(channel,"Local AI Server",NotificationManager.IMPORTANCE_LOW);getSystemService(NotificationManager.class).createNotificationChannel(c);}}
  private Notification notification(String text){Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,channel):new Notification.Builder(this);return b.setContentTitle("Local AI Server").setContentText(text).setSmallIcon(android.R.drawable.stat_sys_download_done).setOngoing(true).build();}
  private void startNative(String model){new Thread(()->{try{File bin=new File(getFilesDir(),"llama-server"); if(!bin.exists()){copyAsset("llama-server",bin);} bin.setExecutable(true,false); File log=new File(getFilesDir(),"llama-server.log"); ArrayList<String> cmd=new ArrayList<>();cmd.add(bin.getAbsolutePath());cmd.add("-m");cmd.add(model);cmd.add("--host");cmd.add("0.0.0.0");cmd.add("--port");cmd.add("8080");cmd.add("-c");cmd.add("2048");cmd.add("-ngl");cmd.add("0"); ProcessBuilder pb=new ProcessBuilder(cmd);pb.redirectErrorStream(true);pb.redirectOutput(ProcessBuilder.Redirect.appendTo(log));process=pb.start(); ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(42,notification("AI 서버 실행 중 · 포트 8080")); process.waitFor();}catch(Exception e){try{FileWriter w=new FileWriter(new File(getFilesDir(),"error.log"),true);w.write(e.toString()+"\n");w.close();}catch(Exception ignored){}}}).start();}
  private void copyAsset(String n,File dst)throws Exception{try(InputStream in=getAssets().open(n);FileOutputStream out=new FileOutputStream(dst)){byte[] b=new byte[1024*1024];int r;while((r=in.read(b))!=-1)out.write(b,0,r);}}
  @Override public void onDestroy(){if(process!=null)process.destroy();super.onDestroy();}
  @Override public android.os.IBinder onBind(Intent i){return null;}
}
