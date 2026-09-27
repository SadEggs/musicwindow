package com.musicbar.overlay;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.graphics.drawable.Icon;
import android.hardware.display.DisplayManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.WindowManager;

/**
 * Foreground service that owns the overlay window.
 */
public class OverlayService extends Service implements MediaBridge.Listener {

    public static final String ACTION_START = "com.musicbar.overlay.action.START";
    public static final String ACTION_STOP = "com.musicbar.overlay.action.STOP";
    public static final String ACTION_RELOAD = "com.musicbar.overlay.action.RELOAD";

    private static final String CHANNEL_ID = "fmbar";
    private static final int NOTIFICATION_ID = 1001;

    public static volatile boolean running;

    private WindowManager windowManager;
    private MusicBarView bar;
    private WindowManager.LayoutParams params;
    private boolean viewAdded;
    private boolean collapsed;
    private long lastActivity;
    private int dragBaseX;
    private int dragBaseY;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private DisplayManager displayManager;

    private final DisplayManager.DisplayListener displayListener =
            new DisplayManager.DisplayListener() {
                @Override
                public void onDisplayAdded(int displayId) {
                }

                @Override
                public void onDisplayRemoved(int displayId) {
                }

                @Override
                public void onDisplayChanged(int displayId) {
                    handler.post(OverlayService.this::applyLayout);
                }
            };

    private final MusicBarView.Callback callback = new MusicBarView.Callback() {
        @Override
        public void onPrev() {
            MediaBridge.get().prev();
            touch();
        }

        @Override
        public void onToggle() {
            MediaBridge.get().toggle();
            touch();
        }

        @Override
        public void onNext() {
            MediaBridge.get().next();
            touch();
        }

        @Override
        public void onSeekTo(long positionMs) {
            MediaBridge.get().seekTo(positionMs);
            touch();
        }

        @Override
        public void onUserActivity() {
            touch();
        }

        @Override
        public void onCollapseToggle(boolean value) {
            collapsed = value;
            applyLayout();
        }

        @Override
        public void onDragStart() {
            if (params == null) {
                return;
            }
            DisplayMetrics metrics = Prefs.metrics(OverlayService.this);
            if ((params.gravity & Gravity.HORIZONTAL_GRAVITY_MASK) == Gravity.CENTER_HORIZONTAL) {
                dragBaseX = (metrics.widthPixels - params.width) / 2 + params.x;
            } else {
                dragBaseX = params.x;
            }
            if ((params.gravity & Gravity.VERTICAL_GRAVITY_MASK) == Gravity.BOTTOM) {
                dragBaseY = metrics.heightPixels - params.height - params.y;
            } else {
                dragBaseY = params.y;
            }
            params.gravity = Gravity.TOP | Gravity.START;
            params.x = dragBaseX;
            params.y = dragBaseY;
        }

        @Override
        public void onDrag(int totalDx, int totalDy) {
            if (params == null || bar == null || !viewAdded) {
                return;
            }
            DisplayMetrics metrics = Prefs.metrics(OverlayService.this);
            int minX = -params.width / 2;
            int maxX = Math.max(minX, metrics.widthPixels - params.width / 2);
            int minY = 0;
            int maxY = Math.max(minY, metrics.heightPixels - params.height / 2);
            params.gravity = Gravity.TOP | Gravity.START;
            params.x = clamp(dragBaseX + totalDx, minX, maxX);
            params.y = clamp(dragBaseY + totalDy, minY, maxY);
            try {
                windowManager.updateViewLayout(bar, params);
            } catch (Throwable ignored) {
                // ignore
            }
        }

        @Override
        public void onDragEnd() {
            if (params == null) {
                return;
            }
            Prefs.setCustomPosition(OverlayService.this, params.x, params.y);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        running = true;
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        createChannel();
        startForeground(NOTIFICATION_ID, buildNotification());
        MediaBridge.get().init(this);
        MediaBridge.get().setListener(this);
        displayManager = (DisplayManager) getSystemService(DISPLAY_SERVICE);
        if (displayManager != null) {
            try {
                displayManager.registerDisplayListener(displayListener, handler);
            } catch (Throwable ignored) {
                // ignore
            }
        }
        lastActivity = SystemClock.elapsedRealtime();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stopOverlay();
            return START_NOT_STICKY;
        }
        if (ACTION_RELOAD.equals(action)) {
            rebuildBar();
            return START_STICKY;
        }
        if (bar == null) {
            buildBar();
        } else {
            applyLayout();
            update();
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        running = false;
        detachView();
        bar = null;
        if (displayManager != null) {
            try {
                displayManager.unregisterDisplayListener(displayListener);
            } catch (Throwable ignored) {
                // ignore
            }
        }
        MediaBridge.get().setListener(null);
        MediaBridge.get().release();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onMediaChanged() {
        update();
    }

    // ----- window -----------------------------------------------------------------

    private void buildBar() {
        detachView();
        bar = new MusicBarView(this, callback);
        bar.setCollapsed(collapsed);
        applyLayout();
        addView();
        update();
    }

    private void rebuildBar() {
        boolean wasCollapsed = collapsed;
        detachView();
        bar = null;
        collapsed = wasCollapsed;
        buildBar();
    }

    private void addView() {
        if (bar == null || viewAdded || windowManager == null || params == null) {
            return;
        }
        try {
            windowManager.addView(bar, params);
            viewAdded = true;
        } catch (Throwable t) {
            viewAdded = false;
        }
    }

    private void detachView() {
        if (bar != null && viewAdded && windowManager != null) {
            try {
                windowManager.removeView(bar);
            } catch (Throwable ignored) {
                // ignore
            }
        }
        viewAdded = false;
    }

    private void applyLayout() {
        if (bar == null || windowManager == null) {
            return;
        }
        DisplayMetrics metrics = Prefs.metrics(this);
        int screenWidth = metrics.widthPixels;
        int screenHeight = metrics.heightPixels;

        int thickness = Math.max(Prefs.dp(this, Prefs.MIN_BAR_DP),
                Prefs.cmToPxY(this, Prefs.thicknessCm(this), metrics));
        int handleSize = Math.max(Prefs.dp(this, Prefs.MIN_HANDLE_DP),
                Prefs.cmToPxY(this, Prefs.handleCm(this), metrics));

        // A short bar cannot fit the full-size controls; let the view slim down.
        bar.setCompact(thickness < Prefs.dp(this, 76));

        int width;
        int height;
        if (collapsed) {
            width = handleSize;
            height = handleSize;
        } else {
            int pct = clamp(Prefs.lengthPct(this), 20, 100);
            width = Math.max(Prefs.dp(this, 160), screenWidth * pct / 100);
            height = thickness;
        }

        int windowFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED;

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                width,
                height,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                windowFlags,
                PixelFormat.TRANSLUCENT);
        lp.setTitle("MusicBar");

        if (Prefs.useCustomPosition(this)) {
            lp.gravity = Gravity.TOP | Gravity.START;
            lp.x = clamp(Prefs.customX(this), -width / 2, Math.max(0, screenWidth - width / 2));
            lp.y = clamp(Prefs.customY(this), 0, Math.max(0, screenHeight - height / 2));
        } else if (Prefs.EDGE_TOP.equals(Prefs.edge(this))) {
            lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            lp.x = 0;
            lp.y = Prefs.dp(this, Prefs.edgeMarginDp(this));
        } else {
            lp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
            lp.x = 0;
            lp.y = Prefs.dp(this, Prefs.edgeMarginDp(this));
        }

        params = lp;
        if (viewAdded) {
            try {
                windowManager.updateViewLayout(bar, lp);
            } catch (Throwable ignored) {
                // ignore
            }
        }
        bar.setCollapsed(collapsed);
        applyAlpha();
    }

    private void update() {
        if (bar == null) {
            return;
        }
        MediaBridge bridge = MediaBridge.get();
        if (shouldHide(bridge)) {
            detachView();
        } else {
            if (!viewAdded) {
                addView();
            }
            if (viewAdded) {
                bar.update(bridge);
            }
        }
        applyAlpha();
    }

    private boolean shouldHide(MediaBridge bridge) {
        if (!Prefs.onlyPlaying(this)) {
            return false;
        }
        if (!bridge.hasSession()) {
            // Nothing is playing: only stay visible while the permission hint matters.
            return bridge.hasNotificationAccess();
        }
        int state = bridge.playbackState();
        boolean active = state == PlaybackState.STATE_PLAYING
                || state == PlaybackState.STATE_PAUSED
                || state == PlaybackState.STATE_BUFFERING
                || state == PlaybackState.STATE_FAST_FORWARDING
                || state == PlaybackState.STATE_REWINDING
                || state == PlaybackState.STATE_SKIPPING_TO_NEXT
                || state == PlaybackState.STATE_SKIPPING_TO_PREVIOUS;
        return !active;
    }

    private void applyAlpha() {
        if (bar == null) {
            return;
        }
        int fadeSeconds = Prefs.fadeDelayS(this);
        boolean active = fadeSeconds <= 0
                || (SystemClock.elapsedRealtime() - lastActivity) < fadeSeconds * 1000L;
        float alpha = (active ? Prefs.alphaActive(this) : Prefs.alphaIdle(this)) / 100f;
        if (alpha < 0.05f) {
            alpha = 0.05f;
        }
        if (alpha > 1f) {
            alpha = 1f;
        }
        bar.setAlpha(alpha);
    }

    private void touch() {
        lastActivity = SystemClock.elapsedRealtime();
        applyAlpha();
    }

    private void stopOverlay() {
        detachView();
        bar = null;
        if (displayManager != null) {
            try {
                displayManager.unregisterDisplayListener(displayListener);
            } catch (Throwable ignored) {
                // ignore
            }
        }
        MediaBridge.get().setListener(null);
        MediaBridge.get().release();
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    // ----- notification -----------------------------------------------------------

    private void createChannel() {
        NotificationManager manager =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager == null || manager.getNotificationChannel(CHANNEL_ID) != null) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notif_channel),
                NotificationManager.IMPORTANCE_MIN);
        channel.setShowBadge(false);
        manager.createNotificationChannel(channel);
    }

    private Notification buildNotification() {
        PendingIntent openIntent = PendingIntent.getActivity(
                this,
                0,
                new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        PendingIntent stopIntent = PendingIntent.getService(
                this,
                1,
                new Intent(this, OverlayService.class).setAction(ACTION_STOP),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_note)
                .setContentTitle(getString(R.string.notif_title))
                .setContentText(getString(R.string.notif_text))
                .setContentIntent(openIntent)
                .setOngoing(true)
                .addAction(new Notification.Action.Builder(
                        Icon.createWithResource(this, R.drawable.ic_note),
                        getString(R.string.btn_stop),
                        stopIntent).build())
                .build();
    }

    private static int clamp(int value, int min, int max) {
        if (value < min) {
            return min;
        }
        return Math.min(value, max);
    }
}
