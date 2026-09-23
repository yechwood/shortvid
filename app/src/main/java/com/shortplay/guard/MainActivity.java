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
import androidx.exifinterface.media.ExifInterface;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.core.view.WindowCompat;
import com.yalantis.ucrop.UCrop;
import com.yalantis.ucrop.UCropActivity;
import java.io.*;
import java.util.*;

public class MainActivity extends AppCompatActivity {
    static final long DEFAULT_MAX_MS=300000L;
    static final String DEFAULT_GUARDIAN_EMAIL="kedemwoodlake@gmail.com";
    LinearLayout root,header;
    RecyclerView gallery;
    SharedPreferences prefs;
    ArrayList<MediaItemData> media=new ArrayList<>();
    ArrayList<MediaItemData> visible=new ArrayList<>();
    ArrayList<MediaItemData> selected=new ArrayList<>();
    HashSet<String> folders=new HashSet<>();
    Handler handler=new Handler(Looper.getMainLooper());
    ExoPlayer exoPlayer;
    Dialog viewerDialog;
    PhotoZoomView viewerImage;
    PlayerView viewerPlayer;
    TextView viewerTitle;
    int viewerIndex=-1;
    boolean selectionMode=false;
    boolean refreshAfterSettings=false;
    String currentFolder=null;
    boolean folderMode=false;
    Bitmap editorBitmap;
    PhotoZoomView editorImage;
    MediaItemData editingItem;
    int savedFirst=-1,savedOffset=0;

    @Override public void onCreate(Bundle b){
        ThemeUtils.applyNightMode(this);
        super.onCreate(b);
        ThemeUtils.applyWindow(this);
        prefs=getSharedPreferences("guard",MODE_PRIVATE);
        showHome();
    }
    int dp(float v){return (int)(v*getResources().getDisplayMetrics().density+.5f);}
    TextView text(String s,float z){TextView t=new TextView(this);t.setText(s);t.setTextSize(z);t.setTextColor(themeText());return t;}
    GradientDrawable rounded(int c,float r){GradientDrawable g=new GradientDrawable();g.setColor(c);g.setCornerRadius(dp(r));return g;}
    long getMaxMs(){return prefs.getLong("max_ms",DEFAULT_MAX_MS);}
    int themeBg(){return ThemeUtils.surface(this);}
    int themeCard(){return ThemeUtils.surfaceContainer(this);}
    int themeText(){return ThemeUtils.onSurface(this);}
    int themeMuted(){return ThemeUtils.onSurfaceVariant(this);}

    void showHome(){
        if(prefs.getBoolean("remember_folder_view",false) && currentFolder==null){
            String remembered=prefs.getString("remembered_folder",null);
            if(remembered!=null && !remembered.isEmpty()) currentFolder=remembered;
            folderMode=prefs.getBoolean("remembered_folder_mode",false);
        }
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(12),dp(8),dp(12),dp(8));root.setBackgroundColor(themeBg());
        header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);header.setPadding(0,0,0,dp(6));
        TextView title=text(selectionMode?"Select media":"ShortVid",27);title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);header.addView(title,new LinearLayout.LayoutParams(0,dp(52),1));
        Button foldersBtn=smallButton(folderMode?"All media":"Folders");header.addView(foldersBtn,new LinearLayout.LayoutParams(dp(78),dp(42)));
        Button settings=smallButton("Settings");header.addView(settings,new LinearLayout.LayoutParams(dp(50),dp(42)));
        if(!selectionMode){Button select=smallButton("Select");header.addView(select,new LinearLayout.LayoutParams(dp(76),dp(42)));select.setOnClickListener(v->{selectionMode=true;selected.clear();showHome();});}
        else {Button move=smallButton("Move");header.addView(move,new LinearLayout.LayoutParams(dp(72),dp(42)));move.setOnClickListener(v->moveSelected());Button done=smallButton("Done");header.addView(done,new LinearLayout.LayoutParams(dp(66),dp(42)));done.setOnClickListener(v->{selectionMode=false;selected.clear();showHome();});}
        root.addView(header);
        TextView sub=text(folderMode?(currentFolder==null?"All folders":"Folder: "+currentFolder):(currentFolder==null?"Photos & videos":"Folder: "+currentFolder),13);
        sub.setTextColor(themeMuted());root.addView(sub,new LinearLayout.LayoutParams(-1,dp(28)));
        gallery=new RecyclerView(this);gallery.setClipToPadding(false);gallery.setPadding(0,dp(4),0,dp(16));gallery.setItemViewCacheSize(16);root.addView(gallery,new LinearLayout.LayoutParams(-1,0,1));
        setContentView(root);
        ThemeUtils.insetRoot(root,dp(12),dp(8),dp(12),dp(8));
        loadClips();render();
        foldersBtn.setOnClickListener(v->{folderMode=!folderMode;currentFolder=null;if(prefs.getBoolean("remember_folder_view",false))prefs.edit().putBoolean("remembered_folder_mode",folderMode).remove("remembered_folder").apply();showHome();});
        settings.setOnClickListener(v->openSettings());
    }

    void loadClips(){
        media.clear();folders.clear();
        String[] permissions=Build.VERSION.SDK_INT>=33?new String[]{Manifest.permission.READ_MEDIA_IMAGES,Manifest.permission.READ_MEDIA_VIDEO}:new String[]{Manifest.permission.READ_EXTERNAL_STORAGE};
        boolean ok=true;for(String p:permissions)ok&=checkSelfPermission(p)==PackageManager.PERMISSION_GRANTED;
        if(!ok){requestPermissions(permissions,7);return;}
        Uri vu=MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
        String[] vc={MediaStore.Video.Media._ID,MediaStore.Video.Media.DISPLAY_NAME,MediaStore.Video.Media.DURATION,MediaStore.Video.Media.DATE_MODIFIED,MediaStore.Video.Media.RELATIVE_PATH,MediaStore.Video.Media.WIDTH,MediaStore.Video.Media.HEIGHT};
        try(Cursor c=getContentResolver().query(vu,vc,null,null,MediaStore.Video.Media.DATE_MODIFIED+" DESC")){
            if(c!=null)while(c.moveToNext()){String f=cleanFolder(c.getString(4));MediaItemData x=new MediaItemData(ContentUris.withAppendedId(vu,c.getLong(0)),c.getString(1),true,c.getLong(2),c.getLong(3),f,c.getInt(5),c.getInt(6));media.add(x);folders.add(f);}
        }catch(Exception ignored){}
        Uri iu=MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        String[] ic={MediaStore.Images.Media._ID,MediaStore.Images.Media.DISPLAY_NAME,MediaStore.Images.Media.DATE_MODIFIED,MediaStore.Images.Media.RELATIVE_PATH,MediaStore.Images.Media.WIDTH,MediaStore.Images.Media.HEIGHT};
        try(Cursor c=getContentResolver().query(iu,ic,null,null,MediaStore.Images.Media.DATE_MODIFIED+" DESC")){
            if(c!=null)while(c.moveToNext()){String f=cleanFolder(c.getString(3));MediaItemData x=new MediaItemData(ContentUris.withAppendedId(iu,c.getLong(0)),c.getString(1),false,0,c.getLong(2),f,c.getInt(4),c.getInt(5));media.add(x);folders.add(f);}
        }catch(Exception ignored){}
        sortMedia();
    }
    String cleanFolder(String s){if(s==null||s.trim().isEmpty())return "Other";s=s.replace("\\\\","/");while(s.endsWith("/"))s=s.substring(0,s.length()-1);return s;}
    void sortMedia(){
        String s=prefs.getString("sort","newest");
        Collections.sort(media,(a,b)->s.equals("name")?a.name.compareToIgnoreCase(b.name):s.equals("oldest")?Long.compare(a.date,b.date):Long.compare(b.date,a.date));
    }
    void buildVisible(){
        visible.clear();
        boolean showVideos=prefs.getBoolean("show_videos",true), showPhotos=prefs.getBoolean("show_photos",true);
        if(currentFolder==null) for(MediaItemData x:media) if((x.video&&showVideos)||(!x.video&&showPhotos)) visible.add(x);
        else for(MediaItemData y:media) if(y.folder.equals(currentFolder)&&((y.video&&showVideos)||(!y.video&&showPhotos))) visible.add(y);
    }
    void render(){
        buildVisible();
        if(folderMode){gallery.setLayoutManager(new GridLayoutManager(this,1));gallery.setAdapter(new FolderAdapter(new ArrayList<>(folders)));return;}
        if(visible.isEmpty()){gallery.setLayoutManager(new GridLayoutManager(this,1));gallery.setAdapter(new EmptyAdapter());return;}
        int w=getResources().getConfiguration().screenWidthDp;int c=prefs.getInt("columns",0);if(c==0)c=w>=600?4:(w<=360?2:3);gallery.setLayoutManager(new GridLayoutManager(this,c));gallery.setAdapter(new GalleryAdapter());
    }
    class EmptyAdapter extends RecyclerView.Adapter<EmptyAdapter.VH>{class VH extends RecyclerView.ViewHolder{VH(View v){super(v);}}public VH onCreateViewHolder(ViewGroup p,int t){LinearLayout box=new LinearLayout(MainActivity.this);box.setOrientation(LinearLayout.VERTICAL);box.setGravity(Gravity.CENTER);TextView icon=text("✦",38);icon.setGravity(Gravity.CENTER);TextView title=text("No media yet",20);title.setGravity(Gravity.CENTER);TextView sub=text("Photos and videos from your device will appear here.",13);sub.setTextColor(themeMuted());sub.setGravity(Gravity.CENTER);box.addView(icon);box.addView(title);box.addView(sub);return new VH(box);}public void onBindViewHolder(VH h,int p){}public int getItemCount(){return 1;}}
    class FolderAdapter extends RecyclerView.Adapter<FolderAdapter.VH>{
        ArrayList<String> list;FolderAdapter(ArrayList<String> l){list=l;Collections.sort(list,String.CASE_INSENSITIVE_ORDER);}
        class VH extends RecyclerView.ViewHolder{TextView title,count;VH(View v,TextView t,TextView c){super(v);title=t;count=c;}}
        public VH onCreateViewHolder(ViewGroup p,int t){LinearLayout row=new LinearLayout(MainActivity.this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(18),dp(8),dp(18),dp(8));row.setBackground(rounded(themeCard(),18));TextView a=text("▣",25);row.addView(a,new LinearLayout.LayoutParams(dp(48),dp(62)));TextView n=text("",18);n.setTextColor(themeText());TextView c=text("",13);c.setTextColor(Color.GRAY);LinearLayout col=new LinearLayout(MainActivity.this);col.setOrientation(LinearLayout.VERTICAL);col.addView(n);col.addView(c);row.addView(col,new LinearLayout.LayoutParams(0,dp(62),1));RecyclerView.LayoutParams rp=new RecyclerView.LayoutParams(-1,dp(82));rp.setMargins(dp(3),dp(4),dp(3),dp(4));row.setLayoutParams(rp);return new VH(row,n,c);}
        public void onBindViewHolder(VH h,int p){String f=list.get(p);int n=0;for(MediaItemData x:media)if(x.folder.equals(f))n++;h.title.setText(f);h.count.setText(n+" item"+(n==1?"":"s"));h.itemView.setOnClickListener(v->{folderMode=false;currentFolder=f;if(prefs.getBoolean("remember_folder_view",false))prefs.edit().putString("remembered_folder",f).putBoolean("remembered_folder_mode",false).apply();showHome();});}
        public int getItemCount(){return list.size();}
    }
    class GalleryAdapter extends RecyclerView.Adapter<GalleryAdapter.VH>{
        class VH extends RecyclerView.ViewHolder{ImageView thumb;TextView name,badge;VH(View v,ImageView i,TextView n,TextView b){super(v);thumb=i;name=n;badge=b;}}
        public VH onCreateViewHolder(ViewGroup p,int t){int w=getResources().getConfiguration().screenWidthDp;int h=w<=360?194:158;LinearLayout card=new LinearLayout(MainActivity.this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(3,3,3,5);card.setBackground(rounded(themeCard(),14));FrameLayout fr=new FrameLayout(MainActivity.this);ImageView im=new ImageView(MainActivity.this);im.setScaleType(ImageView.ScaleType.CENTER_CROP);fr.addView(im,new FrameLayout.LayoutParams(-1,dp(112)));TextView b=text("",10);b.setGravity(Gravity.CENTER);FrameLayout.LayoutParams bp=new FrameLayout.LayoutParams(dp(58),dp(23),Gravity.BOTTOM|Gravity.END);bp.setMargins(0,0,dp(5),dp(5));fr.addView(b,bp);card.addView(fr);TextView n=text("",11);n.setSingleLine(true);n.setEllipsize(android.text.TextUtils.TruncateAt.END);n.setTextColor(themeText());n.setPadding(5,3,5,0);card.addView(n,new LinearLayout.LayoutParams(-1,dp(27)));RecyclerView.LayoutParams rp=new RecyclerView.LayoutParams(-1,dp(h));rp.setMargins(3,3,3,5);card.setLayoutParams(rp);return new VH(card,im,n,b);}
        public void onBindViewHolder(VH h,int p){MediaItemData x=visible.get(p);h.name.setText(x.name);h.badge.setText(x.video?formatDuration(x.duration):"PHOTO");h.badge.setBackground(rounded(Color.argb(205,0,0,0),9));h.itemView.setAlpha(selectionMode&&selected.contains(x)?0.55f:1f);h.thumb.setTag(x.uri);loadThumbnail(x,h.thumb);h.itemView.setOnClickListener(v->{if(selectionMode){if(selected.contains(x))selected.remove(x);else selected.add(x);h.itemView.setAlpha(selected.contains(x)?0.55f:1f);}else openViewer(media.indexOf(x));});h.itemView.setOnLongClickListener(v->{if(!selectionMode){selectionMode=true;selected.clear();selected.add(x);showHome();}return true;});}
        public int getItemCount(){return visible.size();}
    }
    void loadThumbnail(MediaItemData x,ImageView target){Uri u=x.uri;new Thread(()->{Bitmap b=null;try{if(Build.VERSION.SDK_INT>=29)b=getContentResolver().loadThumbnail(u,new android.util.Size(dp(360),dp(300)),null);else if(x.video){MediaMetadataRetriever r=new MediaMetadataRetriever();r.setDataSource(this,u);b=r.getFrameAtTime(0,MediaMetadataRetriever.OPTION_CLOSEST_SYNC);r.release();}else{try(InputStream in=getContentResolver().openInputStream(u)){b=BitmapFactory.decodeStream(in);}}}catch(Exception ignored){}Bitmap z=b;runOnUiThread(()->{if(z!=null&&u.equals(target.getTag()))target.setImageBitmap(z);});}).start();}

    void openViewer(int index){
        savedPosition();
        viewerIndex=index;viewerDialog=new Dialog(this,R.style.ViewerTheme);
        FrameLayout box=new FrameLayout(this);box.setBackgroundColor(Color.BLACK);
        viewerImage=new PhotoZoomView(this);viewerImage.setScaleType(ImageView.ScaleType.FIT_CENTER);viewerImage.setMinimumScale(1f);viewerImage.setMediumScale(2.5f);viewerImage.setMaximumScale(6f);viewerImage.setZoomable(true);
        viewerImage.setSwipeCallback(dx->{if(viewerImage.getScale()>1.05f)return;if(Math.abs(dx)>dp(70)){int n=dx<0?viewerIndex+1:viewerIndex-1;if(n>=0&&n<media.size())animateViewerTo(n,dx<0);}});
        box.addView(viewerImage,new FrameLayout.LayoutParams(-1,-1));
        viewerPlayer=new PlayerView(this);viewerPlayer.setUseController(true);viewerPlayer.setControllerAutoShow(true);viewerPlayer.setControllerHideOnTouch(true);viewerPlayer.setControllerShowTimeoutMs(2500);viewerPlayer.setResizeMode(androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT);viewerPlayer.setVisibility(View.GONE);box.addView(viewerPlayer,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);top.setPadding(dp(8),dp(18),dp(8),0);top.setBackgroundColor(Color.argb(120,0,0,0));
        Button back=smallButton("‹");viewerTitle=text("",13);viewerTitle.setGravity(Gravity.CENTER);viewerTitle.setSingleLine(true);Button more=smallButton("⋮");top.addView(back,new LinearLayout.LayoutParams(dp(52),dp(48)));top.addView(viewerTitle,new LinearLayout.LayoutParams(0,dp(48),1));top.addView(more,new LinearLayout.LayoutParams(dp(52),dp(42)));box.addView(top,new FrameLayout.LayoutParams(-1,dp(74),Gravity.TOP));
        back.setOnClickListener(v->viewerDialog.dismiss());more.setOnClickListener(v->{if(viewerIndex>=0)showMediaMenu(media.get(viewerIndex));});
        viewerDialog.setContentView(box);viewerDialog.setOnDismissListener(v->{releaseViewerPlayer();restorePosition();});viewerDialog.setOnShowListener(v->{immersive(viewerDialog);});viewerDialog.show();immersive(viewerDialog);showViewerItem(index);
    }
    void immersive(Dialog d){ if(d==null||d.getWindow()==null)return; WindowCompat.enableEdgeToEdge(d.getWindow()); WindowInsetsControllerCompat ctl=WindowCompat.getInsetsController(d.getWindow(),d.getWindow().getDecorView()); ctl.hide(WindowInsetsCompat.Type.systemBars()); ctl.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE); }
    void animateViewerTo(int n,boolean forward){if(viewerIndex<0)return;final float w=Math.max(1,viewerImage.getWidth());viewerImage.animate().translationX(forward?-w:w).setDuration(140).withEndAction(()->{viewerImage.setTranslationX(forward?w:-w);showViewerItem(n);viewerImage.animate().translationX(0).setDuration(190).setInterpolator(new android.view.animation.DecelerateInterpolator()).start();}).start();}
    void showViewerItem(int i){if(viewerDialog==null||!viewerDialog.isShowing()||i<0||i>=media.size())return;viewerIndex=i;MediaItemData x=media.get(i);viewerTitle.setText(x.name);releaseViewerPlayer();viewerImage.setScale(1f,false);viewerImage.setTranslationX(0);viewerImage.setVisibility(x.video?View.GONE:View.VISIBLE);viewerPlayer.setVisibility(x.video?View.VISIBLE:View.GONE);if(x.video)startViewerVideo(x);else{viewerImage.setImageDrawable(null);new Thread(()->{try{Bitmap b=loadFullBitmap(x.uri);runOnUiThread(()->{if(viewerIndex==i)viewerImage.setImageBitmap(b);});}catch(Exception ignored){}}).start();}}
    void startViewerVideo(MediaItemData x){long limit=getMaxMs();if(x.duration<=0||x.duration>limit){toast("This video is over the "+format(limit)+" limit.");return;}new Thread(()->{VideoScreeningEngine.Result r=VideoScreeningEngine.screen(this,x.uri,x.duration,x.location,x.name,x.date,x.width,x.height,limit);runOnUiThread(()->{if(!r.allowed){toast(r.reason);return;}exoPlayer=new ExoPlayer.Builder(this).build();viewerPlayer.setPlayer(exoPlayer);exoPlayer.setMediaItem(MediaItem.fromUri(x.uri));exoPlayer.prepare();exoPlayer.play();});}).start();}
    void releaseViewerPlayer(){if(exoPlayer!=null){exoPlayer.release();exoPlayer=null;}if(viewerPlayer!=null)viewerPlayer.setPlayer(null);}
    Bitmap loadFullBitmap(Uri u)throws Exception{try(InputStream in=getContentResolver().openInputStream(u)){Bitmap b=BitmapFactory.decodeStream(in);if(b==null)throw new Exception();return b;}}
    void savedPosition(){if(gallery==null)return;RecyclerView.LayoutManager lm=gallery.getLayoutManager();if(lm instanceof GridLayoutManager){savedFirst=((GridLayoutManager)lm).findFirstVisibleItemPosition();View v=gallery.getChildAt(0);savedOffset=v==null?0:v.getTop();}}
    void restorePosition(){if(gallery==null||savedFirst<0)return;gallery.post(()->{if(gallery.getLayoutManager() instanceof GridLayoutManager)((GridLayoutManager)gallery.getLayoutManager()).scrollToPositionWithOffset(savedFirst,savedOffset);});}

    void openEditor(MediaItemData item){editingItem=item;new Thread(()->{try{editorBitmap=loadFullBitmap(item.uri);runOnUiThread(this::showEditor);}catch(Exception e){runOnUiThread(()->toast("Couldn't load this picture."));}}).start();}
    void showEditor(){
        Dialog d=new Dialog(this,android.R.style.Theme_Material_NoActionBar_Fullscreen);FrameLayout box=new FrameLayout(this);box.setBackgroundColor(Color.BLACK);
        editorImage=new PhotoZoomView(this);editorImage.setScaleType(ImageView.ScaleType.FIT_CENTER);editorImage.setMinimumScale(1f);editorImage.setMediumScale(2.5f);editorImage.setMaximumScale(6f);editorImage.setZoomable(true);editorImage.setImageBitmap(editorBitmap);box.addView(editorImage,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER);bar.setPadding(6,6,6,12);Button crop=smallButton("Crop"),rotate=smallButton("Rotate"),save=smallButton("Save crop"),cancel=smallButton("Cancel");bar.addView(crop,new LinearLayout.LayoutParams(0,dp(54),1));bar.addView(rotate,new LinearLayout.LayoutParams(0,dp(54),1));bar.addView(save,new LinearLayout.LayoutParams(0,dp(54),1));bar.addView(cancel,new LinearLayout.LayoutParams(0,dp(54),1));box.addView(bar,new FrameLayout.LayoutParams(-1,dp(76),Gravity.BOTTOM));
        crop.setOnClickListener(v->startCrop());rotate.setOnClickListener(v->{Matrix m=new Matrix();m.postRotate(90);editorBitmap=Bitmap.createBitmap(editorBitmap,0,0,editorBitmap.getWidth(),editorBitmap.getHeight(),m,true);editorImage.setImageBitmap(editorBitmap);editorImage.setScale(1f,false);});save.setOnClickListener(v->{saveEditedPhoto(editingItem,editorBitmap);d.dismiss();});cancel.setOnClickListener(v->d.dismiss());d.setContentView(box);d.show();
    }
    void startCrop(){
        try{
            File src=new File(getCacheDir(),"crop_source.jpg"),dst=new File(getCacheDir(),"crop_result_"+System.currentTimeMillis()+".jpg");
            try(FileOutputStream out=new FileOutputStream(src)){editorBitmap.compress(Bitmap.CompressFormat.JPEG,100,out);}
            UCrop.Options o=new UCrop.Options();o.setCompressionQuality(98);o.setCompressionFormat(Bitmap.CompressFormat.JPEG);o.setFreeStyleCropEnabled(true);o.setShowCropGrid(true);o.setShowCropFrame(true);o.setMaxScaleMultiplier(10f);o.setAllowedGestures(UCropActivity.ALL,UCropActivity.ALL,UCropActivity.ALL);o.setToolbarTitle("Adjust & crop");o.setToolbarColor(Color.rgb(12,16,21));o.setToolbarWidgetColor(Color.WHITE);o.setStatusBarColor(Color.rgb(7,13,20));o.setActiveControlsWidgetColor(Color.rgb(132,245,212));
            UCrop.of(Uri.fromFile(src),Uri.fromFile(dst)).withOptions(o).withMaxResultSize(8192,8192).start(this);
        }catch(Exception e){toast("Couldn't open the crop editor: "+e.getMessage());}
    }
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){super.onActivityResult(requestCode,resultCode,data);if(requestCode==UCrop.REQUEST_CROP){if(resultCode==RESULT_OK){try{Uri u=UCrop.getOutput(data);editorBitmap=loadFullBitmap(u);if(editorImage!=null){editorImage.setImageBitmap(editorBitmap);editorImage.setScale(1f,false);}toast("Crop applied. Tap “Save crop” to keep it.");}catch(Exception e){toast("Couldn't apply the crop.");}}else if(resultCode==UCrop.RESULT_ERROR){Throwable e=UCrop.getError(data);toast("Crop failed"+(e==null?"":" : "+e.getMessage()));}}}
    void showMediaMenu(MediaItemData x){
        LinearLayout box=new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18),dp(8),dp(18),dp(8));
        TextView title=text(x.name,19); title.setTextColor(themeText()); title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        box.addView(title,new LinearLayout.LayoutParams(-1,dp(48)));
        MaterialButton edit=null;
        if(!x.video){
            edit=new MaterialButton(this); edit.setText("Edit photo"); edit.setAllCaps(false); edit.setTextSize(15); edit.setCornerRadius(dp(14));
            box.addView(edit,new LinearLayout.LayoutParams(-1,dp(52)));
        }
        MaterialButton details=new MaterialButton(this); details.setText("Details"); details.setAllCaps(false); details.setTextSize(15); details.setCornerRadius(dp(14));
        box.addView(details,new LinearLayout.LayoutParams(-1,dp(52)));
        final Dialog menu=new AlertDialog.Builder(this).setView(box).create();
        details.setOnClickListener(v->{menu.dismiss();showMediaDetails(x);});
        if(edit!=null) edit.setOnClickListener(v->{menu.dismiss();openEditor(x);});
        menu.show();
    }

    void showMediaDetails(MediaItemData x){
        if(x==null)return;
        if(Build.VERSION.SDK_INT>=29 && !x.video && checkSelfPermission(Manifest.permission.ACCESS_MEDIA_LOCATION)!=PackageManager.PERMISSION_GRANTED){
            pendingDetails=x;
            requestPermissions(new String[]{Manifest.permission.ACCESS_MEDIA_LOCATION},19);
            return;
        }
        showMediaDetailsNow(x);
    }
    MediaItemData pendingDetails;
    void showMediaDetailsNow(MediaItemData x){
        final LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(22),dp(10),dp(22),dp(6));
        TextView title=text("Details",22);title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);title.setTextColor(themeText());box.addView(title);
        TextView info=text("",14);info.setTextColor(themeText());info.setPadding(0,dp(12),0,dp(4));box.addView(info);
        new Thread(()->{
            String location="Not available";
            String date=new java.text.SimpleDateFormat("MMM d, yyyy • h:mm a",Locale.US).format(new Date(x.date*1000L));
            long size=0;
            try{android.database.Cursor q=getContentResolver().query(x.uri,new String[]{MediaStore.MediaColumns.SIZE},null,null,null);if(q!=null){if(q.moveToFirst())size=q.getLong(0);q.close();}}catch(Exception ignored){}
            if(x.video){
                try{MediaMetadataRetriever r=new MediaMetadataRetriever();r.setDataSource(this,x.uri);String l=r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_LOCATION);if(l!=null&&!l.isEmpty())location=l;r.release();}catch(Exception ignored){}
            }else{
                try{Uri u=x.uri;if(Build.VERSION.SDK_INT>=29)u=MediaStore.setRequireOriginal(u);try(InputStream in=getContentResolver().openInputStream(u)){if(in!=null){ExifInterface e=new ExifInterface(in);double[] ll=e.getLatLong();if(ll!=null)location=String.format(Locale.US,"%.6f, %.6f",ll[0],ll[1]);}}}catch(Exception ignored){}
            }
            final String loc=location;final long bytes=size;
            runOnUiThread(()->info.setText("Name\n"+x.name+"\n\nFolder\n"+x.folder+"\n\nDimensions\n"+x.width+" × "+x.height+(x.video?"\n\nDuration\n"+formatDuration(x.duration):"")+"\n\nSize\n"+formatBytes(bytes)+"\n\nDate\n"+date+"\n\nLocation\n"+loc));
        }).start();
        new AlertDialog.Builder(this).setView(box).setPositiveButton("Done",null).show();
    }
    String formatBytes(long b){if(b<=0)return "Unknown";if(b<1024*1024)return (b/1024)+" KB";if(b<1024*1024*1024)return String.format(Locale.US,"%.1f MB",b/1048576d);return String.format(Locale.US,"%.2f GB",b/1073741824d);}
    void saveEditedPhoto(MediaItemData original,Bitmap b){new Thread(()->{try{String n="ShortVid_"+System.currentTimeMillis()+"_"+original.name.replaceAll("[^a-zA-Z0-9._-]","_");ContentValues cv=new ContentValues();cv.put(MediaStore.Images.Media.DISPLAY_NAME,n);cv.put(MediaStore.Images.Media.MIME_TYPE,"image/jpeg");if(Build.VERSION.SDK_INT>=29)cv.put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/ShortVid");Uri out=getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,cv);if(out==null)throw new Exception();try(OutputStream os=getContentResolver().openOutputStream(out)){b.compress(Bitmap.CompressFormat.JPEG,96,os);}runOnUiThread(()->{toast("Saved to Pictures/ShortVid.");loadClips();render();});}catch(Exception e){runOnUiThread(()->toast("Couldn't save the edited picture."));}}).start();}

    void moveSelected(){
        if(selected.isEmpty()){toast("Select at least one item.");return;}
        ArrayList<String> list=new ArrayList<>(folders);Collections.sort(list,String.CASE_INSENSITIVE_ORDER);
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(18),dp(10),dp(18),dp(10));TextView title=text("Move "+selected.size()+" item"+(selected.size()==1?"":"s")+" to…",19);title.setTextColor(themeText());box.addView(title);
        for(String f:list){Button b=new Button(this);b.setText(f);b.setAllCaps(false);box.addView(b);b.setOnClickListener(v->{moveToFolder(f);});}
        Button newF=new Button(this);newF.setText("+ New folder");newF.setAllCaps(false);box.addView(newF);newF.setOnClickListener(v->askNewFolder());
        new AlertDialog.Builder(this).setView(box).setNegativeButton("Cancel",null).show();
    }
    void askNewFolder(){EditText e=new EditText(this);e.setHint("Folder name, e.g. Vacation");new AlertDialog.Builder(this).setTitle("New folder").setView(e).setNegativeButton("Cancel",null).setPositiveButton("Move", (d,w)->{String n=e.getText().toString().trim();if(!n.isEmpty())moveToFolder("Pictures/"+n);}).show();}
    void moveToFolder(String folder){
        final String target=folder.endsWith("/")?folder:folder+"/";
        final ArrayList<MediaItemData> moving=new ArrayList<>(selected);
        new Thread(()->{
            int ok=0;
            for(MediaItemData x:moving){
                try{
                    String t=target;
                    if(x.video && t.startsWith("Pictures/")) t="Movies/"+t.substring(9);
                    if(!x.video && t.startsWith("Movies/")) t="Pictures/"+t.substring(7);
                    ContentValues cv=new ContentValues();
                    cv.put(MediaStore.MediaColumns.RELATIVE_PATH,t);
                    if(getContentResolver().update(x.uri,cv,null,null)>0) ok++;
                }catch(Exception ignored){}
            }
            final int moved=ok;
            runOnUiThread(()->{
                toast("Moved "+moved+" item"+(moved==1?"":"s")+" to "+target);
                selectionMode=false;selected.clear();currentFolder=null;folderMode=false;if(prefs.getBoolean("remember_folder_view",false))prefs.edit().remove("remembered_folder").putBoolean("remembered_folder_mode",false).apply();showHome();
            });
        }).start();
    }

    void openSettings(){ refreshAfterSettings=true; startActivity(new Intent(this,SettingsActivity.class)); }
    @Override protected void onResume(){ super.onResume(); if(refreshAfterSettings && prefs!=null){ refreshAfterSettings=false; showHome(); } }
    TextView label(String s){TextView t=text(s,12);t.setTextColor(Color.GRAY);t.setPadding(0,dp(10),0,0);return t;}
    void applyFilters(){sortMedia();}
    Button smallButton(String s){MaterialButton b=new MaterialButton(this);b.setText(s);b.setTextSize(12);b.setAllCaps(false);b.setMinHeight(dp(44));b.setMinimumHeight(dp(44));b.setInsetTop(0);b.setInsetBottom(0);b.setCornerRadius(dp(16));b.setContentPadding(dp(10),0,dp(10),0);b.setStateListAnimator(null);b.setContentDescription(s);return b;}
    String format(long ms){long sec=Math.max(0,ms/1000),m=sec/60,h=m/60;return h>0?h+"h "+m%60+"m":m+"m";}
    String formatDuration(long ms){long sec=Math.max(0,ms/1000);return String.format(Locale.US,"%d:%02d",(sec/60)%60,sec%60);}
    void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    @Override public void onRequestPermissionsResult(int r,String[] p,int[] g){super.onRequestPermissionsResult(r,p,g);if(r==7)showHome();if(r==19&&pendingDetails!=null){MediaItemData x=pendingDetails;pendingDetails=null;if(g.length>0&&g[0]==PackageManager.PERMISSION_GRANTED)showMediaDetailsNow(x);else showMediaDetailsNow(x);}}

    static class MediaItemData{Uri uri;String name,folder,location;boolean video;long duration,date;int width,height;MediaItemData(Uri u,String n,boolean v,long d,long da,String f,int w,int h){uri=u;name=n;video=v;duration=d;date=da;folder=f;location=f;width=w;height=h;}}
    static class PhotoZoomView extends com.github.chrisbanes.photoview.PhotoView{
        float sx;boolean tracking;SwipeCallback callback;PhotoZoomView(Context c){super(c);}
        void setSwipeCallback(SwipeCallback c){callback=c;}
        @Override public boolean dispatchTouchEvent(MotionEvent e){if(e.getActionMasked()==MotionEvent.ACTION_DOWN){sx=e.getX();tracking=true;}else if(e.getActionMasked()==MotionEvent.ACTION_UP&&tracking){tracking=false;float dx=e.getX()-sx;if(callback!=null&&getScale()<=1.05f&&Math.abs(dx)>40)callback.onSwipe(dx);}else if(e.getActionMasked()==MotionEvent.ACTION_CANCEL)tracking=false;return super.dispatchTouchEvent(e);}
    }
    interface SwipeCallback{void onSwipe(float dx);}
}