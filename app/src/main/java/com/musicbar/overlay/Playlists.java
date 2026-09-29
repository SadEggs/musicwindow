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
        File target = null;
        if (folder != null && !folder.isEmpty()) {
            target = new File(folder, PREFIX + name + ".m3u");
            if (write(target, songs)) {
                return target;
            }
        }
        // The songs' own folder can be read-only (a mounted card, a borrowed folder), so
        // fall back to a folder every player scans.
        target = new File(fallbackDir(), PREFIX + name + ".m3u");
        return write(target, songs) ? target : null;
    }

    private static boolean write(File file, List<MediaLibrary.Song> songs) {
        Writer writer = null;
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                return false;
            }
            writer = new OutputStreamWriter(new FileOutputStream(file),
                    Charset.forName("UTF-8").newEncoder());
            writer.write("#EXTM3U\n");
            for (MediaLibrary.Song song : songs) {
                if (song == null || song.path == null || song.path.isEmpty()) {
                    continue;
                }
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

    /** A music folder every player scans, used when the songs' own folder cannot be written. */
    private static File fallbackDir() {
        File music = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC);
        return new File(music, "MusicBar");
    }

    public static Uri uriOf(File file) {
        return Uri.fromFile(file);
    }
}
