package com.hiakdhs323.localai;

import android.app.*;
import android.content.*;
import android.os.*;
import java.io.*;
import java.util.*;

public class AiServerService extends Service {
    private java.lang.Process process;
    private final String channel="local_ai";
    private volatile boolean stopping;

    @Override public void onCreate(){
        super.onCreate(); createChannel();
        try{startForeground(42,notification("AI 엔진 준비 중"));}
        catch(Throwable t){writeError("startForeground",t);stopSelf();}
    }

    @Override public int onStartCommand(Intent i,int flags,int id){
        String model=i==null?null:i.getStringExtra("model");
        if(model==null)model=getSharedPreferences("server",MODE_PRIVATE).getString("model",null);
        if(model!=null){
            getSharedPreferences("server",MODE_PRIVATE).edit().putString("model",model).apply();
            if(process==null)startNative(model);
        }
        return START_STICKY;
    }

    private void createChannel(){
        if(Build.VERSION.SDK_INT>=26){
            NotificationChannel c=new NotificationChannel(channel,"Local AI Server",NotificationManager.IMPORTANCE_LOW);
            c.setDescription("화면이 꺼져도 로컬 AI 서버를 유지합니다.");
            getSystemService(NotificationManager.class).createNotificationChannel(c);
        }
    }

    private Notification notification(String s){
        Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,channel):new Notification.Builder(this);
        return b.setContentTitle("Local AI Server").setContentText(s).setOngoing(true)
            .setSmallIcon(android.R.drawable.stat_sys_download_done).build();
    }

    private void startNative(String model){
        new Thread(()->{
            try{
                File bin=new File(getFilesDir(),"llama-server");
                if(!bin.exists()||bin.length()<1000000)copyAsset("llama-server",bin);
                bin.setExecutable(true,false);
                File log=new File(getFilesDir(),"llama-server.log");
                int threads=Math.max(2,Math.min(4,Runtime.getRuntime().availableProcessors()));
                ArrayList<String> cmd=new ArrayList<>();
                Collections.addAll(cmd,bin.getAbsolutePath(),"-m",model,"--host","0.0.0.0","--port","8080",
                    "--jinja","--reasoning-format","deepseek","-c","2048","-t",String.valueOf(threads),
                    "-ngl","0","--temp","0.6","--top-k","20","--top-p","0.95","--min-p","0");
                ProcessBuilder pb=new ProcessBuilder(cmd);
                pb.redirectErrorStream(true);pb.redirectOutput(ProcessBuilder.Redirect.appendTo(log));
                process=pb.start();
                getSystemService(NotificationManager.class).notify(42,notification("Qwen3 1.7B 실행 중 · 포트 8080"));
                int exit=process.waitFor();process=null;
                if(!stopping){
                    writeError("llama-server",new IOException("프로세스 종료 코드: "+exit));
                    getSystemService(NotificationManager.class).notify(42,notification("AI 엔진 종료 · 앱에서 다시 시작하세요."));
                }
            }catch(Throwable e){
                process=null;writeError("native",e);
                try{getSystemService(NotificationManager.class).notify(42,notification("AI 엔진 시작 실패 · error.log 확인"));}catch(Throwable ignored){}
            }
        },"llama-server").start();
    }

    private void copyAsset(String n,File d)throws Exception{
        try(InputStream in=getAssets().open(n);FileOutputStream out=new FileOutputStream(d)){
            byte[] b=new byte[1024*1024];int r;while((r=in.read(b))!=-1)out.write(b,0,r);out.getFD().sync();
        }
    }

    private void writeError(String where,Throwable e){
        try(FileWriter w=new FileWriter(new File(getFilesDir(),"error.log"),true)){
            w.write(new Date()+" ["+where+"] "+e+"\n");for(StackTraceElement x:e.getStackTrace())w.write("  at "+x+"\n");
        }catch(Exception ignored){}
    }

    @Override public void onDestroy(){
        stopping=true;if(process!=null){try{process.destroy();}catch(Throwable ignored){}}super.onDestroy();
    }
    @Override public IBinder onBind(Intent i){return null;}
}