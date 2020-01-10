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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import no.nordicsemi.android.ble.BleManager;
import no.nordicsemi.android.ble.BleManagerCallbacks;
import no.nordicsemi.android.ble.data.Data;

public class BleOperationsViewModel extends AndroidViewModel {

    private static final String TAG = BleOperationsViewModel.class.getSimpleName();

    private MySymBleManager ble = null;
    private BluetoothGatt mConnection = null;

    //live data - observer
    private final MutableLiveData<Boolean> mIsConnected = new MutableLiveData<>();
    public LiveData<Boolean> isConnected() {
        return mIsConnected;
    }

    //live data - temperature
    private final MutableLiveData<Integer> mTemperature = new MutableLiveData<>();
    public LiveData<Integer> getTemperature() {
        return mTemperature;
    }

    //references to the Services and Characteristics of the SYM Pixl
    private BluetoothGattService timeService = null, symService = null;
    private BluetoothGattCharacteristic currentTimeChar = null, integerChar = null, temperatureChar = null, buttonClickChar = null;

    public BleOperationsViewModel(Application application) {
        super(application);
        this.mIsConnected.setValue(false); //to be sure that it's never null
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
        if(!mIsConnected.getValue()) {
            this.ble.connect(device)
                    .retry(1, 100)
                    .useAutoConnect(false)
                    .enqueue();
        }
    }

    public void disconnect() {
        Log.d(TAG, "User request disconnection");
        this.ble.disconnect();
        if(mConnection != null) {
            mConnection.disconnect();
        }
    }
    /* TODO
        vous pouvez placer ici les différentes méthodes permettant à l'utilisateur
        d'interagir avec le périphérique depuis l'activité
     */
    public boolean readTemperature() {
        if(!isConnected().getValue() || temperatureChar == null) return false;
        return ble.readTemperature();
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
        public BleManagerGattCallback getGattCallback() { return mGattCallback; }

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
                /* TODO
                    Ici nous somme sûr que le périphérique possède bien tous les services et caractéristiques
                    attendus et que nous y sommes connectés. Nous pouvous effectuer les premiers échanges BLE:
                    Dans notre cas il s'agit de s'enregistrer pour recevoir les notifications proposées par certaines
                    caractéristiques, on en profitera aussi pour mettre en place les callbacks correspondants.
                 */
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
    }
}
