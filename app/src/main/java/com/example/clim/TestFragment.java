package com.example.clim;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;

import java.util.List;
import java.util.UUID;

public class TestFragment extends Fragment {

    private static final String TAG = "BLE_CLIM_DEBUG";

    // UI Components
    private TextView textStatus;
    private TextView textLog;
    private EditText editHexCommand;
    private Button buttonConnect;
    private Button buttonTest1;
    private Button buttonTest2;
    private Button buttonTest3;
    private Button buttonTest4;
    private Button buttonSendCustom;

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothGatt bluetoothGatt;
    private BluetoothGattCharacteristic writeCharacteristic;
    private BluetoothGattCharacteristic readCharacteristic;

    private boolean isConnected = false;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private static final String DEVICE_MAC = "00:CC:3F:DC:6B:5C";
    private static final UUID SERVICE_UUID = UUID.fromString("2141e110-213a-11e6-b67b-9e71128cae77");

    // --- CORRECTION CRITIQUE ICI : INVERSION DES UUIDS ---
    // UUID finissant par 11 est NOTIFY (Lecture)
    // UUID finissant par 12 est WRITE_NO_RESPONSE (Écriture)
    private static final UUID READ_CHAR_UUID = UUID.fromString("2141e111-213a-11e6-b67b-9e71128cae77");
    private static final UUID WRITE_CHAR_UUID = UUID.fromString("2141e112-213a-11e6-b67b-9e71128cae77");

    private static final UUID CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private static final int REQUEST_PERMISSIONS = 1;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_test, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initViews(view);

        BluetoothManager bluetoothManager = (BluetoothManager) requireContext().getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();

        buttonConnect.setOnClickListener(v -> handleConnectClick());

        // Boutons configurés selon le protocole identifié
        buttonTest1.setText("Get Status");
        buttonTest1.setOnClickListener(v -> sendCommand("00 05 00 20 e0 00"));

        buttonTest2.setText("Set ON");
        buttonTest2.setOnClickListener(v -> sendCommand("00 06 40 20 20 01 01"));

        buttonTest3.setText("Set OFF");
        buttonTest3.setOnClickListener(v -> sendCommand("00 06 40 20 20 01 00"));

        buttonTest4.setText("Get Info");
        buttonTest4.setOnClickListener(v -> sendCommand("00 05 00 00 e0 00"));

        buttonSendCustom.setOnClickListener(v -> {
            String hex = editHexCommand.getText().toString().trim();
            if (!hex.isEmpty()) sendCommand(hex);
        });

        logMessage("Correction Appliquée. UUIDs Inversés. Prêt.");
    }

    private void initViews(View view) {
        textStatus = view.findViewById(R.id.textStatus);
        textLog = view.findViewById(R.id.textLog);
        editHexCommand = view.findViewById(R.id.editHexCommand);
        buttonConnect = view.findViewById(R.id.buttonConnect);
        buttonTest1 = view.findViewById(R.id.buttonTest1);
        buttonTest2 = view.findViewById(R.id.buttonTest2);
        buttonTest3 = view.findViewById(R.id.buttonTest3);
        buttonTest4 = view.findViewById(R.id.buttonTest4);
        buttonSendCustom = view.findViewById(R.id.buttonSendCustom);
    }

    private void handleConnectClick() {
        if (checkPermissions()) {
            if (isConnected) disconnect();
            else connectToDevice();
        } else {
            requestPermissions(new String[]{
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.ACCESS_FINE_LOCATION
            }, REQUEST_PERMISSIONS);
        }
    }

    private boolean checkPermissions() {
        if (getContext() == null) return false;
        return ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
    }

    private void connectToDevice() {
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return;

        textStatus.setText("Connexion...");
        logMessage("Connexion à " + DEVICE_MAC);

        try {
            BluetoothDevice device = bluetoothAdapter.getRemoteDevice(DEVICE_MAC);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                bluetoothGatt = device.connectGatt(requireContext(), false, gattCallback, BluetoothDevice.TRANSPORT_LE);
            } else {
                bluetoothGatt = device.connectGatt(requireContext(), false, gattCallback);
            }
        } catch (IllegalArgumentException e) {
            logMessage("Erreur MAC Address");
        }
    }

    private void disconnect() {
        if (bluetoothGatt != null) {
            if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                bluetoothGatt.disconnect();
                bluetoothGatt.close();
            }
            bluetoothGatt = null;
        }
        isConnected = false;
        updateUIState();
        logMessage("Déconnecté.");
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                isConnected = true;
                logMessage("Connecté. Attente découverte services...");
                mainHandler.postDelayed(() -> {
                    if (bluetoothGatt != null && ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                        bluetoothGatt.discoverServices();
                    }
                }, 1000);
                mainHandler.post(() -> {
                    textStatus.setText("Connecté");
                    updateUIState();
                });
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                isConnected = false;
                logMessage("Déconnecté (Status: " + status + ")");
                if (bluetoothGatt != null) {
                    bluetoothGatt.close();
                    bluetoothGatt = null;
                }
                mainHandler.post(TestFragment.this::updateUIState);
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
                        logMessage("Charactéristiques trouvées (CORRIGÉES).");

                        // DEBUG POUR CONFIRMATION
                        int writeProps = writeCharacteristic.getProperties();
                        logMessage("WRITE Props: " + writeProps + " (Doit être 4 ou 12)");

                        int readProps = readCharacteristic.getProperties();
                        logMessage("READ Props: " + readProps + " (Doit être 16)");

                        enableNotifications(gatt, readCharacteristic);
                    } else {
                        logMessage("ERREUR: UUIDs introuvables.");
                    }
                } else {
                    logMessage("ERREUR: Service UUID introuvable.");
                }
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            logMessage("Write: " + (status == BluetoothGatt.GATT_SUCCESS ? "SUCCÈS" : "ÉCHEC " + status));
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            byte[] data = characteristic.getValue();
            logMessage("RX << " + bytesToHex(data));
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            logMessage("Notifications (CCCD): " + (status == BluetoothGatt.GATT_SUCCESS ? "ACTIVÉES" : "ÉCHEC " + status));
        }
    };

    private void enableNotifications(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return;

        gatt.setCharacteristicNotification(characteristic, true);

        BluetoothGattDescriptor descriptor = characteristic.getDescriptor(CCCD_UUID);
        if (descriptor != null) {
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            gatt.writeDescriptor(descriptor);
        } else {
            // Si l'UUID est bon, le CCCD devrait être là maintenant.
            logMessage("ERREUR: CCCD toujours introuvable (Etrange avec le bon UUID).");
            // Fallback: chercher n'importe quel descripteur
            for (BluetoothGattDescriptor d : characteristic.getDescriptors()) {
                d.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                gatt.writeDescriptor(d);
            }
        }
    }

    private void sendCommand(String hexString) {
        if (!isConnected || writeCharacteristic == null) {
            logMessage("Non connecté.");
            return;
        }

        try {
            byte[] command = hexStringToByteArray(hexString);
            if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return;

            // Comme la propriété est 4 (WRITE_NO_RESPONSE), on force ce mode.
            // C'est maintenant aligné avec les logs.
            writeCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);

            writeCharacteristic.setValue(command);
            logMessage("TX >> " + hexString);

            boolean success = bluetoothGatt.writeCharacteristic(writeCharacteristic);
            if (!success) {
                logMessage("ERREUR API writeCharacteristic (False)");
            }

        } catch (Exception e) {
            logMessage("Exception: " + e.getMessage());
        }
    }

    private byte[] hexStringToByteArray(String hex) {
        hex = hex.replaceAll("\\s+", "");
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4) + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02X ", b));
        return sb.toString().trim();
    }

    private void logMessage(String message) {
        Log.d(TAG, message);
        mainHandler.post(() -> {
            if (textLog == null) return;
            String timestamp = new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date());
            if (textLog.length() > 5000) textLog.setText("");
            textLog.append("\n[" + timestamp + "] " + message);
        });
    }

    private void updateUIState() {
        if (buttonConnect == null) return;
        buttonConnect.setText(isConnected ? "Déconnecter" : "Connecter");
        textStatus.setText(isConnected ? "Connecté" : "Déconnecté");
        buttonTest1.setEnabled(isConnected);
        buttonTest2.setEnabled(isConnected);
        buttonTest3.setEnabled(isConnected);
        buttonTest4.setEnabled(isConnected);
        editHexCommand.setEnabled(isConnected);
        buttonSendCustom.setEnabled(isConnected);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        disconnect();
    }
}