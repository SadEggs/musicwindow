package com.musicbar.overlay;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.media.MediaDescription;
import android.media.browse.MediaBrowser;
import android.os.Handler;
import android.os.Looper;
import android.service.media.MediaBrowserService;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Talks to the current player's own media browser service: the API Android Auto
 * uses to list a player's library and start a track by the id the player itself
 * assigned to it.
 *
 * <p>This is the most accurate way to answer "play this song": the file path and
 * the title both have to be interpreted by the player, while a library id means
 * exactly the item the player already knows.
 *
 * <p>Browsing is deliberately bounded. The tree is walked breadth first, one
 * wave of subscriptions at a time, and the walk stops on the first exact title
 * match, when the caps are reached, or when the caller's time budget runs out -
 * so a library with thousands of folders can never hang the overlay, and the
 * caller always gets an answer.
 */
public class PlayerBrowser {

    /** Result of a lookup. mediaId is null whenever nothing usable was found. */
    public interface Listener {
        void onFound(String mediaId, String how);
    }

    public static final String HOW_EXACT = "exact";
    public static final String HOW_LOOSE = "loose";
    public static final String HOW_NONE = "none";
    public static final String HOW_NO_SERVICE = "no-service";
    public static final String HOW_TIMEOUT = "timeout";
    public static final String HOW_FAILED = "connect-failed";
    private static final String HOW_CONNECTED = "connected";
    private static final String HOW_IDLE = "idle";

    private static final int MAX_NODES = 300;
    private static final int MAX_ITEMS = 4000;
    private static final int EXACT_SCORE = 100;
    private static final int MIN_ACCEPTED_SCORE = 50;

    private static PlayerBrowser instance;

    public static synchronized PlayerBrowser get(Context c) {
        if (instance == null) {
            instance = new PlayerBrowser(c.getApplicationContext());
        }
        return instance;
    }

    private final Context ctx;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private MediaBrowser browser;
    private ComponentName component;
    private boolean connecting;
    private String status = HOW_IDLE;

    private Search search;
    private String pendingTitle;
    private String pendingArtist;
    private long pendingBudget;
    private Listener pendingListener;

    private PlayerBrowser(Context c) {
        this.ctx = c;
    }

    public String status() {
        return status;
    }

    /** Localised one-line summary for the settings page. */
    public String statusText(Context c) {
        if (HOW_IDLE.equals(status)) {
            return c.getString(R.string.browser_idle);
        }
        if (HOW_CONNECTED.equals(status)) {
            return c.getString(R.string.browser_ok)
                    + (component == null ? "" : " " + component.getPackageName());
        }
        if (HOW_NO_SERVICE.equals(status)) {
            return c.getString(R.string.browser_none);
        }
        if (HOW_FAILED.equals(status)) {
            return c.getString(R.string.browser_failed);
        }
        if (HOW_TIMEOUT.equals(status)) {
            return c.getString(R.string.browser_timeout);
        }
        if (HOW_EXACT.equals(status)) {
            return c.getString(R.string.browser_exact);
        }
        if (HOW_LOOSE.equals(status)) {
            return c.getString(R.string.browser_loose);
        }
        if (HOW_NONE.equals(status)) {
            return c.getString(R.string.browser_miss);
        }
        return status;
    }

    /**
     * Look up the player's id for a track. Never blocks and the listener is always
     * called exactly once: either with an id, or with null plus the reason.
     */
    public void findMediaId(String title, String artist, long budgetMs, Listener listener) {
        if (title == null || title.trim().isEmpty()) {
            listener.onFound(null, HOW_NONE);
            return;
        }

        // A newer lookup replaces whatever was still in flight.
        pendingListener = null;
        cancelSearch();

        if (browser != null && browser.isConnected()) {
            startSearch(title, artist, budgetMs, listener);
            return;
        }

        pendingTitle = title;
        pendingArtist = artist;
        pendingBudget = budgetMs;
        pendingListener = listener;

        if (connecting) {
            return;
        }
        if (browser == null) {
            component = findService();
            if (component == null) {
                status = HOW_NO_SERVICE;
                deliver(null, HOW_NO_SERVICE);
                return;
            }
            browser = new MediaBrowser(ctx, component, connectionCallback, null);
        }
        connecting = true;
        try {
            browser.connect();
        } catch (Throwable t) {
            connecting = false;
            browser = null;
            status = HOW_FAILED;
            deliver(null, HOW_FAILED);
        }
    }

    /** Release the binding; call when the overlay service goes away. */
    public void release() {
        cancelSearch();
        pendingListener = null;
        if (browser != null) {
            try {
                browser.disconnect();
            } catch (Throwable ignored) {
                // ignore
            }
            browser = null;
        }
        connecting = false;
        status = HOW_IDLE;
    }

    // ----- discovery --------------------------------------------------------------

    /**
     * The browser service belonging to the player we are controlling. Bluetooth's
     * AVRCP service also answers this intent, and it is not a player, so it is
     * skipped.
     */
    private ComponentName findService() {
        Intent intent = new Intent(MediaBrowserService.SERVICE_INTERFACE);
        List<ResolveInfo> found;
        try {
            found = ctx.getPackageManager().queryIntentServices(intent, 0);
        } catch (Throwable t) {
            return null;
        }
        if (found == null || found.isEmpty()) {
            return null;
        }
        String preferred = MediaBridge.get().packageName();
        ComponentName fallback = null;
        for (ResolveInfo info : found) {
            if (info.serviceInfo == null || info.serviceInfo.name == null) {
                continue;
            }
            String pkg = info.serviceInfo.packageName;
            if (pkg == null || pkg.contains("bluetooth")) {
                continue;
            }
            ComponentName candidate = new ComponentName(pkg, info.serviceInfo.name);
            if (preferred != null && !preferred.isEmpty() && pkg.equals(preferred)) {
                return candidate;
            }
            if (fallback == null) {
                fallback = candidate;
            }
        }
        return fallback;
    }

    // ----- searching --------------------------------------------------------------

    private final class Search {
        final String want;
        final String wantArtist;
        final long deadline;
        final Listener listener;
        final Deque<String> queue = new ArrayDeque<>();
        final Set<String> subscribed = new HashSet<>();
        final Set<String> seen = new HashSet<>();
        String bestId;
        int bestScore;
        int items;
        int pending;
        boolean finished;

        Search(String title, String artist, long budgetMs, Listener listener) {
            this.want = normalize(title);
            this.wantArtist = normalize(artist);
            this.deadline = System.currentTimeMillis() + budgetMs;
            this.listener = listener;
        }
    }

    private void startSearch(String title, String artist, long budgetMs, Listener listener) {
        Search fresh = new Search(title, artist, budgetMs, listener);
        search = fresh;
        String root;
        try {
            root = browser.getRoot();
        } catch (Throwable t) {
            finish(null, HOW_NONE);
            return;
        }
        if (root == null) {
            finish(null, HOW_NONE);
            return;
        }
        subscribe(fresh, root);
        handler.postDelayed(deadlineRunnable, budgetMs + 250L);
    }

    private void subscribe(Search s, String parentId) {
        if (s.finished || parentId == null || s.subscribed.contains(parentId)) {
            return;
        }
        if (s.subscribed.size() >= MAX_NODES || s.items >= MAX_ITEMS) {
            return;
        }
        s.subscribed.add(parentId);
        s.pending++;
        try {
            browser.subscribe(parentId, subscriptionCallback);
        } catch (Throwable t) {
            s.pending--;
            maybeDone(s);
        }
    }

    private final MediaBrowser.SubscriptionCallback subscriptionCallback =
            new MediaBrowser.SubscriptionCallback() {
                @Override
                public void onChildrenLoaded(String parentId,
                                             List<MediaBrowser.MediaItem> children) {
                    handleChildren(parentId, children);
                }

                @Override
                public void onError(String parentId) {
                    Search s = search;
                    if (s == null || s.finished) {
                        return;
                    }
                    s.pending--;
                    maybeDone(s);
                }
            };

    private void handleChildren(String parentId, List<MediaBrowser.MediaItem> children) {
        Search s = search;
        if (s == null || s.finished) {
            return;
        }
        s.pending--;

        if (children != null) {
            for (MediaBrowser.MediaItem item : children) {
                if (item == null) {
                    continue;
                }
                s.items++;
                if (item.isBrowsable()) {
                    String id = item.getMediaId();
                    if (id != null && s.seen.add(id)) {
                        s.queue.add(id);
                    }
                } else if (score(s, item) >= EXACT_SCORE) {
                    finish(s.bestId, HOW_EXACT);
                    return;
                }
            }
        }

        if (System.currentTimeMillis() > s.deadline) {
            finish(s.bestId, s.bestId == null ? HOW_TIMEOUT : HOW_LOOSE);
            return;
        }

        boolean progressed = false;
        while (!s.queue.isEmpty() && s.subscribed.size() < MAX_NODES) {
            subscribe(s, s.queue.poll());
            progressed = true;
        }
        if (!progressed) {
            maybeDone(s);
        }
    }

    private int score(Search s, MediaBrowser.MediaItem item) {
        MediaDescription description = item.getDescription();
        if (description == null) {
            return 0;
        }
        CharSequence raw = description.getTitle();
        String have = normalize(raw == null ? "" : raw.toString());
        if (have.isEmpty()) {
            return 0;
        }
        int value = matchScore(s.want, have);
        if (value <= 0) {
            return 0;
        }
        CharSequence sub = description.getSubtitle();
        String subtitle = normalize(sub == null ? "" : sub.toString());
        if (!s.wantArtist.isEmpty() && !subtitle.isEmpty() && subtitle.contains(s.wantArtist)) {
            value += 1;
        }
        if (value > s.bestScore) {
            s.bestScore = value;
            s.bestId = item.getMediaId();
        }
        return value;
    }

    /**
     * Exact title beats a prefix, which beats a distant match. The prefix case is
     * only trusted when the shorter side is a large part of the longer one, so a
     * short item such as "Love" cannot win against "Love Story".
     */
    private static int matchScore(String want, String have) {
        if (have.equals(want)) {
            return EXACT_SCORE;
        }
        int shorter = Math.min(want.length(), have.length());
        int longer = Math.max(want.length(), have.length());
        boolean prefix = have.startsWith(want) || want.startsWith(have);
        if (prefix && shorter >= Math.max(4, (longer * 6) / 10)) {
            return 50;
        }
        if (longer >= 6 && (have.contains(want) || want.contains(have))) {
            return 20;
        }
        return 0;
    }

    private void maybeDone(Search s) {
        if (s.finished || s.pending > 0 || !s.queue.isEmpty()) {
            return;
        }
        if (s.bestScore >= MIN_ACCEPTED_SCORE && s.bestId != null) {
            finish(s.bestId, s.bestScore >= EXACT_SCORE ? HOW_EXACT : HOW_LOOSE);
        } else {
            finish(null, HOW_NONE);
        }
    }

    private void finish(String mediaId, String how) {
        Search s = search;
        if (s == null || s.finished) {
            return;
        }
        s.finished = true;
        handler.removeCallbacks(deadlineRunnable);
        for (String id : new ArrayList<>(s.subscribed)) {
            try {
                browser.unsubscribe(id);
            } catch (Throwable ignored) {
                // ignore
            }
        }
        s.subscribed.clear();
        search = null;
        status = how;
        s.listener.onFound(mediaId, how);
    }

    private void cancelSearch() {
        Search s = search;
        if (s == null || s.finished) {
            return;
        }
        s.finished = true;
        handler.removeCallbacks(deadlineRunnable);
        for (String id : new ArrayList<>(s.subscribed)) {
            try {
                browser.unsubscribe(id);
            } catch (Throwable ignored) {
                // ignore
            }
        }
        s.subscribed.clear();
        search = null;
    }

    private final Runnable deadlineRunnable = new Runnable() {
        @Override
        public void run() {
            Search s = search;
            if (s != null && !s.finished) {
                finish(s.bestId, s.bestId == null ? HOW_TIMEOUT : HOW_LOOSE);
            }
        }
    };

    private final MediaBrowser.ConnectionCallback connectionCallback =
            new MediaBrowser.ConnectionCallback() {
                @Override
                public void onConnected() {
                    connecting = false;
                    status = HOW_CONNECTED;
                    String title = pendingTitle;
                    String artist = pendingArtist;
                    long budget = pendingBudget;
                    Listener listener = pendingListener;
                    pendingTitle = null;
                    pendingArtist = null;
                    pendingListener = null;
                    if (listener != null) {
                        startSearch(title, artist, budget, listener);
                    }
                }

                @Override
                public void onConnectionSuspended() {
                    connecting = false;
                    status = HOW_FAILED;
                }

                @Override
                public void onConnectionFailed() {
                    connecting = false;
                    browser = null;
                    status = HOW_FAILED;
                    deliver(null, HOW_FAILED);
                }
            };

    private void deliver(String mediaId, String how) {
        Listener listener = pendingListener;
        pendingListener = null;
        pendingTitle = null;
        pendingArtist = null;
        if (listener != null) {
            listener.onFound(mediaId, how);
        }
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
    }
}
