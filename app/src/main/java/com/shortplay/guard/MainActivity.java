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
import java.util.*;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

public class MainActivity extends Activity {
    static final long DEFAULT_MAX_MS = 300_000L;
    static final String DEFAULT_GUARDIAN_EMAIL = "kedemwoodlake@gmail.com";

    LinearLayout root, grid;
    SharedPreferences prefs;
    ArrayList<Clip> clips = new ArrayList<>();
    Handler handler = new Handler(Looper.getMainLooper());
    ExoPlayer exoPlayer;
    Dialog playerDialog;
    TextView gesturePill;
    long gesturePillUntil;
    float downX, downY;
    boolean gestureMoved;
    long lastTap;
    float lastTapX;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("guard", MODE_PRIVATE);
        showHome();
    }

    @Override public void onResume() {
        super.onResume();
        if (prefs != null) showHome();
    }

    int dp(float v) {
        return (int)(v * getResources().getDisplayMetrics().density + .5f);
    }

    TextView text(String s, float size) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(Color.WHITE);
        return t;
    }

    GradientDrawable rounded(int color, float radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radius));
        return g;
    }

    long getMaxMs() {
        return prefs.getLong("max_ms", DEFAULT_MAX_MS);
    }

    void showHome() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(12), dp(16), 0);
        root.setBackgroundColor(Color.rgb(7, 13, 20));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = text("ShortVid", 29);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(56), 1));

        TextView badge = text(format(getMaxMs()) + " max", 13);
        badge.setGravity(Gravity.CENTER);
        badge.setTextColor(Color.rgb(132,245,212));
        badge.setBackground(rounded(Color.rgb(20,42,43), 18));
        header.addView(badge, new LinearLayout.LayoutParams(dp(92), dp(36)));

        root.addView(header);

        TextView sub = text("Your camera videos", 14);
        sub.setTextColor(Color.rgb(160,174,184));
        root.addView(sub, new LinearLayout.LayoutParams(-1, dp(30)));

        ScrollView scroll = new ScrollView(this);
        scroll.setClipToPadding(false);
        scroll.setPadding(0, dp(6), 0, dp(18));
        grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(grid);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);

        loadClips();
        render();
    }

    void loadClips() {
        clips.clear();
        String permission = Build.VERSION.SDK_INT >= 33
                ? Manifest.permission.READ_MEDIA_VIDEO
                : Manifest.permission.READ_EXTERNAL_STORAGE;
        if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{permission}, 7);
            return;
        }

        Uri base = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
        String[] cols = {
                MediaStore.Video.Media._ID, MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.DURATION, MediaStore.Video.Media.DATE_MODIFIED,
                MediaStore.Video.Media.RELATIVE_PATH, MediaStore.Video.Media.WIDTH,
                MediaStore.Video.Media.HEIGHT
        };

        try (Cursor c = getContentResolver().query(base, cols, null, null,
                MediaStore.Video.Media.DATE_MODIFIED + " DESC")) {
            if (c != null) while (c.moveToNext()) {
                long id = c.getLong(0);
                clips.add(new Clip(ContentUris.withAppendedId(base, id), c.getString(1),
                        c.getLong(2), c.getLong(3), c.getString(4) == null ? "" : c.getString(4),
                        c.getInt(5), c.getInt(6)));
            }
        } catch (Exception ignored) {}
    }

    void render() {
        if (grid == null) return;
        grid.removeAllViews();

        if (clips.isEmpty()) {
            TextView empty = text("No camera videos found", 16);
            empty.setGravity(Gravity.CENTER);
            empty.setTextColor(Color.rgb(170,184,194));
            grid.addView(empty, new LinearLayout.LayoutParams(-1, dp(160)));
            return;
        }

        int widthDp = getResources().getConfiguration().screenWidthDp;
        // Qin F21 Pro has a compact 480x640 display; two columns keep cards/touch targets usable.
        int columns = widthDp >= 600 ? 4 : (widthDp <= 360 ? 2 : 3);
        LinearLayout row = null;

        for (int i = 0; i < clips.size(); i++) {
            if (i % columns == 0) {
                row = new LinearLayout(this);
                row.setGravity(Gravity.TOP);
                int rowHeight = getResources().getConfiguration().screenWidthDp <= 360 ? 202 : 166;
                grid.addView(row, new LinearLayout.LayoutParams(-1, dp(rowHeight)));
            }
            LinearLayout card = makeCard(clips.get(i));
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, getResources().getConfiguration().screenWidthDp <= 360 ? dp(194) : dp(158), 1);
            cp.setMargins(dp(3), dp(3), dp(3), dp(5));
            row.addView(card, cp);
        }
    }

    LinearLayout makeCard(Clip c) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(3), dp(3), dp(3), dp(5));
        card.setBackground(rounded(Color.rgb(18,27,36), 14));

        FrameLayout frame = new FrameLayout(this);
        ImageView thumb = new ImageView(this);
        thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
        thumb.setBackground(rounded(Color.rgb(25,36,47), 11));
        frame.addView(thumb, new FrameLayout.LayoutParams(-1, dp(112)));
        loadThumbnail(c, thumb);

        TextView duration = text(formatDuration(c.duration), 11);
        duration.setGravity(Gravity.CENTER);
        duration.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        duration.setBackground(rounded(Color.argb(210,0,0,0), 7));
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(dp(54), dp(24), Gravity.BOTTOM | Gravity.END);
        bp.setMargins(0,0,dp(5),dp(5));
        frame.addView(duration, bp);
        card.addView(frame);

        TextView name = text(c.name, 11);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        name.setTextColor(Color.rgb(218,225,231));
        name.setPadding(dp(5), dp(3), dp(5), 0);
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
            } catch (Exception ignored) {
            } finally {
                try { r.release(); } catch (Exception ignored) {}
            }
            Bitmap out = b;
            runOnUiThread(() -> { if (out != null) target.setImageBitmap(out); });
        }).start();
    }

    void play(Clip c) {
        final long limit = getMaxMs();

        // Hard preflight: no player is constructed before the duration and screening gate pass.
        if (c.duration <= 0 || c.duration > limit) {
            toast("This video is over the " + format(limit) + " limit.");
            return;
        }

        Dialog checking = new Dialog(this);
        TextView t = text("Checking video…", 15);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(30), dp(24), dp(30), dp(24));
        t.setBackground(rounded(Color.rgb(20,29,39), 20));
        checking.setContentView(t);
        Window w = checking.getWindow();
        if (w != null) {
            w.setBackgroundDrawableResource(android.R.color.transparent);
            w.setDimAmount(.35f);
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

        FrameLayout outer = new FrameLayout(this);
        outer.setBackgroundColor(Color.BLACK);

        PlayerView pv = new PlayerView(this);
        pv.setUseController(true);
        pv.setControllerAutoShow(true);
        pv.setControllerHideOnTouch(true);
        pv.setControllerShowTimeoutMs(3000);
        pv.setTimeBarScrubbingEnabled(true);
        pv.setKeepScreenOn(true);
        pv.setResizeMode(androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT);
        outer.addView(pv, new FrameLayout.LayoutParams(-1, -1));

        gesturePill = text("", 13);
        gesturePill.setGravity(Gravity.CENTER);
        gesturePill.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        gesturePill.setBackground(rounded(Color.argb(215,25,31,39), 20));
        gesturePill.setVisibility(View.GONE);
        FrameLayout.LayoutParams gp = new FrameLayout.LayoutParams(dp(150), dp(44),
                Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        gp.setMargins(0, dp(42), 0, 0);
        outer.addView(gesturePill, gp);

        pv.setOnTouchListener((v, e) -> handlePlayerTouch(e, pv));

        playerDialog.setContentView(outer);
        playerDialog.setOnDismissListener(d -> releasePlayer());
        playerDialog.show();

        // Only after the preflight gate has passed do we initialize ExoPlayer.
        exoPlayer = new ExoPlayer.Builder(this).build();
        pv.setPlayer(exoPlayer);
        exoPlayer.setMediaItem(MediaItem.fromUri(c.uri));
        exoPlayer.prepare();

        exoPlayer.addListener(new Player.Listener() {
            @Override public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_READY) {
                    long duration = exoPlayer.getDuration();
                    if (duration <= 0 || duration > getMaxMs()) {
                        playerDialog.dismiss();
                        toast("Playback blocked: the verified duration exceeds the limit.");
                        return;
                    }
                    exoPlayer.play();
                    enforceLimit();
                }
            }
        });
    }

    boolean handlePlayerTouch(MotionEvent e, PlayerView pv) {
        if (e.getAction() == MotionEvent.ACTION_DOWN) {
            downX = e.getX();
            downY = e.getY();
            gestureMoved = false;
            return false;
        }

        if (e.getAction() == MotionEvent.ACTION_MOVE) {
            if (Math.abs(e.getX()-downX) > dp(18) || Math.abs(e.getY()-downY) > dp(18))
                gestureMoved = true;
            return false;
        }

        if (e.getAction() == MotionEvent.ACTION_UP && !gestureMoved) {
            float x = e.getX();
            long now = SystemClock.elapsedRealtime();

            // Subtle double-tap seeking, with the normal Media3 seek bar as the primary seek UI.
            if (now - lastTap < 320 && Math.abs(x - lastTapX) < dp(80)) {
                if (exoPlayer != null) {
                    long jump = 10_000L;
                    if (x < pv.getWidth()/2f)
                        exoPlayer.seekTo(Math.max(0, exoPlayer.getCurrentPosition()-jump));
                    else
                        exoPlayer.seekTo(Math.min(exoPlayer.getDuration(), exoPlayer.getCurrentPosition()+jump));
                    showGesturePill(x < pv.getWidth()/2f ? "−10 seconds" : "+10 seconds");
                }
                lastTap = 0;
                return true;
            }
            lastTap = now;
            lastTapX = x;
        }

        return false;
    }

    void showGesturePill(String s) {
        if (gesturePill == null) return;
        gesturePill.setText(s);
        gesturePill.setVisibility(View.VISIBLE);
        gesturePillUntil = SystemClock.elapsedRealtime() + 900;
        handler.postDelayed(() -> {
            if (gesturePill != null && SystemClock.elapsedRealtime() >= gesturePillUntil)
                gesturePill.setVisibility(View.GONE);
        }, 950);
    }

    void enforceLimit() {
        handler.postDelayed(new Runnable() {
            @Override public void run() {
                if (exoPlayer == null || playerDialog == null || !playerDialog.isShowing()) return;
                if (exoPlayer.getCurrentPosition() > getMaxMs()) {
                    playerDialog.dismiss();
                    toast("Playback stopped at the configured limit.");
                    return;
                }
                handler.postDelayed(this, 500);
            }
        }, 500);
    }

    void releasePlayer() {
        if (exoPlayer != null) {
            exoPlayer.release();
            exoPlayer = null;
        }
    }

    String format(long ms) {
        long sec = Math.max(0,ms/1000), min = sec/60, h = min/60;
        if (h > 0) return h + "h " + (min%60) + "m";
        return min + "m";
    }

    String formatDuration(long ms) {
        long sec = Math.max(0,ms/1000), m = sec/60;
        return String.format(Locale.US, "%d:%02d", m%60, sec%60);
    }

    void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }

    @Override public void onRequestPermissionsResult(int r,String[] p,int[] g) {
        super.onRequestPermissionsResult(r,p,g);
        if (r == 7 && g.length > 0 && g[0] == PackageManager.PERMISSION_GRANTED) {
            loadClips(); render();
        }
    }

    static class Clip {
        Uri uri; String name, location; long duration, date; int width, height;
        Clip(Uri u,String n,long d,long da,String l,int w,int h) {
            uri=u; name=n; duration=d; date=da; location=l; width=w; height=h;
        }
    }
}