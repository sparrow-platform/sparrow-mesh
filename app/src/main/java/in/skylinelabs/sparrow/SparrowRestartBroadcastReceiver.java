package in.skylinelabs.sparrow;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

public class SparrowRestartBroadcastReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.i(" Sparrow", "Service Destroyed");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(new Intent(context, Sparrow.class));
        } else {
            context.startService(new Intent(context, Sparrow.class));
        }
    }
}