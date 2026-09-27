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
import android.widget.Toast;

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
    private FolderPanelView panel;
    private boolean panelAdded;
    private int playToken;
    private long hideSince;
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
            Beep.onTrackChange(OverlayService.this);
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
            Beep.onTrackChange(OverlayService.this);
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
        public void onLibraryToggle() {
            toggleLibrary();
        }

        @Override
        public void onCollapseToggle(boolean value) {
            collapsed = value;
            if (value) {
                hidePanel();
            }
            applyLayout();
        }

        @Override
        public void onPinToggle() {
            boolean value = !Prefs.pinned(OverlayService.this);
            Prefs.sp(OverlayService.this).edit().putBoolean(Prefs.K_PINNED, value).apply();
            if (bar != null) {
                bar.setPinned(value);
            }
            Toast.makeText(OverlayService.this,
                    value ? R.string.toast_pinned : R.string.toast_unpinned,
                    Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onDragStart() {
            if (params == null || Prefs.pinned(OverlayService.this)) {
                return;
            }
            hidePanel();
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
            if (params == null || bar == null || !viewAdded || Prefs.pinned(OverlayService.this)) {
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

    private final FolderPanelView.Callback panelCallback = new FolderPanelView.Callback() {
        @Override
        public void onClose() {
            hidePanel();
        }

        @Override
        public void onPlaySong(MediaLibrary.Song song) {
            playFromLibrary(song);
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
        playToken++;
        PlayerBrowser.get(this).release();
        hidePanel();
        panel = null;
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
        bar.setPinned(Prefs.pinned(this));
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

    // ----- media library panel ----------------------------------------------------

    private void toggleLibrary() {
        if (panelAdded) {
            hidePanel();
        } else {
            showPanel();
        }
    }

    /**
     * Unfolds the library out of the bar: same width as the bar, one third of the
     * screen tall, dropping away from wherever the bar happens to sit. When there
     * is no room underneath it opens upwards instead.
     */
    private void showPanel() {
        if (windowManager == null || params == null) {
            return;
        }
        DisplayMetrics metrics = Prefs.metrics(this);
        int screenWidth = metrics.widthPixels;
        int screenHeight = metrics.heightPixels;

        // Exactly the bar's own length, so the panel lines up with the bar above it.
        int lengthPct = clamp(Prefs.lengthPct(this), 20, 100);
        int panelWidth = Math.max(Prefs.dp(this, 160), screenWidth * lengthPct / 100);
        int panelHeight = Math.max(Prefs.dp(this, 300), screenHeight / 3);

        int barWidth = params.width;
        int barHeight = params.height;

        int barLeft;
        if ((params.gravity & Gravity.HORIZONTAL_GRAVITY_MASK) == Gravity.CENTER_HORIZONTAL) {
            barLeft = (screenWidth - barWidth) / 2 + params.x;
        } else {
            barLeft = params.x;
        }

        int barTop;
        if ((params.gravity & Gravity.VERTICAL_GRAVITY_MASK) == Gravity.BOTTOM) {
            barTop = screenHeight - barHeight - params.y;
        } else {
            barTop = params.y;
        }

        int x = clamp(barLeft, 0, Math.max(0, screenWidth - panelWidth));
        int y;
        if (barTop + barHeight + panelHeight <= screenHeight) {
            y = barTop + barHeight;
        } else {
            y = barTop - panelHeight;
        }
        y = clamp(y, 0, Math.max(0, screenHeight - panelHeight));

        if (panel == null) {
            panel = new FolderPanelView(this, panelCallback);
        }
        panel.setPanelHeight(panelHeight);

        String startFolder = null;
        MediaBridge bridge = MediaBridge.get();
        if (bridge.hasSession()) {
            MediaLibrary library = MediaLibrary.get(this);
            library.refreshIfStale();
            if (library.songCount() == 0) {
                // The permission may have been granted since the last scan.
                library.load();
            }
            startFolder = library.findFolder(bridge.title(), bridge.artist());
        }
        panel.open(startFolder);

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                panelWidth,
                panelHeight,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = x;
        lp.y = y;
        lp.setTitle("MusicBarLibrary");

        try {
            windowManager.addView(panel, lp);
            panelAdded = true;
        } catch (Throwable ignored) {
            panelAdded = false;
        }
        if (bar != null) {
            // The library button turns into the cross that closes this panel, which
            // puts the way out in the panel's top right corner.
            bar.setLibraryOpen(true);
        }
    }

    private void hidePanel() {
        if (panelAdded && panel != null && windowManager != null) {
            try {
                windowManager.removeView(panel);
            } catch (Throwable ignored) {
                // ignore
            }
        }
        panelAdded = false;
        if (bar != null) {
            bar.setLibraryOpen(false);
        }
    }

    /**
     * Play a song the user tapped in the library panel, without pulling the player
     * to the front so the game keeps the screen.
     *
     * The most accurate request is by the id the player itself assigned to the
     * track, so the first step is a bounded lookup in the player's own browser
     * service. From there a ladder of requests is tried - by id, by name, by file
     * - and after each one the track is checked a few times to see whether it
     * really changed. A player that loaded the track but stayed paused gets a
     * resume nudge, which is what makes this work when music was paused before the
     * tap: it used to load the song and sit there doing nothing.
     */
    private void playFromLibrary(MediaLibrary.Song song) {
        MediaBridge bridge = MediaBridge.get();
        if (!bridge.hasSession()) {
            Toast.makeText(this, R.string.toast_play_no_session, Toast.LENGTH_LONG).show();
            return;
        }
        final int token = ++playToken;
        final String before = bridge.trackSignature();

        Toast.makeText(this, getString(R.string.toast_play_searching, song.title),
                Toast.LENGTH_SHORT).show();

        PlayerBrowser.get(this).findMediaId(song.title, song.artist, 4000L,
                (mediaId, how) -> {
                    if (token != playToken) {
                        return;
                    }
                    startPlayAttempts(song, mediaId, before, token);
                });
    }

    private void startPlayAttempts(MediaLibrary.Song song, String mediaId, String before,
                                   int token) {
        int support = MediaBridge.get().playFromSupport();
        int[] order = new int[3];
        int count = 0;
        if (mediaId != null) {
            order[count++] = MediaBridge.CAN_MEDIA_ID;
        }
        if ((support & MediaBridge.CAN_SEARCH) != 0) {
            order[count++] = MediaBridge.CAN_SEARCH;
        }
        if ((support & MediaBridge.CAN_URI) != 0) {
            order[count++] = MediaBridge.CAN_URI;
        }
        if (count == 0) {
            // The session advertises nothing: try anyway, most accurate first.
            order[count++] = MediaBridge.CAN_SEARCH;
            order[count++] = MediaBridge.CAN_URI;
        }
        attemptPlay(song, mediaId, order, count, 0, before, token);
    }

    private void attemptPlay(MediaLibrary.Song song, String mediaId, int[] order, int count,
                             int index, String before, int token) {
        if (token != playToken) {
            return;
        }
        if (index >= count) {
            Toast.makeText(this, R.string.toast_play_unsupported, Toast.LENGTH_LONG).show();
            return;
        }
        MediaBridge bridge = MediaBridge.get();
        int mode = order[index];
        boolean sent;
        if (mode == MediaBridge.CAN_MEDIA_ID) {
            sent = bridge.playFromMediaId(mediaId);
        } else if (mode == MediaBridge.CAN_SEARCH) {
            sent = bridge.playFromSearch(song.title);
        } else {
            sent = bridge.playUri(song.uri());
        }
        if (!sent) {
            attemptPlay(song, mediaId, order, count, index + 1, before, token);
            return;
        }
        checkTrack(song, mediaId, order, count, index, before, token, 1);
    }

    private void checkTrack(MediaLibrary.Song song, String mediaId, int[] order, int count,
                            int index, String before, int token, int round) {
        handler.postDelayed(() -> {
            if (token != playToken) {
                return;
            }
            MediaBridge bridge = MediaBridge.get();
            if (!bridge.trackSignature().equals(before)) {
                if (!bridge.isPlaying()) {
                    // The player loaded the track but stayed paused.
                    bridge.play();
                }
                Toast.makeText(OverlayService.this,
                        getString(R.string.toast_play_ok, bridge.title()), Toast.LENGTH_SHORT).show();
                Beep.onTrackChange(OverlayService.this);
                refreshAfterPlay(token, 4);
                return;
            }
            if (round >= 3) {
                attemptPlay(song, mediaId, order, count, index + 1, before, token);
                return;
            }
            checkTrack(song, mediaId, order, count, index, before, token, round + 1);
        }, 600L);
    }

    /** Metadata often arrives a moment after the switch, so keep the bar in step. */
    private void refreshAfterPlay(int token, int times) {
        if (times <= 0) {
            return;
        }
        handler.postDelayed(() -> {
            if (token != playToken) {
                return;
            }
            update();
            refreshAfterPlay(token, times - 1);
        }, 700L);
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
        boolean hide = shouldHide(bridge);
        if (hide && bridge.hasSession()) {
            // Switching track can make a player report a dead state for a moment.
            // Wait a few seconds before tearing the bar down, so it does not blink
            // out of existence in the middle of a song change. Only applies while a
            // session exists: with no player at all the bar still goes away at once.
            long now = SystemClock.elapsedRealtime();
            if (hideSince == 0L) {
                hideSince = now;
            }
            if (now - hideSince < 3000L) {
                hide = false;
            }
        } else {
            hideSince = 0L;
        }
        if (hide) {
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
        if (panelAdded) {
            // The panel unfolds from the bar, so the bar must stay while browsing.
            return false;
        }
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
