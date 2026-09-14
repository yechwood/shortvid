package com.shortplay.guard;
import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.*;
import android.content.Intent;
import java.util.*;
/** Text-signal guard: Android accessibility APIs cannot lawfully or reliably classify raw pixels. */
public class ScreenGuardService extends AccessibilityService {
 private static final String[] TERMS={"porn","xxx","onlyfans","adult video","explicit nudity","nsfw","sex video"};
 public void onAccessibilityEvent(AccessibilityEvent e){AccessibilityNodeInfo n=getRootInActiveWindow();if(n==null)return;String s=flatten(n).toLowerCase(Locale.US);for(String t:TERMS)if(s.contains(t)){sendBroadcast(new Intent(MainActivity.ACTION_BLOCK).setPackage(getPackageName()));return;}}
 private String flatten(AccessibilityNodeInfo n){StringBuilder b=new StringBuilder();if(n.getText()!=null)b.append(n.getText()).append(' ');if(n.getContentDescription()!=null)b.append(n.getContentDescription()).append(' ');for(int i=0;i<n.getChildCount();i++){AccessibilityNodeInfo c=n.getChild(i);if(c!=null)b.append(flatten(c));}return b.toString();}
 public void onInterrupt(){}
}
