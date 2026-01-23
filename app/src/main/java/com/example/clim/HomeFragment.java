package com.example.clim;

import android. Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android. bluetooth.BluetoothGattCharacteristic;
import android. bluetooth.BluetoothGattService;
import android.bluetooth. BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content. Context;
import android.content. pm.PackageManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget. Button;
import android.widget. Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx. annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;

import java.util.UUID;

public class HomeFragment extends Fragment {

    private TextView textStatus;
    private TextView textTemperature;
    private Switch switchOnOff;
    private Button buttonConnect;
    private Button buttonTempPlus;
    private Button buttonTempMinus;

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothGatt bluetoothGatt;
    private BluetoothGattCharacteristic writeCharacteristic;

    private boolean isConnected = false;
    private int currentTemperature = 22;

    private static final String DEVICE_MAC = "00:CC:3F:DC:6B:5C";
    private static final UUID SERVICE_UUID = UUID. fromString("2141e110-213a-11e6-b67b-9e71128cae77");
    private static final UUID WRITE_CHAR_UUID = UUID.fromString("2141e111-213a-11e6-b67b-9e71128cae77");

    private final ActivityResultLauncher<String[]> requestPermissionsLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                Boolean scanGranted = result.get(Manifest.permission.BLUETOOTH_SCAN);
                Boolean connectGranted = result.get(Manifest.permission.BLUETOOTH_CONNECT);
                Boolean locationGranted = result.get(Manifest. permission.ACCESS_FINE_LOCATION);

                if (scanGranted != null && scanGranted &&
                        connectGranted != null && connectGranted &&
                        locationGranted != null && locationGranted) {
                    connectToDevice();
                } else {
                    Toast.makeText(requireContext(), "Permissions refusées", Toast. LENGTH_SHORT).show();
                }
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R. layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        textStatus = view.findViewById(R.id.textStatus);
        textTemperature = view. findViewById(R.id.textTemperature);
        switchOnOff = view.findViewById(R. id.switchOnOff);
        buttonConnect = view.findViewById(R.id.buttonConnect);
        buttonTempPlus = view.findViewById(R.id.buttonTempPlus);
        buttonTempMinus = view.findViewById(R.id.buttonTempMinus);

        BluetoothManager bluetoothManager = (BluetoothManager) requireContext().getSystemService(Context. BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager. getAdapter();

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
                requestPermissionsLauncher.launch(new String[]{
                        Manifest.permission. BLUETOOTH_SCAN,
                        Manifest. permission.BLUETOOTH_CONNECT,
                        Manifest.permission. ACCESS_FINE_LOCATION
                });
            }
        });

        buttonTempPlus. setOnClickListener(v -> {
            if (currentTemperature < 30) {
                currentTemperature++;
                updateTemperatureDisplay();
                sendTemperatureCommand();
            }
        });

        buttonTempMinus.setOnClickListener(v -> {
            if (currentTemperature > 18) {
                currentTemperature--;
                updateTemperatureDisplay();
                sendTemperatureCommand();
            }
        });

        switchOnOff.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isConnected) {
                sendPowerCommand(isChecked);
            }
        });
    }

    private boolean checkPermissions() {
        return ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission. BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void connectToDevice() {
        if (! checkPermissions()) {
            Toast.makeText(requireContext(), "Permissions manquantes", Toast. LENGTH_SHORT).show();
            return;
        }

        textStatus.setText("Connexion...");
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
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (ActivityCompat. checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                return;
            }

            if (newState == BluetoothProfile. STATE_CONNECTED) {
                isConnected = true;
                requireActivity().runOnUiThread(() -> textStatus.setText("Connecté - Découverte... "));
                gatt.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                isConnected = false;
                requireActivity().runOnUiThread(() -> updateConnectionState());
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                BluetoothGattService service = gatt.getService(SERVICE_UUID);
                if (service != null) {
                    writeCharacteristic = service. getCharacteristic(WRITE_CHAR_UUID);
                    if (writeCharacteristic != null) {
                        requireActivity().runOnUiThread(() -> {
                            textStatus. setText("Connecté");
                            updateConnectionState();
                            Toast.makeText(requireContext(), "Prêt", Toast.LENGTH_SHORT).show();
                        });
                    } else {
                        requireActivity().runOnUiThread(() -> {
                            textStatus.setText("Erreur caractéristique");
                            Toast.makeText(requireContext(), "Caractéristique non trouvée", Toast.LENGTH_LONG).show();
                        });
                    }
                } else {
                    requireActivity().runOnUiThread(() -> {
                        textStatus.setText("Service non trouvé");
                        Toast.makeText(requireContext(), "Service UUID non trouvé", Toast.LENGTH_LONG).show();
                    });
                }
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                requireActivity().runOnUiThread(() -> Toast.makeText(requireContext(), "Commande envoyée", Toast.LENGTH_SHORT).show());
            } else {
                requireActivity().runOnUiThread(() -> Toast.makeText(requireContext(), "Échec envoi", Toast.LENGTH_SHORT).show());
            }
        }
    };

    private void sendTemperatureCommand() {
        if (! isConnected || writeCharacteristic == null) {
            Toast. makeText(requireContext(), "Non connecté", Toast.LENGTH_SHORT).show();
            return;
        }

        byte[] command = buildTemperatureCommand(currentTemperature);

        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        writeCharacteristic.setValue(command);
        bluetoothGatt.writeCharacteristic(writeCharacteristic);
    }

    private void sendPowerCommand(boolean powerOn) {
        if (!isConnected || writeCharacteristic == null) {
            Toast.makeText(requireContext(), "Non connecté", Toast.LENGTH_SHORT).show();
            return;
        }

        byte[] command = buildPowerCommand(powerOn);

        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission. BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        writeCharacteristic.setValue(command);
        bluetoothGatt.writeCharacteristic(writeCharacteristic);
    }

    private byte[] buildTemperatureCommand(int temperature) {
        return new byte[]{
                0x40, 0x00, 0x11,
                (byte) (temperature * 2),
                0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00
        };
    }

    private byte[] buildPowerCommand(boolean powerOn) {
        return new byte[]{
                0x40, 0x00, 0x01,
                (byte) (powerOn ? 0x01 :  0x00),
                0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00
        };
    }

    private void updateConnectionState() {
        buttonConnect. setText(isConnected ? "Déconnecter" : "Connecter");
        textStatus. setText(isConnected ? "Connecté" : "Déconnecté");
        switchOnOff.setEnabled(isConnected);
        buttonTempPlus.setEnabled(isConnected);
        buttonTempMinus.setEnabled(isConnected);
    }

    private void updateTemperatureDisplay() {
        textTemperature.setText(currentTemperature + "°C");
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        disconnect();
    }
}