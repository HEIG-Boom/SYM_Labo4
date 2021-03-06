package ch.heigvd.iict.sym_labo4.viewmodels;

import android.app.Application;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import no.nordicsemi.android.ble.BleManager;
import no.nordicsemi.android.ble.BleManagerCallbacks;
import no.nordicsemi.android.ble.data.Data;

/**
 * Handles all bluetooth operations and services proposed by a specific device.
 *
 * @author HEIG-VD
 * @author Modified by : Jael Dubey, Loris Gilliand, Mateo Tutic, Luc Wachter
 * @version 1.0
 * @since 2019-12-06
 */
public class BleOperationsViewModel extends AndroidViewModel {

    private static final String TAG = BleOperationsViewModel.class.getSimpleName();

    private MySymBleManager ble = null;
    private BluetoothGatt mConnection = null;

    // Indexes on Current Time according to the official specifications
    private final int CURRENT_TIME_SIZE = 10;
    private final int HOUR_INDEX = 4;
    private final int MINUTES_INDEX = 5;
    private final int SECONDS_INDEX = 6;

    // Live data - observer
    private final MutableLiveData<Boolean> mIsConnected = new MutableLiveData<>();

    public LiveData<Boolean> isConnected() {
        return mIsConnected;
    }

    // Live data - temperature
    private final MutableLiveData<Integer> mTemperature = new MutableLiveData<>();

    public LiveData<Integer> getTemperature() {
        return mTemperature;
    }

    // Live data on number of button clicks
    private final MutableLiveData<Integer> mButtonClicks = new MutableLiveData<>();

    public LiveData<Integer> getButtonClicksCount() {
        return mButtonClicks;
    }

    // Live data on the device time
    private final MutableLiveData<String> mTime = new MutableLiveData<>();

    public LiveData<String> getTime() {
        return mTime;
    }

    // References to the Services and Characteristics of the SYM Pixl
    private BluetoothGattService timeService = null, symService = null;
    private BluetoothGattCharacteristic currentTimeChar = null, integerChar = null, temperatureChar = null, buttonClickChar = null;

    public BleOperationsViewModel(Application application) {
        super(application);
        this.mIsConnected.setValue(false); // To be sure that it's never null
        this.ble = new MySymBleManager();
        this.ble.setGattCallbacks(this.bleManagerCallbacks);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        Log.d(TAG, "onCleared");
        this.ble.disconnect();
    }

    public void connect(BluetoothDevice device) {
        Log.d(TAG, "User request connection to: " + device);
        if (!mIsConnected.getValue()) {
            this.ble.connect(device)
                    .retry(1, 100)
                    .useAutoConnect(false)
                    .enqueue();
        }
    }

    public void disconnect() {
        Log.d(TAG, "User request disconnection");
        this.ble.disconnect();
        if (mConnection != null) {
            mConnection.disconnect();
        }
    }
    
    public boolean readTemperature() {
        if (!isConnected().getValue() || temperatureChar == null) return false;
        return ble.readTemperature();
    }

    public void sendInteger(int val) {
        if (isConnected().getValue()) {
            ble.sendInteger(val);
        }
    }

    public boolean sendTime() {
        if (!isConnected().getValue() || currentTimeChar == null) return false;
        return ble.sendTime();
    }

    private BleManagerCallbacks bleManagerCallbacks = new BleManagerCallbacks() {
        @Override
        public void onDeviceConnecting(@NonNull BluetoothDevice device) {
            Log.d(TAG, "onDeviceConnecting");
            mIsConnected.setValue(false);
        }

        @Override
        public void onDeviceConnected(@NonNull BluetoothDevice device) {
            Log.d(TAG, "onDeviceConnected");
            mIsConnected.setValue(true);
        }

        @Override
        public void onDeviceDisconnecting(@NonNull BluetoothDevice device) {
            Log.d(TAG, "onDeviceDisconnecting");
            mIsConnected.setValue(false);
        }

        @Override
        public void onDeviceDisconnected(@NonNull BluetoothDevice device) {
            Log.d(TAG, "onDeviceDisconnected");
            mIsConnected.setValue(false);
        }

        @Override
        public void onLinkLossOccurred(@NonNull BluetoothDevice device) {
            Log.d(TAG, "onLinkLossOccurred");
        }

        @Override
        public void onServicesDiscovered(@NonNull BluetoothDevice device, boolean optionalServicesFound) {
            Log.d(TAG, "onServicesDiscovered");
        }

        @Override
        public void onDeviceReady(@NonNull BluetoothDevice device) {
            Log.d(TAG, "onDeviceReady");
        }

        @Override
        public void onBondingRequired(@NonNull BluetoothDevice device) {
            Log.w(TAG, "onBondingRequired");
        }

        @Override
        public void onBonded(@NonNull BluetoothDevice device) {
            Log.d(TAG, "onBonded");
        }

        @Override
        public void onBondingFailed(@NonNull BluetoothDevice device) {
            Log.e(TAG, "onBondingFailed");
        }

        @Override
        public void onError(@NonNull BluetoothDevice device, @NonNull String message, int errorCode) {
            Log.e(TAG, "onError:" + errorCode);
        }

        @Override
        public void onDeviceNotSupported(@NonNull BluetoothDevice device) {
            Log.e(TAG, "onDeviceNotSupported");
            Toast.makeText(getApplication(), "Device not supported", Toast.LENGTH_SHORT).show();
        }
    };

    /*
     *  This class is used to implement the protocol to communicate with the BLE device
     */
    private class MySymBleManager extends BleManager<BleManagerCallbacks> {

        private MySymBleManager() {
            super(getApplication());
        }

        @Override
        public BleManagerGattCallback getGattCallback() {
            return mGattCallback;
        }

        /**
         * BluetoothGatt callbacks object.
         */
        private final BleManagerGattCallback mGattCallback = new BleManagerGattCallback() {
            final Map<String, String> neededServices = new HashMap<String, String>() {{
                put("timeService", "00001805-0000-1000-8000-00805f9b34fb");
                put("customService", "3c0a1000-281d-4b48-b2a7-f15579a1c38f");
            }};

            final Map<String, String> neededCharacteristics = new HashMap<String, String>() {{
                put("currentTimeChar", "00002a2b-0000-1000-8000-00805f9b34fb");
                put("intChar", "3c0a1001-281d-4b48-b2a7-f15579a1c38f");
                put("temperatureChar", "3c0a1002-281d-4b48-b2a7-f15579a1c38f");
                put("btnChar", "3c0a1003-281d-4b48-b2a7-f15579a1c38f");
            }};

            @Override
            public boolean isRequiredServiceSupported(@NonNull final BluetoothGatt gatt) {
                mConnection = gatt; //trick to force disconnection

                Log.d(TAG, "isRequiredServiceSupported - discovered services:");

                // Iterate through our needed services
                for (Map.Entry<String, String> neededService : neededServices.entrySet()) {
                    // Store service instance
                    timeService = gatt.getService(UUID.fromString(neededServices.get("timeService")));
                    symService = gatt.getService(UUID.fromString(neededServices.get("customService")));

                    if (timeService == null || symService == null) {
                        return false;
                    }
                }

                // Iterate through our needed characteristics
                for (Map.Entry<String, String> neededChar : neededCharacteristics.entrySet()) {
                    currentTimeChar = timeService.getCharacteristic(
                            UUID.fromString(neededCharacteristics.get("currentTimeChar"))
                    );
                    integerChar = symService.getCharacteristic(
                            UUID.fromString(neededCharacteristics.get("intChar"))
                    );
                    temperatureChar = symService.getCharacteristic(
                            UUID.fromString(neededCharacteristics.get("temperatureChar"))
                    );
                    buttonClickChar = symService.getCharacteristic(
                            UUID.fromString(neededCharacteristics.get("btnChar"))
                    );

                    if (currentTimeChar == null || integerChar == null
                            || temperatureChar == null || buttonClickChar == null) {
                        return false;
                    }
                }

                return true;
            }

            @Override
            protected void initialize() {
                // Register to number of button clicks service
                mButtonClicks.setValue(0);
                setNotificationCallback(buttonClickChar).with((device, data) -> {
                    mButtonClicks.setValue(data.getIntValue(Data.FORMAT_UINT8, 0));
                });
                enableNotifications(buttonClickChar).enqueue();

                // Register to current time on the device
                setNotificationCallback(currentTimeChar).with((device, data) -> {
                    // Read current time based on official specification
                    String hour = Integer.toString(data.getIntValue(Data.FORMAT_UINT8, 4));
                    String minutes = Integer.toString(data.getIntValue(Data.FORMAT_UINT8, 5));
                    String seconds = Integer.toString(data.getIntValue(Data.FORMAT_UINT8, 6));

                    mTime.setValue(hour + ":" + minutes + ":" + seconds);
                });
                enableNotifications(currentTimeChar).enqueue();
            }

            @Override
            protected void onDeviceDisconnected() {
                //we reset services and characteristics
                timeService = null;
                currentTimeChar = null;

                symService = null;
                integerChar = null;
                temperatureChar = null;
                buttonClickChar = null;
            }
        };

        public boolean readTemperature() {
            // Read the temperature and push it in the event queue
            readCharacteristic(temperatureChar).with((device, data) -> {
                mTemperature.setValue(data.getIntValue(Data.FORMAT_UINT16, 0) / 10);
            }).enqueue();
            return true;
        }

        public void sendInteger(int val) {
            ByteBuffer bb = ByteBuffer.allocate(4);
            bb.putInt(val);
            bb.order(ByteOrder.LITTLE_ENDIAN);
            writeCharacteristic(integerChar, bb.array()).enqueue();
        }

        public boolean sendTime() {
            // Get the current time with the Calendar API
            Calendar calendar = Calendar.getInstance();
            Integer hour = calendar.get(Calendar.HOUR_OF_DAY);
            Integer minutes = calendar.get(Calendar.MINUTE);
            Integer seconds = calendar.get(Calendar.SECOND);

            // Write to the device according to the official specification
            byte buffer[] = new byte[CURRENT_TIME_SIZE];
            buffer[HOUR_INDEX] = hour.byteValue();
            buffer[MINUTES_INDEX] = minutes.byteValue();
            buffer[SECONDS_INDEX] = seconds.byteValue();
            writeCharacteristic(currentTimeChar, buffer).enqueue();

            return true;
        }
    }
}
