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
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.view.*;
import android.view.ScaleGestureDetector;
import android.widget.*;
import android.media.MediaMetadataRetriever;
import java.io.OutputStream;
import java.util.*;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class MainActivity extends Activity {
    static final long DEFAULT_MAX_MS = 300_000L;
    static final String DEFAULT_GUARDIAN_EMAIL = "kedemwoodlake@gmail.com";

    LinearLayout root;
    RecyclerView gallery;
    SharedPreferences prefs;
    ArrayList<MediaItemData> media = new ArrayList<>();
    Handler handler = new Handler(Looper.getMainLooper());
    ExoPlayer exoPlayer;
    Dialog playerDialog;
    TextView gesturePill;
    long gesturePillUntil;
    float downX, downY;
    boolean gestureMoved;
    long lastTap; float lastTapX;
    Dialog viewerDialog;
    SwipeFrameLayout viewerBox;
    ImageView viewerImage;
    PlayerView viewerPlayer;
    TextView viewerTitle;
    int viewerIndex = -1;
    Bitmap editorBitmap;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("guard", MODE_PRIVATE);
        showHome();
    }

    @Override public void onResume() {
        super.onResume();
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
        root.setPadding(dp(12), dp(8), dp(12), 0);
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

        TextView sub = text("Photos & videos", 14);
        sub.setTextColor(Color.rgb(160,174,184));
        root.addView(sub, new LinearLayout.LayoutParams(-1, dp(28)));

        gallery = new RecyclerView(this);
        gallery.setClipToPadding(false);
        gallery.setPadding(dp(0), dp(5), dp(0), dp(18));
        gallery.setHasFixedSize(true);
        gallery.setItemViewCacheSize(12);
        gallery.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        root.addView(gallery, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);

        loadClips();
        render();
    }

    void loadClips() {
        media.clear();
        String[] permissions = Build.VERSION.SDK_INT >= 33
                ? new String[]{Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO}
                : new String[]{Manifest.permission.READ_EXTERNAL_STORAGE};
        boolean ok = true;
        for (String permission : permissions)
            ok &= checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
        if (!ok) {
            requestPermissions(permissions, 7);
            return;
        }

        Uri videos = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
        String[] vc = {MediaStore.Video.Media._ID, MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.DURATION, MediaStore.Video.Media.DATE_MODIFIED,
                MediaStore.Video.Media.RELATIVE_PATH, MediaStore.Video.Media.WIDTH,
                MediaStore.Video.Media.HEIGHT};
        try (Cursor c = getContentResolver().query(videos, vc, null, null,
                MediaStore.Video.Media.DATE_MODIFIED + " DESC")) {
            if (c != null) while (c.moveToNext()) {
                long id=c.getLong(0);
                media.add(new MediaItemData(ContentUris.withAppendedId(videos,id),c.getString(1),
                        true,c.getLong(2),c.getLong(3),c.getString(4)==null?"":c.getString(4),
                        c.getInt(5),c.getInt(6)));
            }
        } catch (Exception ignored) {}

        Uri images = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        String[] ic = {MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.DATE_MODIFIED, MediaStore.Images.Media.RELATIVE_PATH,
                MediaStore.Images.Media.WIDTH, MediaStore.Images.Media.HEIGHT};
        try (Cursor c = getContentResolver().query(images, ic, null, null,
                MediaStore.Images.Media.DATE_MODIFIED + " DESC")) {
            if (c != null) while (c.moveToNext()) {
                long id=c.getLong(0);
                media.add(new MediaItemData(ContentUris.withAppendedId(images,id),c.getString(1),
                        false,0,c.getLong(2),c.getString(3)==null?"":c.getString(3),
                        c.getInt(4),c.getInt(5)));
            }
        } catch (Exception ignored) {}

        Collections.sort(media,(a,b)->Long.compare(b.date,a.date));
    }

    void render() {
        if (gallery == null) return;
        if (media.isEmpty()) {
            gallery.setLayoutManager(new GridLayoutManager(this, 1));
            gallery.setAdapter(new EmptyAdapter());
            return;
        }
        int widthDp = getResources().getConfiguration().screenWidthDp;
        int columns = widthDp >= 600 ? 4 : (widthDp <= 360 ? 2 : 3);
        gallery.setLayoutManager(new GridLayoutManager(this, columns));
        gallery.setAdapter(new GalleryAdapter());
    }

    class EmptyAdapter extends RecyclerView.Adapter<EmptyAdapter.VH> {
        class VH extends RecyclerView.ViewHolder { VH(View v){super(v);} }
        @Override public VH onCreateViewHolder(android.view.ViewGroup p,int t) {
            TextView v=text("No photos or videos found",16);
            v.setGravity(Gravity.CENTER);
            v.setTextColor(Color.rgb(170,184,194));
            v.setLayoutParams(new RecyclerView.LayoutParams(-1,dp(160)));
            return new VH(v);
        }
        @Override public void onBindViewHolder(VH h,int p){}
        @Override public int getItemCount(){return 1;}
    }

    class GalleryAdapter extends RecyclerView.Adapter<GalleryAdapter.VH> {
        class VH extends RecyclerView.ViewHolder {
            ImageView thumb; TextView name, badge;
            VH(View v,ImageView i,TextView n,TextView b){super(v);thumb=i;name=n;badge=b;}
        }
        @Override public VH onCreateViewHolder(android.view.ViewGroup parent,int type) {
            int widthDp=getResources().getConfiguration().screenWidthDp;
            int h=widthDp<=360?dp(194):dp(158);
            LinearLayout card=new LinearLayout(MainActivity.this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(3),dp(3),dp(3),dp(5));
            card.setBackground(rounded(Color.rgb(18,27,36),14));
            FrameLayout frame=new FrameLayout(MainActivity.this);
            ImageView thumb=new ImageView(MainActivity.this);
            thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
            thumb.setBackground(rounded(Color.rgb(25,36,47),11));
            frame.addView(thumb,new FrameLayout.LayoutParams(-1,dp(112)));
            TextView badge=text("",10);
            badge.setGravity(Gravity.CENTER);
            badge.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
            FrameLayout.LayoutParams bp=new FrameLayout.LayoutParams(dp(54),dp(23),Gravity.BOTTOM|Gravity.END);
            bp.setMargins(0,0,dp(5),dp(5));
            frame.addView(badge,bp);
            card.addView(frame);
            TextView name=text("",11);
            name.setSingleLine(true);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            name.setTextColor(Color.rgb(218,225,231));
            name.setPadding(dp(5),dp(3),dp(5),0);
            card.addView(name,new LinearLayout.LayoutParams(-1,dp(27)));
            RecyclerView.LayoutParams rp=new RecyclerView.LayoutParams(-1,h);
            rp.setMargins(dp(3),dp(3),dp(3),dp(5));
            card.setLayoutParams(rp);
            return new VH(card,thumb,name,badge);
        }
        @Override public void onBindViewHolder(VH h,int position) {
            MediaItemData item=media.get(position);
            h.name.setText(item.name);
            h.badge.setText(item.video?formatDuration(item.duration):"PHOTO");
            h.badge.setTextColor(item.video?Color.WHITE:Color.rgb(220,230,235));
            h.badge.setBackground(rounded(Color.argb(190,0,0,0),7));
            h.thumb.setImageDrawable(null);
            loadThumbnail(item,h.thumb);
            h.itemView.setOnClickListener(v->openViewer(position));
        }
        @Override public int getItemCount(){return media.size();}
    }

    void loadThumbnail(MediaItemData item, ImageView target) {
        final Uri uri=item.uri;
        target.setTag(uri);
        new Thread(() -> {
            Bitmap b=null;
            try {
                if (Build.VERSION.SDK_INT >= 29) {
                    b=getContentResolver().loadThumbnail(uri,new android.util.Size(dp(320),dp(240)),null);
                } else if (item.video) {
                    MediaMetadataRetriever r=new MediaMetadataRetriever();
                    r.setDataSource(this,uri);
                    b=r.getFrameAtTime(0,MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                    r.release();
                } else {
                    b=MediaStore.Images.Media.getBitmap(getContentResolver(),uri);
                }
            } catch(Exception ignored) {}
            Bitmap out=b;
            runOnUiThread(()->{
                if (out!=null && uri.equals(target.getTag())) target.setImageBitmap(out);
            });
        }).start();
    }

    void openViewer(int index) {
        viewerIndex=index;
        viewerDialog=new Dialog(this,android.R.style.Theme_Material_NoActionBar_Fullscreen);
        viewerBox=new SwipeFrameLayout(this);
        viewerBox.setBackgroundColor(Color.BLACK);
        viewerImage=new ImageView(this);
        viewerImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
        viewerImage.setOnTouchListener((v,e)->handleViewerZoomTouch(e));
        viewerBox.addView(viewerImage,new FrameLayout.LayoutParams(-1,-1));

        viewerPlayer=new PlayerView(this);
        viewerPlayer.setUseController(true);
        viewerPlayer.setControllerAutoShow(true);
        viewerPlayer.setControllerHideOnTouch(true);
        viewerPlayer.setControllerShowTimeoutMs(2500);
        viewerPlayer.setResizeMode(androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT);
        viewerPlayer.setVisibility(View.GONE);
        viewerBox.addView(viewerPlayer,new FrameLayout.LayoutParams(-1,-1));

        LinearLayout top=new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(10),dp(20),dp(10),0);
        top.setBackgroundColor(Color.argb(115,0,0,0));
        Button close=smallButton("‹");
        Button edit=smallButton("Edit");
        viewerTitle=text("",14);
        viewerTitle.setGravity(Gravity.CENTER);
        viewerTitle.setSingleLine(true);
        top.addView(close,new LinearLayout.LayoutParams(dp(48),dp(48)));
        top.addView(viewerTitle,new LinearLayout.LayoutParams(0,dp(48),1));
        top.addView(edit,new LinearLayout.LayoutParams(dp(70),dp(42)));
        viewerBox.addView(top,new FrameLayout.LayoutParams(-1,dp(78),Gravity.TOP));
        close.setOnClickListener(v->viewerDialog.dismiss());
        edit.setOnClickListener(v->{ if(!media.get(viewerIndex).video) openEditor(media.get(viewerIndex)); });
        viewerBox.setOnSwipeListener((dx)->{
            if(Math.abs(dx)<dp(60)) return;
            if(dx<0) showViewerItem(viewerIndex+1);
            else showViewerItem(viewerIndex-1);
        });
        viewerDialog.setContentView(viewerBox);
        viewerDialog.setOnDismissListener(v->releaseViewerPlayer());
        viewerDialog.show();
        showViewerItem(viewerIndex);
    }

    Button smallButton(String s) {
        Button b=new Button(this);
        b.setText(s); b.setTextSize(12); b.setTextColor(Color.WHITE);
        b.setAllCaps(false);
        b.setBackground(rounded(Color.argb(175,35,42,50),18));
        return b;
    }

    void showViewerItem(int index) {
        if(viewerDialog==null || !viewerDialog.isShowing() || index<0 || index>=media.size()) return;
        viewerIndex=index;
        MediaItemData item=media.get(index);
        viewerTitle.setText((index+1)+" / "+media.size()+"  "+item.name);
        releaseViewerPlayer();
        viewerScaleFactor=1f; viewerImage.setScaleX(1f); viewerImage.setScaleY(1f);
        viewerImage.setVisibility(item.video?View.GONE:View.VISIBLE);
        viewerPlayer.setVisibility(item.video?View.VISIBLE:View.GONE);
        if(item.video) {
            startViewerVideo(item);
        } else {
            viewerImage.setImageDrawable(null);
            viewerImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
            new Thread(()->{
                try {
                    Bitmap b;
                    b=loadFullBitmap(item.uri);
                    runOnUiThread(()->{ if(viewerIndex==index) viewerImage.setImageBitmap(b); });
                } catch(Exception ignored) {}
            }).start();
        }
    }

    void startViewerVideo(MediaItemData item) {
        final long limit=getMaxMs();
        if(item.duration<=0 || item.duration>limit) { toast("This video is over the "+format(limit)+" limit."); showViewerItem(viewerIndex+1); return; }
        new Thread(()->{
            VideoScreeningEngine.Result result=VideoScreeningEngine.screen(this,item.uri,item.duration,item.location,
                    item.name,item.date,item.width,item.height,limit);
            runOnUiThread(()->{
                if(!result.allowed){ toast(result.reason); return; }
                exoPlayer=new ExoPlayer.Builder(this).build();
                viewerPlayer.setPlayer(exoPlayer);
                exoPlayer.setMediaItem(MediaItem.fromUri(item.uri));
                exoPlayer.prepare();
                exoPlayer.play();
            });
        }).start();
    }

    void releaseViewerPlayer() {
        if(exoPlayer!=null){ exoPlayer.release(); exoPlayer=null; }
        if(viewerPlayer!=null) viewerPlayer.setPlayer(null);
    }

    Bitmap loadFullBitmap(Uri uri) throws Exception {
        try (java.io.InputStream in=getContentResolver().openInputStream(uri)) {
            Bitmap b=BitmapFactory.decodeStream(in);
            if(b==null) throw new Exception("decode failed");
            return b;
        }
    }

    ScaleGestureDetector viewerScale;
    float viewerScaleFactor=1f; float viewerDownX;
    boolean handleViewerZoomTouch(MotionEvent e) {
        if(e.getActionMasked()==MotionEvent.ACTION_DOWN) viewerDownX=e.getX();
        if(e.getActionMasked()==MotionEvent.ACTION_UP && viewerScaleFactor<=1.01f && Math.abs(e.getX()-viewerDownX)>dp(70)) { if(e.getX()<viewerDownX) showViewerItem(viewerIndex+1); else showViewerItem(viewerIndex-1); }
        if(viewerScale==null) viewerScale=new ScaleGestureDetector(this,new ScaleGestureDetector.SimpleOnScaleGestureListener(){
            @Override public boolean onScale(ScaleGestureDetector d) {
                viewerScaleFactor=Math.max(1f,Math.min(5f,viewerScaleFactor*d.getScaleFactor()));
                viewerImage.setScaleX(viewerScaleFactor); viewerImage.setScaleY(viewerScaleFactor); return true;
            }
        });
        viewerScale.onTouchEvent(e); return true;
    }

    void openEditor(MediaItemData item) {
        new Thread(()->{
            try {
                Bitmap b=loadFullBitmap(item.uri);
                editorBitmap=b;
                runOnUiThread(()->showEditor(item));
            } catch(Exception e){ runOnUiThread(()->toast("Couldn't load this picture for editing.")); }
        }).start();
    }

    void showEditor(MediaItemData item) {
        Dialog d=new Dialog(this,android.R.style.Theme_Material_NoActionBar_Fullscreen);
        FrameLayout box=new FrameLayout(this);
        box.setBackgroundColor(Color.rgb(8,10,13));
        ImageView image=new ImageView(this);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setImageBitmap(editorBitmap);
        box.addView(image,new FrameLayout.LayoutParams(-1,-1));
        ScaleGestureDetector scale=new ScaleGestureDetector(this,new ScaleGestureDetector.SimpleOnScaleGestureListener(){
            float factor=1f;
            @Override public boolean onScale(ScaleGestureDetector detector){
                factor*=detector.getScaleFactor(); factor=Math.max(.7f,Math.min(5f,factor));
                image.setScaleX(factor); image.setScaleY(factor); return true;
            }
        });
        image.setOnTouchListener((v,e)->scale.onTouchEvent(e));
        LinearLayout bar=new LinearLayout(this);
        bar.setGravity(Gravity.CENTER);
        bar.setPadding(dp(8),dp(8),dp(8),dp(12));
        Button crop=smallButton("Crop");
        Button rotate=smallButton("Rotate");
        Button save=smallButton("Save");
        Button cancel=smallButton("Cancel");
        bar.addView(crop,new LinearLayout.LayoutParams(0,dp(52),1));
        bar.addView(rotate,new LinearLayout.LayoutParams(0,dp(52),1));
        bar.addView(save,new LinearLayout.LayoutParams(0,dp(52),1));
        bar.addView(cancel,new LinearLayout.LayoutParams(0,dp(52),1));
        box.addView(bar,new FrameLayout.LayoutParams(-1,dp(76),Gravity.BOTTOM));
        crop.setOnClickListener(v->openCropDialog(image));
        rotate.setOnClickListener(v->{
            if(editorBitmap==null)return;
            Matrix m=new Matrix(); m.postRotate(90);
            editorBitmap=Bitmap.createBitmap(editorBitmap,0,0,editorBitmap.getWidth(),editorBitmap.getHeight(),m,true);
            image.setImageBitmap(editorBitmap);
        });
        save.setOnClickListener(v->{ saveEditedPhoto(item,editorBitmap); d.dismiss(); });
        cancel.setOnClickListener(v->d.dismiss());
        d.setContentView(box); d.show();
    }

    void openCropDialog(ImageView image) {
        if(editorBitmap==null)return;
        Dialog cd=new Dialog(this,android.R.style.Theme_Material_NoActionBar_Fullscreen);
        LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setBackgroundColor(Color.BLACK);
        CropToolView cv=new CropToolView(this,editorBitmap);
        box.addView(cv,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout bar=new LinearLayout(this); bar.setGravity(Gravity.CENTER);
        Button cancel=smallButton("Cancel"), apply=smallButton("Crop");
        bar.addView(cancel,new LinearLayout.LayoutParams(0,dp(60),1)); bar.addView(apply,new LinearLayout.LayoutParams(0,dp(60),1));
        box.addView(bar,new LinearLayout.LayoutParams(-1,dp(72)));
        cancel.setOnClickListener(v->cd.dismiss());
        apply.setOnClickListener(v->{ Bitmap b=cv.getCroppedBitmap(); if(b!=null){ editorBitmap=b; image.setImageBitmap(b); image.setScaleX(1); image.setScaleY(1); } cd.dismiss(); });
        cd.setContentView(box); cd.show();
    }
    void saveEditedPhoto(MediaItemData original, Bitmap bitmap) {
        if(bitmap==null)return;
        new Thread(()->{
            try {
                String base=original.name==null?"photo":original.name;
                String name="ShortVid_"+System.currentTimeMillis()+"_"+base.replaceAll("[^a-zA-Z0-9._-]","_");
                Uri out;
                if(Build.VERSION.SDK_INT>=29){
                    ContentValues cv=new ContentValues();
                    cv.put(MediaStore.Images.Media.DISPLAY_NAME,name);
                    cv.put(MediaStore.Images.Media.MIME_TYPE,"image/jpeg");
                    cv.put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/ShortVid");
                    out=getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,cv);
                    if(out==null)throw new Exception("insert failed");
                    try(OutputStream os=getContentResolver().openOutputStream(out)){ bitmap.compress(Bitmap.CompressFormat.JPEG,94,os); }
                } else {
                    String path=MediaStore.Images.Media.insertImage(getContentResolver(),bitmap,name,"Edited in ShortVid");
                    out=Uri.parse(path);
                }
                runOnUiThread(()->{ toast("Edited picture saved to Pictures/ShortVid."); loadClips(); render(); });
            } catch(Exception e){ runOnUiThread(()->toast("Couldn't save the edited picture.")); }
        }).start();
    }

    void play(Clip c) {
        final long limit=getMaxMs();
        if(c.duration<=0||c.duration>limit){toast("This video is over the "+format(limit)+" limit.");return;}
        Dialog checking=new Dialog(this);
        TextView t=text("Checking video…",15); t.setGravity(Gravity.CENTER);
        t.setPadding(dp(30),dp(24),dp(30),dp(24)); t.setBackground(rounded(Color.rgb(20,29,39),20));
        checking.setContentView(t); checking.show();
        new Thread(()->{
            VideoScreeningEngine.Result result=VideoScreeningEngine.screen(this,c.uri,c.duration,c.location,c.name,c.date,c.width,c.height,limit);
            runOnUiThread(()->{if(checking.isShowing())checking.dismiss();if(!result.allowed){toast(result.reason);return;}openPlayer(c);});
        }).start();
    }

    void openPlayer(Clip c) {
        playerDialog=new Dialog(this,android.R.style.Theme_Material_NoActionBar_Fullscreen);
        FrameLayout outer=new FrameLayout(this); outer.setBackgroundColor(Color.BLACK);
        PlayerView pv=new PlayerView(this);
        pv.setUseController(true); pv.setControllerAutoShow(true); pv.setControllerHideOnTouch(true);
        pv.setControllerShowTimeoutMs(3000); pv.setTimeBarScrubbingEnabled(true); pv.setKeepScreenOn(true);
        pv.setResizeMode(androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT);
        outer.addView(pv,new FrameLayout.LayoutParams(-1,-1));
        gesturePill=text("",13); gesturePill.setGravity(Gravity.CENTER); gesturePill.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        gesturePill.setBackground(rounded(Color.argb(215,25,31,39),20)); gesturePill.setVisibility(View.GONE);
        FrameLayout.LayoutParams gp=new FrameLayout.LayoutParams(dp(150),dp(44),Gravity.TOP|Gravity.CENTER_HORIZONTAL);
        gp.setMargins(0,dp(42),0,0); outer.addView(gesturePill,gp);
        pv.setOnTouchListener((v,e)->handlePlayerTouch(e,pv));
        playerDialog.setContentView(outer); playerDialog.setOnDismissListener(d->releasePlayer()); playerDialog.show();
        exoPlayer=new ExoPlayer.Builder(this).build(); pv.setPlayer(exoPlayer);
        exoPlayer.setMediaItem(MediaItem.fromUri(c.uri)); exoPlayer.prepare();
        exoPlayer.addListener(new Player.Listener(){@Override public void onPlaybackStateChanged(int state){
            if(state==Player.STATE_READY){long duration=exoPlayer.getDuration();
                if(duration<=0||duration>getMaxMs()){playerDialog.dismiss();toast("Playback blocked: the verified duration exceeds the limit.");return;}
                exoPlayer.play(); enforceLimit();}
        }});
    }

    boolean handlePlayerTouch(MotionEvent e,PlayerView pv){
        if(e.getAction()==MotionEvent.ACTION_DOWN){downX=e.getX();downY=e.getY();gestureMoved=false;return false;}
        if(e.getAction()==MotionEvent.ACTION_MOVE){if(Math.abs(e.getX()-downX)>dp(18)||Math.abs(e.getY()-downY)>dp(18))gestureMoved=true;return false;}
        if(e.getAction()==MotionEvent.ACTION_UP&&!gestureMoved){float x=e.getX();long now=SystemClock.elapsedRealtime();
            if(now-lastTap<320&&Math.abs(x-lastTapX)<dp(80)&&exoPlayer!=null){long jump=10000L;
                if(x<pv.getWidth()/2f)exoPlayer.seekTo(Math.max(0,exoPlayer.getCurrentPosition()-jump));
                else exoPlayer.seekTo(Math.min(exoPlayer.getDuration(),exoPlayer.getCurrentPosition()+jump));
                showGesturePill(x<pv.getWidth()/2f?"−10 seconds":"+10 seconds");lastTap=0;return true;}
            lastTap=now;lastTapX=x;}
        return false;
    }

    void showGesturePill(String s){if(gesturePill==null)return;gesturePill.setText(s);gesturePill.setVisibility(View.VISIBLE);
        gesturePillUntil=SystemClock.elapsedRealtime()+900;handler.postDelayed(()->{if(gesturePill!=null&&SystemClock.elapsedRealtime()>=gesturePillUntil)gesturePill.setVisibility(View.GONE);},950);}

    void enforceLimit(){handler.postDelayed(new Runnable(){@Override public void run(){
        if(exoPlayer==null)return;if(playerDialog!=null&&playerDialog.isShowing()&&exoPlayer.getCurrentPosition()>getMaxMs()){playerDialog.dismiss();toast("Playback stopped at the configured limit.");return;}
        if(playerDialog!=null&&playerDialog.isShowing())handler.postDelayed(this,500);}},500);}

    void releasePlayer(){if(exoPlayer!=null){exoPlayer.release();exoPlayer=null;}}

    String format(long ms){long sec=Math.max(0,ms/1000),min=sec/60,h=min/60;if(h>0)return h+"h "+(min%60)+"m";return min+"m";}
    String formatDuration(long ms){long sec=Math.max(0,ms/1000),m=sec/60;return String.format(Locale.US,"%d:%02d",m%60,sec%60);}
    void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}

    @Override public void onRequestPermissionsResult(int r,String[] p,int[] g){super.onRequestPermissionsResult(r,p,g);if(r==7&&g.length>0){loadClips();render();}}

    class SwipeFrameLayout extends FrameLayout {
        float sx,sy; boolean tracking;
        interface Listener{void onSwipe(float dx);}
        Listener listener;
        SwipeFrameLayout(Context c){super(c);}
        void setOnSwipeListener(Listener l){listener=l;}
        @Override public boolean onInterceptTouchEvent(MotionEvent e){
            if(viewerImage!=null && viewerImage.getVisibility()==View.VISIBLE) return false;
            if(e.getAction()==MotionEvent.ACTION_DOWN){sx=e.getX();sy=e.getY();tracking=true;return false;}
            if(e.getAction()==MotionEvent.ACTION_MOVE&&tracking){
                float dx=e.getX()-sx,dy=e.getY()-sy;
                if(Math.abs(dx)>dp(28)&&Math.abs(dx)>Math.abs(dy)*1.25f){tracking=false;if(listener!=null)listener.onSwipe(dx);return true;}
            }
            if(e.getAction()==MotionEvent.ACTION_UP||e.getAction()==MotionEvent.ACTION_CANCEL)tracking=false;
            return false;
        }
    }

    static class MediaItemData {
        Uri uri; String name,location; long duration,date; int width,height; boolean video;
        MediaItemData(Uri u,String n,boolean v,long d,long da,String l,int w,int h){uri=u;name=n;video=v;duration=d;date=da;location=l;width=w;height=h;}
    }
    static class Clip {
        Uri uri; String name,location; long duration,date; int width,height;
        Clip(Uri u,String n,long d,long da,String l,int w,int h){uri=u;name=n;duration=d;date=da;location=l;width=w;height=h;}
    }
}