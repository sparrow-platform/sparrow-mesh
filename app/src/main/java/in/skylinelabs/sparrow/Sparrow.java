package in.skylinelabs.sparrow;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.net.wifi.WifiManager;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest;
import android.os.Handler;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.connection.ConnectionsClient;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

public class Sparrow extends Service {

    private Context context;
    private ConnectionsClient connectionsClient;

    private String  TAG_SPARROW_WIFI_SERVICE = "SPARROW WIFI SERVICE";

    WifiP2pManager manager;
    WifiP2pManager.Channel channel;
    WifiManager mWifiManager;

    int SERVICE_BROADCASTING_INTERVAL = 5000;
    int SERVICE_DISCOVERING_INTERVAL = 5000;
    WifiP2pDnsSdServiceInfo serviceInfo;
    WifiP2pDnsSdServiceRequest serviceRequest;
    final HashMap<String, String> wifiNeighbours = new HashMap<String, String>();

    final Handler broadcastHandler = new Handler();
    final Handler mServiceDiscoveringHandler = new Handler();

    WifiManager.WifiLock mWifiLock = null;


    WifiP2pManager.ActionListener broadcastPeerDiscoveryActionListner;
    WifiP2pManager.ActionListener broadcastActionListner;

    @Override
    public void onCreate() {
        super.onCreate();
        connectionsClient = Nearby.getConnectionsClient(this);
        context = getApplicationContext();

        startMyOwnForeground();
    }

    public Sparrow() {
        super();
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);

        startSparrowWifiService();

        startTimer();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        stopSparrowWifiService();
        stoptimertask();
        super.onDestroy();
    }

    private void sendMessegeToActivity(String message) throws IOException {
        Log.i("sender", "Broadcasting message");
        Intent intent = new Intent("payload-received");
        intent.putExtra("message", message);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
        Toast toast=Toast.makeText(context,message,Toast.LENGTH_LONG);
        toast.show();

    }


    /**************************************WIFI********************************************/

    private void stopSparrowWifiService(){
        if( mWifiLock == null )
            Log.w(TAG_SPARROW_WIFI_SERVICE, "WifiLock was not created previously");

        if( mWifiLock != null && mWifiLock.isHeld() ){
            mWifiLock.release();
        }

        stopBroadcastingService();
    }

    public void stopBroadcastingService(){
        mWifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        manager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        channel = manager.initialize(this, getMainLooper(), null);

        manager.clearLocalServices(channel, broadcastPeerDiscoveryActionListner);
        manager.stopPeerDiscovery(channel, broadcastPeerDiscoveryActionListner);
        manager.clearServiceRequests(channel, broadcastPeerDiscoveryActionListner);

        manager.clearLocalServices(channel, broadcastActionListner);
        manager.stopPeerDiscovery(channel, broadcastActionListner);
        manager.clearServiceRequests(channel, broadcastActionListner);
    }


    private void startSparrowWifiService() {
        mWifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if( mWifiLock == null )
            mWifiLock = mWifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL, TAG_SPARROW_WIFI_SERVICE);

        mWifiLock.setReferenceCounted(false);

        if( !mWifiLock.isHeld() )
            mWifiLock.acquire();


        manager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        channel = manager.initialize(this, getMainLooper(), null);

        Map record = new HashMap();
        record.put("name", "Sparrow");
        record.put("data", "PUT DATA HERE");

        String name = "SPARROW" + getRandomString(10);

        serviceInfo = WifiP2pDnsSdServiceInfo.newInstance(name, "Communication", record);

        try {
            sendMessegeToActivity("My name is " + name);
        } catch (IOException e) {
            e.printStackTrace();
        }

        startBroadcastingService();
        prepareServiceDiscovery();
        startServiceDiscovery();


    }

    public void startBroadcastingService(){

        stopBroadcastingService();

        broadcastActionListner = new WifiP2pManager.ActionListener() {

            @Override
            public void onSuccess() {
                // service broadcasting started

                broadcastHandler
                        .postDelayed(mServiceBroadcastingRunnable,
                                SERVICE_BROADCASTING_INTERVAL);
            }

            @Override
            public void onFailure(int error) {
                // react to failure of adding the local service
                Log.d(TAG_SPARROW_WIFI_SERVICE, "Failed to clear old WifiP2p");
            }
        };

        manager.addLocalService(channel, serviceInfo,broadcastActionListner);
    }

    private Runnable mServiceBroadcastingRunnable = new Runnable() {
        @Override
        public void run() {
            broadcastPeerDiscoveryActionListner = new WifiP2pManager.ActionListener() {
                @Override
                public void onSuccess() {
                }

                @Override
                public void onFailure(int error) {
                }
            };

            manager.discoverPeers(channel, broadcastPeerDiscoveryActionListner);
            broadcastHandler
                    .postDelayed(mServiceBroadcastingRunnable, SERVICE_BROADCASTING_INTERVAL);
        }
    };

    private Runnable mServiceDiscoveringRunnable = new Runnable() {
        @Override
        public void run() {
            startServiceDiscovery();
        }
    };

    public void prepareServiceDiscovery() {
        manager.setDnsSdResponseListeners(channel,
                (instanceName, registrationType, srcDevice) -> {
                    // do all the things you need to do with detected service
                    srcDevice.deviceName = wifiNeighbours
                            .containsKey(srcDevice.deviceAddress) ? wifiNeighbours
                            .get(srcDevice.deviceAddress) : srcDevice.deviceName;
                    Log.d(TAG_SPARROW_WIFI_SERVICE, "Device name  " + instanceName);
                    try {
                        sendMessegeToActivity(instanceName);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }, (fullDomainName, record, device) -> {
                    Log.d(TAG_SPARROW_WIFI_SERVICE, "Received data from Sparrow net" + record.toString());
                    wifiNeighbours.put(device.deviceAddress, record.get("name").toString());
                    // do all the things you need to do with detailed information about detected service
                });

        serviceRequest = WifiP2pDnsSdServiceRequest.newInstance();
    }

    private void startServiceDiscovery() {
        manager.removeServiceRequest(channel, serviceRequest,
                new WifiP2pManager.ActionListener() {
                    @Override
                    public void onSuccess() {
                        manager.addServiceRequest(channel, serviceRequest,
                                new WifiP2pManager.ActionListener() {

                                    @Override
                                    public void onSuccess() {
                                        manager.discoverServices(channel,
                                                new WifiP2pManager.ActionListener() {

                                                    @Override
                                                    public void onSuccess() {
                                                        //service discovery started

                                                        mServiceDiscoveringHandler.postDelayed(
                                                                mServiceDiscoveringRunnable,
                                                                SERVICE_DISCOVERING_INTERVAL);
                                                    }

                                                    @Override
                                                    public void onFailure(int error) {
                                                        // react to failure of starting service discovery
                                                    }
                                                });
                                    }

                                    @Override
                                    public void onFailure(int error) {
                                        // react to failure of adding service request
                                    }
                                });
                    }

                    @Override
                    public void onFailure(int reason) {
                        // react to failure of removing service request
                    }
                });
    }

    /**************************************WIFI********************************************/







    /********************************TIMER********************/

    private Timer timer;
    private TimerTask timerTask;

    public void startTimer() {
        timer = new Timer();
        initializeTimerTask();
        timer.schedule(timerTask, 5000, 6000); //
    }

    public void initializeTimerTask() {
        timerTask = new TimerTask() {
            public void run() {

                if(mWifiManager.isWifiEnabled()==false)
                {
                    mWifiManager.setWifiEnabled(true);
                }
//                startSparrowWifiService();
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




    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private static final String ALLOWED_CHARACTERS ="0123456789qwertyuiopasdfghjklzxcvbnm";

    private static String getRandomString(final int sizeOfRandomString)
    {
        final Random random=new Random();
        final StringBuilder sb=new StringBuilder(sizeOfRandomString);
        for(int i=0;i<sizeOfRandomString;++i)
            sb.append(ALLOWED_CHARACTERS.charAt(random.nextInt(ALLOWED_CHARACTERS.length())));
        return sb.toString();
    }

    private String getTimestamp(){
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
        String currentDateandTime = sdf.format(new Date());
        return currentDateandTime;
    };


}