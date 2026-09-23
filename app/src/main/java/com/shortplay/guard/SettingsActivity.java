package com.shortplay.guard;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.radiobutton.MaterialRadioButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import java.util.*;

public class SettingsActivity extends AppCompatActivity {
    android.content.SharedPreferences prefs;
    LinearLayout root;
    int dp(float v){return (int)(v*getResources().getDisplayMetrics().density+.5f);}
    int bg(){return ThemeUtils.surface(this);}
    int card(){return ThemeUtils.surfaceContainer(this);}
    int fg(){return ThemeUtils.onSurface(this);}
    int muted(){return ThemeUtils.onSurfaceVariant(this);}
    TextView tv(String s,float z){TextView t=new TextView(this);t.setText(s);t.setTextSize(z);t.setTextColor(fg());return t;}
    GradientDrawable round(int c,float r){GradientDrawable g=new GradientDrawable();g.setColor(c);g.setCornerRadius(dp(r));return g;}
    @Override public void onCreate(Bundle b){
        ThemeUtils.applyNightMode(this);
        super.onCreate(b);
        ThemeUtils.applyWindow(this);
        prefs=getSharedPreferences("guard",MODE_PRIVATE);
        build();
    }
    TextView section(String s){
        TextView t=tv(s.toUpperCase(Locale.US),12);t.setTextColor(muted());t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        t.setPadding(dp(6),dp(18),dp(6),dp(8));return t;
    }
    LinearLayout cardLayout(){
        LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(16),dp(10),dp(16),dp(10));c.setBackground(round(card(),20));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,0,0,dp(8));c.setLayoutParams(p);return c;
    }
    TextView desc(String s){TextView t=tv(s,12);t.setTextColor(muted());t.setPadding(0,0,0,dp(7));return t;}
    void build(){
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(14),dp(10),dp(14),dp(18));root.setBackgroundColor(bg());
        LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER_VERTICAL);
        MaterialButton back=new MaterialButton(this);back.setText("Back");back.setTextSize(13);back.setTextColor(fg());back.setAllCaps(false);back.setCornerRadius(dp(16));
        bar.addView(back,new LinearLayout.LayoutParams(dp(52),dp(52)));
        LinearLayout titles=new LinearLayout(this);titles.setOrientation(LinearLayout.VERTICAL);
        TextView h=tv("Settings",25);h.setTypeface(Typeface.DEFAULT,Typeface.BOLD);titles.addView(h);
        titles.addView(desc("Make ShortVid feel exactly the way you want."));bar.addView(titles,new LinearLayout.LayoutParams(0,dp(60),1));
        root.addView(bar);back.setOnClickListener(v->finish());

        ScrollView scroll=new ScrollView(this);LinearLayout content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);

        content.addView(section("Appearance"));
        LinearLayout appearance=cardLayout();
        appearance.addView(tv("Theme",17));appearance.addView(desc("Choose how ShortVid looks."));
        RadioGroup tg=new RadioGroup(this);String[] themes={"Dark","Light","System"};int savedTheme=prefs.getInt("theme",2);
        for(int i=0;i<themes.length;i++){MaterialRadioButton r=new MaterialRadioButton(this);r.setText(themes[i]);r.setTextColor(fg());r.setTextSize(15);r.setPadding(0,dp(4),0,dp(4));r.setTag(i);tg.addView(r);if(i==savedTheme)r.setChecked(true);}
        tg.setOnCheckedChangeListener((g,id)->{View v=g.findViewById(id);if(v!=null){int value=(Integer)v.getTag();prefs.edit().putInt("theme",value).apply();ThemeUtils.applyNightMode(this);}});
        appearance.addView(tg);content.addView(appearance);

        content.addView(section("Gallery"));
        LinearLayout gallery=cardLayout();
        gallery.addView(tv("Grid size",17));gallery.addView(desc("Control how many items appear across the screen."));
        RadioGroup cg=new RadioGroup(this);String[] cols={"Automatic","2 columns","3 columns","4 columns"};int savedCols=prefs.getInt("columns",0);
        for(int i=0;i<cols.length;i++){MaterialRadioButton r=new MaterialRadioButton(this);r.setText(cols[i]);r.setTextColor(fg());r.setTextSize(15);r.setTag(i);cg.addView(r);if(i==savedCols)r.setChecked(true);}
        cg.setOnCheckedChangeListener((g,id)->{View v=g.findViewById(id);if(v!=null)prefs.edit().putInt("columns",(Integer)v.getTag()).apply();});gallery.addView(cg);
        gallery.addView(tv("Sort order",17));gallery.addView(desc("Choose how media is arranged."));
        RadioGroup sg=new RadioGroup(this);String[] sorts={"Newest first","Oldest first","Name"};String sort=prefs.getString("sort","newest");int si=sort.equals("oldest")?1:sort.equals("name")?2:0;
        for(int i=0;i<sorts.length;i++){MaterialRadioButton r=new MaterialRadioButton(this);r.setText(sorts[i]);r.setTextColor(fg());r.setTextSize(15);r.setTag(i);sg.addView(r);if(i==si)r.setChecked(true);}
        sg.setOnCheckedChangeListener((g,id)->{View v=g.findViewById(id);if(v!=null){int i=(Integer)v.getTag();prefs.edit().putString("sort",i==1?"oldest":i==2?"name":"newest").apply();}});
        gallery.addView(sg);content.addView(gallery);

        content.addView(section("Media"));
        LinearLayout media=cardLayout();
        MaterialSwitch photos=new MaterialSwitch(this);photos.setText("Show photos");photos.setTextColor(fg());photos.setTextSize(16);photos.setChecked(prefs.getBoolean("show_photos",true));photos.setPadding(0,dp(5),0,dp(5));media.addView(photos);
        MaterialSwitch videos=new MaterialSwitch(this);videos.setText("Show videos");videos.setTextColor(fg());videos.setTextSize(16);videos.setChecked(prefs.getBoolean("show_videos",true));videos.setPadding(0,dp(5),0,dp(5));media.addView(videos);
        photos.setOnCheckedChangeListener((b,v)->{if(!v&&!videos.isChecked()){photos.setChecked(true);return;}prefs.edit().putBoolean("show_photos",v).apply();});
        videos.setOnCheckedChangeListener((b,v)->{if(!v&&!photos.isChecked()){videos.setChecked(true);return;}prefs.edit().putBoolean("show_videos",v).apply();});
        content.addView(media);

        content.addView(section("Behavior"));
        LinearLayout behavior=cardLayout();
        MaterialSwitch folders=new MaterialSwitch(this);folders.setText("Remember folder view");folders.setTextColor(fg());folders.setTextSize(16);folders.setChecked(prefs.getBoolean("remember_folder_view",false));folders.setPadding(0,dp(5),0,dp(5));behavior.addView(folders);
        folders.setOnCheckedChangeListener((b,v)->prefs.edit().putBoolean("remember_folder_view",v).apply());
        TextView hint=tv("Swipe between media in the viewer. Pinch to zoom photos. Use the ⋮ menu for Edit and Details.",13);hint.setTextColor(muted());hint.setPadding(0,dp(10),0,dp(5));behavior.addView(hint);
        content.addView(behavior);

        content.addView(section("Reset"));
        LinearLayout reset=cardLayout();MaterialButton rb=new MaterialButton(this);rb.setText("Reset gallery settings");rb.setAllCaps(false);rb.setTextSize(15);rb.setCornerRadius(dp(16));reset.addView(rb);
        rb.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("Reset gallery settings?").setMessage("This restores appearance, grid, sorting, and visibility preferences. Your photos and videos are not changed.").setNegativeButton("Cancel",null).setPositiveButton("Reset",(d,w)->{prefs.edit().remove("theme").remove("columns").remove("sort").remove("show_photos").remove("show_videos").remove("remember_folder_view").apply();build();}).show());
        content.addView(reset);

        scroll.setClipToPadding(false);scroll.setPadding(0,0,0,dp(8));scroll.addView(content);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);ThemeUtils.insetRoot(root,dp(14),dp(10),dp(14),dp(18));
    }
}
