package com.waleta.research1;

import android.content.Context;
import android.content.SharedPreferences;

public class UsbPermissionStore {

    private static final String PREF = "usb_perm";
    private static final String KEY_ALLOWED = "is_allowed";

    public static void saveAllowed(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        sp.edit().putBoolean(KEY_ALLOWED, true).apply();
    }

    public static boolean isAllowed(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        return sp.getBoolean(KEY_ALLOWED, false);
    }
}
