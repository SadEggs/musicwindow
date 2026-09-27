package com.musicbar.overlay;

import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.TextUtils;

import java.io.InputStream;
import java.util.List;

/**
 * Reads the currently active media session (Poweramp) and exposes a tiny,
 * view-friendly API. Everything runs on the main thread.
 */
public class MediaBridge {

    public interface Listener {
        void onMediaChanged();
    }

    private static final long TICK_FAST_MS = 500L;

    private static MediaBridge instance;

    public static synchronized MediaBridge get() {
        if (instance == null) {
            instance = new MediaBridge();
        }
        return instance;
    }

    private final Handler handler = new Handler(Looper.getMainLooper());

    private Context app;
    private MediaSessionManager manager;
    private MediaController controller;
    private Listener listener;

    private boolean started;
    private boolean sessionsListenerAdded;

    private String status = "";
    private Bitmap artCache;
    private String artKey = "";

    private final MediaSessionManager.OnActiveSessionsChangedListener sessionsChanged =
            new MediaSessionManager.OnActiveSessionsChangedListener() {
                @Override
                public void onActiveSessionsChanged(List<MediaController> controllers) {
                    pick(controllers);
                }
            };

    private final MediaController.Callback controllerCallback = new MediaController.Callback() {
        @Override
        public void onPlaybackStateChanged(PlaybackState state) {
            notifyChanged();
        }

        @Override
        public void onMetadataChanged(MediaMetadata metadata) {
            refreshArt();
            notifyChanged();
        }

        @Override
        public void onSessionDestroyed() {
            refreshSessions();
        }
    };

    private final Runnable poll = new Runnable() {
        @Override
        public void run() {
            if (controller == null) {
                refreshSessions();
            }
            handler.postDelayed(this, controller == null ? 2000L : 8000L);
        }
    };

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            notifyChanged();
            handler.postDelayed(this, TICK_FAST_MS);
        }
    };

    private MediaBridge() {
    }

    // ----- lifecycle -------------------------------------------------------------

    public void init(Context ctx) {
        if (started) {
            return;
        }
        started = true;
        app = ctx.getApplicationContext();
        manager = (MediaSessionManager) app.getSystemService(Context.MEDIA_SESSION_SERVICE);
        ensureSessionsListener();
        refreshSessions();
        handler.postDelayed(tick, TICK_FAST_MS);
        handler.postDelayed(poll, 2000L);
    }

    public void release() {
        started = false;
        handler.removeCallbacks(tick);
        handler.removeCallbacks(poll);
        if (manager != null && sessionsListenerAdded) {
            try {
                manager.removeOnActiveSessionsChangedListener(sessionsChanged);
            } catch (Throwable ignored) {
                // ignore
            }
        }
        sessionsListenerAdded = false;
        setController(null);
        app = null;
        manager = null;
        listener = null;
    }

    public void setListener(Listener l) {
        listener = l;
    }

    /** Called when the notification listener connects/disconnects. */
    public void onListenerChanged(Context ctx) {
        if (!started) {
            init(ctx);
            return;
        }
        ensureSessionsListener();
        refreshSessions();
    }

    public void requestRefresh() {
        refreshSessions();
        notifyChanged();
    }

    // ----- session discovery -----------------------------------------------------

    private ComponentName listenerComponent() {
        return new ComponentName(app, MediaListenerService.class);
    }

    public boolean hasNotificationAccess() {
        if (app == null) {
            return false;
        }
        try {
            String flat = Settings.Secure.getString(
                    app.getContentResolver(), "enabled_notification_listeners");
            return flat != null && flat.contains(app.getPackageName());
        } catch (Throwable t) {
            return false;
        }
    }

    private void ensureSessionsListener() {
        if (manager == null || sessionsListenerAdded || !hasNotificationAccess()) {
            return;
        }
        try {
            manager.addOnActiveSessionsChangedListener(
                    sessionsChanged, listenerComponent(), handler);
            sessionsListenerAdded = true;
        } catch (Throwable ignored) {
            // SecurityException until the user grants notification access.
        }
    }

    public void refreshSessions() {
        if (app == null || manager == null) {
            return;
        }
        if (!hasNotificationAccess() || MediaListenerService.get() == null) {
            status = app.getString(R.string.bar_need_nls);
            setController(null);
            return;
        }
        try {
            List<MediaController> list = manager.getActiveSessions(listenerComponent());
            status = "";
            pick(list);
        } catch (SecurityException e) {
            status = app.getString(R.string.bar_need_nls);
            setController(null);
        } catch (Throwable t) {
            status = "";
        }
    }

    private void pick(List<MediaController> list) {
        MediaController chosen = null;
        if (list != null && !list.isEmpty()) {
            String preferred = Prefs.preferredPackage(app);
            if (!TextUtils.isEmpty(preferred)) {
                for (MediaController c : list) {
                    if (preferred.equals(c.getPackageName())) {
                        chosen = c;
                        break;
                    }
                }
            }
            if (chosen == null) {
                for (MediaController c : list) {
                    if (isPlayingState(c)) {
                        chosen = c;
                        break;
                    }
                }
            }
            if (chosen == null) {
                for (MediaController c : list) {
                    if (c.getMetadata() != null) {
                        chosen = c;
                        break;
                    }
                }
            }
            if (chosen == null) {
                chosen = list.get(0);
            }
        }
        setController(chosen);
    }

    private boolean isPlayingState(MediaController c) {
        PlaybackState st = c.getPlaybackState();
        return st != null && st.getState() == PlaybackState.STATE_PLAYING;
    }

    private void setController(MediaController c) {
        MediaController current = controller;
        boolean same = current == null
                ? c == null
                : (c != null && current.getSessionToken().equals(c.getSessionToken()));
        if (same) {
            return;
        }
        if (current != null) {
            try {
                current.unregisterCallback(controllerCallback);
            } catch (Throwable ignored) {
                // ignore
            }
        }
        controller = c;
        artCache = null;
        artKey = "";
        if (controller != null) {
            try {
                controller.registerCallback(controllerCallback, handler);
            } catch (Throwable ignored) {
                // ignore
            }
        }
        refreshArt();
        notifyChanged();
    }

    private void notifyChanged() {
        Listener l = listener;
        if (l != null) {
            l.onMediaChanged();
        }
    }

    // ----- state accessors -------------------------------------------------------

    public MediaController controller() {
        return controller;
    }

    public String statusText() {
        return status == null ? "" : status;
    }

    public boolean hasSession() {
        return controller != null;
    }

    public boolean isPlaying() {
        PlaybackState st = controller == null ? null : controller.getPlaybackState();
        return st != null && st.getState() == PlaybackState.STATE_PLAYING;
    }

    /** Raw PlaybackState constant, or STATE_NONE when there is no session. */
    public int playbackState() {
        PlaybackState st = controller == null ? null : controller.getPlaybackState();
        return st == null ? PlaybackState.STATE_NONE : st.getState();
    }

    public String packageName() {
        return controller == null ? "" : controller.getPackageName();
    }

    public String title() {
        MediaMetadata md = controller == null ? null : controller.getMetadata();
        if (md == null) {
            return "";
        }
        String t = md.getString(MediaMetadata.METADATA_KEY_TITLE);
        if (TextUtils.isEmpty(t)) {
            t = md.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE);
        }
        return t == null ? "" : t;
    }

    public String artist() {
        MediaMetadata md = controller == null ? null : controller.getMetadata();
        if (md == null) {
            return "";
        }
        String a = md.getString(MediaMetadata.METADATA_KEY_ARTIST);
        if (TextUtils.isEmpty(a)) {
            a = md.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST);
        }
        if (TextUtils.isEmpty(a)) {
            a = md.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE);
        }
        return a == null ? "" : a;
    }

    public long durationMs() {
        MediaMetadata md = controller == null ? null : controller.getMetadata();
        if (md == null) {
            return 0L;
        }
        long d = md.getLong(MediaMetadata.METADATA_KEY_DURATION);
        return d > 0L ? d : 0L;
    }

    /** Interpolated position, good enough for a progress bar. */
    public long positionMs() {
        MediaController c = controller;
        if (c == null) {
            return 0L;
        }
        PlaybackState st = c.getPlaybackState();
        if (st == null) {
            return 0L;
        }
        long pos = st.getPosition();
        if (st.getState() == PlaybackState.STATE_PLAYING) {
            float speed = st.getPlaybackSpeed();
            if (speed <= 0f) {
                speed = 1f;
            }
            long delta = SystemClock.elapsedRealtime() - st.getLastPositionUpdateTime();
            if (delta > 0L) {
                pos += (long) (delta * speed);
            }
        }
        if (pos < 0L) {
            pos = 0L;
        }
        long dur = durationMs();
        if (dur > 0L && pos > dur) {
            pos = dur;
        }
        return pos;
    }

    // ----- album art -------------------------------------------------------------

    private void refreshArt() {
        if (app == null) {
            return;
        }
        MediaMetadata md = controller == null ? null : controller.getMetadata();
        if (md == null) {
            artCache = null;
            artKey = "";
            return;
        }
        String uri = md.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI);
        if (TextUtils.isEmpty(uri)) {
            uri = md.getString(MediaMetadata.METADATA_KEY_ART_URI);
        }
        if (TextUtils.isEmpty(uri)) {
            uri = md.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI);
        }
        Bitmap bmp = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
        if (bmp == null) {
            bmp = md.getBitmap(MediaMetadata.METADATA_KEY_ART);
        }
        if (bmp != null) {
            artCache = bmp;
            artKey = "bmp";
            return;
        }
        if (TextUtils.isEmpty(uri)) {
            artCache = null;
            artKey = "";
            return;
        }
        if (uri.equals(artKey)) {
            return;
        }
        artKey = uri;
        artCache = decodeArt(uri);
    }

    private Bitmap decodeArt(String uri) {
        InputStream in = null;
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            in = app.getContentResolver().openInputStream(Uri.parse(uri));
            if (in == null) {
                return null;
            }
            BitmapFactory.decodeStream(in, null, bounds);
            in.close();

            int sample = 1;
            int longest = Math.max(bounds.outWidth, bounds.outHeight);
            while (longest / sample > 512) {
                sample *= 2;
            }
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sample;
            in = app.getContentResolver().openInputStream(Uri.parse(uri));
            if (in == null) {
                return null;
            }
            return BitmapFactory.decodeStream(in, null, opts);
        } catch (Throwable t) {
            return null;
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (Throwable ignored) {
                    // ignore
                }
            }
        }
    }

    public Bitmap art() {
        return artCache;
    }

    // ----- controls --------------------------------------------------------------

    public void prev() {
        MediaController c = controller;
        if (c != null) {
            try {
                c.getTransportControls().skipToPrevious();
            } catch (Throwable ignored) {
                // ignore
            }
        }
    }

    public void next() {
        MediaController c = controller;
        if (c != null) {
            try {
                c.getTransportControls().skipToNext();
            } catch (Throwable ignored) {
                // ignore
            }
        }
    }

    /**
     * Asks the player itself to start a specific file. Nothing is brought to the
     * foreground, so a game keeps running while the track changes. Returns false
     * when there is no session to ask; a player that ignores the command is
     * detected by the caller comparing the title a moment later.
     */
    public boolean playUri(Uri uri) {
        MediaController c = controller;
        if (c == null || uri == null) {
            return false;
        }
        try {
            c.getTransportControls().playFromUri(uri, null);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    // ----- what the current player accepts for "play this exact item" ------------

    public static final int CAN_SEARCH = 1;
    public static final int CAN_URI = 2;
    public static final int CAN_MEDIA_ID = 4;

    /**
     * Reads the transport actions the session advertises. This is how we learn
     * which kind of "play this song" request a player is willing to answer before
     * bothering it - a player that advertises none will simply ignore us.
     */
    public int playFromSupport() {
        MediaController c = controller;
        if (c == null) {
            return 0;
        }
        PlaybackState state = c.getPlaybackState();
        if (state == null) {
            return 0;
        }
        long actions = state.getActions();
        int support = 0;
        if ((actions & PlaybackState.ACTION_PLAY_FROM_SEARCH) != 0) {
            support |= CAN_SEARCH;
        }
        if ((actions & PlaybackState.ACTION_PLAY_FROM_URI) != 0) {
            support |= CAN_URI;
        }
        if ((actions & PlaybackState.ACTION_PLAY_FROM_MEDIA_ID) != 0) {
            support |= CAN_MEDIA_ID;
        }
        return support;
    }

    /**
     * "Play something matching this text." The standard request every player
     * implements for Android Auto and voice assistants, and unlike the file URI
     * route it is honoured by players that never look at MediaStore ids.
     */
    public boolean playFromSearch(String query) {
        MediaController c = controller;
        if (c == null || query == null || query.isEmpty()) {
            return false;
        }
        try {
            c.getTransportControls().playFromSearch(query, null);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    public void toggle() {
        MediaController c = controller;
        if (c == null) {
            return;
        }
        try {
            if (isPlaying()) {
                c.getTransportControls().pause();
            } else {
                c.getTransportControls().play();
            }
        } catch (Throwable ignored) {
            // ignore
        }
    }

    public void seekTo(long positionMs) {
        MediaController c = controller;
        if (c == null) {
            return;
        }
        long dur = durationMs();
        long target = positionMs;
        if (target < 0L) {
            target = 0L;
        }
        if (dur > 0L && target > dur) {
            target = dur;
        }
        try {
            c.getTransportControls().seekTo(target);
        } catch (Throwable ignored) {
            // ignore
        }
    }
}
