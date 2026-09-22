package com.shortplay.guard;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.Color;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import java.io.*;
import java.net.*;
import java.util.Random;
import java.util.concurrent.*;

public class AdminActivity extends Activity {
    static final String GUARDIAN = MainActivity.DEFAULT_GUARDIAN_EMAIL;
    static final String EMAIL_SEND_URL = "https://formsubmit.co/" + GUARDIAN;
    static final int CODE_LENGTH = 6;
    static final long RESEND_DELAY_MS = 60_000L;

    ExecutorService mailExecutor = Executors.newSingleThreadExecutor();
    String pendingCode;
    long codeSentAt = 0L;
    int failedAttempts = 0;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        showAuthorization();
    }

    @Override protected void onDestroy() {
        mailExecutor.shutdownNow();
        super.onDestroy();
    }

    TextView t(String s,int size){
        TextView v=new TextView(this);
        v.setText(s);v.setTextColor(Color.WHITE);v.setTextSize(size);v.setPadding(0,12,0,12);
        return v;
    }

    void base(LinearLayout r){
        r.setOrientation(LinearLayout.VERTICAL);
        r.setPadding(36,42,36,30);
        r.setBackgroundColor(Color.rgb(9,19,28));
    }

    void showAuthorization(){
        LinearLayout r=new LinearLayout(this);base(r);
        r.addView(t("Settings authorization",28));
        r.addView(t("Enter the authorized email, then request a one-time code. The code is sent to the mailbox and must be entered here.",16));

        EditText email=new EditText(this);
        email.setHint("Email address");
        email.setTextColor(Color.WHITE);email.setHintTextColor(Color.GRAY);
        email.setInputType(33);
        r.addView(email,new LinearLayout.LayoutParams(-1,60));

        Button send=new Button(this);send.setText("Get code");
        r.addView(send);

        TextView status=t("A 6-digit code will be sent to the authorized mailbox.",13);
        status.setTextColor(Color.LTGRAY);r.addView(status);

        EditText code=new EditText(this);
        code.setHint("Enter 6-digit code");
        code.setTextColor(Color.WHITE);code.setHintTextColor(Color.GRAY);
        code.setInputType(2);
        code.setVisibility(View.GONE);
        r.addView(code,new LinearLayout.LayoutParams(-1,60));

        Button verify=new Button(this);verify.setText("Verify code");
        verify.setVisibility(View.GONE);
        r.addView(verify);

        TextView note=t("The authorized mailbox is "+GUARDIAN+". The app does not grant access just because that address is typed.",13);
        note.setTextColor(Color.LTGRAY);r.addView(note);

        send.setOnClickListener(v -> {
            String entered=email.getText().toString().trim();
            if(!GUARDIAN.equalsIgnoreCase(entered)){
                Toast.makeText(this,"That email is not the authorized mailbox.",Toast.LENGTH_LONG).show();
                return;
            }
            long now=System.currentTimeMillis();
            if(now-codeSentAt<RESEND_DELAY_MS){
                long left=(RESEND_DELAY_MS-(now-codeSentAt)+999)/1000;
                Toast.makeText(this,"Please wait "+left+" seconds before requesting another code.",Toast.LENGTH_LONG).show();
                return;
            }
            pendingCode=String.format(java.util.Locale.US,"%06d",new Random().nextInt(1_000_000));
            codeSentAt=now;
            failedAttempts=0;
            send.setEnabled(false);
            status.setText("Sending verification code…");
            mailExecutor.execute(() -> {
                boolean ok=sendCodeEmail(pendingCode);
                runOnUiThread(() -> {
                    send.setEnabled(true);
                    if(ok){
                        code.setVisibility(View.VISIBLE);
                        verify.setVisibility(View.VISIBLE);
                        code.requestFocus();
                        ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(code,InputMethodManager.SHOW_IMPLICIT);
                        status.setText("Code sent. Check "+GUARDIAN+" and enter it below.");
                    }else{
                        pendingCode=null;
                        status.setText("The code could not be sent. Please try again.");
                        Toast.makeText(this,"Could not send the verification email.",Toast.LENGTH_LONG).show();
                    }
                });
            });
        });

        verify.setOnClickListener(v -> {
            String entered=code.getText().toString().trim();
            if(pendingCode==null || System.currentTimeMillis()-codeSentAt>10*60_000L){
                Toast.makeText(this,"That code has expired. Request a new code.",Toast.LENGTH_LONG).show();
                return;
            }
            if(++failedAttempts>5){
                pendingCode=null;
                Toast.makeText(this,"Too many incorrect attempts. Request a new code.",Toast.LENGTH_LONG).show();
                return;
            }
            if(pendingCode.equals(entered)){
                pendingCode=null;
                showSettings();
            }else{
                Toast.makeText(this,"Incorrect verification code.",Toast.LENGTH_LONG).show();
            }
        });

        setContentView(r);
    }

    boolean sendCodeEmail(String code){
        HttpURLConnection c=null;
        try{
            URL u=new URL(EMAIL_SEND_URL);
            c=(HttpURLConnection)u.openConnection();
            c.setRequestMethod("POST");
            c.setConnectTimeout(15_000);
            c.setReadTimeout(20_000);
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type","application/x-www-form-urlencoded");
            String body="subject="+enc("ShortVid settings verification code")
                    +"&message="+enc("Your ShortVid settings verification code is: "+code+"\n\nThis code expires in 10 minutes.");
            try(OutputStream out=c.getOutputStream()){out.write(body.getBytes("UTF-8"));}
            int status=c.getResponseCode();
            return status>=200 && status<400;
        }catch(Exception e){
            return false;
        }finally{
            if(c!=null)c.disconnect();
        }
    }

    String enc(String s){
        try{return URLEncoder.encode(s,"UTF-8");}catch(Exception e){return s;}
    }

    void showSettings(){
        LinearLayout r=new LinearLayout(this);base(r);
        r.addView(t("ShortVid settings",28));
        r.addView(t("Playback limit",16));

        SeekBar limit=new SeekBar(this);
        limit.setMax(30);
        long current=getSharedPreferences("guard",MODE_PRIVATE).getLong("max_ms",MainActivity.DEFAULT_MAX_MS);
        int minutes=(int)Math.max(1,Math.min(30,current/60000L));
        limit.setProgress(minutes-1);
        r.addView(limit,new LinearLayout.LayoutParams(-1,55));

        TextView value=t(minutes+" minutes",18);
        value.setTextColor(Color.rgb(132,245,212));r.addView(value);
        limit.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar s,int p,boolean from){value.setText((p+1)+" minutes");}
            public void onStartTrackingTouch(SeekBar s){}
            public void onStopTrackingTouch(SeekBar s){}
        });

        r.addView(t("The duration is checked before the player is started.",13));

        EditText warning=new EditText(this);
        warning.setHint("Warning email");
        warning.setTextColor(Color.WHITE);warning.setHintTextColor(Color.GRAY);
        warning.setInputType(33);
        warning.setText(GUARDIAN);
        warning.setEnabled(false);
        r.addView(warning,new LinearLayout.LayoutParams(-1,60));

        Button save=new Button(this);save.setText("Save settings");r.addView(save);
        save.setOnClickListener(v->{
            long ms=(limit.getProgress()+1)*60000L;
            getSharedPreferences("guard",MODE_PRIVATE).edit()
                    .putLong("max_ms",ms)
                    .putString("email",GUARDIAN)
                    .apply();
            Toast.makeText(this,"Settings saved.",Toast.LENGTH_SHORT).show();
            finish();
        });

        Button back=new Button(this);back.setText("Cancel");r.addView(back);
        back.setOnClickListener(v->finish());
        setContentView(r);
    }
}