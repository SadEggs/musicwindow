package com.musicbar.overlay;

import android.content.Context;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Explorer style browser that unfolds out of the bar. Tiles are laid out in
 * three columns, filled left to right first and then top to bottom, with folders
 * ahead of songs. The header carries the parent-directory button, the current
 * path and the close cross.
 */
public class FolderPanelView extends LinearLayout {

    public interface Callback {
        void onClose();

        void onPlaySong(MediaLibrary.Song song);

        /** Ask for the whole folder branch - this folder and every sub-folder - to be played. */
        void onPlayFolder(String folder);
    }

    private static final int COLUMNS = 3;

    /** Upper bound on tiles for one folder, so a huge branch cannot stall the panel. */
    private static final int MAX_TILES = 600;

    private final Callback cb;
    private final TextView parentButton;
    private final TextView folderButton;
    private final TextView modeButton;
    private final TextView deepButton;
    private final TextView pathView;
    private final LinearLayout grid;
    private final int rowHeightPx;
    private final int headerHeightPx;

    private String folder = MediaLibrary.ROOT;
    private int panelHeightPx;

    /** Shows what the two play actions will do: shuffle the branch, or play it in order. */
    private void refreshMode() {
        modeButton.setText(Prefs.panelShuffle(getContext())
                ? R.string.panel_shuffle_on : R.string.panel_shuffle_off);
        deepButton.setText(Prefs.deepShuffle(getContext())
                ? R.string.panel_deep_on : R.string.panel_deep_off);
    }

    public FolderPanelView(Context ctx, Callback callback) {
        super(ctx);
        this.cb = callback;
        setOrientation(VERTICAL);
        // Never take focus: a focused window makes the system show the navigation bar, which
        // over a full-screen game covers the very thing this overlay exists to stay out of.
        // Touch, clicks and scrolling all work without focus.
        setFocusable(false);
        setFocusableInTouchMode(false);
        setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);
        setBackgroundResource(R.drawable.bg_bar);
        // Tapping tiles should not make the system touch sound: the panel sits over
        // a game and the tablet speaker stays quiet.
        setSoundEffectsEnabled(false);

        rowHeightPx = dp(78);
        headerHeightPx = dp(46);

        LinearLayout header = new LinearLayout(ctx);
        header.setOrientation(HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(6), dp(4), dp(6), dp(4));

        parentButton = new TextView(ctx);
        parentButton.setText(R.string.lib_parent);
        parentButton.setTextSize(13f);
        parentButton.setTextColor(0xFFFFFFFF);
        parentButton.setPadding(dp(8), dp(6), dp(8), dp(6));
        parentButton.setBackgroundResource(R.drawable.bg_btn);
        parentButton.setOnClickListener(v -> goUp());
        header.addView(parentButton,
                new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));

        // Hands this whole branch to the player as a playlist, which is how a branch deeper
        // than one level gets a queue of its own.
        folderButton = new TextView(ctx);
        folderButton.setText(R.string.lib_play_folder);
        folderButton.setTextSize(13f);
        folderButton.setTextColor(0xFFFFFFFF);
        folderButton.setPadding(dp(8), dp(6), dp(8), dp(6));
        folderButton.setBackgroundResource(R.drawable.bg_btn);
        folderButton.setContentDescription(ctx.getString(R.string.cd_play_folder));
        folderButton.setOnClickListener(v -> {
            if (cb != null) {
                cb.onPlayFolder(folder);
            }
        });
        LayoutParams folderParams = new LayoutParams(LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT);
        folderParams.leftMargin = dp(6);
        header.addView(folderButton, folderParams);

        // What the two play actions will do, and the switch between the two ways of doing it.
        modeButton = new TextView(ctx);
        modeButton.setTextSize(13f);
        modeButton.setTextColor(0xFFFFFFFF);
        modeButton.setPadding(dp(8), dp(6), dp(8), dp(6));
        modeButton.setBackgroundResource(R.drawable.bg_btn);
        modeButton.setContentDescription(ctx.getString(R.string.panel_shuffle_hint));
        modeButton.setOnClickListener(v -> {
            Prefs.setPanelShuffle(getContext(), !Prefs.panelShuffle(getContext()));
            refreshMode();
        });
        LayoutParams modeParams = new LayoutParams(LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT);
        modeParams.leftMargin = dp(6);
        header.addView(modeButton, modeParams);

        // Whether that shuffling reaches into the sub-folders: the difference between "this
        // folder" and "everything under it", which a leftover setting used to decide silently.
        deepButton = new TextView(ctx);
        deepButton.setTextSize(13f);
        deepButton.setTextColor(0xFFFFFFFF);
        deepButton.setPadding(dp(8), dp(6), dp(8), dp(6));
        deepButton.setBackgroundResource(R.drawable.bg_btn);
        deepButton.setContentDescription(ctx.getString(R.string.deep_hint));
        deepButton.setOnClickListener(v -> {
            Prefs.setDeepShuffle(getContext(), !Prefs.deepShuffle(getContext()));
            refreshMode();
        });
        LayoutParams deepParams = new LayoutParams(LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT);
        deepParams.leftMargin = dp(6);
        header.addView(deepButton, deepParams);
        refreshMode();

        pathView = new TextView(ctx);
        pathView.setTextSize(12f);
        pathView.setTextColor(0xFFB0BEC5);
        pathView.setSingleLine(true);
        pathView.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        pathView.setGravity(Gravity.CENTER);
        LayoutParams pathParams = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        pathParams.leftMargin = dp(8);
        pathParams.rightMargin = dp(8);
        header.addView(pathView, pathParams);

        TextView closeButton = new TextView(ctx);
        closeButton.setText(R.string.lib_close);
        closeButton.setTextSize(16f);
        closeButton.setTextColor(0xFFFFFFFF);
        closeButton.setTypeface(Typeface.DEFAULT_BOLD);
        closeButton.setPadding(dp(12), dp(4), dp(12), dp(4));
        closeButton.setBackgroundResource(R.drawable.bg_btn);
        closeButton.setContentDescription(ctx.getString(R.string.cd_close_library));
        closeButton.setOnClickListener(v -> {
            if (cb != null) {
                cb.onClose();
            }
        });
        header.addView(closeButton,
                new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));

        addView(header, new LayoutParams(LayoutParams.MATCH_PARENT, headerHeightPx));

        ScrollView scroll = new ScrollView(ctx);
        scroll.setFillViewport(true);
        grid = new LinearLayout(ctx);
        grid.setOrientation(VERTICAL);
        scroll.addView(grid, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));
        addView(scroll, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));
    }

    /** The service tells the panel how tall its window is, so paging can be computed. */
    public void setPanelHeight(int px) {
        panelHeightPx = px;
    }

    /** Show the library, opening inside {@code startFolder} when it is known. */
    public void open(String startFolder) {
        folder = startFolder == null ? MediaLibrary.ROOT : startFolder;
        rebuild();
    }

    public String currentFolder() {
        return folder;
    }

    private void goUp() {
        if (MediaLibrary.ROOT.equals(folder)) {
            return;
        }
        folder = MediaLibrary.parentOf(folder);
        rebuild();
    }

    private void rebuild() {
        Context ctx = getContext();
        MediaLibrary library = MediaLibrary.get(ctx);
        library.refreshIfStale();

        boolean atRoot = MediaLibrary.ROOT.equals(folder);
        pathView.setText(atRoot ? ctx.getString(R.string.lib_root) : folder);
        parentButton.setEnabled(!atRoot);
        parentButton.setClickable(!atRoot);
        parentButton.setAlpha(atRoot ? 0.4f : 1f);

        boolean hasSongs = library.songCountInTree(folder) > 0;
        folderButton.setEnabled(hasSongs);
        folderButton.setClickable(hasSongs);
        folderButton.setAlpha(hasSongs ? 1f : 0.4f);

        grid.removeAllViews();

        if (library.songCount() == 0) {
            grid.addView(makeNotice(R.string.lib_empty));
            return;
        }

        List<Item> items = new ArrayList<>();
        for (String child : library.foldersIn(folder)) {
            int tree = library.songCountInTree(child);
            String name = MediaLibrary.nameOf(child);
            StringBuilder label = new StringBuilder(name == null ? child : name);
            if (tree > 0) {
                // How many songs a tap inside here would shuffle: the whole branch, which is
                // the number that matters, rather than the folder's own files alone.
                label.append(" \u00b7 ").append(ctx.getString(R.string.folder_songs, tree));
            }
            items.add(Item.folder(child, label.toString()));
        }
        for (MediaLibrary.Song song : library.songsIn(folder)) {
            items.add(Item.song(song, song.title));
        }
        // Songs deeper in this branch are deliberately not listed here. The folder rows above
        // say how many are down there, and a flat list of the whole tree buries the folder that
        // was actually being looked for.
        if (items.isEmpty()) {
            grid.addView(makeNotice(R.string.lib_no_song));
            return;
        }

        int rows = rowsPerPage();
        int perPage = rows * COLUMNS;
        int total = items.size();
        int pages = Math.max(1, (total + perPage - 1) / perPage);

        for (int page = 0; page < pages; page++) {
            LinearLayout pageBox = new LinearLayout(ctx);
            pageBox.setOrientation(VERTICAL);
            for (int row = 0; row < rows; row++) {
                LinearLayout rowBox = new LinearLayout(ctx);
                rowBox.setOrientation(HORIZONTAL);
                for (int column = 0; column < COLUMNS; column++) {
                    // Left to right inside a row, then on to the next row.
                    int index = page * perPage + row * COLUMNS + column;
                    View cell = index < total ? makeCell(items.get(index)) : new View(ctx);
                    rowBox.addView(cell, new LayoutParams(0, rowHeightPx, 1f));
                }
                pageBox.addView(rowBox, new LayoutParams(LayoutParams.MATCH_PARENT, rowHeightPx));
            }
            grid.addView(pageBox,
                    new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        }
    }

    private int rowsPerPage() {
        int available = panelHeightPx > 0 ? panelHeightPx - headerHeightPx : dp(320);
        return Math.max(1, available / Math.max(1, rowHeightPx));
    }

    private TextView makeNotice(int textRes) {
        TextView notice = new TextView(getContext());
        notice.setText(textRes);
        notice.setTextSize(13f);
        notice.setTextColor(0xFF90A4AE);
        notice.setPadding(dp(14), dp(14), dp(14), dp(14));
        return notice;
    }

    private View makeCell(Item item) {
        Context ctx = getContext();
        LinearLayout cell = new LinearLayout(ctx);
        cell.setOrientation(VERTICAL);
        cell.setGravity(Gravity.CENTER);
        cell.setPadding(dp(2), dp(4), dp(2), dp(4));
        cell.setSoundEffectsEnabled(false);

        ImageView icon = new ImageView(ctx);
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        icon.setImageResource(item.folder ? R.drawable.ic_folder : R.drawable.ic_note);
        cell.addView(icon, new LayoutParams(dp(36), dp(36)));

        TextView label = new TextView(ctx);
        label.setText(item.label);
        label.setTextSize(11f);
        label.setTextColor(0xFFECEFF1);
        label.setGravity(Gravity.CENTER);
        label.setMaxLines(2);
        label.setEllipsize(TextUtils.TruncateAt.END);
        LayoutParams labelParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        labelParams.topMargin = dp(2);
        cell.addView(label, labelParams);

        cell.setOnClickListener(v -> {
            if (cb == null) {
                return;
            }
            if (item.folder) {
                folder = item.folderKey;
                rebuild();
            } else if (item.song != null) {
                cb.onPlaySong(item.song);
            }
        });
        return cell;
    }

    /** The part of a song's folder path that lies below the folder being shown. */
    private static String below(String base, String full) {
        String root = base == null ? "" : base;
        String path = full == null ? "" : full;
        if (root.isEmpty()) {
            return path;
        }
        if (path.startsWith(root + "/")) {
            return path.substring(root.length() + 1);
        }
        return path;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    /** One tile: either a folder to descend into, or a song to play. */
    private static final class Item {
        final boolean folder;
        final String folderKey;
        final String label;
        final MediaLibrary.Song song;

        private Item(boolean folder, String folderKey, String label, MediaLibrary.Song song) {
            this.folder = folder;
            this.folderKey = folderKey;
            this.label = label;
            this.song = song;
        }

        static Item folder(String key, String label) {
            return new Item(true, key, label, null);
        }

        static Item song(MediaLibrary.Song song, String label) {
            return new Item(false, null, label, song);
        }
    }
}
