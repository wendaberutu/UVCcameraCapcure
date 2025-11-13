package com.waleta.research1;

import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.usb.UsbDevice;

import java.util.Map;

public class UsbCameraMap {

    private static final String PREF_NAME = "usb_camera_map";

    private static SharedPreferences getPrefs(Context ctx) {
        return ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    // KEY kamera = vendorId_productId_serial(optional)
    private static String getKey(UsbDevice dev) {
        if (dev.getSerialNumber() != null) {
            return dev.getVendorId() + "_" + dev.getProductId() + "_" + dev.getSerialNumber();
        }
        return dev.getVendorId() + "_" + dev.getProductId();
    }

    // Simpan slot
    public static void saveIndexForDevice(Context ctx, UsbDevice dev, int slot) {
        getPrefs(ctx).edit().putInt(getKey(dev), slot).apply();
    }

    // Ambil slot kamera (jika belum ada → -1)
    public static int getIndexForDevice(Context ctx, UsbDevice dev) {
        return getPrefs(ctx).getInt(getKey(dev), -1);
    }

    // CARI slot bebas (0–2)
    public static int getFreeSlot(Context ctx) {
        SharedPreferences prefs = getPrefs(ctx);
        boolean[] used = new boolean[3];

        for (Object value : prefs.getAll().values()) {
            int slot = (int) value;
            if (slot >= 0 && slot < 3) used[slot] = true;
        }

        for (int i = 0; i < 3; i++) {
            if (!used[i]) return i;
        }

        return 0; // fallback
    }

    // Reset 1 kamera
    public static void clearDevice(Context ctx, UsbDevice dev) {
        getPrefs(ctx).edit().remove(getKey(dev)).apply();
    }

    // Reset semua mapping
    public static void clearAll(Context ctx) {
        getPrefs(ctx).edit().clear().apply();
    }
}
