package com.hiakdhs323.localai;

import android.Manifest; import android.app.*; import android.content.*; import android.content.pm.PackageManager; import android.os.*; import android.widget.*; import java.io.*; import java.net.*; import java.util.concurrent.*;

public class MainActivity extends Activity {
  EditText url,name; TextView status,info; ProgressBar progress;
  @Override public void onCreate(Bundle b){super.onCreate(b);setContentView(R.layout.activity_main); url=findViewById(R.id.modelUrl);name=findViewById(R.id.modelName);status=findViewById(R.id.status);info=findViewById(R.id.info);progress=findViewById(R.id.progress);
    if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},10);
    findViewById(R.id.download).setOnClickListener(v->downloadModel());
    findViewById(R.id.start).setOnClickListener(v->startServer()); findViewById(R.id.stop).setOnClickListener(v->stopServer());
  }
  File modelFile(){String n=name.getText().toString().trim(); if(n.isEmpty())n="model.gguf"; return new File(getFilesDir(),n);}
  void downloadModel(){String u=url.getText().toString().trim(); if(u.isEmpty()){Toast.makeText(this,"모델 URL을 입력하세요.",Toast.LENGTH_SHORT).show();return;} progress.setVisibility(ProgressBar.VISIBLE); new Thread(()->{try{HttpURLConnection c=(HttpURLConnection)new URL(u).openConnection();c.setConnectTimeout(20000);c.setReadTimeout(60000);c.connect();long total=c.getContentLengthLong();try(InputStream in=c.getInputStream();FileOutputStream out=new FileOutputStream(modelFile())){byte[] buf=new byte[1024*1024];long done=0;int r;while((r=in.read(buf))!=-1){out.write(buf,0,r);done+=r;if(total>0){int p=(int)(done*100/total);runOnUiThread(()->progress.setProgress(p));}}}runOnUiThread(()->{progress.setVisibility(View.GONE);status.setText("● 모델 준비 완료");Toast.makeText(this,"모델 다운로드 완료",Toast.LENGTH_SHORT).show();});}catch(Exception e){runOnUiThread(()->{progress.setVisibility(View.GONE);Toast.makeText(this,"다운로드 실패: "+e.getMessage(),Toast.LENGTH_LONG).show();});}}).start();}
  void startServer(){File m=modelFile();if(!m.exists()){Toast.makeText(this,"먼저 GGUF 모델을 다운로드하세요.",Toast.LENGTH_SHORT).show();return;} Intent i=new Intent(this,AiServerService.class);i.putExtra("model",m.getAbsolutePath());if(Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);status.setText("● AI 서버 시작 요청됨");}
  void stopServer(){stopService(new Intent(this,AiServerService.class));status.setText("● 서버 중지됨");}
}
