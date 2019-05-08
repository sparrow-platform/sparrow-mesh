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
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.connection.ConnectionsClient;

import java.util.ArrayList;
import java.util.Collection;
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

    WifiP2pManager.ActionListener broadcastACtionListner;
    WifiP2pManager.ActionListener discoveryActionLisner;
    WifiP2pManager.ActionListener serviceRequestActionListner;

    WifiP2pDnsSdServiceRequest serviceRequest;

    final HashMap<String, String> wifiNeighbours = new HashMap<String, String>();


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
        mWifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);

        startSparrowWifiService();
        startTimer();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        stoptimertask();
    }

    private void sendMessegeToActivity(String message) {
        Log.i("sender", "Broadcasting message");
        Intent intent = new Intent("payload-received");
        intent.putExtra("message", message);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }


    /**************************************WIFI********************************************/
    private void startSparrowWifiService() {

        /*******************BROADCAST****************/
        Map record = new HashMap();
        record.put("name", "Sparrow");
        record.put("data", "PUT DATA HERE");

        String name = "SPARROW" + getRandomString(10);

        WifiP2pDnsSdServiceInfo serviceInfo =
                WifiP2pDnsSdServiceInfo.newInstance(name, "Communication", record);

        sendMessegeToActivity("My name is " + name);
        manager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        channel = manager.initialize(this, getMainLooper(), null);

        broadcastACtionListner = new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {Log.d(TAG_SPARROW_WIFI_SERVICE, "Added P2P service");}

            @Override
            public void onFailure(int arg0) {Log.d(TAG_SPARROW_WIFI_SERVICE, "Failed to add P2P service");}
        };
        manager.addLocalService(channel, serviceInfo, broadcastACtionListner);


        /*******************BROADCAST****************/


        /*******************DISCOVERY****************/
        WifiP2pManager.DnsSdTxtRecordListener txtListener = (fullDomain, record1, device) -> {
            Log.d(TAG_SPARROW_WIFI_SERVICE, "Received data from Sparrow net" + record1.toString());
            wifiNeighbours.put(device.deviceAddress, record1.get("name").toString());
        };

        WifiP2pManager.DnsSdServiceResponseListener servListener = (instanceName, registrationType, resourceType) -> {
            resourceType.deviceName = wifiNeighbours
                    .containsKey(resourceType.deviceAddress) ? wifiNeighbours
                    .get(resourceType.deviceAddress) : resourceType.deviceName;
            Log.d(TAG_SPARROW_WIFI_SERVICE, "Device name  " + instanceName);
            sendMessegeToActivity(instanceName);

        };

        manager.setDnsSdResponseListeners(channel, servListener, txtListener);

        serviceRequest = WifiP2pDnsSdServiceRequest.newInstance();

        serviceRequestActionListner = new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {Log.d(TAG_SPARROW_WIFI_SERVICE, "Service request Success");}

            @Override
            public void onFailure(int code) {Log.d(TAG_SPARROW_WIFI_SERVICE, "Service request Failuer");}
        };
        manager.addServiceRequest(channel, serviceRequest,serviceRequestActionListner);


        discoveryActionLisner = new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {Log.d(TAG_SPARROW_WIFI_SERVICE, "Discover services  Success");}

            @Override
            public void onFailure(int code) {
                if (code == WifiP2pManager.P2P_UNSUPPORTED) {
                    Log.d(TAG_SPARROW_WIFI_SERVICE, "P2P isn't supported on this device.");
                }
            }
        };
        manager.discoverServices(channel, discoveryActionLisner);
        /*******************DISCOVERY****************/
    }




    /**************************************WIFI********************************************/







    /********************************TIMER********************/

    private Timer timer;
    private TimerTask timerTask;

    public void startTimer() {
        timer = new Timer();
        initializeTimerTask();
        timer.schedule(timerTask, 5000, 15000); //
    }

    public void initializeTimerTask() {
        timerTask = new TimerTask() {
            public void run() {
                Log.i(TAG_SPARROW_WIFI_SERVICE, "in timer ++++  ");
                if(mWifiManager.isWifiEnabled()==false)
                {
                    mWifiManager.setWifiEnabled(true);
                }
                manager.clearLocalServices(channel,broadcastACtionListner);
                manager.clearLocalServices(channel,discoveryActionLisner);
                manager.clearLocalServices(channel,serviceRequestActionListner);

                manager.stopPeerDiscovery(channel, discoveryActionLisner);
                manager.stopPeerDiscovery(channel, broadcastACtionListner);
                manager.stopPeerDiscovery(channel, serviceRequestActionListner);

                manager.clearServiceRequests(channel, broadcastACtionListner);
                manager.clearServiceRequests(channel, discoveryActionLisner);
                manager.clearServiceRequests(channel, serviceRequestActionListner);

                startSparrowWifiService();
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



}





//    private List<WifiP2pDevice> peers = new ArrayList<WifiP2pDevice>();
//
//    private WifiP2pManager.PeerListListener peerListListener  = new WifiP2pManager.PeerListListener() {
//        @Override
//        public void onPeersAvailable(WifiP2pDeviceList peerList) {
//
//            Collection<WifiP2pDevice> refreshedPeers = peerList.getDeviceList();
//            if (!refreshedPeers.equals(peers)) {
//                peers.clear();
//                peers.addAll(refreshedPeers);
//
//                Log.i(TAG_SPARROW_WIFI_SERVICE, "Wifi devices found: " + peers.toString());
//
//            }
//
//            if (peers.size() == 0) {
//                Log.i(TAG_SPARROW_WIFI_SERVICE, "No Wifi devices found");
//                return;
//            }
//        }
//    };
//
//
//    /**********Discover  p2p devices****************/
//    manager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
//    channel = manager.initialize(this, getMainLooper(), null);
//    intentFilter = new IntentFilter();
//        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
//        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
//        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
//        intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);
//
//    receiver = new WiFiDirectBroadcastReceiver(manager, channel, peerListListener);
//    registerReceiver(receiver, intentFilter);
//
//
//        manager.discoverPeers(channel, new WifiP2pManager.ActionListener() {
//        @Override
//        public void onSuccess() {
//        }
//
//        @Override
//        public void onFailure(int reasonCode) {
//        }
//    });
//
//
///**********Discover  p2p devices****************/