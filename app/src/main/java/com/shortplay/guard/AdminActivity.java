package com.shortplay.guard;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.Color;
import android.view.*;
import android.widget.*;

public class AdminActivity extends Activity {
    static final String GUARDIAN = MainActivity.DEFAULT_GUARDIAN_EMAIL;

    public void onCreate(Bundle b) {
        super.onCreate(b);
        showAuthorization();
    }

    TextView t(String s,int size){
        TextView v=new TextView(this);v.setText(s);v.setTextColor(Color.WHITE);v.setTextSize(size);v.setPadding(0,12,0,12);return v;
    }

    void base(LinearLayout r){
        r.setOrientation(LinearLayout.VERTICAL);
        r.setPadding(36,42,36,30);
        r.setBackgroundColor(Color.rgb(9,19,28));
    }

    void showAuthorization(){
        LinearLayout r=new LinearLayout(this);base(r);
        r.addView(t("Settings authorization",28));
        r.addView(t("Settings are restricted to the authorized guardian email.",16));
        EditText email=new EditText(this);
        email.setHint("Enter authorized email");
        email.setTextColor(Color.WHITE);email.setHintTextColor(Color.GRAY);
        email.setInputType(33);
        r.addView(email,new LinearLayout.LayoutParams(-1,60));
        Button verify=new Button(this);verify.setText("Authorize");
        r.addView(verify);
        TextView note=t("Authorized address: "+GUARDIAN,13);note.setTextColor(Color.LTGRAY);r.addView(note);
        verify.setOnClickListener(v->{
            if(GUARDIAN.equalsIgnoreCase(email.getText().toString().trim())) showSettings();
            else Toast.makeText(this,"That email is not authorized.",Toast.LENGTH_LONG).show();
        });
        setContentView(r);
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

        r.addView(t("Default is 5 minutes. The duration is checked before the player is started, and checked again by the decoder.",13));
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