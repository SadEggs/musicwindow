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
 * three columns, filled top to bottom first and then left to right, with folders
 * ahead of songs. The header carries the parent-directory button, the current
 * path and the close cross.
 */
public class FolderPanelView extends LinearLayout {

    public interface Callback {
        void onClose();

        void onPlaySong(MediaLibrary.Song song);
    }

    private static final int COLUMNS = 3;

    private final Callback cb;
    private final TextView parentButton;
    private final TextView pathView;
    private final LinearLayout grid;
    private final int rowHeightPx;
    private final int headerHeightPx;

    private String folder = MediaLibrary.ROOT;
    private int panelHeightPx;

    public FolderPanelView(Context ctx, Callback callback) {
        super(ctx);
        this.cb = callback;
        setOrientation(VERTICAL);
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

        grid.removeAllViews();

        if (library.songCount() == 0) {
            grid.addView(makeNotice(R.string.lib_empty));
            return;
        }

        List<Item> items = new ArrayList<>();
        for (String child : library.foldersIn(folder)) {
            items.add(Item.folder(child));
        }
        for (MediaLibrary.Song song : library.songsIn(folder)) {
            items.add(Item.song(song));
        }
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
                    // Top to bottom inside a column, then on to the next column.
                    int index = page * perPage + column * rows + row;
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

        static Item folder(String key) {
            String name = MediaLibrary.nameOf(key);
            return new Item(true, key, name == null ? key : name, null);
        }

        static Item song(MediaLibrary.Song song) {
            return new Item(false, null, song.title, song);
        }
    }
}
