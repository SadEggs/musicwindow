package com.musicbar.overlay;

import android.app.Notification;
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

    /**
     * The player's own media notification, if it is posted right now. Its action buttons
     * are the player's real controls - including the shuffle toggle, which it does not
     * publish through the media session API at all.
     */
    public Notification findNotification(String pkg) {
        if (pkg == null) {
            return null;
        }
        try {
            StatusBarNotification[] active = getActiveNotifications();
            if (active == null) {
                return null;
            }
            for (StatusBarNotification sbn : active) {
                if (pkg.equals(sbn.getPackageName())) {
                    return sbn.getNotification();
                }
            }
        } catch (Throwable ignored) {
            // Notification access can be revoked at any moment.
        }
        return null;
    }
}
