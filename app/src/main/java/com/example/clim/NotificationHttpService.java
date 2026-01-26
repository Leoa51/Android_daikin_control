package com.example.clim;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;

public class NotificationHttpService extends Service {
    private static final String TAG = "HTTPService";
    private static final int PORT = 8080;
    private static final String CHANNEL_ID = "http_service_channel";
    private static final String NOTIF_CHANNEL_ID = "wifi_notif_channel";

    private ServerSocket serverSocket;
    private Thread serverThread;
    private boolean running = false;
    private ClimBluetoothManager btManager;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannels();
        startForeground(1, createForegroundNotification());

        btManager = ClimBluetoothManager.getInstance(this);

        btManager.addConnectionListener(new ClimBluetoothManager.ConnectionListener() {
            @Override
            public void onConnectionStateChanged(boolean connected) {
                Log.d(TAG, "BT connexion: " + connected);
                updateForegroundNotification(connected);
            }

            @Override
            public void onServicesDiscovered() {
                Log.d(TAG, "BT prêt");
            }
        });

        new android.os.Handler().postDelayed(() -> {
            if (!btManager.isConnected()) {
                Log.d(TAG, "Auto-connexion BT...");
                btManager.connect();
            }
        }, 1000);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!running) {
            startServer();
        }
        return START_STICKY;
    }

    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Serveur HTTP",
                    NotificationManager.IMPORTANCE_LOW
            );
            serviceChannel.setDescription("Serveur de notifications local");

            NotificationChannel notifChannel = new NotificationChannel(
                    NOTIF_CHANNEL_ID,
                    "Notifications WiFi",
                    NotificationManager.IMPORTANCE_HIGH
            );
            notifChannel.setDescription("Notifications reçues via WiFi");

            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(serviceChannel);
            manager.createNotificationChannel(notifChannel);
        }
    }

    private Notification createForegroundNotification() {
        String ipAddress = getWifiIpAddress();
        String text = ipAddress != null
                ? "Serveur: http://" + ipAddress + ":" + PORT
                : "Serveur actif sur le port " + PORT;

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Serveur HTTP démarré")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    private void updateForegroundNotification(boolean btConnected) {
        String ipAddress = getWifiIpAddress();
        String text = ipAddress != null
                ? "Serveur: http://" + ipAddress + ":" + PORT
                : "Serveur actif sur le port " + PORT;

        if (btConnected) {
            text += " | BT: ✓";
        } else {
            text += " | BT: ✗";
        }

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Serveur HTTP démarré")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();

        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        manager.notify(1, notification);
    }

    private void startServer() {
        serverThread = new Thread(() -> {
            try {
                serverSocket = new ServerSocket(PORT);
                running = true;
                String ip = getWifiIpAddress();
                Log.d(TAG, "Serveur démarré sur " + (ip != null ? ip : "localhost") + ":" + PORT);

                while (running) {
                    try {
                        Socket client = serverSocket.accept();
                        handleClient(client);
                    } catch (IOException e) {
                        if (running) {
                            Log.e(TAG, "Erreur connexion client: " + e.getMessage());
                        }
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, "Erreur serveur: " + e.getMessage());
            }
        });
        serverThread.start();
    }

    private void handleClient(Socket client) {
        new Thread(() -> {
            try {
                BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
                PrintWriter out = new PrintWriter(client.getOutputStream(), true);

                String line = in.readLine();
                Log.d(TAG, "Requête reçue: " + line);

                if (line == null) {
                    client.close();
                    return;
                }

                String[] parts = line.split(" ");
                if (parts.length < 2) {
                    sendResponse(out, 400, "Bad Request");
                    client.close();
                    return;
                }

                String method = parts[0];
                String path = parts[1];

                if (method.equals("GET") && path.startsWith("/notify")) {
                    handleNotifyRequest(path, out);
                } else if (method.equals("GET") && path.equals("/status")) {
                    handleStatusRequest(out);
                } else if (method.equals("GET") && path.startsWith("/clim")) {
                    handleClimRequest(path, out);
                } else {
                    sendResponse(out, 404, "Not Found");
                }

                client.close();
            } catch (Exception e) {
                Log.e(TAG, "Erreur traitement client: " + e.getMessage());
            }
        }).start();
    }

    private void handleNotifyRequest(String path, PrintWriter out) {
        try {
            String title = "Notification WiFi";
            String message = "Message reçu";

            if (path.contains("?")) {
                String query = path.split("\\?", 2)[1];
                String[] params = query.split("&");

                for (String param : params) {
                    String[] keyValue = param.split("=", 2);
                    if (keyValue.length == 2) {
                        String key = URLDecoder.decode(keyValue[0], "UTF-8");
                        String value = URLDecoder.decode(keyValue[1], "UTF-8");

                        if (key.equals("title")) {
                            title = value;
                        } else if (key.equals("message") || key.equals("body")) {
                            message = value;
                        }
                    }
                }
            }

            sendNotification(title, message);
            sendResponse(out, 200, "Notification envoyée: " + title);

        } catch (Exception e) {
            Log.e(TAG, "Erreur parsing paramètres: " + e.getMessage());
            sendResponse(out, 500, "Erreur: " + e.getMessage());
        }
    }

    private void handleClimRequest(String path, PrintWriter out) {
        try {
            String command = null;
            String parameter = null;

            if (path.contains("?")) {
                String query = path.split("\\?", 2)[1];
                String[] params = query.split("&");

                for (String param : params) {
                    String[] keyValue = param.split("=", 2);
                    if (keyValue.length == 2) {
                        String key = URLDecoder.decode(keyValue[0], "UTF-8");
                        String value = URLDecoder.decode(keyValue[1], "UTF-8");

                        if (key.equals("command")) {
                            command = value;
                        } else if (key.equals("parameter")) {
                            parameter = value;
                        }
                    }
                }
            }

            if (command == null) {
                sendResponse(out, 400, "Erreur: paramètre 'command' manquant");
                return;
            }

            if (!btManager.isConnected()) {
                sendResponse(out, 503, "Erreur: Clim non connectée (reconnexion en cours...)");
                btManager.connect();
                return;
            }

            boolean success = false;
            String response = "Commande inconnue";

            switch (command.toUpperCase()) {
                case "ON":
                    success = btManager.sendCommand(DaikinCommands.powerOn());
                    response = "Clim allumée";
                    break;

                case "OFF":
                    success = btManager.sendCommand(DaikinCommands.powerOff());
                    response = "Clim éteinte";
                    break;

                case "MODE":
                    if (parameter == null) {
                        sendResponse(out, 400, "Erreur: paramètre 'parameter' requis pour MODE (AUTO, COOL, HEAT, FAN, DRY)");
                        return;
                    }
                    switch (parameter.toUpperCase()) {
                        case "AUTO":
                            success = btManager.sendCommand(DaikinCommands.setMode(DaikinCommands.Mode.AUTO));
                            response = "Mode AUTO activé";
                            break;
                        case "COOL":
                            success = btManager.sendCommand(DaikinCommands.setMode(DaikinCommands.Mode.COOL));
                            response = "Mode COOL activé";
                            break;
                        case "HEAT":
                            success = btManager.sendCommand(DaikinCommands.setMode(DaikinCommands.Mode.HEAT));
                            response = "Mode HEAT activé";
                            break;
                        case "FAN":
                            success = btManager.sendCommand(DaikinCommands.setMode(DaikinCommands.Mode.FAN));
                            response = "Mode FAN activé";
                            break;
                        case "DRY":
                            success = btManager.sendCommand(DaikinCommands.setMode(DaikinCommands.Mode.DRY));
                            response = "Mode DRY activé";
                            break;
                        default:
                            sendResponse(out, 400, "Erreur: mode invalide (AUTO, COOL, HEAT, FAN, DRY)");
                            return;
                    }
                    break;

                case "TEMP":
                case "TEMPERATURE":
                    if (parameter == null) {
                        sendResponse(out, 400, "Erreur: paramètre 'parameter' requis pour TEMP (16-32)");
                        return;
                    }
                    try {
                        int temp = Integer.parseInt(parameter);
                        if (temp < 16 || temp > 32) {
                            sendResponse(out, 400, "Erreur: température doit être entre 16 et 32°C");
                            return;
                        }
                        success = btManager.sendCommand(DaikinCommands.setTemperature(temp));
                        response = "Température réglée à " + temp + "°C";
                    } catch (NumberFormatException e) {
                        sendResponse(out, 400, "Erreur: température invalide");
                        return;
                    }
                    break;

                case "FAN":
                case "FANSPEED":
                    if (parameter == null) {
                        sendResponse(out, 400, "Erreur: paramètre 'parameter' requis pour FAN (AUTO, SILENT, 1-5)");
                        return;
                    }
                    switch (parameter.toUpperCase()) {
                        case "AUTO":
                            success = btManager.sendCommand(DaikinCommands.setFanSpeed(DaikinCommands.FanSpeed.AUTO));
                            response = "Ventilateur AUTO";
                            break;
                        case "SILENT":
                            success = btManager.sendCommand(DaikinCommands.setFanSpeed(DaikinCommands.FanSpeed.SILENT));
                            response = "Ventilateur SILENCIEUX";
                            break;
                        case "1":
                            success = btManager.sendCommand(DaikinCommands.setFanSpeed(DaikinCommands.FanSpeed.LEVEL1));
                            response = "Ventilateur niveau 1";
                            break;
                        case "2":
                            success = btManager.sendCommand(DaikinCommands.setFanSpeed(DaikinCommands.FanSpeed.LEVEL2));
                            response = "Ventilateur niveau 2";
                            break;
                        case "3":
                            success = btManager.sendCommand(DaikinCommands.setFanSpeed(DaikinCommands.FanSpeed.LEVEL3));
                            response = "Ventilateur niveau 3";
                            break;
                        case "4":
                            success = btManager.sendCommand(DaikinCommands.setFanSpeed(DaikinCommands.FanSpeed.LEVEL4));
                            response = "Ventilateur niveau 4";
                            break;
                        case "5":
                            success = btManager.sendCommand(DaikinCommands.setFanSpeed(DaikinCommands.FanSpeed.LEVEL5));
                            response = "Ventilateur niveau 5";
                            break;
                        default:
                            sendResponse(out, 400, "Erreur: vitesse ventilateur invalide (AUTO, SILENT, 1-5)");
                            return;
                    }
                    break;

                case "SWING":
                    if (parameter == null) {
                        sendResponse(out, 400, "Erreur: paramètre 'parameter' requis pour SWING (STOP, ON, 1-5)");
                        return;
                    }
                    switch (parameter.toUpperCase()) {
                        case "STOP":
                            success = btManager.sendCommand(DaikinCommands.setSwing(DaikinCommands.Swing.STOP));
                            response = "Oscillation arrêtée";
                            break;
                        case "ON":
                        case "OSCILLATE":
                            success = btManager.sendCommand(DaikinCommands.setSwing(DaikinCommands.Swing.OSCILLATE));
                            response = "Oscillation activée";
                            break;
                        case "1":
                            success = btManager.sendCommand(DaikinCommands.setSwing(DaikinCommands.Swing.POS1));
                            response = "Position 1 (haut)";
                            break;
                        case "2":
                            success = btManager.sendCommand(DaikinCommands.setSwing(DaikinCommands.Swing.POS2));
                            response = "Position 2";
                            break;
                        case "3":
                            success = btManager.sendCommand(DaikinCommands.setSwing(DaikinCommands.Swing.POS3));
                            response = "Position 3 (milieu)";
                            break;
                        case "4":
                            success = btManager.sendCommand(DaikinCommands.setSwing(DaikinCommands.Swing.POS4));
                            response = "Position 4";
                            break;
                        case "5":
                            success = btManager.sendCommand(DaikinCommands.setSwing(DaikinCommands.Swing.POS5));
                            response = "Position 5 (bas)";
                            break;
                        default:
                            sendResponse(out, 400, "Erreur: position swing invalide (STOP, ON, 1-5)");
                            return;
                    }
                    break;

                case "POWERFUL":
                    if (parameter == null) {
                        sendResponse(out, 400, "Erreur: paramètre 'parameter' requis pour POWERFUL (ON, OFF)");
                        return;
                    }
                    boolean powerfulOn = parameter.equalsIgnoreCase("ON") || parameter.equals("1");
                    success = btManager.sendCommand(DaikinCommands.setPowerful(powerfulOn));
                    response = "Mode Powerful " + (powerfulOn ? "activé" : "désactivé");
                    break;

                case "ECONO":
                    if (parameter == null) {
                        sendResponse(out, 400, "Erreur: paramètre 'parameter' requis pour ECONO (ON, OFF)");
                        return;
                    }
                    boolean econoOn = parameter.equalsIgnoreCase("ON") || parameter.equals("1");
                    success = btManager.sendCommand(DaikinCommands.setEcono(econoOn));
                    response = "Mode Econo " + (econoOn ? "activé" : "désactivé");
                    break;

                case "STREAMER":
                    if (parameter == null) {
                        sendResponse(out, 400, "Erreur: paramètre 'parameter' requis pour STREAMER (ON, OFF)");
                        return;
                    }
                    boolean streamerOn = parameter.equalsIgnoreCase("ON") || parameter.equals("1");
                    success = btManager.sendCommand(DaikinCommands.setStreamer(streamerOn));
                    response = "Mode Streamer " + (streamerOn ? "activé" : "désactivé");
                    break;

                case "COMFORT":
                    if (parameter == null) {
                        sendResponse(out, 400, "Erreur: paramètre 'parameter' requis pour COMFORT (ON, OFF)");
                        return;
                    }
                    boolean comfortOn = parameter.equalsIgnoreCase("ON") || parameter.equals("1");
                    success = btManager.sendCommand(DaikinCommands.setComfort(comfortOn));
                    response = "Mode Comfort " + (comfortOn ? "activé" : "désactivé");
                    break;

                case "BRIGHTNESS":
                    if (parameter == null) {
                        sendResponse(out, 400, "Erreur: paramètre 'parameter' requis pour BRIGHTNESS (0-3)");
                        return;
                    }
                    try {
                        int brightness = Integer.parseInt(parameter);
                        if (brightness < 0 || brightness > 3) {
                            sendResponse(out, 400, "Erreur: luminosité doit être entre 0 et 3");
                            return;
                        }
                        success = btManager.sendCommand(DaikinCommands.setBrightness(brightness));
                        response = "Luminosité réglée à " + brightness;
                    } catch (NumberFormatException e) {
                        sendResponse(out, 400, "Erreur: luminosité invalide");
                        return;
                    }
                    break;

                case "LOCK":
                case "CHILDLOCK":
                    if (parameter == null) {
                        sendResponse(out, 400, "Erreur: paramètre 'parameter' requis pour LOCK (ON, OFF)");
                        return;
                    }
                    boolean lockOn = parameter.equalsIgnoreCase("ON") || parameter.equals("1");
                    success = btManager.sendCommand(DaikinCommands.setChildLock(lockOn));
                    response = "Verrouillage enfant " + (lockOn ? "activé" : "désactivé");
                    break;

                case "READ":
                case "STATUS":
                    success = btManager.sendCommand(DaikinCommands.readStatus());
                    response = "Lecture de l'état en cours";
                    break;

                case "READTEMP":
                case "ROOMTEMP":
                    success = btManager.sendCommand(DaikinCommands.readRoomTemperature());
                    response = "Lecture de la température ambiante";
                    break;

                case "OUTDOORTEMP":
                    success = btManager.sendCommand(DaikinCommands.readOutdoorTemperature());
                    response = "Lecture de la température extérieure";
                    break;

                default:
                    sendResponse(out, 400, "Erreur: commande inconnue - " + command);
                    return;
            }

            if (success) {
                sendResponse(out, 200, response);
                Log.d(TAG, "Commande BLE exécutée: " + response);
            } else {
                sendResponse(out, 500, "Échec d'exécution: " + response);
                Log.e(TAG, "Échec commande BLE: " + response);
            }

        } catch (Exception e) {
            Log.e(TAG, "Erreur parsing paramètres: " + e.getMessage());
            sendResponse(out, 500, "Erreur: " + e.getMessage());
        }
    }

    private void handleStatusRequest(PrintWriter out) {
        String ip = getWifiIpAddress();
        boolean btConnected = btManager.isConnected();

        String status = "{\n" +
                "  \"status\": \"running\",\n" +
                "  \"ip\": \"" + (ip != null ? ip : "unknown") + "\",\n" +
                "  \"port\": " + PORT + ",\n" +
                "  \"bluetooth\": " + (btConnected ? "\"connected\"" : "\"disconnected\"") + "\n" +
                "}";

        sendJsonResponse(out, 200, status);
    }

    private void sendResponse(PrintWriter out, int statusCode, String message) {
        String statusText = statusCode == 200 ? "OK" :
                statusCode == 400 ? "Bad Request" :
                        statusCode == 404 ? "Not Found" :
                                statusCode == 503 ? "Service Unavailable" : "Internal Server Error";

        out.println("HTTP/1.1 " + statusCode + " " + statusText);
        out.println("Content-Type: text/plain; charset=UTF-8");
        out.println("Access-Control-Allow-Origin: *");
        out.println("Connection: close");
        out.println();
        out.println(message);
    }

    private void sendJsonResponse(PrintWriter out, int statusCode, String json) {
        out.println("HTTP/1.1 " + statusCode + " OK");
        out.println("Content-Type: application/json; charset=UTF-8");
        out.println("Access-Control-Allow-Origin: *");
        out.println("Connection: close");
        out.println();
        out.println(json);
    }

    private void sendNotification(String title, String message) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, NOTIF_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);

        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify((int) System.currentTimeMillis(), builder.build());

        Log.d(TAG, "Notification envoyée: " + title + " - " + message);
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

            WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wifiManager != null) {
                WifiInfo wifiInfo = wifiManager.getConnectionInfo();
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
            Log.e(TAG, "Erreur récupération IP: " + e.getMessage());
        }
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopServer();
    }

    private void stopServer() {
        running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            Log.e(TAG, "Erreur fermeture serveur: " + e.getMessage());
        }
        Log.d(TAG, "Serveur arrêté");
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}