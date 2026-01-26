package com.example.clim;

public class DaikinCommands {

    public static byte[] powerOn() {
        return new byte[]{0x00, 0x07, 0x00, 0x40, 0x20, 0x20, 0x01, 0x01};
    }

    public static byte[] powerOff() {
        return new byte[]{0x00, 0x07, 0x00, 0x40, 0x20, 0x20, 0x01, 0x00};
    }

    public static byte[] setMode(Mode mode) {
        return new byte[]{0x00, 0x07, 0x00, 0x40, 0x20, 0x20, 0x02, mode.value};
    }

    public enum Mode {
        AUTO((byte)0x00),
        COOL((byte)0x02),
        HEAT((byte)0x04),
        FAN((byte)0x06),
        DRY((byte)0x08);

        final byte value;
        Mode(byte value) { this.value = value; }
    }

    public static byte[] setTemperature(int celsius) {
        if (celsius < 16) celsius = 16;
        if (celsius > 32) celsius = 32;
        return new byte[]{0x00, 0x07, 0x00, 0x40, 0x20, 0x20, 0x03, (byte)(celsius * 2)};
    }

    public static byte[] setFanSpeed(FanSpeed speed) {
        return new byte[]{0x00, 0x07, 0x00, 0x40, 0x20, 0x20, 0x04, speed.value};
    }

    public enum FanSpeed {
        AUTO((byte)0x00),
        SILENT((byte)0x01),
        LEVEL1((byte)0x03),
        LEVEL2((byte)0x05),
        LEVEL3((byte)0x06),
        LEVEL4((byte)0x07),
        LEVEL5((byte)0x0A);

        final byte value;
        FanSpeed(byte value) { this.value = value; }
    }

    public static byte[] setSwing(Swing swing) {
        return new byte[]{0x00, 0x07, 0x00, 0x40, 0x20, 0x20, 0x05, swing.value};
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
        return new byte[]{0x00, 0x07, 0x00, 0x40, 0x21, 0x20, 0x01, (byte)(enabled ? 0x01 : 0x00)};
    }

    public static byte[] setEcono(boolean enabled) {
        return new byte[]{0x00, 0x07, 0x00, 0x40, 0x21, 0x20, 0x02, (byte)(enabled ? 0x01 : 0x00)};
    }

    public static byte[] setStreamer(boolean enabled) {
        return new byte[]{0x00, 0x07, 0x00, 0x40, 0x21, 0x20, 0x03, (byte)(enabled ? 0x01 : 0x00)};
    }

    public static byte[] setComfort(boolean enabled) {
        return new byte[]{0x00, 0x07, 0x00, 0x40, 0x21, 0x20, 0x04, (byte)(enabled ? 0x01 : 0x00)};
    }

    public static byte[] setBrightness(int level) {
        if (level < 0) level = 0;
        if (level > 3) level = 3;
        return new byte[]{0x00, 0x07, 0x00, 0x40, 0x10, 0x30, 0x02, (byte)level};
    }

    public static byte[] setChildLock(boolean enabled) {
        return new byte[]{0x00, 0x07, 0x00, 0x40, 0x10, 0x10, 0x03, (byte)(enabled ? 0x01 : 0x00)};
    }

    public static byte[] readStatus() {
        return new byte[]{0x00, 0x06, 0x00, 0x00, 0x00, 0x00, 0x00};
    }

    public static byte[] readRoomTemperature() {
        return new byte[]{0x00, 0x06, 0x00, 0x20, 0x20, 0x60, 0x01};
    }

    public static byte[] readOutdoorTemperature() {
        return new byte[]{0x00, 0x06, 0x00, 0x20, 0x60, 0x60, 0x01};
    }
}