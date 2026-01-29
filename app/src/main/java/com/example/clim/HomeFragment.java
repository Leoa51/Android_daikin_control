package com.example.clim;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;

public class HomeFragment extends Fragment {

    private TextView textStatus;
    private TextView textTemperature;
    private Switch switchOnOff;
    private Button buttonConnect;
    private Button buttonTempPlus;
    private Button buttonTempMinus;

    // On utilise UNIQUEMENT le manager, pas de BluetoothAdapter/Gatt ici
    private ClimBluetoothManager btManager;
    private int currentTemperature = 22;

    // Gestionnaire de permissions propre pour Android 12+ vs anciens
    private final ActivityResultLauncher<String[]> requestPermissionsLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                boolean allGranted = true;
                for (Boolean granted : result.values()) {
                    if (!granted) {
                        allGranted = false;
                        break;
                    }
                }
                if (allGranted) {
                    connectViaManager();
                } else {
                    Toast.makeText(requireContext(), "Permissions refusées, connexion impossible", Toast.LENGTH_SHORT).show();
                }
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Initialisation UI
        textStatus = view.findViewById(R.id.textStatus);
        textTemperature = view.findViewById(R.id.textTemperature);
        switchOnOff = view.findViewById(R.id.switchOnOff);
        buttonConnect = view.findViewById(R.id.buttonConnect);
        buttonTempPlus = view.findViewById(R.id.buttonTempPlus);
        buttonTempMinus = view.findViewById(R.id.buttonTempMinus);

        // Récupération du Manager Singleton
        btManager = ClimBluetoothManager.getInstance(requireContext());

        // Setup des listeners UI
        setupClickListeners();

        // Écouter les changements d'état du Bluetooth venant du Manager
        btManager.addConnectionListener(connectionListener);

        // Initialisation de l'état affiché
        updateConnectionState(btManager.isConnected());
        updateTemperatureDisplay();

        // Tentative de connexion auto si on arrive depuis la liste
        if (!btManager.isConnected() && btManager.getDeviceAddress() != null) {
            checkPermissionsAndConnect();
        }
    }

    private final ClimBluetoothManager.ConnectionListener connectionListener = new ClimBluetoothManager.ConnectionListener() {
        @Override
        public void onConnectionStateChanged(boolean connected) {
            requireActivity().runOnUiThread(() -> {
                updateConnectionState(connected);
                if (connected) {
                    Toast.makeText(requireContext(), "Connecté via Manager", Toast.LENGTH_SHORT).show();
                }
            });
        }

        @Override
        public void onServicesDiscovered() {
            requireActivity().runOnUiThread(() -> textStatus.setText("Prêt à commander"));
        }
    };

    private void setupClickListeners() {
        buttonConnect.setOnClickListener(v -> {
            if (btManager.isConnected()) {
                btManager.disconnect();
            } else {
                checkPermissionsAndConnect();
            }
        });

        buttonTempPlus.setOnClickListener(v -> {
            if (currentTemperature < 32) {
                currentTemperature++;
                updateTemperatureDisplay();
                if (btManager.isConnected()) {
                    btManager.sendCommand(DaikinCommands.setTemperature(currentTemperature));
                }
            }
        });

        buttonTempMinus.setOnClickListener(v -> {
            if (currentTemperature > 16) {
                currentTemperature--;
                updateTemperatureDisplay();
                if (btManager.isConnected()) {
                    btManager.sendCommand(DaikinCommands.setTemperature(currentTemperature));
                }
            }
        });

        switchOnOff.setOnClickListener(v -> {
            boolean turnOn = switchOnOff.isChecked();
            if (btManager.isConnected()) {
                if (turnOn) {
                    btManager.sendCommand(DaikinCommands.powerOn());
                } else {
                    btManager.sendCommand(DaikinCommands.powerOff());
                }
            } else {
                // Si pas connecté, on remet le switch à sa position précédente visuellement
                switchOnOff.setChecked(!turnOn);
                Toast.makeText(requireContext(), "Non connecté", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void checkPermissionsAndConnect() {
        // Vérification adresse MAC
        if (btManager.getDeviceAddress() == null) {
            Toast.makeText(requireContext(), "Aucun appareil sélectionné. Allez dans 'Scan'", Toast.LENGTH_LONG).show();
            return;
        }

        if (hasPermissions()) {
            connectViaManager();
        } else {
            // Demande les permissions selon la version d'Android
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                requestPermissionsLauncher.launch(new String[]{
                        Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.BLUETOOTH_CONNECT
                });
            } else {
                requestPermissionsLauncher.launch(new String[]{
                        Manifest.permission.BLUETOOTH,
                        Manifest.permission.BLUETOOTH_ADMIN,
                        Manifest.permission.ACCESS_FINE_LOCATION
                });
            }
        }
    }

    private boolean hasPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
                    ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED;
        } else {
            return ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void connectViaManager() {
        textStatus.setText("Connexion en cours...");
        btManager.connect();
    }

    private void updateConnectionState(boolean isConnected) {
        textStatus.setText(isConnected ? "Connecté (" + btManager.getDeviceAddress() + ")" : "Déconnecté");
        buttonConnect.setText(isConnected ? "Déconnecter" : "Connecter");

        // On active/désactive les boutons pour éviter les commandes dans le vide
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
        // Important : retirer le listener pour éviter les fuites de mémoire ou crashs
        if (btManager != null) {
            btManager.removeConnectionListener(connectionListener);
        }
    }
}