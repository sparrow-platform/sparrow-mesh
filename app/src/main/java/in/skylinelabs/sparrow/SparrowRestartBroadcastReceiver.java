package in.skylinelabs.sparrow;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class SparrowRestartBroadcastReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.i(SparrowRestartBroadcastReceiver.class.getSimpleName(), "Service Destroyed");
        context.startService(new Intent(context, Sparrow.class));;
    }
}