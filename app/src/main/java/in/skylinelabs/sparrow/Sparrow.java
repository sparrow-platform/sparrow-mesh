package in.skylinelabs.sparrow;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.connection.AdvertisingOptions;
import com.google.android.gms.nearby.connection.ConnectionInfo;
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback;
import com.google.android.gms.nearby.connection.ConnectionResolution;
import com.google.android.gms.nearby.connection.ConnectionsClient;
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes;
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
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class Sparrow extends Service {

    private Context context;
    private ConnectionsClient connectionsClient;
    private String TAG = "SparrowLog", codeName = "SPARROW";
    private ArrayList<String> activeEndpoints;

    private String  TAG_SPARROW_WIFI_SERVICE = "SPARROW WIFI SERVICE";


    WifiManager mWifiManager;



    @Override
    public void onCreate() {
        super.onCreate();
        connectionsClient = Nearby.getConnectionsClient(this);
        context = getApplicationContext();
        mWifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);

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
        super.onDestroy();
        stopSparrowWifiService();
        stoptimertask();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            turnOffHotspot();
        }
    }

    private void sendMessegeToActivity(String message) {
        Log.d("sender", "Broadcasting message");
        Intent intent = new Intent("payload-received");
        intent.putExtra("message", message);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }


    /**************************************WIFI********************************************/
    private final BroadcastReceiver mWifiScanReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent intent) {
            if (intent.getAction().equals(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)) {
                List<ScanResult> mScanResults = mWifiManager.getScanResults();
                // add your logic here
                for(ScanResult wifi : mScanResults){
                    Log.i(TAG_SPARROW_WIFI_SERVICE, wifi.SSID);
                }

            }
        }
    };

    private void startSparrowWifiService() {
        if(mWifiManager.isWifiEnabled()==false)
        {
            mWifiManager.setWifiEnabled(true);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            turnOnHotspot();
        }

        Log.i(TAG_SPARROW_WIFI_SERVICE, "Attempting to start hotspot");

        WifiConfiguration wifiConfiguration = new WifiConfiguration();
        wifiConfiguration.SSID = "SparrowNet Node";
        Method method;
        try {
            method = mWifiManager.getClass().getDeclaredMethod("setWifiApEnabled", WifiConfiguration.class, Boolean.TYPE);
            method.setAccessible(true);
            method.invoke(mWifiManager, wifiConfiguration, true);
        } catch (Exception e) {
            e.printStackTrace();
            Log.i(TAG_SPARROW_WIFI_SERVICE, "Error turning on hotspot");
        }

    }

    private void stopSparrowWifiService() {

    }


    private void getFromWifi() {
        if(mWifiManager.isWifiEnabled()==false)
        {
            mWifiManager.setWifiEnabled(true);
        }
        registerReceiver(mWifiScanReceiver,
                new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
        mWifiManager.startScan();
    }

    /**************************************WIFI********************************************/







    /********************************TIMER********************/

    private Timer timer;
    private TimerTask timerTask;

    public void startTimer() {
        timer = new Timer();
        initializeTimerTask();
        timer.schedule(timerTask, 1000, 10000); //
    }

    public void initializeTimerTask() {
        timerTask = new TimerTask() {
            public void run() {
                Log.i(TAG_SPARROW_WIFI_SERVICE, "in timer ++++  ");
                getFromWifi();
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




    private WifiManager.LocalOnlyHotspotReservation mReservation;

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void turnOnHotspot() {
        WifiManager manager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        manager.startLocalOnlyHotspot(new WifiManager.LocalOnlyHotspotCallback() {

            @Override
            public void onStarted(WifiManager.LocalOnlyHotspotReservation reservation) {
                super.onStarted(reservation);
                Log.d(TAG, "Wifi Hotspot is on now");
                mReservation = reservation;
            }

            @Override
            public void onStopped() {
                super.onStopped();
                Log.d(TAG, "onStopped: ");
            }

            @Override
            public void onFailed(int reason) {
                super.onFailed(reason);
                Log.d(TAG, "onFailed: ");
            }
        }, new Handler());
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void turnOffHotspot() {
        if (mReservation != null) {
            mReservation.close();
        }
    }


}