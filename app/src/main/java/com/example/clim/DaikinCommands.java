package com.example.clim;

public class DaikinCommands {


    public static byte[] powerOn() {
        return new byte[]{0x00, 0x07, 0x00, 0x40, 0x20, 0x20, 0x01, 0x01};
    }

    public static byte[] powerOff() {
        return new byte[]{0x00, 0x07, 0x00, 0x40, 0x20, 0x20, 0x01, 0x00};
    }

    public static byte[] setMode(Mode mode) {
        return new byte[]{0x00, 0x07, 0x00, 0x40, 0x30, 0x20, 0x01, mode.value};
    }

    public enum Mode {
        FAN((byte)0x00),
        DRY((byte)0x01),
        AUTO((byte)0x02),
        COOL((byte)0x03),
        HEAT((byte)0x04),
        VENTILATION((byte)0x05);

        final byte value;
        Mode(byte value) { this.value = value; }
    }

    public static byte[] setTemperature(int celsius) {
        if (celsius < 16) celsius = 16;
        if (celsius > 32) celsius = 32;

        int raw = celsius * 128;
        byte hi = (byte) ((raw >> 8) & 0xFF);
        byte lo = (byte) (raw & 0xFF);

//        Log.d("packet", "setTemperature() called with: celsius = [" + celsius + "] hi = [" + hi + "] lo = [" + lo + "]");

        return new byte[]{0x00, 0x08, 0x00, 0x40, 0x40, 0x20, 0x02, hi, lo};

    }

    public static byte[] setCoolFanSpeed(FanSpeed speed) {
        return new byte[]{0x00, 0x07, 0x00, 0x40, 0x50, 0x20, 0X01, speed.value};
    }

    public static byte[] setHeetFanSpeed(FanSpeed speed) {
        return new byte[]{0x00, 0x07, 0x00, 0x40, 0x50, 0x20, 0X01, speed.value};
    }


    public enum FanSpeed {
//        AUTO((byte)0x00),
//        SILENT((byte)0x01),
        LOW((byte)0x01),
        MEDIUM_LOW((byte)0x02),
        MEDIUM((byte)0x03),
        MEDIUM_HIGH((byte)0x04),
        HIGH((byte)0x05);

        final byte value;
        FanSpeed(byte value) { this.value = value; }
    }

    public static byte[] setSwing(Swing swing) {
        return new byte[]{0x00, 0x07, 0x00, 0x21, 0x20, 0x05, swing.value};
    }

    public enum Swing {
        STOP((byte)0x00),
        OSCILLATE((byte)0x01),
        POS1((byte)0x02),
        POS2((byte)0x03),
        POS3((byte)0x04),
        POS4((byte)0x05),
        POS5((byte)0x06);

        final byte value;
        Swing(byte value) { this.value = value; }
    }

    public static byte[] setPowerful(boolean enabled) {
        // ID 0x21 (Options), Field 0x01 (Powerful)
        return new byte[]{0x00, 0x07, 0x00, 0x21, 0x21, 0x01, (byte)(enabled ? 0x01 : 0x00)};
    }

    public static byte[] setEcono(boolean enabled) {
        return new byte[]{0x00, 0x07, 0x00, 0x21, 0x21, 0x02, (byte)(enabled ? 0x01 : 0x00)};
    }

    public static byte[] setStreamer(boolean enabled) {
        return new byte[]{0x00, 0x07, 0x00, 0x21, 0x21, 0x03, (byte)(enabled ? 0x01 : 0x00)};
    }

    public static byte[] setComfort(boolean enabled) {
        return new byte[]{0x00, 0x07, 0x00, 0x21, 0x21, 0x04, (byte)(enabled ? 0x01 : 0x00)};
    }

    public static byte[] setBrightness(int level) {
        if (level < 0) level = 0;
        if (level > 3) level = 3;
        // Fonction 0x11 (Device settings)
        return new byte[]{0x00, 0x07, 0x00, 0x11, 0x10, 0x30, (byte)level};
    }

    public static byte[] setChildLock(boolean enabled) {
        return new byte[]{0x00, 0x07, 0x00, 0x11, 0x10, 0x10, (byte)(enabled ? 0x01 : 0x00)};
    }

    public static byte[] readStatus() {
        return new byte[]{0x00, 0x06, 0x00, 0x00, 0x00, 0x00, 0x00};
    }

    public static byte[] readRoomTemperature() {
        // Lecture des capteurs (0x20), Capteur Interne (0x20), Champ 0x60
        return new byte[]{0x00, 0x06, 0x00, 0x20, 0x20, 0x60, 0x01};
    }

    public static byte[] readOutdoorTemperature() {
        // Capteur Extérieur (0x60)
        return new byte[]{0x00, 0x06, 0x00, 0x20, 0x60, 0x60, 0x01};
    }
}