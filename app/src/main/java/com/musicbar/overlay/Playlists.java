package com.musicbar.overlay;

import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.List;

/**
 * Writes an .m3u playlist for a folder branch so the player can build the queue itself.
 *
 * <p>A queue built by a player from a point-song belongs to that player, and Poweramp in
 * particular keeps a folder's queue to the folder's own songs: sub-folders are separate
 * entries there, so a shuffle over "this folder" never reaches the songs below it. Handing
 * the player a playlist file sidesteps the whole question. The player opens it like any
 * other playlist, which means shuffle, gapless switching, equaliser presets and every other
 * audio setting stay exactly where they were - inside the player.
 *
 * <p>Writing into the user's music folders needs "all files access", so every entry point
 * checks {@link #allowed(Context)} first and tells the user what to grant.
 */
public final class Playlists {

    /** Prefix of every playlist this app writes, so they are easy to recognise and delete. */
    public static final String PREFIX = "MusicBar \u968f\u673a - ";

    private Playlists() {
    }

    /** Whether a playlist may be written into the user's storage right now. */
    public static boolean allowed(Context c) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            // Before Android 11 the ordinary storage permission covers this.
            return true;
        }
        try {
            return Environment.isExternalStorageManager();
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Write the whole branch as an .m3u next to the songs it lists, replacing the file from
     * last time. Returns null when there is nothing to write, or nowhere to write it.
     */
    public static File write(Context c, String folder, List<MediaLibrary.Song> songs) {
        if (songs == null || songs.isEmpty()) {
            return null;
        }
        String name = MediaLibrary.nameOf(folder);
        if (name == null || name.isEmpty()) {
            name = "\u5168\u90e8";
        }
        // Always the same file: one playlist, overwritten every time, in one folder of its
        // own. Writing next to each folder's songs would leave one file per folder behind,
        // which is exactly the kind of litter this app should not create.
        File dir = playlistDir();
        File target = new File(dir, PREFIX + "\u64ad\u653e\u5217\u8868.m3u");
        return write(target, songs, name) ? target : null;
    }

    private static boolean write(File file, List<MediaLibrary.Song> songs, String title) {
        Writer writer = null;
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                return false;
            }
            writer = new OutputStreamWriter(new FileOutputStream(file),
                    Charset.forName("UTF-8").newEncoder());
            writer.write("#EXTM3U\n");
            // Players that understand it show this as the playlist's name.
            writer.write("#PLAYLIST:" + title + "\n");
            for (MediaLibrary.Song song : songs) {
                if (song == null || song.path == null || song.path.isEmpty()) {
                    continue;
                }
                // Extended info first: players vary in how much of it they need, but an entry
                // without it is rejected outright by more than one of them.
                writer.write("#EXTINF:");
                writer.write(Long.toString(Math.max(0L, song.durationMs / 1000L)));
                writer.write(',');
                writer.write(song.title == null ? "" : song.title);
                writer.write('\n');
                writer.write(song.path);
                writer.write('\n');
            }
            writer.flush();
            writer.close();
            writer = null;
            return true;
        } catch (Throwable t) {
            return false;
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (Throwable ignored) {
                    // Nothing left to do about it.
                }
            }
        }
    }

    /**
     * The one folder this app writes into: inside the standard music folder, so a player
     * that scans there still lists the playlist, and kept apart from the user's own files.
     * Falls back to the app's private external folder when that location is not writable.
     */
    static File playlistDir() {
        File music = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC);
        File dir = new File(music, "MusicBar");
        if ((dir.isDirectory() || dir.mkdirs()) && dir.canWrite()) {
            return dir;
        }
        return new File(Environment.getExternalStorageDirectory(), "MusicBar");
    }

    /** The playlist file, whether or not it has been written yet. */
    public static File playlistFile() {
        return new File(playlistDir(), PREFIX + "\u64ad\u653e\u5217\u8868.m3u");
    }

    /**
     * Delete playlists this app wrote, including the ones 0.16 left next to the songs.
     * Only files whose name starts with this app's prefix are ever touched. Returns how
     * many were removed.
     */
    public static int cleanup() {
        int removed = 0;
        removed += deleteIn(playlistDir());
        // 0.16 wrote one file per folder, inside the folder holding the songs.
        for (String folder : MediaLibrary.foldersSnapshot()) {
            if (folder == null || folder.isEmpty()) {
                continue;
            }
            removed += deleteIn(new File(folder));
        }
        return removed;
    }

    /** Delete the playlists this app wrote in one folder, and nothing else. */
    private static int deleteIn(File dir) {
        int removed = 0;
        File[] files = dir == null ? null : dir.listFiles();
        if (files == null) {
            return 0;
        }
        for (File file : files) {
            if (!file.isFile()) {
                continue;
            }
            String name = file.getName();
            boolean playlist = name.endsWith(".m3u") || name.endsWith(".m3u8");
            if (playlist && name.startsWith(PREFIX) && file.delete()) {
                removed++;
            }
        }
        return removed;
    }

    /**
     * The playlist as a content URI. A file:// URI cannot be read by the player at all under
     * scoped storage, so the playlist is served through this app's own provider instead and
     * the player is granted read access to that one URI.
     */
    public static Uri uriOf(File file) {
        if (file == null) {
            return null;
        }
        return new Uri.Builder()
                .scheme("content")
                .authority(PlaylistProvider.AUTHORITY)
                .appendPath(file.getName())
                .build();
    }
}
