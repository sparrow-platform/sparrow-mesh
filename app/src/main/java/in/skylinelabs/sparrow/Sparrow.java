package in.skylinelabs.sparrow;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.connection.AdvertisingOptions;
import com.google.android.gms.nearby.connection.ConnectionInfo;
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback;
import com.google.android.gms.nearby.connection.ConnectionResolution;
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes;
import com.google.android.gms.nearby.connection.DiscoveryOptions;
import com.google.android.gms.nearby.connection.Payload;
import com.google.android.gms.nearby.connection.PayloadCallback;
import com.google.android.gms.nearby.connection.PayloadTransferUpdate;
import com.google.android.gms.nearby.connection.Strategy;

import java.util.Timer;
import java.util.TimerTask;

public class Sparrow extends Service {
    public int counter=0;

    @Override
    public void onCreate() {
        super.onCreate();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            startMyOwnForeground();
        else
            startForeground(1, new Notification());
    }

    private void startMyOwnForeground(){
        String NOTIFICATION_CHANNEL_ID = "com.example.simpleapp";
        String channelName = "My Background Service";
        NotificationChannel chan = new NotificationChannel(NOTIFICATION_CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_NONE);
        chan.setLightColor(Color.BLUE);
        chan.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        assert manager != null;
        manager.createNotificationChannel(chan);

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID);
        Notification notification = notificationBuilder.setOngoing(true)
                .setSmallIcon(R.drawable.sparrow)
                .setContentTitle("Sparrow is keeping you connected")
                .setPriority(NotificationManager.IMPORTANCE_MIN)
                .setCategory(Notification.CATEGORY_SERVICE)
                .build();
        startForeground(2, notification);
    }

    public Sparrow(Context applicationContext) {
        super();
        Log.i(" Sparrow", "Service started");
    }

    public Sparrow() {
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        startTimer();
        initiateNearby();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(" Sparrow", "Service Destroyed");
        Intent broadcastIntent = new Intent(this, SparrowRestartBroadcastReceiver.class);
        sendBroadcast(broadcastIntent);
        stoptimertask();
    }


    /********************************NEARBY********************/
    public void initiateNearby(){
//
//        Nearby.getConnectionsClient(this)
//                .startAdvertising(
//                        /* endpointName= */ "Device A",
//                        /* serviceId= */ "com.example.package_name",
//                        mConnectionLifecycleCallback,
//                        new AdvertisingOptions(Strategy.P2P_CLUSTER));
//
//
//        Nearby.getConnectionsClient(this)
//                .startDiscovery(
//                        /* serviceId= */ "com.example.package_name",
//                        mEndpointDiscoveryCallback,
//                        new DiscoveryOptions(Strategy.P2P_CLUSTER));
//
////        Nearby.getConnectionsClient(this)
////                .requestConnection(
////                        /* endpointName= */ "Device B",
////                        advertiserEndpointId,
////                        mConnectionLifecycleCallback);
////
////        Nearby.getConnectionsClient(this).sendPayload(endpointId, payload);

    }
//
//    private final ConnectionLifecycleCallback mConnectionLifecycleCallback =
//            new ConnectionLifecycleCallback() {
//                @Override
//                public void onConnectionInitiated(String endpointId, ConnectionInfo connectionInfo) {
//                    // Automatically accept the connection on both sides.
//                    Nearby.getConnectionsClient(this).acceptConnection(endpointId, mPayloadCallback);
//                }
//
//                @Override
//                public void onConnectionResult(String endpointId, ConnectionResolution result) {
//                    switch (result.getStatus().getStatusCode()) {
//                        case ConnectionsStatusCodes.STATUS_OK:
//                            // We're connected! Can now start sending and receiving data.
//                            break;
//                        case ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED:
//                            // The connection was rejected by one or both sides.
//                            break;
//                        default:
//                            // The connection was broken before it was accepted.
//                            break;
//                    }
//                }
//
//                @Override
//                public void onDisconnected(String endpointId) {
//                    // We've been disconnected from this endpoint. No more data can be
//                    // sent or received.
//                }
//            };
//
//
//
//    private final PayloadCallback mPayloadCallback =
//            new PayloadCallback() {
//                @Override
//                public void onPayloadReceived(String endpointId, Payload payload) {
//                    // A new payload is being sent over.
//                }
//
//                @Override
//                public void onPayloadTransferUpdate(String endpointId, PayloadTransferUpdate update) {
//                    // Payload progress has updated.
//                }
//            };
//

    /********************************NEARBY********************/






    /********************************TIMER********************/

    private Timer timer;
    private TimerTask timerTask;
    long oldTime=0;
    public void startTimer() {
        timer = new Timer();
        initializeTimerTask();
        timer.schedule(timerTask, 1000, 1000); //
    }
    public void initializeTimerTask() {
        timerTask = new TimerTask() {
            public void run() {
                Log.i("in timer", "in timer ++++  "+ (counter++));
            }
        };
    }
    public void stoptimertask() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
    }
    /********************************TIMER********************/



    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}