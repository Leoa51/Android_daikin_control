package com.example.clim;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
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
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.fragment.app.Fragment;

import java.util.UUID;

public class TestFragment extends Fragment {

    private TextView textStatus;
    private TextView textLog;
    private EditText editHexCommand;
    private Button buttonConnect;
    private Button buttonTest1;
    private Button buttonTest2;
    private Button buttonTest3;
    private Button buttonTest4;
    private Button buttonTestNotif;
    private Button buttonSendCustom;

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothGatt bluetoothGatt;
    private BluetoothGattCharacteristic writeCharacteristic;
    private BluetoothGattCharacteristic readCharacteristic;

    private boolean isConnected = false;

    private static final String DEVICE_MAC = "00:CC:3F:DC:6B:5C";
    private static final UUID SERVICE_UUID = UUID.fromString("2141e110-213a-11e6-b67b-9e71128cae77");
    private static final UUID WRITE_CHAR_UUID = UUID.fromString("2141e111-213a-11e6-b67b-9e71128cae77");
    private static final UUID READ_CHAR_UUID = UUID.fromString("2141e112-213a-11e6-b67b-9e71128cae77");

    private static final int REQUEST_PERMISSIONS = 1;
    private static final int REQUEST_NOTIFICATION_PERMISSION = 2;
    private static final String CHANNEL_ID = "test_channel";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_test, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        textStatus = view.findViewById(R.id.textStatus);
        textLog = view.findViewById(R.id.textLog);
        editHexCommand = view.findViewById(R.id.editHexCommand);
        buttonConnect = view.findViewById(R.id.buttonConnect);
        buttonTest1 = view.findViewById(R.id.buttonTest1);
        buttonTest2 = view.findViewById(R.id.buttonTest2);
        buttonTest3 = view.findViewById(R.id.buttonTest3);
        buttonTest4 = view.findViewById(R.id.buttonTest4);
        buttonTestNotif = view.findViewById(R.id.buttonTestNotif);
        buttonSendCustom = view.findViewById(R.id.buttonSendCustom);

        BluetoothManager bluetoothManager = (BluetoothManager) requireContext().getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();

        createNotificationChannel();

        if (bluetoothAdapter == null) {
            Toast.makeText(requireContext(), "Bluetooth non supporté", Toast.LENGTH_SHORT).show();
            return;
        }

        buttonConnect.setOnClickListener(v -> {
            if (checkPermissions()) {
                if (isConnected) {
                    disconnect();
                } else {
                    connectToDevice();
                }
            } else {
                requestPermissions(new String[]{
                        Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.ACCESS_FINE_LOCATION
                }, REQUEST_PERMISSIONS);
            }
        });

        buttonTest1.setOnClickListener(v -> sendCommand("00 06 00 00 00 00 00"));
        buttonTest2.setOnClickListener(v -> sendCommand("00 0e 00 01 30 30 00 31 00 45 00 46 00 47 00"));
        buttonTest3.setOnClickListener(v -> sendCommand("00 08 00 40 40 20 02 0b 80"));
        buttonTest4.setOnClickListener(v -> sendCommand("00 06 00 00 20 15 00"));

        buttonTestNotif.setOnClickListener(v -> sendTestNotification());

        buttonSendCustom.setOnClickListener(v -> {
            String hex = editHexCommand.getText().toString().trim();
            if (!hex.isEmpty()) {
                sendCommand(hex);
            } else {
                Toast.makeText(requireContext(), "Entrez une commande hex", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "Test Channel";
            String description = "Channel pour les notifications de test";
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);

            NotificationManager notificationManager = requireContext().getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    private void sendTestNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATION_PERMISSION);
                return;
            }
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(requireContext(), CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("titre")
                .setContentText("Vous êtes contents, il y a une notif dans l'app")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(requireContext());
        notificationManager.notify(1, builder.build());

        logMessage("Notification envoyée");
    }

    private boolean checkPermissions() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            return ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                    ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
        } else {
            return ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED &&
                    ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED &&
                    ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSIONS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                connectToDevice();
            } else {
                Toast.makeText(requireContext(), "Permissions refusées", Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == REQUEST_NOTIFICATION_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                sendTestNotification();
            } else {
                Toast.makeText(requireContext(), "Permission notification refusée", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void connectToDevice() {
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(requireContext(), "Permission manquante", Toast.LENGTH_SHORT).show();
            return;
        }

        textStatus.setText("Connexion...");
        logMessage("Connexion à " + DEVICE_MAC);
        BluetoothDevice device = bluetoothAdapter.getRemoteDevice(DEVICE_MAC);
        bluetoothGatt = device.connectGatt(requireContext(), false, gattCallback);
    }

    private void disconnect() {
        if (bluetoothGatt != null) {
            if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            bluetoothGatt.disconnect();
            bluetoothGatt.close();
            bluetoothGatt = null;
        }
        isConnected = false;
        updateConnectionState();
        logMessage("Déconnecté");
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                return;
            }

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                isConnected = true;
                requireActivity().runOnUiThread(() -> {
                    textStatus.setText("Connecté - Découverte...");
                    logMessage("Connecté ! Découverte des services...");
                });
                gatt.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                isConnected = false;
                requireActivity().runOnUiThread(() -> {
                    updateConnectionState();
                    logMessage("Déconnexion");
                });
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                BluetoothGattService service = gatt.getService(SERVICE_UUID);
                if (service != null) {
                    writeCharacteristic = service.getCharacteristic(WRITE_CHAR_UUID);
                    readCharacteristic = service.getCharacteristic(READ_CHAR_UUID);

                    if (writeCharacteristic != null) {
                        int properties = writeCharacteristic.getProperties();
                        requireActivity().runOnUiThread(() -> {
                            textStatus.setText("Connecté");
                            updateConnectionState();
                            logMessage("Service trouvé ! UUID Write: " + WRITE_CHAR_UUID);
                            logMessage("Propriétés Write: " + properties);
                            logMessage("  - WRITE: " + ((properties & BluetoothGattCharacteristic.PROPERTY_WRITE) != 0));
                            logMessage("  - WRITE_NO_RESPONSE: " + ((properties & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0));
                            logMessage("Prêt à envoyer des commandes");

                            if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                                if (readCharacteristic != null) {
                                    gatt.setCharacteristicNotification(readCharacteristic, true);
                                    logMessage("Notifications activées sur READ");
                                }
                            }
                        });
                    } else {
                        requireActivity().runOnUiThread(() -> logMessage("ERREUR: Caractéristique Write non trouvée"));
                    }
                } else {
                    requireActivity().runOnUiThread(() -> logMessage("ERREUR: Service non trouvé"));
                }
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            String statusText = (status == BluetoothGatt.GATT_SUCCESS) ? "Succès" : "Échec (" + status + ")";
            requireActivity().runOnUiThread(() -> logMessage("Write: " + statusText));
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            byte[] data = characteristic.getValue();
            String hexData = bytesToHex(data);
            requireActivity().runOnUiThread(() -> logMessage("Notification reçue: " + hexData));
        }
    };

    private void sendCommand(String hexString) {
        if (!isConnected || writeCharacteristic == null) {
            Toast.makeText(requireContext(), "Non connecté", Toast.LENGTH_SHORT).show();
            return;
        }

        if (bluetoothGatt == null) {
            Toast.makeText(requireContext(), "GATT null", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            byte[] command = hexStringToByteArray(hexString);
            logMessage("Envoi: " + hexString);

            if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                logMessage("ERREUR: Permission manquante");
                return;
            }

            int writeType = writeCharacteristic.getWriteType();
            logMessage("Write type actuel: " + writeType);

            if ((writeCharacteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) {
                writeCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
                logMessage("Mode: WRITE_TYPE_NO_RESPONSE");
            } else if ((writeCharacteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_WRITE) != 0) {
                writeCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
                logMessage("Mode: WRITE_TYPE_DEFAULT");
            } else {
                logMessage("ERREUR: Aucun type d'écriture supporté");
                return;
            }

            writeCharacteristic.setValue(command);
            boolean success = bluetoothGatt.writeCharacteristic(writeCharacteristic);

            if (!success) {
                logMessage("ERREUR: writeCharacteristic() a retourné false");
            } else {
                logMessage("Commande mise en file d'attente");
            }
        } catch (Exception e) {
            logMessage("ERREUR: " + e.getMessage());
        }
    }

    private byte[] hexStringToByteArray(String hex) {
        hex = hex.replaceAll("\\s+", "");
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X ", b));
        }
        return sb.toString().trim();
    }

    private void logMessage(String message) {
        requireActivity().runOnUiThread(() -> {
            String timestamp = new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date());
            String currentLog = textLog.getText().toString();
            if (currentLog.equals("En attente...")) {
                textLog.setText("[" + timestamp + "] " + message);
            } else {
                textLog.append("\n[" + timestamp + "] " + message);
            }
        });
    }

    private void updateConnectionState() {
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