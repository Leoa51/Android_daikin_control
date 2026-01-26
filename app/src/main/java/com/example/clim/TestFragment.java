package com.example.clim;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
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
    private Button buttonStartServer;
    private Button buttonStopServer;
    private Button buttonShowIP;
    private Button buttonDebugNetwork;
    private Button buttonSendCustom;

    private ClimBluetoothManager btManager;
    private boolean isConnected = false;
    private boolean serverRunning = false;

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
        buttonStartServer = view.findViewById(R.id.buttonStartServer);
        buttonStopServer = view.findViewById(R.id.buttonStopServer);
        buttonShowIP = view.findViewById(R.id.buttonShowIP);
        buttonDebugNetwork = view.findViewById(R.id.buttonDebugNetwork);
        buttonSendCustom = view.findViewById(R.id.buttonSendCustom);

        createNotificationChannel();

        btManager = ClimBluetoothManager.getInstance(requireContext());

        btManager.addConnectionListener(new ClimBluetoothManager.ConnectionListener() {
            @Override
            public void onConnectionStateChanged(boolean connected) {
                requireActivity().runOnUiThread(() -> {
                    isConnected = connected;
                    updateConnectionState();
                    textStatus.setText(connected ? "Connecté" : "Déconnecté");
                    logMessage(connected ? "Connecté !" : "Déconnecté");
                });
            }

            @Override
            public void onServicesDiscovered() {
                requireActivity().runOnUiThread(() -> {
                    logMessage("Services découverts, prêt à envoyer des commandes");
                });
            }
        });

        btManager.addNotificationListener(data -> {
            requireActivity().runOnUiThread(() -> {
                logMessage("Notification reçue: " + bytesToHex(data));
            });
        });

        buttonConnect.setOnClickListener(v -> {
            if (checkPermissions()) {
                if (btManager.isConnected()) {
                    btManager.disconnect();
                } else {
                    logMessage("Connexion en cours...");
                    btManager.connect();
                }
            } else {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    requestPermissions(new String[]{
                            Manifest.permission.BLUETOOTH_SCAN,
                            Manifest.permission.BLUETOOTH_CONNECT
                    }, REQUEST_PERMISSIONS);
                } else {
                    requestPermissions(new String[]{
                            Manifest.permission.BLUETOOTH,
                            Manifest.permission.BLUETOOTH_ADMIN,
                            Manifest.permission.ACCESS_FINE_LOCATION
                    }, REQUEST_PERMISSIONS);
                }
            }
        });

        buttonTest1.setOnClickListener(v -> {
            logMessage("Envoi commande: Power ON");
            boolean success = btManager.sendCommand(DaikinCommands.powerOn());
            if (!success) {
                logMessage("ERREUR: Échec envoi Power ON");
            }
        });

        buttonTest2.setOnClickListener(v -> {
            logMessage("Envoi commande: Power OFF");
            boolean success = btManager.sendCommand(DaikinCommands.powerOff());
            if (!success) {
                logMessage("ERREUR: Échec envoi Power OFF");
            }
        });

        buttonTest3.setOnClickListener(v -> {
            logMessage("Envoi commande: Mode COOL");
            boolean success = btManager.sendCommand(DaikinCommands.setMode(DaikinCommands.Mode.COOL));
            if (!success) {
                logMessage("ERREUR: Échec envoi Mode COOL");
            }
        });

        buttonTest4.setOnClickListener(v -> {
            logMessage("Envoi commande: Température 24°C");
            boolean success = btManager.sendCommand(DaikinCommands.setTemperature(24));
            if (!success) {
                logMessage("ERREUR: Échec envoi Température");
            }
        });

        buttonTestNotif.setOnClickListener(v -> sendTestNotification());

        buttonStartServer.setOnClickListener(v -> startHttpServer());
        buttonStopServer.setOnClickListener(v -> stopHttpServer());
        buttonShowIP.setOnClickListener(v -> showServerInfo());
        buttonDebugNetwork.setOnClickListener(v -> showAllNetworkInterfaces());

        buttonSendCustom.setOnClickListener(v -> {
            String hex = editHexCommand.getText().toString().trim();
            if (!hex.isEmpty()) {
                sendCustomCommand(hex);
            } else {
                Toast.makeText(requireContext(), "Entrez une commande hex", Toast.LENGTH_SHORT).show();
            }
        });

        updateConnectionState();
        updateServerButtons();
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
                .setContentTitle("Test Notification")
                .setContentText("Ceci est une notification de test depuis TestFragment")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(requireContext());
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            notificationManager.notify(1, builder.build());
            logMessage("Notification envoyée");
        }
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
                logMessage("Permissions accordées, connexion...");
                btManager.connect();
            } else {
                Toast.makeText(requireContext(), "Permissions refusées", Toast.LENGTH_SHORT).show();
                logMessage("Permissions refusées");
            }
        } else if (requestCode == REQUEST_NOTIFICATION_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                sendTestNotification();
            } else {
                Toast.makeText(requireContext(), "Permission notification refusée", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void sendCustomCommand(String hexString) {
        if (!btManager.isConnected()) {
            Toast.makeText(requireContext(), "Non connecté", Toast.LENGTH_SHORT).show();
            logMessage("ERREUR: Non connecté");
            return;
        }

        try {
            byte[] command = hexStringToByteArray(hexString);
            logMessage("Envoi commande custom: " + hexString);
            boolean success = btManager.sendCommand(command);
            if (!success) {
                logMessage("ERREUR: Échec envoi commande custom");
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

    private void startHttpServer() {
        Intent serviceIntent = new Intent(requireContext(), NotificationHttpService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            requireContext().startForegroundService(serviceIntent);
        } else {
            requireContext().startService(serviceIntent);
        }
        serverRunning = true;
        updateServerButtons();
        logMessage("Serveur HTTP démarré");
        showServerInfo();
    }

    private void stopHttpServer() {
        Intent serviceIntent = new Intent(requireContext(), NotificationHttpService.class);
        requireContext().stopService(serviceIntent);
        serverRunning = false;
        updateServerButtons();
        logMessage("Serveur HTTP arrêté");
    }

    private void showServerInfo() {
        String ip = getWifiIpAddress();
        if (ip != null) {
            String info = "Serveur accessible:\n" +
                    "Notification: http://" + ip + ":8080/notify?title=Test&message=Hello\n" +
                    "Clim: http://" + ip + ":8080/clim?command=ON";
            logMessage(info);
            Toast.makeText(requireContext(), "IP: " + ip, Toast.LENGTH_LONG).show();
        } else {
            logMessage("Impossible de récupérer l'adresse IP WiFi");
            Toast.makeText(requireContext(), "WiFi non connecté", Toast.LENGTH_SHORT).show();
        }
    }

    private void showAllNetworkInterfaces() {
        try {
            logMessage("=== Interfaces réseau détectées ===");
            java.util.Enumeration<java.net.NetworkInterface> interfaces = java.net.NetworkInterface.getNetworkInterfaces();

            while (interfaces.hasMoreElements()) {
                java.net.NetworkInterface netInterface = interfaces.nextElement();
                String name = netInterface.getName();

                logMessage("Interface: " + name);

                java.util.Enumeration<java.net.InetAddress> addresses = netInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    java.net.InetAddress addr = addresses.nextElement();
                    if (addr instanceof java.net.Inet4Address && !addr.isLoopbackAddress()) {
                        logMessage("  -> IP: " + addr.getHostAddress());
                    }
                }
            }
            logMessage("===================================");
        } catch (Exception e) {
            logMessage("Erreur: " + e.getMessage());
        }
    }

    private String getWifiIpAddress() {
        try {
            java.net.NetworkInterface netInterface = java.net.NetworkInterface.getByName("wlan0");
            if (netInterface != null) {
                java.util.Enumeration<java.net.InetAddress> addresses = netInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    java.net.InetAddress addr = addresses.nextElement();
                    if (!addr.isLoopbackAddress() && addr instanceof java.net.Inet4Address) {
                        return addr.getHostAddress();
                    }
                }
            }

            java.net.NetworkInterface apInterface = java.net.NetworkInterface.getByName("ap0");
            if (apInterface != null) {
                java.util.Enumeration<java.net.InetAddress> addresses = apInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    java.net.InetAddress addr = addresses.nextElement();
                    if (!addr.isLoopbackAddress() && addr instanceof java.net.Inet4Address) {
                        return addr.getHostAddress();
                    }
                }
            }

            android.net.wifi.WifiManager wifiManager = (android.net.wifi.WifiManager)
                    requireContext().getApplicationContext().getSystemService(android.content.Context.WIFI_SERVICE);
            if (wifiManager != null) {
                android.net.wifi.WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                int ip = wifiInfo.getIpAddress();

                if (ip != 0) {
                    return String.format("%d.%d.%d.%d",
                            (ip & 0xff),
                            (ip >> 8 & 0xff),
                            (ip >> 16 & 0xff),
                            (ip >> 24 & 0xff));
                }
            }
        } catch (Exception e) {
            android.util.Log.e("TestFragment", "Erreur IP: " + e.getMessage());
        }
        return null;
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

            final android.widget.ScrollView scrollView = (android.widget.ScrollView) textLog.getParent();
            scrollView.post(() -> scrollView.fullScroll(android.view.View.FOCUS_DOWN));
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

    private void updateServerButtons() {
        buttonStartServer.setEnabled(!serverRunning);
        buttonStopServer.setEnabled(serverRunning);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
    }
}