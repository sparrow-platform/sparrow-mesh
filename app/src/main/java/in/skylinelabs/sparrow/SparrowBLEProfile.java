package in.skylinelabs.sparrow;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;

import java.util.UUID;

/**
 * Implementation of the Bluetooth GATT Time Profile.
 * https://www.bluetooth.com/specifications/adopted-specifications
 */
public class SparrowBLEProfile {
    private static final String TAG = SparrowBLEProfile.class.getSimpleName();


    public static UUID SPARROW_SERVICE = UUID.fromString("bc7a0884-8f30-11e9-bc42-526af7764f64");
    public static UUID SPARROW_NOTIFICATION = UUID.fromString("bc7a0d48-8f30-11e9-bc42-526af7764f64");
    //public static UUID SPARROW_TX = UUID.fromString("00002a2b-0000-2000-9000-00805f9b34f4");
    //public static UUID SPARROW_RX = UUID.fromString("00002a2b-0000-2000-9000-00805f9b34f8");
    public static UUID CLIENT_CONFIG = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");


    /**
     * Return a configured {@link BluetoothGattService} instance for the
     * Current Time Service.
     */
    public static BluetoothGattService createSparrowService() {
        BluetoothGattService service = new BluetoothGattService(SPARROW_SERVICE,
                BluetoothGattService.SERVICE_TYPE_PRIMARY);

        BluetoothGattCharacteristic sparrowNotification = new BluetoothGattCharacteristic(SPARROW_NOTIFICATION,
                //Read-only characteristic, supports notifications
                BluetoothGattCharacteristic.PROPERTY_NOTIFY | BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ );

        BluetoothGattDescriptor configDescriptor = new BluetoothGattDescriptor(CLIENT_CONFIG,
                //Read/write descriptor
                BluetoothGattDescriptor.PERMISSION_READ | BluetoothGattDescriptor.PERMISSION_WRITE);
        sparrowNotification.addDescriptor(configDescriptor);

        /*
        BluetoothGattCharacteristic sparrowRX = new BluetoothGattCharacteristic(SPARROW_RX,
                //Read-only characteristic, supports notifications
                BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ);

        BluetoothGattCharacteristic sparrowTX = new BluetoothGattCharacteristic(SPARROW_TX,
                //Read-only characteristic, supports notifications
                BluetoothGattCharacteristic.PROPERTY_WRITE,
                BluetoothGattCharacteristic.PERMISSION_WRITE);
        */

        service.addCharacteristic(sparrowNotification);

        //service.addCharacteristic(sparrowTX);
        //service.addCharacteristic(sparrowRX);

        return service;
    }
}