package com.example.clim;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ClimBluetoothManager {
    private static final String TAG = "ClimBTManager";
    private static ClimBluetoothManager instance;

    private static final String DEVICE_MAC = "00:CC:3F:DC:6B:5C";
    private static final UUID SERVICE_UUID = UUID.fromString("2141e110-213a-11e6-b67b-9e71128cae77");
    private static final UUID READ_CHAR_UUID = UUID.fromString("2141e111-213a-11e6-b67b-9e71128cae77");
    private static final UUID WRITE_CHAR_UUID = UUID.fromString("2141e112-213a-11e6-b67b-9e71128cae77");

    private Context context;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothGatt bluetoothGatt;
    private BluetoothGattCharacteristic writeCharacteristic;
    private BluetoothGattCharacteristic readCharacteristic;

    private boolean isConnected = false;
    private List<ConnectionListener> connectionListeners = new ArrayList<>();
    private List<NotificationListener> notificationListeners = new ArrayList<>();

    public interface ConnectionListener {
        void onConnectionStateChanged(boolean connected);
        void onServicesDiscovered();
    }

    public interface NotificationListener {
        void onNotificationReceived(byte[] data);
    }

    private ClimBluetoothManager(Context context) {
        this.context = context.getApplicationContext();
        BluetoothManager manager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = manager.getAdapter();
    }

    public static synchronized ClimBluetoothManager getInstance(Context context) {
        if (instance == null) {
            instance = new ClimBluetoothManager(context);
        }
        return instance;
    }

    public void addConnectionListener(ConnectionListener listener) {
        if (!connectionListeners.contains(listener)) {
            connectionListeners.add(listener);
        }
    }

    public void removeConnectionListener(ConnectionListener listener) {
        connectionListeners.remove(listener);
    }

    public void addNotificationListener(NotificationListener listener) {
        if (!notificationListeners.contains(listener)) {
            notificationListeners.add(listener);
        }
    }

    public void removeNotificationListener(NotificationListener listener) {
        notificationListeners.remove(listener);
    }

    public boolean isConnected() {
        return isConnected && bluetoothGatt != null && writeCharacteristic != null;
    }

    public void connect() {
        if (isConnected) {
            Log.d(TAG, "Déjà connecté");
            return;
        }

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Permission manquante");
            return;
        }

        if (bluetoothGatt != null) {
            try {
                bluetoothGatt.disconnect();
                bluetoothGatt.close();
            } catch (Exception e) {
                Log.e(TAG, "Erreur fermeture GATT: " + e.getMessage());
            }
            bluetoothGatt = null;
        }

        BluetoothDevice device = bluetoothAdapter.getRemoteDevice(DEVICE_MAC);
        bluetoothGatt = device.connectGatt(context, false, gattCallback);
        Log.d(TAG, "Connexion en cours...");
    }

    public void disconnect() {
        if (bluetoothGatt != null) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            try {
                bluetoothGatt.disconnect();
                bluetoothGatt.close();
            } catch (Exception e) {
                Log.e(TAG, "Erreur déconnexion: " + e.getMessage());
            }
            bluetoothGatt = null;
        }
        isConnected = false;
        writeCharacteristic = null;
        readCharacteristic = null;
        notifyConnectionListeners(false);
    }

    public boolean sendCommand(byte[] command) {
        if (!isConnected || writeCharacteristic == null || bluetoothGatt == null) {
            Log.e(TAG, "Non connecté ou caractéristique manquante");
            return false;
        }

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Permission manquante");
            return false;
        }

        try {
            writeCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
            writeCharacteristic.setValue(command);
            boolean success = bluetoothGatt.writeCharacteristic(writeCharacteristic);

            if (success) {
                Log.d(TAG, "Commande envoyée: " + bytesToHex(command));
            } else {
                Log.e(TAG, "Échec envoi commande");
            }

            return success;
        } catch (Exception e) {
            Log.e(TAG, "Erreur envoi: " + e.getMessage());
            return false;
        }
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                return;
            }

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                isConnected = true;
                Log.d(TAG, "Connecté, découverte des services...");
                gatt.discoverServices();
                notifyConnectionListeners(true);
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                isConnected = false;
                Log.d(TAG, "Déconnecté");
                notifyConnectionListeners(false);
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                BluetoothGattService service = gatt.getService(SERVICE_UUID);
                if (service != null) {
                    writeCharacteristic = service.getCharacteristic(WRITE_CHAR_UUID);
                    readCharacteristic = service.getCharacteristic(READ_CHAR_UUID);

                    if (writeCharacteristic != null && readCharacteristic != null) {
                        Log.d(TAG, "Caractéristiques trouvées");

                        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                            gatt.setCharacteristicNotification(readCharacteristic, true);
                        }

                        notifyServicesDiscovered();
                    } else {
                        Log.e(TAG, "Caractéristiques manquantes");
                    }
                } else {
                    Log.e(TAG, "Service non trouvé");
                }
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Write OK");
            } else {
                Log.e(TAG, "Write failed: " + status);
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            byte[] data = characteristic.getValue();
            Log.d(TAG, "Notification: " + bytesToHex(data));
            notifyDataReceived(data);
        }
    };

    private void notifyConnectionListeners(boolean connected) {
        for (ConnectionListener listener : new ArrayList<>(connectionListeners)) {
            listener.onConnectionStateChanged(connected);
        }
    }

    private void notifyServicesDiscovered() {
        for (ConnectionListener listener : new ArrayList<>(connectionListeners)) {
            listener.onServicesDiscovered();
        }
    }

    private void notifyDataReceived(byte[] data) {
        for (NotificationListener listener : new ArrayList<>(notificationListeners)) {
            listener.onNotificationReceived(data);
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X ", b));
        }
        return sb.toString().trim();
    }
}