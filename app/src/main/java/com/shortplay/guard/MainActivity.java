package com.shortplay.guard;

import android.Manifest;
import android.app.*;
import android.os.*;
import android.provider.MediaStore;
import android.content.*;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.view.*;
import android.widget.*;
import android.media.MediaMetadataRetriever;
import android.media.AudioManager;
import java.util.*;

public class MainActivity extends Activity {
    static final long DEFAULT_MAX_MS = 300_000L;
    static final String DEFAULT_GUARDIAN_EMAIL = "kedemwoodlake@gmail.com";
    LinearLayout root, grid;
    SharedPreferences prefs;
    ArrayList<Clip> clips = new ArrayList<>();
    Handler handler = new Handler(Looper.getMainLooper());
    VideoView player;
    Dialog playerDialog;
    long playStarted;
    float downX, downY;
    boolean gestureMoved;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("guard", MODE_PRIVATE);
        showHome();
    }

    @Override public void onResume() { super.onResume(); if (prefs != null) showHome(); }

    int dp(float v) { return (int)(v * getResources().getDisplayMetrics().density + .5f); }

    TextView label(String s, float size) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextSize(size); t.setTextColor(Color.WHITE);
        t.setGravity(Gravity.CENTER_VERTICAL);
        return t;
    }

    Button pill(String s) {
        Button b = new Button(this);
        b.setText(s); b.setTextSize(12); b.setTextColor(Color.rgb(8,18,28));
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.rgb(132,245,212)); g.setCornerRadius(dp(24));
        b.setBackground(g);
        return b;
    }

    void showHome() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12), dp(12), dp(12), 0);
        root.setBackgroundColor(Color.rgb(9,19,28));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = label("ShortVid", 28);
        title.setTypeface(null, Typeface.BOLD);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(52), 1));

        Button settings = pill("Settings");
        settings.setOnClickListener(v -> startActivity(new Intent(this, AdminActivity.class)));
        header.addView(settings, new LinearLayout.LayoutParams(dp(105), dp(48)));
        root.addView(header);

        TextView info = label("Personal videos • maximum " + format(getMaxMs()), 14);
        info.setTextColor(Color.rgb(132,245,212));
        root.addView(info, new LinearLayout.LayoutParams(-1, dp(38)));

        ScrollView scroll = new ScrollView(this);
        grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(grid);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);

        loadClips();
        render();
    }

    long getMaxMs() {
        return prefs.getLong("max_ms", DEFAULT_MAX_MS);
    }

    void loadClips() {
        clips.clear();
        String permission = Build.VERSION.SDK_INT >= 33 ? Manifest.permission.READ_MEDIA_VIDEO : Manifest.permission.READ_EXTERNAL_STORAGE;
        if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{permission}, 7);
            return;
        }
        Uri base = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
        String[] cols = {MediaStore.Video.Media._ID, MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.DURATION, MediaStore.Video.Media.DATE_MODIFIED,
                MediaStore.Video.Media.RELATIVE_PATH, MediaStore.Video.Media.WIDTH,
                MediaStore.Video.Media.HEIGHT};
        try (Cursor c = getContentResolver().query(base, cols, null, null,
                MediaStore.Video.Media.DATE_MODIFIED + " DESC")) {
            if (c != null) while (c.moveToNext()) {
                long id=c.getLong(0), d=c.getLong(2);
                String name=c.getString(1), path=c.getString(4);
                clips.add(new Clip(ContentUris.withAppendedId(base,id), name, d,
                        c.getLong(3), path == null ? "Device storage" : path,
                        c.getInt(5), c.getInt(6)));
            }
        } catch (Exception ignored) {}
    }

    void render() {
        if (grid == null) return;
        grid.removeAllViews();
        if (clips.isEmpty()) {
            grid.addView(label("No videos found. Grant video access and tap Settings or reopen the app.", 16),
                    new LinearLayout.LayoutParams(-1, dp(90)));
            return;
        }

        int columns = getResources().getConfiguration().screenWidthDp >= 600 ? 4 : 3;
        LinearLayout row = null;
        for (int i=0;i<clips.size();i++) {
            if (i % columns == 0) {
                row = new LinearLayout(this);
                row.setGravity(Gravity.TOP);
                grid.addView(row, new LinearLayout.LayoutParams(-1, dp(150)));
            }
            Clip c = clips.get(i);
            LinearLayout card = makeCard(c);
            row.addView(card, new LinearLayout.LayoutParams(0, dp(142), 1));
        }
    }

    LinearLayout makeCard(Clip c) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(3), dp(3), dp(3), dp(6));

        FrameLayout frame = new FrameLayout(this);
        ImageView thumb = new ImageView(this);
        thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
        thumb.setBackgroundColor(Color.rgb(24,34,44));
        frame.addView(thumb, new FrameLayout.LayoutParams(-1, dp(108)));
        loadThumbnail(c, thumb);

        TextView duration = label(formatDuration(c.duration), 11);
        duration.setGravity(Gravity.CENTER);
        duration.setTextColor(Color.WHITE);
        duration.setBackgroundColor(Color.argb(190,0,0,0));
        FrameLayout.LayoutParams badge = new FrameLayout.LayoutParams(dp(55), dp(24), Gravity.BOTTOM|Gravity.END);
        badge.setMargins(0,0,dp(4),dp(4));
        frame.addView(duration,badge);
        card.addView(frame);

        TextView name = label(c.name, 11);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        card.addView(name, new LinearLayout.LayoutParams(-1, dp(27)));

        card.setOnClickListener(v -> play(c));
        return card;
    }

    void loadThumbnail(Clip c, ImageView target) {
        new Thread(() -> {
            Bitmap b = null;
            MediaMetadataRetriever r = new MediaMetadataRetriever();
            try {
                r.setDataSource(this, c.uri);
                b = r.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
            } catch(Exception ignored) {} finally { try { r.release(); } catch(Exception ignored) {} }
            Bitmap out=b;
            runOnUiThread(() -> { if(out!=null) target.setImageBitmap(out); });
        }).start();
    }

    void play(Clip c) {
        final long limit = getMaxMs();

        // Hard preflight: verify duration from the actual media stream before
        // constructing VideoView. A stale MediaStore duration can never grant
        // playback access.
        if (c.duration <= 0 || c.duration > limit) {
            toast("Blocked: this video is longer than " + format(limit) + ".");
            return;
        }

        final Dialog checking = new Dialog(this);
        TextView checkingText = label("Preparing video…", 16);
        checkingText.setGravity(Gravity.CENTER);
        checkingText.setPadding(dp(32), dp(28), dp(32), dp(28));
        checkingText.setTextColor(Color.WHITE);
        checkingText.setBackgroundColor(Color.rgb(18, 28, 38));
        checking.setContentView(checkingText);
        Window cw = checking.getWindow();
        if (cw != null) {
            cw.setBackgroundDrawableResource(android.R.color.transparent);
            cw.setDimAmount(.35f);
        }
        checking.show();

        new Thread(() -> {
            VideoScreeningEngine.Result result =
                    VideoScreeningEngine.screen(this, c.uri, c.duration, c.location,
                            c.name, c.date, c.width, c.height, limit);
            runOnUiThread(() -> {
                if (checking.isShowing()) checking.dismiss();
                if (!result.allowed) {
                    toast(result.reason);
                    return;
                }
                openPlayer(c);
            });
        }).start();
    }

    void openPlayer(Clip c) {
        playerDialog = new Dialog(this, android.R.style.Theme_Material_NoActionBar_Fullscreen);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundColor(Color.BLACK);

        FrameLayout videoArea = new FrameLayout(this);
        player = new VideoView(this);
        videoArea.addView(player, new FrameLayout.LayoutParams(-1,-1));

        TextView seekOverlay = label("", 18);
        seekOverlay.setGravity(Gravity.CENTER);
        seekOverlay.setTextColor(Color.WHITE);
        seekOverlay.setBackgroundColor(Color.argb(150,0,0,0));
        seekOverlay.setVisibility(View.GONE);
        videoArea.addView(seekOverlay, new FrameLayout.LayoutParams(dp(180),dp(70),Gravity.CENTER));

        videoArea.setOnTouchListener((v,e)->{
            if(e.getAction()==MotionEvent.ACTION_DOWN){downX=e.getX();downY=e.getY();gestureMoved=false;return true;}
            if(e.getAction()==MotionEvent.ACTION_MOVE){
                float dx=e.getX()-downX, dy=e.getY()-downY;
                if(Math.abs(dx)>dp(18)||Math.abs(dy)>dp(18)) gestureMoved=true;
                if(Math.abs(dx)>Math.abs(dy) && Math.abs(dx)>dp(18)){
                    int pos=player.getCurrentPosition();
                    int delta=(int)(dx*1500f/getResources().getDisplayMetrics().widthPixels);
                    int next=Math.max(0,Math.min(player.getDuration(),pos+delta));
                    player.seekTo(next);
                    seekOverlay.setText(formatDuration(next)+" / "+formatDuration(player.getDuration()));
                    seekOverlay.setVisibility(View.VISIBLE);
                    downX=e.getX();
                } else if(Math.abs(dy)>Math.abs(dx) && Math.abs(dy)>dp(24)){
                    AudioManager am=(AudioManager)getSystemService(AUDIO_SERVICE);
                    int max=am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                    int cur=am.getStreamVolume(AudioManager.STREAM_MUSIC);
                    int change=dy>0?-1:1;
                    am.setStreamVolume(AudioManager.STREAM_MUSIC,Math.max(0,Math.min(max,cur+change)),0);
                    seekOverlay.setText("Volume " + Math.round(am.getStreamVolume(AudioManager.STREAM_MUSIC)*100f/max) + "%");
                    seekOverlay.setVisibility(View.VISIBLE);
                    downY=e.getY();
                }
                return true;
            }
            if(e.getAction()==MotionEvent.ACTION_UP){
                if(!gestureMoved) { if(player.isPlaying()) player.pause(); else player.start(); }
                seekOverlay.setVisibility(View.GONE);
                return true;
            }
            return true;
        });
        box.addView(videoArea,new LinearLayout.LayoutParams(-1,0,1));

        LinearLayout controls=new LinearLayout(this);
        controls.setPadding(dp(8),dp(5),dp(8),dp(8));
        controls.setGravity(Gravity.CENTER_VERTICAL);
        TextView hint=label("Swipe ← → to seek • ↑ ↓ volume",11);
        hint.setTextColor(Color.LTGRAY);
        controls.addView(hint,new LinearLayout.LayoutParams(0,dp(44),1));
        Button close=pill("Close");
        close.setOnClickListener(v->playerDialog.dismiss());
        controls.addView(close,new LinearLayout.LayoutParams(dp(90),dp(44)));
        box.addView(controls);

        playerDialog.setContentView(box);
        playerDialog.setOnDismissListener(d->{if(player!=null){player.stopPlayback();player=null;}});
        // Screening has already verified the stream. Only now is the player initialized.
        player.setVideoURI(c.uri);
        player.setOnPreparedListener(mp->{
            if(mp.getDuration()<=0 || mp.getDuration()>getMaxMs()){
                playerDialog.dismiss(); toast("Playback blocked: verified duration exceeds the configured limit."); return;
            }
            playStarted=SystemClock.elapsedRealtime();
            player.start();
            enforceLimit(c.uri);
        });
        playerDialog.show();
    }

    void enforceLimit(Uri uri) {
        handler.postDelayed(new Runnable(){
            public void run(){
                if(player==null || playerDialog==null || !playerDialog.isShowing()) return;
                if(player.isPlaying() && player.getCurrentPosition()>getMaxMs()){
                    playerDialog.dismiss(); toast("Playback stopped at the configured limit."); return;
                }
                handler.postDelayed(this,500);
            }
        },500);
    }

    String format(long ms) {
        long sec=Math.max(0,ms/1000), min=sec/60, h=min/60;
        if(h>0) return h+"h "+(min%60)+"m";
        return min+"m";
    }
    String formatDuration(long ms) {
        long sec=Math.max(0,ms/1000), m=sec/60;
        return String.format(Locale.US,"%d:%02d",m%60,sec%60);
    }
    void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    @Override public void onRequestPermissionsResult(int r,String[] p,int[] g){
        super.onRequestPermissionsResult(r,p,g);
        if(r==7&&g.length>0&&g[0]==PackageManager.PERMISSION_GRANTED){loadClips();render();}
    }

    static class Clip {
        Uri uri; String name,location; long duration,date; int width,height;
        Clip(Uri u,String n,long d,long da,String l,int w,int h){uri=u;name=n;duration=d;date=da;location=l;width=w;height=h;}
    }
}