package com.shortplay.guard;
import android.content.*;
/** Android may deliver this only to the current/default dialer on newer versions. */
public class SecretCodeReceiver extends BroadcastReceiver { public void onReceive(Context c,Intent i){ Intent launch=new Intent(c,AdminActivity.class);launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);c.startActivity(launch); } }
