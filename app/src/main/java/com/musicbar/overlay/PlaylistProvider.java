package com.musicbar.overlay;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

/**
 * Serves the one playlist file this app writes.
 *
 * <p>Under scoped storage another app cannot open a {@code file://} path at all, which is
 * exactly why Poweramp reported a playback failure when it was handed one: it received the
 * request and then could not read the file. A content URI with a granted read permission is
 * the supported way across that boundary. This provider serves a single directory, only
 * files whose name starts with this app's playlist prefix, and only for reading - every
 * other path is refused.
 */
public class PlaylistProvider extends ContentProvider {

    public static final String AUTHORITY = "com.musicbar.overlay.playlists";

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        String name = uri == null ? null : uri.getLastPathSegment();
        File dir = Playlists.playlistDir();
        if (name == null || dir == null) {
            throw new FileNotFoundException("no playlist folder");
        }
        if (!name.startsWith(Playlists.PREFIX) || !name.endsWith(".m3u")) {
            throw new FileNotFoundException("not this app's playlist");
        }
        try {
            File file = new File(dir, name).getCanonicalFile();
            if (!file.getParentFile().equals(dir.getCanonicalFile())) {
                throw new FileNotFoundException("outside the playlist folder");
            }
            return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
        } catch (IOException e) {
            throw new FileNotFoundException("unresolvable path");
        }
    }

    @Override
    public String getType(Uri uri) {
        return "audio/x-mpegurl";
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
                        String sortOrder) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
