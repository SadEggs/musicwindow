package com.musicbar.overlay;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.graphics.drawable.Icon;
import android.hardware.display.DisplayManager;
import android.media.session.PlaybackState;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.Toast;

import java.io.File;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

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
    private String shuffleWatch;
    private String shuffleActionWatch;
    private long hideSince;
    private long lastActivity;
    private int dragBaseX;
    private int dragBaseY;

    /** The folder the user was last working in, so the panel stays where they left it. */
    private String anchorFolder;

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
            if (stepEngine(-1)) {
                return;
            }
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
            if (stepEngine(1)) {
                return;
            }
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
            // A tap is a request to shuffle where that song lives: that folder on its own when it
            // is flat, the whole branch below it when it is not, and the tapped song first. This
            // has to hold even while another shuffle is already running. The old check lived
            // inside playFromLibrary and was skipped exactly then, so tapping a song in a
            // sub-folder mid-shuffle played that one song and left the big folder's order alone.
            if (Prefs.treePlay(OverlayService.this)) {
                if (Prefs.panelShuffle(OverlayService.this)) {
                    startEngine(song.folder, song, true);
                } else {
                    // Order mode: this one song, and the shuffle that may have been running is
                    // stopped so nothing takes over when it ends.
                    stopEngine();
                    playFromLibrary(song);
                }
                return;
            }
            playFromLibrary(song);
        }

        @Override
        public void onPlayFolder(String folder) {
            // All plays this folder's branch through the app's own engine, in whichever mode the
            // panel is set to: shuffled, or straight through in the branch's own order.
            startEngine(folder, null, Prefs.panelShuffle(OverlayService.this));
        }
    };

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(Lang.wrap(base));
    }

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
        // Prefer the folder the user was last working in. Looking the playing track up by
        // title lands in whichever folder happens to hold a song of that name first, which
        // is how the panel used to jump to a different folder after a track change.
        if (anchorFolder != null && !anchorFolder.isEmpty()
                && MediaLibrary.get(this).songCountInTree(anchorFolder) > 0) {
            startFolder = anchorFolder;
        }
        panel.open(startFolder);

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                panelWidth,
                panelHeight,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
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

        // A single song makes the player build its queue from the song's own folder, and in
        // Poweramp that means that folder's own files and nothing below it - which is what
        // makes the sub-folders disappear from the queue as soon as the track changes, and
        // leaves shuffle covering one folder. When this folder is a tree and the setting is
        // on, hand over the same playlist the all button writes, rotated so the tapped song
        // comes first: the queue is then the whole tree and shuffle covers all of it.
        anchorFolder = song.folder;
        engineSongFolder = song.folder;

        // A request from the engine: go straight to the file. Walking the player's browse tree
        // costs seconds, and the folder-queue subscribe further down drops the request whenever
        // the panel has moved on in the meantime - exactly the case when the shuffle is started
        // from a folder other than the one the player was last left on.
        if (engineOn) {
            startPlayAttempts(song, null, before, token);
            return;
        }

        if (Prefs.playRoute(this) == Prefs.ROUTE_URI) {
            // Playing the file itself needs no walk through the player's browse tree, which
            // is what used to make a tap take several seconds to start.
            startPlayAttempts(song, null, before, token);
            return;
        }

        final PlayerBrowser browser = PlayerBrowser.get(this);
        browser.findMediaId(song.title, song.artist, 2000L,
                (mediaId, how) -> {
                    if (token != playToken) {
                        return;
                    }
                    if (!Prefs.folderPlay(this)) {
                        startPlayAttempts(song, mediaId, before, token);
                        return;
                    }
                    // Tell the player which folder this song lives in before asking for it:
                    // it then builds the folder's queue instead of a one-track queue, which
                    // is also the only way shuffle can mean "shuffle inside this folder".
                    browser.keepSubscribed(browser.lastParent());
                    handler.postDelayed(() -> {
                        if (token == playToken) {
                            startPlayAttempts(song, mediaId, before, token);
                        }
                    }, 250L);
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
        // Playing one song discards the queue, and the shuffle mode with it.
        MediaBridge.get().snapshotModes();
        // Remember what the player's own shuffle switch looks like right now. The mode
        // cannot be read through the media session on this platform, but the player's own
        // shuffle action shows its state in its icon, so a change can be detected.
        shuffleActionWatch = MediaBridge.get().shuffleActionSignature();
        shuffleWatch = MediaBridge.get().shuffleButtonSignature();
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
        int route = Prefs.playRoute(this);
        int mode = order[index];
        if (route != Prefs.ROUTE_AUTO && route != routeOf(mode)) {
            // This route was switched off on the settings page.
            attemptPlay(song, mediaId, order, count, index + 1, before, token);
            return;
        }
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
                // Once the new queue exists, put the shuffle / repeat mode back.
                handler.postDelayed(() -> {
                    if (token == playToken) {
                        restoreShuffle();
                    }
                }, 1200L);
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
    /** Maps a point-song capability to the route number used by the setting. */
    private static int routeOf(int capability) {
        if (capability == MediaBridge.CAN_MEDIA_ID) {
            return Prefs.ROUTE_MEDIA_ID;
        }
        return capability == MediaBridge.CAN_SEARCH ? Prefs.ROUTE_SEARCH : Prefs.ROUTE_URI;
    }

    /**
     * Picking one song makes the player build a fresh queue, and most players set shuffle
     * back to off while doing it. How to undo that depends on what the player exposes, so
     * the settings page picks the mechanism.
     */
    /**
     * Hand a whole folder branch to the player as an .m3u playlist, so the player builds the
     * queue itself. Poweramp keeps a folder's own queue to that folder's songs and treats
     * sub-folders as separate entries, so this is the only way a shuffle can reach a branch
     * deeper than one level - and because the player opens the file like any other playlist,
     * shuffle, gapless switching and every audio setting stay inside the player.
     */
    private void playFolderTree(String folder) {
        playFolderTree(folder, null);
    }

    /**
     * Play a folder's whole tree through a playlist the player itself will accept. A tapped
     * song is moved to the front, so a play that is not shuffled still starts with it.
     */
    private void playFolderTree(String folder, MediaLibrary.Song first) {
        MediaLibrary library = MediaLibrary.get(this);
        List<MediaLibrary.Song> songs = library.songsInTree(folder);
        anchorFolder = folder;
        if (first != null && songs.size() > 1) {
            List<MediaLibrary.Song> ordered = new ArrayList<>(songs.size());
            for (MediaLibrary.Song s : songs) {
                if (sameSong(s, first)) {
                    ordered.add(s);
                }
            }
            for (MediaLibrary.Song s : songs) {
                if (!sameSong(s, first)) {
                    ordered.add(s);
                }
            }
            if (ordered.size() == songs.size()) {
                songs = ordered;
            }
        }
        if (songs.isEmpty()) {
            Toast.makeText(this, getString(R.string.playlist_empty), Toast.LENGTH_SHORT).show();
            return;
        }
        if (!Playlists.allowed(this)) {
            Toast.makeText(this, getString(R.string.playlist_need_perm), Toast.LENGTH_LONG).show();
            return;
        }
        final File file = Playlists.write(this, folder, songs);
        if (file == null) {
            Toast.makeText(this, getString(R.string.playlist_failed), Toast.LENGTH_LONG).show();
            return;
        }
        final MediaBridge bridge = MediaBridge.get();
        final String before = bridge.trackSignature();
        final Uri uri = Playlists.uriOf(file);
        // Make the playlist readable for the player: a plain path would not be, and the
        // player would report a playback failure instead of playing anything.
        final String player = bridge.packageName();
        if (uri != null && player != null && !player.isEmpty()) {
            try {
                grantUriPermission(player, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Throwable ignored) {
                // Without the grant the manual route below still works.
            }
        }
        // Best effort: ask the system scanner to look at it, which some players follow.
        try {
            MediaScannerConnection.scanFile(this,
                    new String[]{file.getAbsolutePath()},
                    new String[]{"audio/x-mpegurl"}, null);
        } catch (Throwable ignored) {
            // A player that does not need the scan is unaffected.
        }
        Toast.makeText(this, getString(R.string.playlist_created, songs.size()),
                Toast.LENGTH_SHORT).show();
        // First: if the player already has this playlist in its own library, ask for it by
        // media id - that is what tapping it inside the player does, and it takes the whole
        // list. Second: hand it over as a content URI. Third: if nothing started, say where
        // the file is so it can be opened by hand.
        final PlayerBrowser browser = PlayerBrowser.get(this);
        final String name = file.getName();
        final String title = name.endsWith(".m3u")
                ? name.substring(0, name.length() - 4) : name;
        browser.findMediaId(title, "", 2500L, (mediaId, how) -> handler.post(() -> {
            if (mediaId != null && !mediaId.isEmpty() && bridge.playFromMediaId(mediaId)) {
                watchPlaylistStart(bridge, before, file);
                return;
            }
            if (uri != null && bridge.playUri(uri)) {
                watchPlaylistStart(bridge, before, file);
                return;
            }
            Toast.makeText(this, getString(R.string.playlist_manual, file.getName()),
                    Toast.LENGTH_LONG).show();
        }));
    }

    /** The same file, as far as the library is concerned. */
    private boolean sameSong(MediaLibrary.Song a, MediaLibrary.Song b) {
        return a != null && b != null && a.path != null && a.path.equals(b.path);
    }

    /** True when a folder holds fewer songs than its whole tree, i.e. it has sub-folders. */
    private boolean treeIsBigger(String folder) {
        if (folder == null || folder.isEmpty()) {
            return false;
        }
        MediaLibrary library = MediaLibrary.get(this);
        return library.songCountInTree(folder) > library.songCountIn(folder);
    }

    /** The player can accept the request and still not start; then the manual route is next. */
    // ---- the app's own shuffle over a folder tree -----------------------------------
    // Poweramp's folder queue holds one folder and nothing below it, and no MediaSession
    // call can hand a player a queue, so the player cannot shuffle a tree by itself. This
    // is the app doing the shuffling: it decides the order and hands over one song at a
    // time, switching shortly before each one ends. The price is the seamless join.

    private static final long ENGINE_TICK_MS = 400L;
    private static final long ENGINE_LEAD_MS = 1500L;
    private static final long ENGINE_QUIET_MS = 3000L;

    private final List<MediaLibrary.Song> engineQueue = new ArrayList<>();
    private boolean engineOn;
    private String engineSongFolder;
    private boolean engineShuffle = true;
    private int engineIndex;
    private long engineQuietUntil;
    private String engineSeen;

    private final Runnable engineTick = new Runnable() {
        @Override
        public void run() {
            if (!engineOn) {
                return;
            }
            engineStep();
            handler.postDelayed(this, ENGINE_TICK_MS);
        }
    };

    /**
     * Move one step through the shuffle list the engine built, which is what a skip should do
     * while a tree shuffle is running: forward stays inside the tree, and backward retraces the
     * list rather than starting a new order. False means no shuffle is running, and the caller
     * falls back to the player's own skip.
     */
    private boolean stepEngine(int delta) {
        if (!Prefs.treePlay(this) || !engineOn || engineQueue.isEmpty()) {
            return false;
        }
        int next = engineIndex + delta;
        if (next >= engineQueue.size()) {
            // The end of the list: a fresh order when shuffling, otherwise the branch again.
            if (engineShuffle) {
                List<MediaLibrary.Song> again = new ArrayList<>(engineQueue);
                Collections.shuffle(again);
                engineQueue.clear();
                engineQueue.addAll(again);
            }
            next = 0;
        } else if (next < 0) {
            next = engineQueue.size() - 1;
        }
        engineIndex = next;
        enginePlayCurrent();
        return true;
    }

    /** Start shuffling a folder's whole tree, beginning with the song the user tapped. */
    private void startEngine(String folder, MediaLibrary.Song first, boolean shuffle) {
        engineShuffle = shuffle;
        List<MediaLibrary.Song> songs = MediaLibrary.get(this).songsInTree(folder);
        if (songs.isEmpty()) {
            Toast.makeText(this, getString(R.string.playlist_empty), Toast.LENGTH_SHORT).show();
            return;
        }
        List<MediaLibrary.Song> rest = new ArrayList<>(songs);
        if (first != null) {
            for (int i = rest.size() - 1; i >= 0; i--) {
                if (sameSong(rest.get(i), first)) {
                    rest.remove(i);
                }
            }
        }
        if (shuffle) {
            Collections.shuffle(rest);
        }
        engineQueue.clear();
        if (first != null) {
            engineQueue.add(first);
        }
        engineQueue.addAll(rest);
        engineIndex = 0;
        engineOn = true;
        engineSeen = null;
        anchorFolder = folder;
        handler.removeCallbacks(engineTick);
        handler.postDelayed(engineTick, ENGINE_TICK_MS);
        Toast.makeText(this, getString(R.string.engine_started, engineQueue.size()),
                Toast.LENGTH_SHORT).show();
        enginePlayCurrent();
        // Starting a shuffle is also a request to hear it: if the player was paused when the
        // button was pressed, some players accept the selection and stay silent otherwise.
        handler.postDelayed(() -> {
            if (engineOn && !MediaBridge.get().isPlaying()) {
                MediaBridge.get().play();
            }
        }, 1500L);
    }

    public void stopEngine() {
        engineOn = false;
        handler.removeCallbacks(engineTick);
        if (bar != null) {
            bar.setShuffleInfo(0, 0);
        }
    }

    private void enginePlayCurrent() {
        if (engineIndex < 0 || engineIndex >= engineQueue.size()) {
            return;
        }
        if (bar != null) {
            // One-based for reading: the first song of a fresh shuffle shows as 1.
            bar.setShuffleInfo(engineIndex + 1, engineQueue.size());
        }
        engineSongFolder = engineQueue.get(engineIndex).folder;
        // Ignore track changes for a moment: the one about to arrive is this request.
        engineQuietUntil = System.currentTimeMillis() + ENGINE_QUIET_MS;
        engineSeen = null;
        playFromLibrary(engineQueue.get(engineIndex));
    }

    private void engineStep() {
        MediaBridge bridge = MediaBridge.get();
        if (!bridge.hasSession()) {
            return;
        }
        long now = System.currentTimeMillis();
        String signature = bridge.trackSignature();
        long position = bridge.positionMs();
        long duration = bridge.durationMs();
        boolean ending = duration > 0 && position > 0 && position >= duration - ENGINE_LEAD_MS;
        boolean moved = signature != null && engineSeen != null && !signature.equals(engineSeen);
        engineSeen = signature;
        if (now < engineQuietUntil) {
            return;
        }
        if (!ending && !moved) {
            return;
        }
        engineIndex++;
        if (engineIndex >= engineQueue.size()) {
            // Through the branch once, then a fresh order when shuffling: in order mode it just
            // starts the branch again from its first song.
            if (engineShuffle) {
                List<MediaLibrary.Song> again = new ArrayList<>(engineQueue);
                Collections.shuffle(again);
                engineQueue.clear();
                engineQueue.addAll(again);
            }
            engineIndex = 0;
        }
        enginePlayCurrent();
    }

    /** The player can accept the request and still not start; then the manual route is next. */
    private void watchPlaylistStart(MediaBridge bridge, String before, File file) {
        handler.postDelayed(() -> {
            String now = bridge.trackSignature();
            if (now == null || now.equals(before)) {
                Toast.makeText(this, getString(R.string.playlist_manual, file.getName()),
                        Toast.LENGTH_LONG).show();
            }
        }, 2500L);
    }

    private void restoreShuffle() {
        MediaBridge bridge = MediaBridge.get();
        int mode = Prefs.reshuffleMode(this);
        if (mode == Prefs.RESHUFFLE_OFF) {
            return;
        }
        if (bridge.shuffleMode() != MediaBridge.MODE_UNKNOWN) {
            // A player that does expose the mode needs no guessing.
            bridge.restoreModes(this);
            return;
        }
        if (mode == Prefs.RESHUFFLE_COMMAND && bridge.setShuffleMode(MediaBridge.SHUFFLE_ALL)) {
            return;
        }
        if (mode == Prefs.RESHUFFLE_NOTIFY) {
            pressNotificationShuffle(bridge);
            return;
        }
        // Automatic and always: the player's own shuffle action is the switch that exists.
        // "Automatic" presses it only when the action really did change while the player
        // rebuilt its queue, so nobody who keeps shuffle off gets it switched on.
        if (bridge.hasShuffleCustomAction()) {
            String now = bridge.shuffleActionSignature();
            boolean changed = shuffleActionWatch != null && now != null
                    && !now.equals(shuffleActionWatch);
            if (mode == Prefs.RESHUFFLE_ALWAYS || changed) {
                bridge.sendShuffleCustomAction();
            }
            return;
        }
        pressNotificationShuffle(bridge);
    }

    /** The fallback for a player that has a shuffle button in its notification instead. */
    private void pressNotificationShuffle(MediaBridge bridge) {
        String now = bridge.shuffleButtonSignature();
        if (shuffleWatch != null && now != null && !now.equals(shuffleWatch)) {
            bridge.pressShuffleButton();
        }
    }

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
