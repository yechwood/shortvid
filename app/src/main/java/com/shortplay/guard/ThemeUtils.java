package com.shortplay.guard;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.android.material.color.MaterialColors;

final class ThemeUtils {
    private ThemeUtils() {}

    static void applyNightMode(Activity a) {
        SharedPreferences p=a.getSharedPreferences("guard",Context.MODE_PRIVATE);
        int t=p.getInt("theme",2);
        if(t==1) AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        else if(t==0) AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        else AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
    }

    static void applyWindow(Activity a) {
        WindowCompat.enableEdgeToEdge(a.getWindow());
        if(android.os.Build.VERSION.SDK_INT>=29) a.getWindow().setNavigationBarContrastEnforced(false);
    }

    static void insetRoot(View root, int left, int top, int right, int bottom) {
        ViewCompat.setOnApplyWindowInsetsListener(root,(v,insets)->{
            Insets i=insets.getInsets(WindowInsetsCompat.Type.systemBars()|WindowInsetsCompat.Type.displayCutout());
            v.setPadding(left+i.left,top+i.top,right+i.right,bottom+i.bottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(root);
    }

    static int color(Context c,int attr,int fallback) {
        return MaterialColors.getColor(c,attr,fallback);
    }

    static int surface(Context c){return color(c,com.google.android.material.R.attr.colorSurface,0xFF101318);}
    static int surfaceContainer(Context c){return color(c,com.google.android.material.R.attr.colorSurfaceContainer,0xFF1B1F24);}
    static int onSurface(Context c){return color(c,com.google.android.material.R.attr.colorOnSurface,0xFFFFFFFF);}
    static int onSurfaceVariant(Context c){return color(c,com.google.android.material.R.attr.colorOnSurfaceVariant,0xFFBEC7C4);}
    static int primary(Context c){return color(c,com.google.android.material.R.attr.colorPrimary,0xFF6DE7C5);}
}