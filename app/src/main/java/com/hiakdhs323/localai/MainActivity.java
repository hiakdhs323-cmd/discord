package com.hiakdhs323.localai;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.StatFs;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;

public class MainActivity extends Activity {
    private static final String DEFAULT_URL =
        "https://huggingface.co/ggml-org/Qwen3-1.7B-GGUF/resolve/main/Qwen3-1.7B-Q4_K_M.gguf?download=true";
    private static final String DEFAULT_NAME = "Qwen3-1.7B-Q4_K_M.gguf";
    private static final long MIN_FREE = 1500000000L;
    private TextView status;
    private ProgressBar progress;
    private EditText url, name;
    private volatile boolean downloading;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        Thread.setDefaultUncaughtExceptionHandler((t,e)->writeFatalCrash(e));
        setContentView(R.layout.activity_main);
        status=findViewById(R.id.status); progress=findViewById(R.id.progress);
        url=findViewById(R.id.modelUrl); name=findViewById(R.id.modelName);
        url.setText(DEFAULT_URL); name.setText(DEFAULT_NAME);
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
            new Handler(Looper.getMainLooper()).postDelayed(()->{ try { requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},10); } catch(Throwable ignored){} },1200);
        findViewById(R.id.download).setOnClickListener(v->downloadModel());
        findViewById(R.id.start).setOnClickListener(v->startServer());
        findViewById(R.id.stop).setOnClickListener(v->stopServer());
        File model=modelFile();
        new Handler(Looper.getMainLooper()).postDelayed(()->{
            try {
                File current=modelFile();
                if(current.exists() && current.length()>500000000L){ status.setText("● 모델 설치 완료 · AI 서버 시작 중"); startServer(); }
                else { status.setText("● 첫 실행 · Qwen3 모델 자동 설치"); downloadModel(); }
            } catch(Throwable e) { status.setText("● 초기화 실패 · error.log 확인"); writeFatalCrash(e); }
        },500);
    }

    private File modelFile(){
        String n=name.getText().toString().trim();
        if(n.isEmpty()) n=DEFAULT_NAME;
        return new File(getFilesDir(),n);
    }

    private void downloadModel(){
        if(downloading) return;
        final String u=url.getText().toString().trim();
        if(u.isEmpty()){Toast.makeText(this,"모델 주소가 없습니다.",Toast.LENGTH_SHORT).show();return;}
        downloading=true; progress.setVisibility(View.VISIBLE); progress.setIndeterminate(true);
        status.setText("● Qwen3 1.7B 자동 설치 중…");
        new Thread(()->{
            File target=modelFile(), part=new File(modelFile().getAbsolutePath()+".part");
            try{
                StatFs fs=new StatFs(getFilesDir().getAbsolutePath());
                if(fs.getAvailableBytes()<MIN_FREE) throw new IOException("저장공간이 부족합니다. 최소 1.5GB 이상 필요합니다.");
                long existing=part.exists()?part.length():0;
                HttpURLConnection c=(HttpURLConnection)new URL(u).openConnection();
                c.setInstanceFollowRedirects(true); c.setConnectTimeout(30000); c.setReadTimeout(120000);
                c.setRequestProperty("User-Agent","LocalAIServer/2.0");
                if(existing>0)c.setRequestProperty("Range","bytes="+existing+"-");
                c.connect();
                int code=c.getResponseCode(); boolean partial=existing>0 && code==HttpURLConnection.HTTP_PARTIAL;
                if(!partial&&existing>0){existing=0;part.delete();}
                if(code>=400)throw new IOException("HTTP "+code);
                long total=c.getContentLengthLong();
                progress.setIndeterminate(false); progress.setMax(100);
                try(InputStream in=c.getInputStream(); FileOutputStream out=new FileOutputStream(part,partial)){
                    byte[] buf=new byte[1024*1024]; long done=existing; int r;
                    while((r=in.read(buf))!=-1){out.write(buf,0,r);done+=r;long expected=total>0?total+existing:1282439264L;
                        int p=(int)Math.max(0,Math.min(100,done*100L/Math.max(1,expected)));runOnUiThread(()->progress.setProgress(p));}
                    out.getFD().sync();
                } finally {c.disconnect();}
                if(part.length()<1000000000L)throw new IOException("다운로드가 중단되었습니다.");
                String sha=sha256(part);
                if(!sha.equalsIgnoreCase("d2387ca2dbfee2ffabce7120d3770dadca0b293052bc2f0e138fdc940d9bc7b5"))
                    throw new IOException("모델 무결성 검사 실패");
                if(target.exists())target.delete();
                if(!part.renameTo(target))throw new IOException("모델 저장 실패");
                runOnUiThread(()->{downloading=false;progress.setVisibility(View.GONE);status.setText("● 설치 완료 · AI 서버 시작");startServer();});
            }catch(Exception e){downloading=false;runOnUiThread(()->{progress.setVisibility(View.GONE);status.setText("● 설치 실패 · 다시 시도");Toast.makeText(this,"모델 설치 실패: "+e.getMessage(),Toast.LENGTH_LONG).show();});}
        },"model-installer").start();
    }

    private String sha256(File f)throws Exception{
        MessageDigest md=MessageDigest.getInstance("SHA-256");
        try(InputStream in=new FileInputStream(f)){byte[] b=new byte[1024*1024];int n;while((n=in.read(b))!=-1)md.update(b,0,n);}
        StringBuilder s=new StringBuilder();for(byte x:md.digest())s.append(String.format("%02x",x));return s.toString();
    }

    private void startServer(){
        File m=modelFile();if(!m.exists()){Toast.makeText(this,"모델 설치가 아직 끝나지 않았습니다.",Toast.LENGTH_SHORT).show();return;}
        try{Intent i=new Intent(this,AiServerService.class);i.putExtra("model",m.getAbsolutePath());
            if(Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);
            status.setText("● AI 서버 실행 중 · 화면 꺼짐 유지");}
        catch(Exception e){status.setText("● 서버 시작 실패");Toast.makeText(this,"서버 시작 실패: "+e.getMessage(),Toast.LENGTH_LONG).show();}
    }

    private void writeFatalCrash(Throwable e){
        try(FileWriter w=new FileWriter(new File(getFilesDir(),"error.log"),true)){
            w.write(new java.util.Date()+" [MainActivity FATAL] "+e+"\\n");
            for(StackTraceElement x:e.getStackTrace()) w.write("  at "+x+"\\n");
        }catch(Throwable ignored){}
    }

    private void stopServer(){try{stopService(new Intent(this,AiServerService.class));}catch(Exception ignored){}status.setText("● 서버 중지됨");}
}