package com.musicbar.overlay;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Reads the system audio library through MediaStore and folds it into a folder
 * tree, so the overlay can browse "folder then songs" the way a file explorer
 * does.
 *
 * <p>This works for any player, not just Poweramp, because it never talks to the
 * player: it reads the same library the system media scanner keeps, and matches
 * the currently playing title against it. It only needs READ_MEDIA_AUDIO, so no
 * "all files access" style permission is involved.
 */
public final class MediaLibrary {

    /** Folder key of the storage root. */
    public static final String ROOT = "";

    /** Bounds on walking a whole branch of the folder tree. */
    private static final int MAX_TREE_DEPTH = 24;
    private static final int MAX_TREE_SONGS = 5000;

    public static final class Song {
        public final long id;
        public final String title;
        public final String artist;
        public final String folder;
        public final long durationMs;

        Song(long id, String title, String artist, String folder, long durationMs) {
            this.id = id;
            this.title = title;
            this.artist = artist;
            this.folder = folder;
            this.durationMs = durationMs;
        }

        public Uri uri() {
            return ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id);
        }
    }

    private static MediaLibrary instance;

    public static synchronized MediaLibrary get(Context c) {
        if (instance == null) {
            instance = new MediaLibrary(c.getApplicationContext());
        }
        return instance;
    }

    private final Context ctx;
    private final List<Song> all = new ArrayList<>();
    private final Map<String, List<Song>> songsByFolder = new HashMap<>();
    private final Map<String, Set<String>> childrenByFolder = new HashMap<>();
    private long loadedAt;
    private boolean loaded;

    private MediaLibrary(Context c) {
        this.ctx = c;
    }

    public synchronized boolean isLoaded() {
        return loaded;
    }

    public synchronized int songCount() {
        return all.size();
    }

    /** Rescan when the cached copy is missing or older than two minutes. */
    public synchronized void refreshIfStale() {
        if (!loaded || System.currentTimeMillis() - loadedAt > 120000L) {
            load();
        }
    }

    public synchronized void load() {
        all.clear();
        songsByFolder.clear();
        childrenByFolder.clear();

        Cursor cursor = null;
        try {
            boolean modern = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q;
            String pathColumn = modern
                    ? MediaStore.Audio.Media.RELATIVE_PATH
                    : MediaStore.Audio.Media.DATA;

            String[] columns = new String[] {
                    MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media.ARTIST,
                    MediaStore.Audio.Media.DURATION,
                    MediaStore.Audio.Media.IS_MUSIC,
                    pathColumn,
            };

            cursor = ctx.getContentResolver().query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, columns, null, null, null);

            if (cursor != null) {
                int idxId = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
                int idxTitle = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE);
                int idxArtist = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST);
                int idxDuration = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION);
                int idxMusic = cursor.getColumnIndex(MediaStore.Audio.Media.IS_MUSIC);
                int idxPath = cursor.getColumnIndexOrThrow(pathColumn);

                while (cursor.moveToNext()) {
                    if (idxMusic >= 0 && cursor.getInt(idxMusic) == 0) {
                        continue;
                    }
                    long id = cursor.getLong(idxId);
                    String title = cursor.getString(idxTitle);
                    String artist = cursor.getString(idxArtist);
                    long duration = cursor.getLong(idxDuration);
                    String folder = folderOf(cursor.getString(idxPath), modern);

                    Song song = new Song(id,
                            title == null ? "" : title,
                            artist == null ? "" : artist,
                            folder,
                            duration);
                    all.add(song);

                    List<Song> bucket = songsByFolder.get(folder);
                    if (bucket == null) {
                        bucket = new ArrayList<>();
                        songsByFolder.put(folder, bucket);
                    }
                    bucket.add(song);
                    registerAncestors(folder);
                }
            }
        } catch (Throwable ignored) {
            // Permission missing or provider unavailable: leave the library empty.
        } finally {
            if (cursor != null) {
                try {
                    cursor.close();
                } catch (Throwable ignored) {
                    // ignore
                }
            }
        }

        loaded = true;
        loadedAt = System.currentTimeMillis();
    }

    public synchronized List<String> foldersIn(String folder) {
        Set<String> kids = childrenByFolder.get(key(folder));
        List<String> list = new ArrayList<>();
        if (kids != null) {
            list.addAll(kids);
        }
        Collections.sort(list, (a, b) -> a.compareToIgnoreCase(b));
        return list;
    }

    public synchronized List<Song> songsIn(String folder) {
        List<Song> bucket = songsByFolder.get(key(folder));
        List<Song> list = new ArrayList<>();
        if (bucket != null) {
            list.addAll(bucket);
        }
        Collections.sort(list, (a, b) -> a.title.compareToIgnoreCase(b.title));
        return list;
    }

    /** How many songs live directly in this folder. */
    public synchronized int songCountIn(String folder) {
        List<Song> bucket = songsByFolder.get(key(folder));
        return bucket == null ? 0 : bucket.size();
    }

    /** How many songs live in this folder and in everything under it. */
    public synchronized int songCountInTree(String folder) {
        return songsInTree(folder).size();
    }

    /**
     * Every song in this folder and in its sub-folders, folder by folder and then by title,
     * so a whole branch can be shown or shuffled without descending into each level first.
     * The depth and the size are both bounded: a library can nest arbitrarily deep, and the
     * panel must never try to build a view for tens of thousands of tiles.
     */
    public synchronized List<Song> songsInTree(String folder) {
        List<Song> out = new ArrayList<>();
        collectTree(key(folder), out, 0);
        return out;
    }

    private void collectTree(String dir, List<Song> out, int depth) {
        if (depth > MAX_TREE_DEPTH || out.size() >= MAX_TREE_SONGS) {
            return;
        }
        List<Song> own = songsByFolder.get(dir);
        if (own != null) {
            List<Song> sorted = new ArrayList<>(own);
            Collections.sort(sorted, (a, b) -> a.title.compareToIgnoreCase(b.title));
            out.addAll(sorted);
        }
        Set<String> kids = childrenByFolder.get(dir);
        if (kids == null) {
            return;
        }
        List<String> names = new ArrayList<>(kids);
        Collections.sort(names, (a, b) -> a.compareToIgnoreCase(b));
        for (String kid : names) {
            collectTree(kid, out, depth + 1);
        }
    }

    /** Whether two folder keys mean the same folder. */
    public static boolean sameFolder(String a, String b) {
        return key(a).equals(key(b));
    }

    /**
     * Best guess at the folder holding the track that is playing right now, by
     * matching title (and artist when it helps). Returns null when unknown.
     */
    public synchronized String findFolder(String title, String artist) {
        String want = normalize(title);
        if (want.isEmpty()) {
            return null;
        }
        String wantArtist = normalize(artist);
        Song best = null;
        int bestScore = 0;
        for (Song song : all) {
            String have = normalize(song.title);
            if (have.isEmpty()) {
                continue;
            }
            int score;
            if (have.equals(want)) {
                score = 3;
            } else if (have.startsWith(want) || want.startsWith(have)) {
                score = 2;
            } else if (have.contains(want) || want.contains(have)) {
                score = 1;
            } else {
                continue;
            }
            if (!wantArtist.isEmpty() && normalize(song.artist).contains(wantArtist)) {
                score += 1;
            }
            if (score > bestScore) {
                bestScore = score;
                best = song;
            }
        }
        return best == null ? null : best.folder;
    }

    public static String parentOf(String folder) {
        if (folder == null || folder.isEmpty()) {
            return ROOT;
        }
        int slash = folder.lastIndexOf('/');
        return slash < 0 ? ROOT : folder.substring(0, slash);
    }

    /** Last path segment, or null for the root (the caller localises the label). */
    public static String nameOf(String folder) {
        if (folder == null || folder.isEmpty()) {
            return null;
        }
        int slash = folder.lastIndexOf('/');
        return slash < 0 ? folder : folder.substring(slash + 1);
    }

    private static String key(String folder) {
        return folder == null ? ROOT : folder;
    }

    private void registerAncestors(String folder) {
        String current = key(folder);
        int guard = 0;
        while (!current.isEmpty() && guard++ < 64) {
            String parent = parentOf(current);
            Set<String> kids = childrenByFolder.get(parent);
            if (kids == null) {
                kids = new HashSet<>();
                childrenByFolder.put(parent, kids);
            }
            kids.add(current);
            current = parent;
        }
    }

    private static String folderOf(String raw, boolean modern) {
        if (raw == null) {
            return ROOT;
        }
        String value = raw.trim();
        if (value.isEmpty()) {
            return ROOT;
        }

        if (!modern) {
            int slash = value.lastIndexOf('/');
            if (slash >= 0) {
                value = value.substring(0, slash);
            }
            // Absolute path such as /storage/emulated/0/Music/Album: drop the
            // storage prefix so both branches produce the same style of key.
            if (value.startsWith("/storage/")) {
                int first = value.indexOf('/', "/storage/".length());
                if (first >= 0) {
                    int second = value.indexOf('/', first + 1);
                    if (second >= 0) {
                        value = value.substring(second + 1);
                    }
                }
            }
        }

        while (value.startsWith("/")) {
            value = value.substring(1);
        }
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
    }
}
