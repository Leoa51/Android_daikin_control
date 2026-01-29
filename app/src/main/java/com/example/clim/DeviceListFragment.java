package com.example.clim;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import java.util.ArrayList;

public class DeviceListFragment extends Fragment {

    private ListView listView;
    private Button buttonScan;
    private ArrayList<String> deviceStrings = new ArrayList<>();
    private ArrayList<BluetoothDevice> bluetoothDevices = new ArrayList<>();
    private ArrayAdapter<String> adapter;

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bluetoothLeScanner;
    private boolean isScanning = false;
    private Handler handler = new Handler();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_device_list, container, false);
        listView = view.findViewById(R.id.listViewDevices);
        buttonScan = view.findViewById(R.id.buttonScan);

        adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_list_item_1, deviceStrings);
        listView.setAdapter(adapter);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter != null) {
            bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
        }

        buttonScan.setOnClickListener(v -> startScan());

        listView.setOnItemClickListener((parent, v, position, id) -> {
            BluetoothDevice device = bluetoothDevices.get(position);
            stopScan();

            ClimBluetoothManager btManager = ClimBluetoothManager.getInstance(requireContext());

            btManager.setDeviceAddress(device.getAddress());


            btManager.connect();

            // 3. Naviguer vers le contrôle
            Navigation.findNavController(v).navigate(R.id.homeFragment);

            Toast.makeText(requireContext(), "Cible définie : " + device.getAddress(), Toast.LENGTH_SHORT).show();
        });

        return view;
    }

    private void startScan() {
        if (isScanning || bluetoothLeScanner == null) return;

        deviceStrings.clear();
        bluetoothDevices.clear();
        adapter.notifyDataSetChanged();

        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        isScanning = true;
        buttonScan.setText("Recherche...");
        handler.postDelayed(this::stopScan, 10000);
        bluetoothLeScanner.startScan(scanCallback);
    }

    private void stopScan() {
        if (!isScanning) return;
        isScanning = false;
        buttonScan.setText("Lancer le Scan");
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
            bluetoothLeScanner.stopScan(scanCallback);
        }
    }

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice device = result.getDevice();
            if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return;

            String name = device.getName();
            if (name == null) name = "Appareil Inconnu";

            if (!bluetoothDevices.contains(device)) {
                // Filtre optionnel pour Madoka
                if (name.contains("Madoka") || device.getAddress().startsWith("00:CC:3F")) {
                    bluetoothDevices.add(device);
                    deviceStrings.add(name + "\n" + device.getAddress());
                    adapter.notifyDataSetChanged();
                }
            }
        }
    };
}