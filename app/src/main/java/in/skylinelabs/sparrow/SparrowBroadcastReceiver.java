package in.skylinelabs.sparrow;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

public class SparrowBroadcastReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String action=intent.getStringExtra("action");
        if(action!=null) {
            switch (action) {
                case "restart":
                    Log.i(" Sparrow", "Service Destroyed");
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(new Intent(context, Sparrow.class));
                } else {
                    context.startService(new Intent(context, Sparrow.class));
                }
                    break;
                case "exit":
                    context.stopService(new Intent(context, Sparrow.class));
            }
        }
        
    }
}