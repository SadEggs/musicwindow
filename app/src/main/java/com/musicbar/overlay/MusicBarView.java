package com.musicbar.overlay;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * The overlay bar itself. Built programmatically so the window size stays the
 * single source of truth for the layout.
 */
public class MusicBarView extends LinearLayout {

    public interface Callback {
        void onPrev();

        void onToggle();

        void onNext();

        void onPinToggle();

        void onLibraryToggle();

        void onSeekTo(long positionMs);

        void onUserActivity();

        void onCollapseToggle(boolean collapsed);

        void onDragStart();

        void onDrag(int totalDx, int totalDy);

        void onDragEnd();
    }

    private final Callback cb;
    private final int slop;

    private final LinearLayout fullBox;
    private final FrameLayout handleBox;
    private final ImageView artView;
    private final ImageView handleIcon;
    private final FrameLayout textStack;
    private final LinearLayout texts;
    private final LinearLayout previewBox;
    private final TextView previewTitle;
    private final TextView previewHint;
    private final TextView titleView;
    private final TextView artistView;
    private final TextView curView;
    private final TextView totalView;
    private final SeekBar seekBar;
    private final ImageButton playButton;
    private final ImageButton pinButton;
    private ImageButton libraryButton;
    private ImageButton closeButton;

    private static final long LONG_PRESS_MS = 320L;
    private static final int SWIPE_MIN_DP = 56;
    private static final int SWIPE_START_DP = 16;
    /** The dimmed colour the secondary text uses, matched by the clock and the battery. */
    private static final int STATUS_COLOR = 0xFFB0BEC5;
    private static final long CLOCK_STEP_MS = 1000L;

    private LinearLayout statusRow;
    private TextView timeView;
    private TextView batteryText;
    private BatteryView batteryView;
    private final SimpleDateFormat clockFormat =
            new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
    private final Handler clock = new Handler(Looper.getMainLooper());
    private final Runnable clockTick = new Runnable() {
        @Override
        public void run() {
            updateClock();
            clock.postDelayed(this, CLOCK_STEP_MS);
        }
    };
    private final BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateBattery(intent);
        }
    };

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (statusRow != null) {
            statusRow.setVisibility(Prefs.showStatus(getContext()) ? VISIBLE : GONE);
        }
        updateClock();
        // Start on the next whole second so the seconds do not drift.
        clock.postDelayed(clockTick, CLOCK_STEP_MS - (System.currentTimeMillis() % CLOCK_STEP_MS));
        Intent sticky = null;
        try {
            sticky = getContext().registerReceiver(batteryReceiver,
                    new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        } catch (Throwable ignored) {
            // A missing battery readout is not worth failing over.
        }
        if (sticky != null) {
            updateBattery(sticky);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        clock.removeCallbacks(clockTick);
        try {
            getContext().unregisterReceiver(batteryReceiver);
        } catch (Throwable ignored) {
            // It was never registered, or the context is already gone.
        }
        super.onDetachedFromWindow();
    }

    /** Reload the strip after the setting changed, without rebuilding the bar. */
    public void refreshStatusVisibility() {
        boolean on = Prefs.showStatus(getContext());
        if (statusRow != null) {
            statusRow.setVisibility(on ? VISIBLE : GONE);
        }
        if (on) {
            updateClock();
        }
    }

    private void updateClock() {
        if (timeView != null) {
            timeView.setText(clockFormat.format(new Date()));
        }
    }

    private void updateBattery(Intent intent) {
        if (intent == null) {
            return;
        }
        int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        boolean charging = status == BatteryManager.BATTERY_STATUS_CHARGING
                || status == BatteryManager.BATTERY_STATUS_FULL;
        int percent = (level < 0 || scale <= 0) ? -1 : Math.round(level * 100f / scale);
        if (batteryView != null) {
            batteryView.setLevel(percent, charging);
        }
        if (batteryText != null) {
            batteryText.setText(percent < 0 ? "" : percent + "%");
        }
    }

    private final Handler ui = new Handler(Looper.getMainLooper());

    private boolean compact;
    private boolean collapsed;
    private boolean userSeeking;
    private boolean dragging;
    private boolean longPressed;
    private boolean swipeFired;
    private boolean pinned;
    private boolean idleAtDown;
    private boolean swipeAllowed;
    private boolean lastPlaying;
    private boolean swipeMode;
    private float swipeDx;
    private MediaBridge bridge;
    private float downRawX;
    private float downRawY;
    private long durationMs;
    private Bitmap lastArt;
    private Bitmap lastCircleSrc;
    private Bitmap lastCircled;

    private static final String SENTINEL = "\u0000";

    public MusicBarView(Context ctx, Callback callback) {
        super(ctx);
        this.cb = callback;
        this.slop = ViewConfiguration.get(ctx).getScaledTouchSlop();

        setOrientation(VERTICAL);
        setBackgroundResource(R.drawable.bg_bar);
        setPadding(dp(12), dp(8), dp(12), dp(8));
        // The bar handles its own feedback: the system's touch sound would beep on
        // every prev/next tap, which is unwanted over the tablet speaker.
        setSoundEffectsEnabled(false);
        // Nothing in the bar ever takes focus. A focused window would make the system show
        // the navigation bar over the game, and the bar works by touch alone.
        setFocusable(false);
        setFocusableInTouchMode(false);
        setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);

        // ---- full layout -------------------------------------------------------
        fullBox = new LinearLayout(ctx);
        fullBox.setOrientation(VERTICAL);
        addView(fullBox, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        // ---- status strip: the clock on the left, the battery on the right -------------
        statusRow = new LinearLayout(ctx);
        statusRow.setOrientation(HORIZONTAL);
        statusRow.setGravity(Gravity.CENTER_VERTICAL);
        fullBox.addView(statusRow,
                new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        timeView = new TextView(ctx);
        timeView.setTextSize(9f);
        timeView.setTextColor(STATUS_COLOR);
        statusRow.addView(timeView,
                new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));

        // Pushes the battery to the right edge.
        statusRow.addView(new View(ctx), new LayoutParams(0, 1, 1f));

        batteryView = new BatteryView(ctx);
        batteryView.setColor(STATUS_COLOR);
        LayoutParams batteryParams =
                new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        batteryParams.rightMargin = dp(3);
        statusRow.addView(batteryView, batteryParams);

        batteryText = new TextView(ctx);
        batteryText.setTextSize(9f);
        batteryText.setTextColor(STATUS_COLOR);
        statusRow.addView(batteryText,
                new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));

        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        fullBox.addView(row, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));

        artView = new ImageView(ctx);
        artView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        LayoutParams artParams = new LayoutParams(dp(46), dp(46));
        artParams.rightMargin = dp(10);
        row.addView(artView, artParams);

        // The song text and the incoming song share one clipped stack, so a swipe
        // can slide the next track in while the current one slides out.
        textStack = new FrameLayout(ctx);
        textStack.setClipChildren(true);
        textStack.setClipToPadding(true);
        // The swipe zone is outlined so it is obvious where the gesture works.
        textStack.setBackgroundResource(R.drawable.bg_swipe_area);
        textStack.setPadding(dp(6), dp(3), dp(6), dp(3));
        row.addView(textStack, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));

        texts = new LinearLayout(ctx);
        texts.setOrientation(VERTICAL);
        textStack.addView(texts, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));

        titleView = new TextView(ctx);
        titleView.setTextColor(0xFFFFFFFF);
        titleView.setTextSize(16f);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        titleView.setSingleLine(true);
        titleView.setEllipsize(TextUtils.TruncateAt.MARQUEE);
        titleView.setMarqueeRepeatLimit(-1);
        titleView.setHorizontallyScrolling(true);
        titleView.setSelected(true);
        texts.addView(titleView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        artistView = new TextView(ctx);
        artistView.setTextColor(0xFFB0BEC5);
        artistView.setTextSize(12f);
        artistView.setSingleLine(true);
        artistView.setEllipsize(TextUtils.TruncateAt.END);
        LayoutParams artistParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        artistParams.topMargin = dp(2);
        texts.addView(artistView, artistParams);

        // The card that slides in while swiping: the song that is about to play.
        previewBox = new LinearLayout(ctx);
        previewBox.setOrientation(VERTICAL);
        previewBox.setVisibility(INVISIBLE);
        textStack.addView(previewBox, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));

        previewTitle = new TextView(ctx);
        previewTitle.setTextColor(0xFFFFFFFF);
        previewTitle.setTextSize(16f);
        previewTitle.setTypeface(Typeface.DEFAULT_BOLD);
        previewTitle.setSingleLine(true);
        previewTitle.setEllipsize(TextUtils.TruncateAt.END);
        previewBox.addView(previewTitle,
                new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        previewHint = new TextView(ctx);
        previewHint.setTextColor(0xFF90A4AE);
        previewHint.setTextSize(12f);
        previewHint.setSingleLine(true);
        LayoutParams hintParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        hintParams.topMargin = dp(2);
        previewBox.addView(previewHint, hintParams);

        ImageButton prevButton = makeButton(R.drawable.ic_prev, R.string.cd_prev);
        prevButton.setOnClickListener(v -> {
            if (cb != null) {
                cb.onPrev();
            }
        });
        row.addView(prevButton, buttonParams(false));

        playButton = makeButton(R.drawable.ic_play, R.string.cd_play);
        playButton.setOnClickListener(v -> {
            if (cb != null) {
                cb.onToggle();
            }
        });
        row.addView(playButton, buttonParams(true));

        ImageButton nextButton = makeButton(R.drawable.ic_next, R.string.cd_next);
        nextButton.setOnClickListener(v -> {
            if (cb != null) {
                cb.onNext();
            }
        });
        row.addView(nextButton, buttonParams(false));

        pinButton = makeButton(R.drawable.ic_pin, R.string.cd_pin);
        pinButton.setOnClickListener(v -> {
            if (cb != null) {
                cb.onPinToggle();
            }
        });
        row.addView(pinButton, buttonParams(false));

        libraryButton = makeButton(R.drawable.ic_folder, R.string.cd_library);
        libraryButton.setOnClickListener(v -> {
            if (cb != null) {
                cb.onLibraryToggle();
            }
        });
        // The old right-hand chevron turned into this library button; collapsing
        // moved to a long press so the feature is not lost.
        libraryButton.setOnLongClickListener(v -> {
            setCollapsed(true, true);
            return true;
        });
        row.addView(libraryButton, buttonParams(false));

        // The way out of the panel: a cross just to the right of the folder button,
        // shown only while the panel is open.
        closeButton = makeButton(R.drawable.ic_close, R.string.cd_close_library);
        closeButton.setVisibility(GONE);
        closeButton.setOnClickListener(v -> {
            if (cb != null) {
                cb.onLibraryToggle();
            }
        });
        row.addView(closeButton, buttonParams(false));

        LinearLayout progressRow = new LinearLayout(ctx);
        progressRow.setOrientation(HORIZONTAL);
        progressRow.setGravity(Gravity.CENTER_VERTICAL);
        LayoutParams progressRowParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        progressRowParams.topMargin = dp(6);
        fullBox.addView(progressRow, progressRowParams);

        curView = makeTime(Gravity.END);
        progressRow.addView(curView, new LayoutParams(dp(44), LayoutParams.WRAP_CONTENT));

        seekBar = new SeekBar(ctx);
        seekBar.setMax(1000);
        seekBar.setProgressDrawable(getResources().getDrawable(R.drawable.bar_progress, null));
        seekBar.setThumb(getResources().getDrawable(R.drawable.thumb_bar, null));
        seekBar.setThumbOffset(0);
        seekBar.setSplitTrack(false);
        seekBar.setOnSeekBarChangeListener(seekListener);
        progressRow.addView(seekBar, new LayoutParams(0, dp(22), 1f));

        totalView = makeTime(Gravity.START);
        progressRow.addView(totalView, new LayoutParams(dp(44), LayoutParams.WRAP_CONTENT));

        // ---- collapsed handle --------------------------------------------------
        handleBox = new FrameLayout(ctx);
        handleBox.setBackgroundResource(R.drawable.bg_handle);
        handleBox.setVisibility(GONE);
        handleBox.setContentDescription(ctx.getString(R.string.cd_expand));
        addView(handleBox, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        handleIcon = new ImageView(ctx);
        handleIcon.setScaleType(ImageView.ScaleType.CENTER_CROP);
        handleIcon.setImageResource(R.drawable.ic_note);
        handleBox.addView(handleIcon,
                new FrameLayout.LayoutParams(dp(34), dp(34), Gravity.CENTER));

        setOnTouchListener(touchListener);
        handleBox.setOnTouchListener(touchListener);
    }

    // ----- public API ------------------------------------------------------------

    public void update(MediaBridge bridge) {
        Context ctx = getContext();
        this.bridge = bridge;

        String status = bridge.statusText();
        if (!bridge.hasSession() && !TextUtils.isEmpty(status)) {
            setTextIfChanged(titleView, status);
            artistView.setVisibility(GONE);
            artView.setVisibility(GONE);
            handleIcon.setImageResource(R.drawable.ic_note);
            durationMs = 0L;
            seekBar.setEnabled(false);
            if (!userSeeking) {
                seekBar.setProgress(0);
            }
            setTextIfChanged(curView, "--:--");
            setTextIfChanged(totalView, "--:--");
            setPlayingIcon(false);
            return;
        }

        String title = bridge.title();
        if (TextUtils.isEmpty(title)) {
            title = ctx.getString(R.string.bar_unknown_title);
        }
        setTextIfChanged(titleView, title);
        if (!titleView.isSelected()) {
            titleView.setSelected(true);
        }

        if (Prefs.showArtist(ctx)) {
            artistView.setVisibility(VISIBLE);
            String artist = bridge.artist();
            setTextIfChanged(artistView, TextUtils.isEmpty(artist)
                    ? ctx.getString(R.string.bar_unknown_artist) : artist);
        } else {
            artistView.setVisibility(GONE);
        }

        boolean showArt = Prefs.showArt(ctx);
        Bitmap art = showArt ? bridge.art() : null;
        if (showArt && art != null) {
            artView.setVisibility(VISIBLE);
            if (art != lastArt) {
                artView.setImageBitmap(art);
                lastArt = art;
            }
            if (art != lastCircleSrc) {
                lastCircleSrc = art;
                lastCircled = circleCrop(art);
                handleIcon.setImageBitmap(lastCircled);
            }
        } else {
            artView.setVisibility(GONE);
            lastArt = null;
            lastCircleSrc = null;
            lastCircled = null;
            handleIcon.setImageResource(R.drawable.ic_note);
        }

        setPlayingIcon(bridge.isPlaying());

        durationMs = bridge.durationMs();
        boolean showTime = Prefs.showTime(ctx);
        curView.setVisibility(showTime ? VISIBLE : GONE);
        totalView.setVisibility(showTime ? VISIBLE : GONE);

        if (durationMs > 0L) {
            seekBar.setEnabled(!collapsed);
            if (!userSeeking) {
                long pos = bridge.positionMs();
                seekBar.setProgress((int) Math.min(1000L, pos * 1000L / durationMs));
                setTextIfChanged(curView, formatTime(pos));
            }
            setTextIfChanged(totalView, formatTime(durationMs));
        } else {
            seekBar.setEnabled(false);
            if (!userSeeking) {
                seekBar.setProgress(0);
                setTextIfChanged(curView, "--:--");
            }
            setTextIfChanged(totalView, "--:--");
        }
    }

    /**
     * Slim the inner layout down when the window is too short to fit the
     * full-size controls (i.e. a small "short-side width" setting). Without
     * this, thin bars would clip their content instead of looking right.
     */
    /** Pinned bars refuse to move, so a game cannot be disturbed by a stray drag. */
    public void setPinned(boolean value) {
        pinned = value;
        if (pinButton != null) {
            pinButton.setImageResource(value ? R.drawable.ic_pin_on : R.drawable.ic_pin);
            pinButton.setAlpha(value ? 1f : 0.55f);
        }
    }

    /**
     * While the library panel is open, a cross appears just right of the folder
     * button, so the way out sits at the panel's top right corner.
     */
    public void setLibraryOpen(boolean open) {
        if (closeButton != null) {
            closeButton.setVisibility(open ? VISIBLE : GONE);
        }
        if (libraryButton != null) {
            libraryButton.setAlpha(open ? 1f : 0.85f);
        }
    }

    public void setCompact(boolean value) {
        if (compact == value) {
            return;
        }
        compact = value;
        int vPad = dp(value ? 3 : 8);
        setPadding(dp(value ? 8 : 12), vPad, dp(value ? 8 : 12), vPad);
        artView.setVisibility(value ? GONE : VISIBLE);
        titleView.setTextSize(value ? 13f : 16f);
        artistView.setTextSize(value ? 9f : 12f);
        curView.setTextSize(value ? 8f : 11f);
        totalView.setTextSize(value ? 8f : 11f);
        requestLayout();
    }

    public boolean isCollapsed() {
        return collapsed;
    }

    public void setCollapsed(boolean value) {
        setCollapsed(value, false);
    }

    private void setCollapsed(boolean value, boolean notify) {
        collapsed = value;
        fullBox.setVisibility(value ? GONE : VISIBLE);
        handleBox.setVisibility(value ? VISIBLE : GONE);
        if (notify && cb != null) {
            cb.onCollapseToggle(value);
        }
    }

    // ----- internals -------------------------------------------------------------

    private void setPlayingIcon(boolean playing) {
        if (playing == lastPlaying) {
            return;
        }
        lastPlaying = playing;
        playButton.setImageResource(playing ? R.drawable.ic_pause : R.drawable.ic_play);
        playButton.setContentDescription(getContext().getString(
                playing ? R.string.cd_pause : R.string.cd_play));
    }

    private final SeekBar.OnSeekBarChangeListener seekListener =
            new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                    if (fromUser && durationMs > 0L) {
                        curView.setText(formatTime(durationMs * progress / 1000L));
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar bar) {
                    userSeeking = true;
                    if (cb != null) {
                        cb.onUserActivity();
                    }
                }

                @Override
                public void onStopTrackingTouch(SeekBar bar) {
                    userSeeking = false;
                    if (durationMs > 0L && cb != null) {
                        cb.onSeekTo(durationMs * bar.getProgress() / 1000L);
                    }
                    if (cb != null) {
                        cb.onUserActivity();
                    }
                }
            };

    /**
     * Holding still for a moment turns the gesture into "move the bar"; a quick
     * horizontal swipe stays a track change, so the two never fight each other.
     */
    private final Runnable longPressRunnable = new Runnable() {
        @Override
        public void run() {
            longPressed = true;
        }
    };

    private final OnTouchListener touchListener = new OnTouchListener() {
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downRawX = event.getRawX();
                    downRawY = event.getRawY();
                    dragging = false;
                    longPressed = false;
                    swipeFired = false;
                    swipeMode = false;
                    swipeDx = 0f;
                    // Remember whether the bar was dimmed when the finger landed: waking it
                    // up must not also pause the music.
                    idleAtDown = isDimmed();
                    // A swipe only counts when the finger lands inside the outlined zone;
                    // sliding along the rest of the bar must not switch songs.
                    swipeAllowed = v == handleBox || touchInFrame(event.getX());
                    ui.postDelayed(longPressRunnable, LONG_PRESS_MS);
                    if (cb != null) {
                        cb.onUserActivity();
                    }
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    float dx = event.getRawX() - downRawX;
                    float dy = event.getRawY() - downRawY;
                    if (longPressed) {
                        if (pinned) {
                            // Pinned to the screen: dragging is disabled.
                            return true;
                        }
                        // Held long enough: this gesture moves the window.
                        if (!dragging && Math.hypot(dx, dy) > slop) {
                            dragging = true;
                            if (cb != null) {
                                cb.onDragStart();
                            }
                        }
                        if (dragging && cb != null) {
                            cb.onDrag((int) dx, (int) dy);
                        }
                        return true;
                    }
                    // A swipe starts only after a deliberate sideways move inside the framed
                    // zone, so a slightly sloppy tap is still read as a tap and a slide along
                    // the rest of the bar switches nothing.
                    if (swipeAllowed
                            && Math.abs(dx) > Math.max(slop * 3f, dp(SWIPE_START_DP))
                            && Math.abs(dx) > Math.abs(dy) * 1.5f) {
                        // Clearly sideways: never let this become a window drag.
                        ui.removeCallbacks(longPressRunnable);
                        if (collapsed) {
                            // The little handle has no room for a preview, so it keeps
                            // the old behaviour: switch as soon as it is far enough.
                            if (!swipeFired && Math.abs(dx) >= dp(SWIPE_MIN_DP)) {
                                swipeFired = true;
                                if (cb != null) {
                                    if (dx < 0f) {
                                        cb.onNext();
                                    } else {
                                        cb.onPrev();
                                    }
                                }
                            }
                        } else {
                            // Slide the incoming song in, and only switch on release.
                            if (!swipeMode) {
                                swipeMode = true;
                                beginSwipe(dx);
                            }
                            swipeDx = dx;
                            updateSwipe(dx);
                        }
                    }
                    return true;
                }
                case MotionEvent.ACTION_UP:
                    ui.removeCallbacks(longPressRunnable);
                    if (swipeMode) {
                        endSwipe(swipeDx);
                    } else if (dragging) {
                        if (cb != null) {
                            cb.onDragEnd();
                        }
                    } else if (!swipeFired) {
                        if (idleAtDown) {
                            // The bar was dimmed: this tap only brought it back to full
                            // strength, so it must not toggle playback as well.
                            idleAtDown = false;
                        } else {
                            handleTap(v);
                        }
                    }
                    dragging = false;
                    longPressed = false;
                    swipeFired = false;
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    ui.removeCallbacks(longPressRunnable);
                    if (swipeMode) {
                        cancelSwipe();
                    }
                    if (dragging && cb != null) {
                        cb.onDragEnd();
                    }
                    dragging = false;
                    longPressed = false;
                    swipeFired = false;
                    swipeMode = false;
                    return true;
                default:
                    return false;
            }
        }
    };

    /**
     * Whether the bar is currently showing its dimmed, resting state. The two alpha levels
     * come from the settings, so this stays right when they are changed; if they are set
     * close together there is nothing to wake and this is always false.
     */
    private boolean isDimmed() {
        Context ctx = getContext();
        float idle = Prefs.alphaIdle(ctx) / 100f;
        float active = Prefs.alphaActive(ctx) / 100f;
        if (active - idle < 0.1f) {
            return false;
        }
        return getAlpha() < (idle + active) / 2f;
    }

    /**
     * Whether a touch at this position inside the bar landed on the outlined swipe zone.
     * The zone is the only place that switches songs, which is what the border is there to
     * say; the rest of the bar answers taps and the long press that moves the window.
     */
    private boolean touchInFrame(float x) {
        if (textStack == null || textStack.getWidth() == 0) {
            return false;
        }
        int[] barPos = new int[2];
        int[] framePos = new int[2];
        getLocationOnScreen(barPos);
        textStack.getLocationOnScreen(framePos);
        float left = framePos[0] - barPos[0];
        return x >= left && x <= left + textStack.getWidth();
    }

    private void handleTap(View v) {
        if (v == handleBox) {
            setCollapsed(false, true);
        } else if (cb != null) {
            cb.onToggle();
        }
    }

    // ----- swipe preview ---------------------------------------------------------
    //
    // Swiping is what changes the song, so the bar slides the incoming track in as
    // the finger moves and only commits on release. That makes the gesture obvious
    // (especially with the bar unpinned, where a drag is also possible) and shows
    // what is about to play before anything actually changes.

    private int stackWidth() {
        return Math.max(dp(120), textStack.getWidth());
    }

    /**
     * How far a swipe must travel before letting go really switches the song: a
     * share of the song text width, adjustable on the settings page.
     */
    private int commitDistance() {
        int pct = Prefs.swipePct(getContext());
        return Math.max(dp(SWIPE_MIN_DP), stackWidth() * pct / 100);
    }

    private void beginSwipe(float dx) {
        boolean next = dx < 0f;
        Context ctx = getContext();
        String incoming = bridge == null ? "" : bridge.queueItemTitle(next ? 1 : -1);
        String label = ctx.getString(next ? R.string.bar_swipe_next : R.string.bar_swipe_prev);

        textStack.setBackgroundResource(R.drawable.bg_swipe_area_active);
        previewTitle.setTextSize(compact ? 13f : 16f);
        previewHint.setTextSize(compact ? 9f : 12f);
        previewTitle.setText(TextUtils.isEmpty(incoming) ? label : incoming);
        previewHint.setText(ctx.getString(R.string.bar_swipe_more));
        previewHint.setTextColor(0xFF90A4AE);

        int width = stackWidth();
        previewBox.setVisibility(VISIBLE);
        previewBox.setTranslationX(next ? width : -width);
        previewBox.setAlpha(0.3f);
        texts.setTranslationX(0f);
        texts.setAlpha(1f);
    }

    private void updateSwipe(float dx) {
        int width = stackWidth();
        float clamped = Math.max(-width, Math.min(width, dx));
        float progress = Math.min(1f, Math.abs(clamped) / (float) width);
        boolean next = dx < 0f;

        texts.setTranslationX(clamped);
        previewBox.setTranslationX((next ? width : -width) + clamped);
        texts.setAlpha(1f - 0.5f * progress);
        previewBox.setAlpha(0.3f + 0.7f * progress);

        boolean armed = Math.abs(clamped) >= commitDistance();
        previewHint.setText(getContext().getString(armed
                ? R.string.bar_swipe_release : R.string.bar_swipe_more));
        previewHint.setTextColor(armed ? 0xFF8BC34A : 0xFF90A4AE);
    }

    private void endSwipe(float dx) {
        swipeMode = false;
        textStack.setBackgroundResource(R.drawable.bg_swipe_area);
        int width = stackWidth();
        boolean next = dx < 0f;

        if (Math.abs(dx) < commitDistance()) {
            // Not far enough: everything slides back and the song stays as it was.
            texts.animate().translationX(0f).alpha(1f).setDuration(140L).start();
            previewBox.animate().translationX(next ? width : -width).alpha(0.3f)
                    .setDuration(140L)
                    .withEndAction(() -> {
                        if (!swipeMode) {
                            previewBox.setVisibility(INVISIBLE);
                        }
                    })
                    .start();
            return;
        }

        final boolean goNext = next;
        texts.animate().translationX(goNext ? -width : width).alpha(0f)
                .setDuration(130L).start();
        previewBox.animate().translationX(0f).alpha(1f).setDuration(130L)
                .withEndAction(() -> {
                    if (cb != null) {
                        if (goNext) {
                            cb.onNext();
                        } else {
                            cb.onPrev();
                        }
                    }
                    // The preview card now stands where the title was, so hand its
                    // text over and put everything back; the next update() will draw
                    // the real song in exactly the same place.
                    setTextIfChanged(titleView, previewTitle.getText());
                    if (swipeMode) {
                        return;
                    }
                    texts.setTranslationX(0f);
                    texts.setAlpha(1f);
                    previewBox.setTranslationX(0f);
                    previewBox.setVisibility(INVISIBLE);
                })
                .start();
    }

    private void cancelSwipe() {
        swipeMode = false;
        textStack.setBackgroundResource(R.drawable.bg_swipe_area);
        texts.animate().translationX(0f).alpha(1f).setDuration(120L).start();
        previewBox.setTranslationX(0f);
        previewBox.setVisibility(INVISIBLE);
    }

    private ImageButton makeButton(int iconRes, int contentDescRes) {
        ImageButton button = new ImageButton(getContext());
        button.setImageResource(iconRes);
        button.setBackgroundResource(R.drawable.bg_btn);
        button.setScaleType(ImageView.ScaleType.CENTER);
        button.setPadding(dp(6), dp(6), dp(6), dp(6));
        button.setContentDescription(getContext().getString(contentDescRes));
        button.setSoundEffectsEnabled(false);
        // Silent, but a light tick confirms the press so the button never feels dead.
        button.setOnTouchListener((view, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            }
            return false;
        });
        return button;
    }

    /**
     * Buttons take the whole height of the bar and a finger-sized width, so a tap
     * lands even on a thin bar or when the tablet is held at arm's length.
     */
    private LayoutParams buttonParams(boolean primary) {
        LayoutParams params = new LayoutParams(
                dp(primary ? 54 : 48), LayoutParams.MATCH_PARENT);
        params.leftMargin = dp(4);
        return params;
    }

    private TextView makeTime(int gravity) {
        TextView view = new TextView(getContext());
        view.setTextColor(0xFFB0BEC5);
        view.setTextSize(11f);
        view.setSingleLine(true);
        view.setGravity(gravity | Gravity.CENTER_VERTICAL);
        view.setText("--:--");
        return view;
    }

    private static void setTextIfChanged(TextView view, CharSequence text) {
        CharSequence current = view.getText();
        if (current == null || !current.equals(text)) {
            view.setText(text);
        }
    }

    private static String formatTime(long ms) {
        long value = ms < 0L ? 0L : ms;
        long totalSeconds = value / 1000L;
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;
        if (hours > 0L) {
            return String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format(Locale.US, "%d:%02d", minutes, seconds);
    }

    /** Centre-crops a bitmap into a circle for the collapsed handle. */
    private static Bitmap circleCrop(Bitmap src) {
        try {
            int size = Math.min(src.getWidth(), src.getHeight());
            if (size <= 0) {
                return src;
            }
            Bitmap out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(out);
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setShader(new BitmapShader(src, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
            float dx = (src.getWidth() - size) / 2f;
            float dy = (src.getHeight() - size) / 2f;
            canvas.translate(-dx, -dy);
            float radius = size / 2f;
            canvas.drawCircle(dx + radius, dy + radius, radius, paint);
            return out;
        } catch (Throwable t) {
            return src;
        }
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
