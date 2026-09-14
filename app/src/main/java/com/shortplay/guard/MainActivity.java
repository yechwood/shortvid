package com.shortplay.guard;

import android.Manifest;
import android.app.*;
import android.os.*;
import android.provider.MediaStore;
import android.provider.Settings;
import android.content.*;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.view.*;
import android.widget.*;
import android.media.MediaMetadataRetriever;
import java.util.*;

/** The only playback entry point. Validation happens before and during every play. */
public class MainActivity extends Activity {
    static final long MAX_MS = 300_000L;
    static final String ACTION_BLOCK = "com.shortplay.guard.BLOCK";
    LinearLayout root, list; TextView subtitle, usage; SharedPreferences prefs;
    ArrayList<Clip> clips = new ArrayList<>(); String sort = "Date"; boolean locked = false;
    Handler timer = new Handler(Looper.getMainLooper()); VideoView playing; long playStart, accumulated;
    BroadcastReceiver blocker = new BroadcastReceiver(){ public void onReceive(Context c, Intent i){ blockAndRequireEmail("Adult-content signal detected"); }};

    @Override public void onCreate(Bundle b) { super.onCreate(b); prefs=getSharedPreferences("guard",MODE_PRIVATE); if(Build.VERSION.SDK_INT>=33) registerReceiver(blocker,new IntentFilter(ACTION_BLOCK), Context.RECEIVER_NOT_EXPORTED); else registerReceiver(blocker,new IntentFilter(ACTION_BLOCK)); if (!accessibilityEnabled()) showPermissionGate(); else showHome(); }
    @Override public void onResume(){ super.onResume(); if (!locked && accessibilityEnabled()) showHome(); }
    @Override public void onDestroy(){ unregisterReceiver(blocker); stopPlaying(); super.onDestroy(); }

    boolean accessibilityEnabled(){
        String enabled=Settings.Secure.getString(getContentResolver(),Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return enabled!=null && enabled.toLowerCase(Locale.US).contains(getPackageName().toLowerCase(Locale.US)+"/"+ScreenGuardService.class.getName().toLowerCase(Locale.US));
    }
    int pad(){ return (int)(18*getResources().getDisplayMetrics().density); }
    TextView text(String value,int size){ TextView t=new TextView(this); t.setText(value);t.setTextSize(size);t.setTextColor(Color.WHITE);t.setPadding(pad(),10,pad(),10); return t; }
    Button button(String value){ Button b=new Button(this);b.setText(value);b.setTextColor(Color.rgb(9,19,28));b.setTextSize(14); GradientDrawable bg=new GradientDrawable();bg.setColor(Color.rgb(132,245,212));bg.setCornerRadius(30);b.setBackground(bg);b.setPadding(pad(),8,pad(),8);return b; }
    void base(){ root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(pad(),pad(),pad(),pad());root.setBackgroundColor(Color.rgb(9,19,28));setContentView(root); }
    void showPermissionGate(){
        base(); Space sp=new Space(this);root.addView(sp,new LinearLayout.LayoutParams(1,0,1));
        TextView title=text("ShortPlay",34);root.addView(title); root.addView(text("A safer, short-form video space.",18));
        root.addView(text("Screen protection must be enabled before playback. It lets ShortPlay react when visible content contains adult-content warning signals. It does not record your screen or save what you watch.",16));
        Button open=button("Open accessibility settings"); open.setOnClickListener(v->startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));root.addView(open);
        root.addView(text("After enabling “ShortPlay Screen Guard”, return here.",13)); Space end=new Space(this);root.addView(end,new LinearLayout.LayoutParams(1,0,1));
    }
    void showHome(){
        if(!accessibilityEnabled()){showPermissionGate();return;} locked=false; base();
        LinearLayout top=new LinearLayout(this); top.setGravity(Gravity.CENTER_VERTICAL); TextView title=text("ShortPlay",29);top.addView(title,new LinearLayout.LayoutParams(0,-2,1)); Button refresh=button("Refresh");refresh.setOnClickListener(v->{loadClips();renderList();});top.addView(refresh);root.addView(top);
        subtitle=text("Only videos under 5:00 can play",15);subtitle.setTextColor(Color.rgb(132,245,212));root.addView(subtitle);
        usage=text("Today: "+format(prefs.getLong("today_ms",0))+"  •  Total: "+format(prefs.getLong("total_ms",0)),15);root.addView(usage);
        LinearLayout filters=new LinearLayout(this); String[] opts={"Date","Name","Location"}; for(String s:opts){Button f=button(s);f.setTextSize(12);f.setOnClickListener(v->{sort=((Button)v).getText().toString();renderList();});filters.addView(f,new LinearLayout.LayoutParams(0,-2,1));}root.addView(filters);
        ScrollView scroll=new ScrollView(this);list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);scroll.addView(list);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1)); loadClips();renderList();
    }
    void loadClips(){ clips.clear(); String permission=Build.VERSION.SDK_INT>=33?Manifest.permission.READ_MEDIA_VIDEO:Manifest.permission.READ_EXTERNAL_STORAGE; if(checkSelfPermission(permission)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{permission},7);return;}
        Uri u=MediaStore.Video.Media.EXTERNAL_CONTENT_URI; String[] cols={MediaStore.Video.Media._ID,MediaStore.Video.Media.DISPLAY_NAME,MediaStore.Video.Media.DURATION,MediaStore.Video.Media.DATE_ADDED,MediaStore.Video.Media.RELATIVE_PATH};
        try(Cursor c=getContentResolver().query(u,cols,null,null,null)){if(c!=null)while(c.moveToNext()){long id=c.getLong(0),d=c.getLong(2);String n=c.getString(1),p=c.getString(4);clips.add(new Clip(ContentUris.withAppendedId(u,id),n,d,c.getLong(3),p==null?"Storage":p));}}catch(Exception ignored){}
    }
    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] results){super.onRequestPermissionsResult(requestCode,permissions,results);if(requestCode==7&&results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED){loadClips();renderList();}}
    void renderList(){if(list==null)return; list.removeAllViews(); Comparator<Clip> cmp=sort.equals("Name")?Comparator.comparing(a->a.name.toLowerCase()):sort.equals("Location")?Comparator.comparing(a->a.location):Comparator.comparingLong(a->-a.date);Collections.sort(clips,cmp);
        if(clips.isEmpty())list.addView(text("No videos found yet. Add videos to your device, then tap Refresh.",16));
        for(Clip c:clips){ LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.VERTICAL);row.setPadding(4,12,4,12); TextView name=text(c.name,17);row.addView(name);TextView meta=text(c.location+"  •  "+format(c.duration),13);meta.setTextColor(Color.LTGRAY);row.addView(meta);Button play=button(c.duration>MAX_MS?"Over 5 min — blocked":"Play");play.setEnabled(c.duration>0&&c.duration<=MAX_MS);play.setAlpha(play.isEnabled()?1:.42f);play.setOnClickListener(v->play(c));row.addView(play);list.addView(row);}}
    void play(Clip c){ if(locked||!accessibilityEnabled()){showPermissionGate();return;} if(c.duration<=0||c.duration>MAX_MS){toast("This video is blocked: it exceeds five minutes.");return;} Dialog d=new Dialog(this); LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(pad(),pad(),pad(),pad());box.setBackgroundColor(Color.rgb(9,19,28));VideoView vv=new VideoView(this);box.addView(vv,new LinearLayout.LayoutParams(-1,0,1)); LinearLayout bar=new LinearLayout(this);Button close=button("Close");Button zoom=button("Fill screen");bar.addView(close,new LinearLayout.LayoutParams(0,-2,1));bar.addView(zoom,new LinearLayout.LayoutParams(0,-2,1));box.addView(bar);d.setContentView(box); d.getWindow();
        vv.setVideoURI(c.uri); vv.setOnPreparedListener(mp->{if(mp.getDuration()>MAX_MS){d.dismiss();toast("Playback blocked: verified duration exceeds five minutes.");return;} mp.setOnVideoSizeChangedListener((m,w,h)->{});playing=vv;playStart=SystemClock.elapsedRealtime();vv.start();});
        zoom.setOnClickListener(v->{ViewGroup.LayoutParams lp=vv.getLayoutParams();lp.height=lp.height==0?ViewGroup.LayoutParams.MATCH_PARENT:0;vv.setLayoutParams(lp);});close.setOnClickListener(v->d.dismiss());d.setOnDismissListener(x->stopPlaying());d.show();
        timer.postDelayed(new Runnable(){public void run(){if(playing==vv&&vv.isPlaying()){if(vv.getCurrentPosition()>=MAX_MS){d.dismiss();toast("Five-minute limit reached.");}else timer.postDelayed(this,500);}}},500);
    }
    void stopPlaying(){if(playing!=null){long delta=Math.max(0,SystemClock.elapsedRealtime()-playStart);String today="day_"+(System.currentTimeMillis()/86400000L);long prior=prefs.getLong("day_key",0)==System.currentTimeMillis()/86400000L?prefs.getLong("today_ms",0):0;prefs.edit().putLong("day_key",System.currentTimeMillis()/86400000L).putLong("today_ms",prior+delta).putLong("total_ms",prefs.getLong("total_ms",0)+delta).apply();playing.stopPlayback();playing=null;}}
    void blockAndRequireEmail(String why){ stopPlaying(); locked=true; base();root.addView(text("Playback paused",30));root.addView(text(why+". To protect this device, playback is locked until a verification link is sent to the guardian email address.",16)); Button request=button("Request email verification");request.setOnClickListener(v->requestEmail());root.addView(request);root.addView(text("If this is an emergency, contact the guardian who manages this device.",13)); }
    void requestEmail(){ String endpoint=prefs.getString("endpoint","");String email=prefs.getString("email","");if(endpoint.isEmpty()||email.isEmpty()){toast("Guardian email service has not been configured.");return;}new Thread(()->{try{java.net.HttpURLConnection c=(java.net.HttpURLConnection)new java.net.URL(endpoint).openConnection();c.setRequestMethod("POST");c.setRequestProperty("Content-Type","application/json");c.setDoOutput(true);c.getOutputStream().write(("{\\\"email\\\":\\\""+email.replace("\\\"","")+"\\\",\\\"event\\\":\\\"adult_content_lock\\\"}").getBytes());int code=c.getResponseCode();runOnUiThread(()->toast(code<300?"Verification email requested.":"Could not request verification."));}catch(Exception e){runOnUiThread(()->toast("Could not reach the guardian email service."));}}).start(); }
    void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    String format(long ms){long m=ms/60000,h=m/60;return h>0?h+"h "+(m%60)+"m":m+"m";}
    static class Clip{Uri uri;String name,location;long duration,date;Clip(Uri u,String n,long d,long da,String l){uri=u;name=n;duration=d;date=da;location=l;}}
}

