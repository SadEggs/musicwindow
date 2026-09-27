package com.musicbar.overlay;

import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

/**
 * This service exists only to obtain the right to call
 * MediaSessionManager.getActiveSessions(). We do not read notification content.
 */
public class MediaListenerService extends NotificationListenerService {

    private static volatile MediaListenerService instance;

    public static MediaListenerService get() {
        return instance;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
    }

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        instance = this;
        MediaBridge.get().onListenerChanged(getApplicationContext());
    }

    @Override
    public void onListenerDisconnected() {
        super.onListenerDisconnected();
        instance = null;
        MediaBridge.get().onListenerChanged(getApplicationContext());
    }

    @Override
    public void onDestroy() {
        instance = null;
        super.onDestroy();
        MediaBridge.get().onListenerChanged(getApplicationContext());
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        // Poweramp updates its media notification on track changes; use it as a cheap hint.
        MediaBridge.get().requestRefresh();
    }
}
