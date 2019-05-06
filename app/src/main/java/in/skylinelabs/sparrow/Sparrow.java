package in.skylinelabs.sparrow;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.connection.AdvertisingOptions;
import com.google.android.gms.nearby.connection.ConnectionInfo;
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback;
import com.google.android.gms.nearby.connection.ConnectionResolution;
import com.google.android.gms.nearby.connection.ConnectionsClient;
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo;
import com.google.android.gms.nearby.connection.DiscoveryOptions;
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback;
import com.google.android.gms.nearby.connection.Payload;
import com.google.android.gms.nearby.connection.PayloadCallback;
import com.google.android.gms.nearby.connection.PayloadTransferUpdate;
import com.google.android.gms.nearby.connection.Strategy;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;

import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

public class Sparrow extends Service {
    public int counter=0;
    private Context context;
    private ConnectionsClient connectionsClient;
    private String TAG = "SparrowLog", codeName = "SPARROW";
    private ArrayList<String> activeEndpoints;

    @Override
    public void onCreate() {
        super.onCreate();
        connectionsClient = Nearby.getConnectionsClient(this);
        context = getApplicationContext();
        activeEndpoints = new ArrayList<>();
        startMyOwnForeground();
        startHeartBeatBrodacster(7000);
    }

    private void startMyOwnForeground(){
        Intent intentAction = new Intent(context, SparrowBroadcastReceiver.class);

        intentAction.putExtra("action", "exit");
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, -1, intentAction, PendingIntent.FLAG_UPDATE_CURRENT);
        NotificationCompat.Builder notificationBuilder;
        String NOTIFICATION_CHANNEL_ID = "in.skylinelabs.sparrow";
        String channelName = "Sparrow Background Service";

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationChannel notificationChannel = null;
            notificationChannel = new NotificationChannel(NOTIFICATION_CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_NONE);
            notificationChannel.setLightColor(Color.BLUE);
            notificationChannel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);

            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            assert manager != null;
            manager.createNotificationChannel(notificationChannel);

            notificationBuilder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID);
        }
        else {
            notificationBuilder = new NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID);
        }
        Notification notification = notificationBuilder.setOngoing(true)
                .setSmallIcon(R.drawable.sparrow)
                .setContentTitle("Sparrow is keeping you connected")
                .setPriority(NotificationManager.IMPORTANCE_MIN)
                .setCategory(Notification.CATEGORY_SERVICE)
                .addAction(R.drawable.ic_clear_black_24dp, "Stop", pendingIntent)
                .build();
        startForeground(1, notification);
    }

    public Sparrow() {
        super();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        //startTimer();
        startNearby();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        Log.i(" Sparrow", "Service Destroyed");
        stopNearby();
        /*
        Intent broadcastIntent = new Intent(this, SparrowBroadcastReceiver.class);
        broadcastIntent.putExtra("action","restart");
        sendBroadcast(broadcastIntent);
        */
        //stoptimertask();
    }

    private void sendMessegeToActivity(String message) {
        Log.d("sender", "Broadcasting message");
        Intent intent = new Intent("payload-received");
        // You can also include some extra data.
        intent.putExtra("message", message);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }


    /********************************NEARBY********************/
    private void startNearby(){
        startAdvertising();
        startDiscovery();
    }

    private void stopNearby(){
        connectionsClient.stopAdvertising();
        connectionsClient.stopDiscovery();
        connectionsClient.stopAllEndpoints();
    }



    private void startDiscovery() {
        DiscoveryOptions discoveryOptions =
                new DiscoveryOptions.Builder().setStrategy(Strategy.P2P_STAR).build();
        connectionsClient
                .startDiscovery(getPackageName(), endpointDiscoveryCallback, discoveryOptions)
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        Log.d(TAG,"Discovery success");
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.d(TAG,"Discovery failure");
                    }
                });
    }

    private void startAdvertising() {
        AdvertisingOptions advertisingOptions =
                new AdvertisingOptions.Builder().setStrategy(Strategy.P2P_STAR).build();
        connectionsClient
                .startAdvertising(
                        codeName, getPackageName(), connectionLifecycleCallback, advertisingOptions)
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        Log.d(TAG,"Advertising success");
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.d(TAG,"Advertising Failure");
                    }
                });
    }

    private final PayloadCallback payloadCallback =
            new PayloadCallback() {
                @Override
                public void onPayloadReceived(String endpointId, Payload payload) {
                    if(!activeEndpoints.contains(endpointId))
                        activeEndpoints.add(endpointId);
                    try {
                        String receivedMessage = new String(payload.asBytes(),"UTF-8");
                        sendMessegeToActivity(new String(receivedMessage));
                        if(receivedMessage.contains("SYN"))
                            connectionsClient.sendPayload(endpointId,Payload.fromBytes(("ACK from: "+ Build.MODEL+","+getTimestamp()).getBytes("UTF-8")));
                    } catch (UnsupportedEncodingException e) {
                        e.printStackTrace();
                    }
                    Log.d(TAG,"Payload receivevd from: "+endpointId +" :" +payload.asBytes().toString());
                }

                @Override
                public void onPayloadTransferUpdate(String endpointId, PayloadTransferUpdate update) {
                    Log.d(TAG,"Payload tranfer update");
                }
            };

    private final EndpointDiscoveryCallback endpointDiscoveryCallback =
            new EndpointDiscoveryCallback() {
                @Override
                public void onEndpointFound(String endpointId, DiscoveredEndpointInfo info) {
                    Log.i(TAG, "onEndpointFound: endpoint found, connecting "+endpointId);
                    connectionsClient.requestConnection(codeName, endpointId, connectionLifecycleCallback);
                }

                @Override
                public void onEndpointLost(String endpointId) {
                    Log.i(TAG, "onEndPointLost: End point lost "+endpointId);
                }
            };

    // Callbacks for connections to other devices
    private final ConnectionLifecycleCallback connectionLifecycleCallback =
            new ConnectionLifecycleCallback() {
                @Override
                public void onConnectionInitiated(String endpointId, ConnectionInfo connectionInfo) {
                    Log.i(TAG, "onConnectionInitiated: accepting connection");
                    if(!activeEndpoints.contains(endpointId))
                        connectionsClient.acceptConnection(endpointId, payloadCallback);
                    else
                        connectionsClient.rejectConnection(endpointId);
                }

                @Override
                public void onConnectionResult(final String endpointId, ConnectionResolution result) {
                    if (result.getStatus().isSuccess()) {
                        Log.i(TAG, "onConnectionResult: connection successful");
                        activeEndpoints.add(endpointId);
                        setTimeout(70000);  //Need dynamic values here
                        /*
                        connectionsClient.stopDiscovery();
                        connectionsClient.stopAdvertising();
                        */
                    } else {
                        final Handler handler = new Handler();
                        handler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                connectionsClient.requestConnection(codeName, endpointId, connectionLifecycleCallback);
                            }
                        }, 2000);
                        Log.i(TAG, "onConnectionResult: connection failed " + result.getStatus().getStatusMessage());
                    }
                }

                @Override
                public void onDisconnected(String endpointId) {
                    Log.i(TAG, "onDisconnected: disconnected from the opponent");
                    activeEndpoints.remove(endpointId);
                }
            };



    private void startHeartBeatBrodacster(final int interval) {
        final Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                for(String endpoint: activeEndpoints){
                    try {
                        connectionsClient.sendPayload(endpoint,Payload.fromBytes(("SYN from: "+ Build.MODEL +","+getTimestamp()).getBytes("UTF-8")));
                    } catch (UnsupportedEncodingException e) {
                        e.printStackTrace();
                    }
                }
                handler.postDelayed(this,interval);
            }
        }, interval);

    }

    private void setTimeout(final int interval) {
        final Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                stopNearby();
                startNearby();
                handler.postDelayed(this,interval);
            }
        }, interval);
    }

    private String getTimestamp(){
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
        String currentDateandTime = sdf.format(new Date());
        return currentDateandTime;
    };


    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}