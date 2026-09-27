package com.musicbar.overlay;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;

/**
 * Starts the overlay after boot when the user enabled autostart.
 */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Prefs.autostart(context)) {
            return;
        }
        if (!Settings.canDrawOverlays(context)) {
            return;
        }
        try {
            Intent service = new Intent(context, OverlayService.class)
                    .setAction(OverlayService.ACTION_START);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(service);
            } else {
                context.startService(service);
            }
        } catch (Throwable ignored) {
            // Some ROMs block foreground service starts from BOOT_COMPLETED.
        }
    }
}
