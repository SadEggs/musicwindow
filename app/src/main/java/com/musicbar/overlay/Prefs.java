package com.musicbar.overlay;

import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.display.DisplayManager;
import android.util.DisplayMetrics;
import android.view.Display;

/**
 * All tunables live here. Everything is applied live: MainActivity writes the
 * preference and tells OverlayService to rebuild the bar.
 */
public final class Prefs {

    private static final String NAME = "fmbar";

    public static final String K_THICKNESS_CM = "thickness_cm";
    public static final String K_LENGTH_PCT = "length_pct";
    public static final String K_EDGE = "edge";
    public static final String K_EDGE_MARGIN_DP = "edge_margin_dp";
    public static final String K_ALPHA_IDLE = "alpha_idle";
    public static final String K_ALPHA_ACTIVE = "alpha_active";
    public static final String K_FADE_DELAY_S = "fade_delay_s";
    public static final String K_ONLY_PLAYING = "only_playing";
    public static final String K_SHOW_ARTIST = "show_artist";
    public static final String K_SHOW_TIME = "show_time";
    public static final String K_SHOW_ART = "show_art";
    public static final String K_PREF_PKG = "pref_pkg";
    public static final String K_CUSTOM_X = "custom_x";
    public static final String K_CUSTOM_Y = "custom_y";
    public static final String K_USE_CUSTOM = "use_custom";
    public static final String K_AUTOSTART = "autostart";
    public static final String K_HANDLE_CM = "handle_cm";
    public static final String K_PINNED = "pinned";
    public static final String K_SWIPE_PCT = "swipe_pct";
    public static final String K_BEEP_MODE = "beep_mode";
    public static final String K_KEEP_SHUFFLE = "keep_shuffle";
    public static final String K_RESHUFFLE = "reshuffle_mode";
    public static final String K_PLAY_ROUTE = "play_route";
    public static final String K_FOLDER_PLAY = "folder_play";

    /** Whether a folder in the library panel also lists the songs of its sub-folders. */
    public static final String K_SUB_SONGS = "sub_songs";

    /** The interface language: follow the system, Chinese, or English. */
    public static final String K_LANG = "lang";

    /** Whether the bar shows the clock and the battery. */
    public static final String K_SHOW_STATUS = "show_status";

    /** Whether tapping one song plays its folder's whole tree instead of the folder alone. */
    public static final String K_TREE_PLAY = "tree_play";

    public static final String EDGE_TOP = "top";
    public static final String EDGE_BOTTOM = "bottom";
    public static final String DEFAULT_PKG = "com.maxmpz.audioplayer";

    /**
     * Smallest usable on-screen footprint. Anything thinner would clip the inner
     * controls, so both the service (when laying out) and the settings page
     * (when showing the hint) use these same values.
     */
    public static final float MIN_BAR_DP = 32f;
    public static final float MIN_HANDLE_DP = 28f;

    private Prefs() {
    }

    public static SharedPreferences sp(Context c) {
        return c.getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    public static float thicknessCm(Context c) {
        return sp(c).getFloat(K_THICKNESS_CM, 3.0f);
    }

    public static int lengthPct(Context c) {
        return sp(c).getInt(K_LENGTH_PCT, 60);
    }

    public static String edge(Context c) {
        return sp(c).getString(K_EDGE, EDGE_BOTTOM);
    }

    public static int edgeMarginDp(Context c) {
        return sp(c).getInt(K_EDGE_MARGIN_DP, 8);
    }

    public static int alphaIdle(Context c) {
        return sp(c).getInt(K_ALPHA_IDLE, 35);
    }

    public static int alphaActive(Context c) {
        return sp(c).getInt(K_ALPHA_ACTIVE, 90);
    }

    public static int fadeDelayS(Context c) {
        return sp(c).getInt(K_FADE_DELAY_S, 4);
    }

    public static boolean onlyPlaying(Context c) {
        return sp(c).getBoolean(K_ONLY_PLAYING, true);
    }

    public static boolean showArtist(Context c) {
        return sp(c).getBoolean(K_SHOW_ARTIST, true);
    }

    public static boolean showTime(Context c) {
        return sp(c).getBoolean(K_SHOW_TIME, true);
    }

    public static boolean showArt(Context c) {
        return sp(c).getBoolean(K_SHOW_ART, false);
    }

    public static String preferredPackage(Context c) {
        String v = sp(c).getString(K_PREF_PKG, DEFAULT_PKG);
        return v == null ? "" : v.trim();
    }

    public static boolean useCustomPosition(Context c) {
        return sp(c).getBoolean(K_USE_CUSTOM, false);
    }

    public static boolean autostart(Context c) {
        return sp(c).getBoolean(K_AUTOSTART, true);
    }

    /** Diameter of the collapsed handle, in centimetres. */
    public static float handleCm(Context c) {
        return sp(c).getFloat(K_HANDLE_CM, 1.6f);
    }

    public static boolean pinned(Context c) {
        return sp(c).getBoolean(K_PINNED, false);
    }

    /**
     * How far a swipe must travel, as a percentage of the song text area, before
     * letting go switches the track. Lower is twitchier, higher needs a longer slide.
     */
    public static int swipePct(Context c) {
        int v = sp(c).getInt(K_SWIPE_PCT, 40);
        return Math.max(10, Math.min(90, v));
    }

    /** 0 = never beep on a track change, 1 = only over Bluetooth, 2 = always. */
    public static int beepMode(Context c) {
        int v = sp(c).getInt(K_BEEP_MODE, 1);
        return Math.max(0, Math.min(2, v));
    }

    /**
     * Put the player's shuffle / repeat mode back after a panel tap. Playing one
     * specific song makes the player build a fresh queue, and the old shuffle mode goes
     * with the queue it just discarded.
     */
    public static boolean keepShuffle(Context c) {
        return sp(c).getBoolean(K_KEEP_SHUFFLE, true);
    }

    // How to undo the shuffle reset a player performs while it builds a new queue:
    // 0 = off, 1 = automatic (recommended), 2 = always press the player's switch once,
    // 3 = the transport command, 4 = the player's notification button.
    public static final int RESHUFFLE_OFF = 0;
    public static final int RESHUFFLE_AUTO = 1;
    public static final int RESHUFFLE_ALWAYS = 2;
    public static final int RESHUFFLE_COMMAND = 3;
    public static final int RESHUFFLE_NOTIFY = 4;

    // Which request a point-song uses: 0 = automatic ladder, 1 = media id, 2 = search,
    // 3 = file uri. Not every player keeps its shuffle mode on every route, so this is
    // selectable while the best default is being worked out.
    public static final int ROUTE_AUTO = 0;
    public static final int ROUTE_MEDIA_ID = 1;
    public static final int ROUTE_SEARCH = 2;
    public static final int ROUTE_URI = 3;

    public static int reshuffleMode(Context c) {
        return clamp(sp(c).getInt(K_RESHUFFLE, RESHUFFLE_OFF), 0, 4);
    }

    public static int playRoute(Context c) {
        return clamp(sp(c).getInt(K_PLAY_ROUTE, ROUTE_URI), 0, 3);
    }

    /**
     * Tell the player which folder a song lives in before asking for that song, so it builds
     * the folder's queue rather than a queue holding one track. This is also what makes
     * shuffle mean "shuffle inside this folder" instead of "shuffle one song".
     */
    public static boolean folderPlay(Context c) {
        return sp(c).getBoolean(K_FOLDER_PLAY, true);
    }

    /**
     * Whether a folder in the library panel also lists the songs of its sub-folders.
     * Songs living deeper in the tree are otherwise only reachable by descending one
     * level at a time, which makes a big folder look as if it held only a few tracks.
     */
    public static boolean showSubSongs(Context c) {
        return sp(c).getBoolean(K_SUB_SONGS, true);
    }

    /** 0 = follow the system, 1 = Chinese, 2 = English. */
    public static int lang(Context c) {
        return sp(c).getInt(K_LANG, Lang.SYSTEM);
    }

    public static boolean treePlay(Context c) {
        // Off by default: Poweramp takes a playlist handed to its media session as a track,
        // fails to open it and skips on, which is worse than plain folder playback. Worth
        // turning on only once the diagnostic says the player lists this playlist itself.
        return sp(c).getBoolean(K_TREE_PLAY, false);
    }

    public static boolean showStatus(Context c) {
        return sp(c).getBoolean(K_SHOW_STATUS, true);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public static int customX(Context c) {
        return sp(c).getInt(K_CUSTOM_X, 0);
    }

    public static int customY(Context c) {
        return sp(c).getInt(K_CUSTOM_Y, 0);
    }

    public static void setCustomPosition(Context c, int x, int y) {
        sp(c).edit()
                .putInt(K_CUSTOM_X, x)
                .putInt(K_CUSTOM_Y, y)
                .putBoolean(K_USE_CUSTOM, true)
                .apply();
    }

    public static void clearCustomPosition(Context c) {
        sp(c).edit()
                .putBoolean(K_USE_CUSTOM, false)
                .putInt(K_CUSTOM_X, 0)
                .putInt(K_CUSTOM_Y, 0)
                .apply();
    }

    // ----- physical size helpers -------------------------------------------------

    @SuppressWarnings("deprecation")
    public static DisplayMetrics metrics(Context c) {
        DisplayManager dm = (DisplayManager) c.getSystemService(Context.DISPLAY_SERVICE);
        DisplayMetrics m = new DisplayMetrics();
        if (dm != null) {
            Display d = dm.getDisplay(Display.DEFAULT_DISPLAY);
            if (d != null) {
                d.getRealMetrics(m);
                return m;
            }
        }
        return c.getResources().getDisplayMetrics();
    }

    /** Physical dots per inch along X, with a sanity fallback to the logical density. */
    public static float dpiX(DisplayMetrics m) {
        float v = m.xdpi;
        if (v < 100f || v > 1200f) {
            v = m.densityDpi;
        }
        return v;
    }

    /** Physical dots per inch along Y, with a sanity fallback to the logical density. */
    public static float dpiY(DisplayMetrics m) {
        float v = m.ydpi;
        if (v < 100f || v > 1200f) {
            v = m.densityDpi;
        }
        return v;
    }

    public static int cmToPxY(Context c, float cm, DisplayMetrics m) {
        return Math.round(cm / 2.54f * dpiY(m));
    }

    public static int cmToPxX(Context c, float cm, DisplayMetrics m) {
        return Math.round(cm / 2.54f * dpiX(m));
    }

    public static int dp(Context c, float dp) {
        return Math.round(dp * c.getResources().getDisplayMetrics().density);
    }
}
