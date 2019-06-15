package in.skylinelabs.sparrow;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Handler;
import android.os.IBinder;
import android.os.ParcelUuid;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.widget.Toast;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class SparrowBLE extends Service {
    private Context context;
    private String TAG = "SparrowBLE";
    private int nextDeviceIndex = 0;

    private BluetoothManager mBluetoothManager;
    private BluetoothGattServer mBluetoothGattServer;
    private BluetoothLeAdvertiser mBluetoothLeAdvertiser;
    private BluetoothLeScanner mBluetoothLeScanner;
    private BluetoothGatt currentBluetoothGatt;

    private ArrayList<MessegeBLE> messegeBuffer = new ArrayList<>();

    /* Collection of notification subscribers */
    private Set<BluetoothDevice> mConnectedClientDevices = new HashSet<>();
    private Set<BluetoothDevice> mConnectedServerDevices = new HashSet<>();
    private ArrayList<BluetoothDevice> mAvailableDevices = new ArrayList<>();


    private Handler handler;

    @Override
    public void onCreate() {
        super.onCreate();
        context = getApplicationContext();
        startForeground();

        mBluetoothManager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        handler = new Handler();
        LocalBroadcastManager.getInstance(this).registerReceiver(mMessageReceiver,
                new IntentFilter("send-payload"));
        startServer();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        BluetoothAdapter bluetoothAdapter = mBluetoothManager.getAdapter();
        // We can't continue without proper Bluetooth support
        if (!checkBluetoothSupport(bluetoothAdapter)) {
            Toast.makeText(context, "Bluetooth support unavailable", Toast.LENGTH_SHORT).show();
            // TODO: 15/5/19 Use wifi instead
            return START_STICKY;
        }

        // Register for system Bluetooth events
        IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        registerReceiver(mBluetoothReceiver, filter);
        if (!bluetoothAdapter.isEnabled()) {
            Log.d(TAG, "Bluetooth is currently disabled...enabling");
            bluetoothAdapter.enable();
        } else {
            Log.d(TAG, "Bluetooth enabled...starting services");
        }
        return START_STICKY;
    }

    private BroadcastReceiver mBluetoothReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.STATE_OFF);

            switch (state) {
                case BluetoothAdapter.STATE_ON:
                    startServer();
                    break;
                case BluetoothAdapter.STATE_OFF:
                    stopServer();
                    break;
                default:
                    // Do nothing
            }

        }
    };

    private boolean checkBluetoothSupport(BluetoothAdapter bluetoothAdapter) {

        if (bluetoothAdapter == null) {
            Log.w(TAG, "Bluetooth is not supported");
            return false;
        }

        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Log.w(TAG, "Bluetooth LE is not supported");
            return false;
        }

        return true;
    }

    private void startAdvertising() {
        BluetoothAdapter bluetoothAdapter = mBluetoothManager.getAdapter();
        mBluetoothLeAdvertiser = bluetoothAdapter.getBluetoothLeAdvertiser();
        if (mBluetoothLeAdvertiser == null) {
            Log.w(TAG, "Failed to create advertiser");
            return;
        }

        AdvertiseSettings settings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
                .setConnectable(true)
                .setTimeout(0)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                .build();

        Random random = new Random();
        bluetoothAdapter.setName("sp"+random.nextInt(100));
        AdvertiseData data = new AdvertiseData.Builder()
                .setIncludeDeviceName(true)
                .setIncludeTxPowerLevel(false)
                .addServiceUuid(new ParcelUuid(SparrowBLEProfile.SPARROW_SERVICE))
                .build();

        mBluetoothLeAdvertiser
                .startAdvertising(settings, data, mAdvertiseCallback);
        startDiscovering();
    }

    /**
     * Stop Bluetooth advertisements.
     */
    private void stopAdvertising() {
        if (mBluetoothLeAdvertiser == null) return;

        mBluetoothLeAdvertiser.stopAdvertising(mAdvertiseCallback);

        stopDiscovering();
    }

    /**
     * Initialize the GATT server instance with the services/characteristics
     * from the Time Profile.
     */
    private void startServer() {
        mBluetoothGattServer = mBluetoothManager.openGattServer(context, mGattServerCallback);
        if (mBluetoothGattServer == null) {
            Log.w(TAG, "Unable to create GATT server");
            return;
        }
        mBluetoothGattServer.addService(SparrowBLEProfile.createSparrowService());
        startAdvertising();
    }

    /**
     * Shut down the GATT server.
     */
    private void stopServer() {
        if (mBluetoothGattServer == null) return;

        mBluetoothGattServer.close();
        stopAdvertising();
    }

    private void sendMessegeToActivity(String message) throws IOException {
        Log.i("sender", "Broadcasting message");
        Intent intent = new Intent("payload-received");
        intent.putExtra("message", message);
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
    }

    private BroadcastReceiver mMessageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Get extra data included in the Intent
            String message = intent.getStringExtra("message");
            messegeBuffer.add(new MessegeBLE(message,1.0,60));
            //notifyToConnectedDeivces(message);
            //messages.append(message+"\n");
            Log.d(TAG, "Sending message: " + message);
        }
    };

    private void notifyToDeivce(BluetoothDevice device, String message){
        BluetoothGattCharacteristic sparrowCharacteristic = mBluetoothGattServer
                .getService(SparrowBLEProfile.SPARROW_SERVICE)
                .getCharacteristic(SparrowBLEProfile.SPARROW_NOTIFICATION);
        for(int i=0;i < message.length();i+=20) {
            sparrowCharacteristic.setValue(message.substring(i,i+20<message.length()?i+20:message.length()));
                mBluetoothGattServer.notifyCharacteristicChanged(device, sparrowCharacteristic,false);
        }
    }

    private BluetoothGattCallback mGattConnectionCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);

            Log.i(TAG, "Client BluetoothDevice ConnectionChange " + newState);
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i(TAG, "Client BluetoothDevice CONNECTED: " + gatt.getDevice().getName());
                gatt.discoverServices();
                gatt.requestMtu(512);
                mConnectedServerDevices.add(gatt.getDevice());

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i(TAG, "Client BluetoothDevice DISCONNECTED: " + gatt.getDevice().getName());
                //Remove device from any active subscriptions
                mConnectedServerDevices.remove(gatt.getDevice());

            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);
            BluetoothGattCharacteristic sparrowCharacteristic = gatt
                    .getService(SparrowBLEProfile.SPARROW_SERVICE)
                    .getCharacteristic(SparrowBLEProfile.SPARROW_NOTIFICATION);

            gatt.setCharacteristicNotification(sparrowCharacteristic,true);

            BluetoothGattDescriptor descriptor = sparrowCharacteristic.getDescriptor(SparrowBLEProfile.CLIENT_CONFIG);
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            gatt.writeDescriptor(descriptor);

        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);
            try {
                sendMessegeToActivity(characteristic.getStringValue(0));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    };

    private BluetoothGattServerCallback mGattServerCallback = new BluetoothGattServerCallback() {

        @Override
        public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
            Log.i(TAG, "Server BluetoothDevice ConnectionChange " + newState);
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i(TAG, "Server BluetoothDevice CONNECTED: " + device);
                mConnectedClientDevices.add(device);
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i(TAG, "Server BluetoothDevice DISCONNECTED: " + device);
                //Remove device from any active subscriptions
                mConnectedClientDevices.remove(device);
            }
        }

        @Override
        public void onDescriptorWriteRequest(BluetoothDevice device, int requestId, BluetoothGattDescriptor descriptor, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
            Log.i(TAG, "Descriptor write request recieved: " + device.getName());
            for (MessegeBLE msg: messegeBuffer) {
                if(!msg.isSent(device.getAddress())){
                    notifyToDeivce(device,msg.getData());
                    msg.sentTo(device.getAddress());
                }
            }
            mBluetoothGattServer.cancelConnection(device);
            super.onDescriptorWriteRequest(device, requestId, descriptor, preparedWrite, responseNeeded, offset, value);
        }

        @Override
        public void onMtuChanged(BluetoothDevice device, int mtu) {
            super.onMtuChanged(device, mtu);
            Log.d(TAG,"MTu changed to: "+mtu);
        }

        @Override
        public void onServiceAdded(int status, BluetoothGattService service) {
            super.onServiceAdded(status, service);
            Log.d(TAG,"Service added" + status);
        }
    };

    /**
     * Callback to receive information about the advertisement process.
     */
    private AdvertiseCallback mAdvertiseCallback = new AdvertiseCallback() {
        @Override
        public void onStartSuccess(AdvertiseSettings settingsInEffect) {
            Log.i(TAG, "LE Advertise Started.");
        }

        @Override
        public void onStartFailure(int errorCode) {
            Log.w(TAG, "LE Advertise Failed: "+errorCode);
        }
    };

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopServer();
    }



    private ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            Log.d(TAG,"BLE scan result: "+result.getScanRecord().getDeviceName());
            List<ParcelUuid> serviceUuids = result.getScanRecord().getServiceUuids();
            if(serviceUuids != null && serviceUuids.contains(new ParcelUuid(SparrowBLEProfile.SPARROW_SERVICE))) {
                Log.d(TAG, "Sparrow device detected: " + result.getScanRecord().getDeviceName());
                if(!mAvailableDevices.contains(result.getDevice())) {
                    mAvailableDevices.add(result.getDevice());
                    //result.getDevice().connectGatt(context,false,mGattConnectionCallback);
                    Log.d(TAG,"Connecting to sparrow device");
                }
            }
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            super.onBatchScanResults(results);
            Log.d(TAG,"BLE scan batch result: "+results.size());
            for (ScanResult scanResult: results) {
                Log.d(TAG,"BLE scan result: "+scanResult.getScanRecord().getDeviceName());
                List<ParcelUuid> serviceUuids = scanResult.getScanRecord().getServiceUuids();
                if(serviceUuids != null && serviceUuids.contains(new ParcelUuid(SparrowBLEProfile.SPARROW_SERVICE))) {
                    Log.d(TAG, "Sparrow device detected: " + scanResult.getScanRecord().getDeviceName());
                    if(!mAvailableDevices.contains(scanResult.getDevice())) {
                        mAvailableDevices.add(scanResult.getDevice());
                        Log.d(TAG,"Connecting to sparrow device");
                    }
                }
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
            Log.d(TAG,"BLE scan failed "+ errorCode);

        }
    };
    private void stopDiscovering() {
        BluetoothAdapter bluetoothAdapter = mBluetoothManager.getAdapter();
        mBluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
        mBluetoothLeScanner.stopScan(scanCallback);
    }
    private void startDiscovering() {
        BluetoothAdapter bluetoothAdapter = mBluetoothManager.getAdapter();
        mBluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
        if (mBluetoothLeScanner == null) {
            Log.w(TAG, "Failed to create advertiser");
            return;
        }
        List<ScanFilter> scanFilters = new ArrayList<>();
        ScanFilter filter = new ScanFilter.Builder().build();
        scanFilters.add(filter);
        ScanSettings scanSettings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
                .build();

        Runnable messegeBufferManager = new Runnable() {
            @Override
            public void run() {
                // TODO: 15/6/19 Make sure messege buffer is maintained
            }
        };

        Runnable changeConnection = new Runnable() {
            @Override
            public void run() {
                if(mAvailableDevices.size() > 0) {
                    if (currentBluetoothGatt != null)
                        currentBluetoothGatt.close();
                    currentBluetoothGatt = mAvailableDevices.get(nextDeviceIndex).connectGatt(context, false, mGattConnectionCallback);
                    Log.d(TAG,"Connecting to: "+nextDeviceIndex);
                    nextDeviceIndex = mAvailableDevices.size() <= ++nextDeviceIndex? 0 : nextDeviceIndex;
                }
                handler.postDelayed(this,5000);
            }
        };

        Runnable startScan = new Runnable() {
            @Override
            public void run() {
                Log.d(TAG,"Starting BLE discovery");
                mBluetoothLeScanner.startScan(scanFilters,scanSettings,scanCallback);
                handler.postDelayed(this,20000);
            }
        };

        Runnable stopcan = new Runnable() {
            @Override
            public void run() {
                Log.d(TAG,"Stopping BLE discovery");
                mBluetoothLeScanner.stopScan(scanCallback);
                //mConnectedDevices.clear();
                handler.postDelayed(this,20000);
            }
        };

        handler.postDelayed(startScan,2000);
        handler.postDelayed(changeConnection,8000);
        handler.postDelayed(stopcan,8000);

    }

    private void startForeground(){
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

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }
}
