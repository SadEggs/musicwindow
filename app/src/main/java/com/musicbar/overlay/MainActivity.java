package com.musicbar.overlay;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.service.media.MediaBrowserService;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/**
 * Permission wizard + settings page. Every change is applied to the running
 * overlay immediately.
 */
public class MainActivity extends Activity {

    private static final int REQUEST_NOTIFICATIONS = 100;
    private static final int REQUEST_LIBRARY = 101;

    private static boolean notificationAskDone;

    private SharedPreferences sp;
    private LinearLayout root;
    private TextView statusView;
    private TextView overlayRow;
    private TextView nlsRow;
    private TextView batteryRow;
    private TextView notificationRow;
    private TextView libraryRow;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private Runnable pendingApply;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sp = Prefs.sp(this);

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int padding = Prefs.dp(this, 16);
        root.setPadding(padding, padding, padding, padding * 2);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(scroll);

        statusView = new TextView(this);
        statusView.setTextSize(17f);
        statusView.setTextColor(0xFFECEFF1);
        root.addView(statusView);

        // ---- permissions ------------------------------------------------------
        addSection(R.string.sec_perm);
        overlayRow = addPermRow(R.string.perm_overlay, v -> requestOverlayPermission());
        nlsRow = addPermRow(R.string.perm_nls, v -> requestNotificationAccess());
        batteryRow = addPermRow(R.string.perm_batt, v -> requestIgnoreBattery());
        notificationRow = addPermRow(R.string.perm_notif, v -> requestNotificationPermission());
        libraryRow = addPermRow(R.string.perm_library, v -> requestLibraryPermission());

        // ---- start / stop -----------------------------------------------------
        addSection(R.string.sec_run);
        addButton(R.string.btn_start, v -> startOverlay());
        addButton(R.string.btn_stop, v -> stopOverlay());

        // ---- size -------------------------------------------------------------
        addSection(R.string.sec_size);
        addFloatInput(R.string.set_thickness, Prefs.K_THICKNESS_CM, 3.0f);
        addSlider(R.string.set_length, Prefs.K_LENGTH_PCT, 20, 100, 60, "%");
        addEdgePicker();
        addIntInput(R.string.set_margin, Prefs.K_EDGE_MARGIN_DP, 8);
        addFloatInput(R.string.set_handle_cm, Prefs.K_HANDLE_CM, 1.6f);
        addButton(R.string.set_reset_pos, v -> {
            Prefs.clearCustomPosition(this);
            applyLive();
        });
        addNote(R.string.hint_drag);
        addNote(R.string.hint_library);
        addNote(R.string.hint_poweramp);

        // ---- appearance -------------------------------------------------------
        addSection(R.string.sec_look);
        addSlider(R.string.set_alpha_idle, Prefs.K_ALPHA_IDLE, 5, 100, 35, "%");
        addSlider(R.string.set_alpha_active, Prefs.K_ALPHA_ACTIVE, 20, 100, 90, "%");
        addSlider(R.string.set_fade, Prefs.K_FADE_DELAY_S, 0, 20, 4, "s");
        addCheckBox(R.string.set_pinned, Prefs.K_PINNED, false);
        addCheckBox(R.string.set_only_playing, Prefs.K_ONLY_PLAYING, true);
        addCheckBox(R.string.set_show_artist, Prefs.K_SHOW_ARTIST, true);
        addCheckBox(R.string.set_show_time, Prefs.K_SHOW_TIME, true);
        addCheckBox(R.string.set_show_art, Prefs.K_SHOW_ART, false);
        addCheckBox(R.string.set_autostart, Prefs.K_AUTOSTART, true);
        addNote(R.string.set_collapse_hint);

        // ---- advanced ---------------------------------------------------------
        addSection(R.string.sec_adv);
        addTextInput(R.string.set_pref_pkg, Prefs.K_PREF_PKG, Prefs.DEFAULT_PKG);
        addButton(R.string.btn_open_settings, v -> openAppSettings());

        addSection(R.string.sec_apply);
        addButton(R.string.btn_apply, v -> applyLive());
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
        if (!notificationAskDone
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && !hasNotificationPermission()) {
            notificationAskDone = true;
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    REQUEST_NOTIFICATIONS);
        }
    }

    // ---- state -----------------------------------------------------------------

    private void refreshStatus() {
        boolean overlay = Settings.canDrawOverlays(this);
        boolean nls = hasNotificationAccess();
        boolean battery = isIgnoringBatteryOptimizations();
        boolean notification = hasNotificationPermission();

        setPermRow(overlayRow, R.string.perm_overlay, overlay);
        setPermRow(nlsRow, R.string.perm_nls, nls);
        setPermRow(batteryRow, R.string.perm_batt, battery);
        setPermRow(notificationRow, R.string.perm_notif, notification);
        setPermRow(libraryRow, R.string.perm_library, hasLibraryPermission());

        StringBuilder sb = new StringBuilder();
        sb.append(getString(OverlayService.running
                ? R.string.status_running : R.string.status_stopped));
        if (OverlayService.running) {
            String pkg = MediaBridge.get().packageName();
            sb.append('\n');
            sb.append(pkg == null || pkg.isEmpty()
                    ? getString(R.string.status_player_none)
                    : getString(R.string.status_player, pkg));
            // Diagnostics for "tap a song in the folder panel": which point-song
            // requests the player says it answers, and whether the device offers a
            // media browser service at all.
            sb.append('\n').append(playSupportText());
            sb.append('\n').append(browserServicesText());
        }
        statusView.setText(sb.toString());
    }

    private String yesNo(boolean value) {
        return getString(value ? R.string.diag_yes : R.string.diag_no);
    }

    private String playSupportText() {
        int support = MediaBridge.get().playFromSupport();
        return getString(R.string.diag_playfrom)
                + ": " + getString(R.string.diag_search)
                + "=" + yesNo((support & MediaBridge.CAN_SEARCH) != 0)
                + " " + getString(R.string.diag_uri)
                + "=" + yesNo((support & MediaBridge.CAN_URI) != 0)
                + " " + getString(R.string.diag_mediaid)
                + "=" + yesNo((support & MediaBridge.CAN_MEDIA_ID) != 0);
    }

    private String browserServicesText() {
        Intent intent = new Intent(MediaBrowserService.SERVICE_INTERFACE);
        List<ResolveInfo> found = getPackageManager().queryIntentServices(intent, 0);
        StringBuilder sb = new StringBuilder(getString(R.string.diag_browser));
        if (found == null || found.isEmpty()) {
            sb.append(": ").append(getString(R.string.diag_none));
            return sb.toString();
        }
        for (ResolveInfo info : found) {
            if (info.serviceInfo == null) {
                continue;
            }
            sb.append("\n  ").append(info.serviceInfo.packageName)
                    .append('/').append(info.serviceInfo.name);
        }
        return sb.toString();
    }

    private void setPermRow(TextView row, int labelRes, boolean granted) {
        row.setText(getString(labelRes) + "\n"
                + getString(granted ? R.string.perm_ok : R.string.perm_no));
        row.setTextColor(granted ? 0xFF66BB6A : 0xFFFF7043);
    }

    private boolean hasNotificationAccess() {
        try {
            String flat = Settings.Secure.getString(
                    getContentResolver(), "enabled_notification_listeners");
            return flat != null && flat.contains(getPackageName());
        } catch (Throwable t) {
            return false;
        }
    }

    private boolean isIgnoringBatteryOptimizations() {
        try {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            return pm != null && pm.isIgnoringBatteryOptimizations(getPackageName());
        } catch (Throwable t) {
            return false;
        }
    }

    private boolean hasNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return true;
        }
        return checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }

    // ---- actions ---------------------------------------------------------------

    private void startOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            toast(R.string.toast_need_overlay);
            requestOverlayPermission();
            return;
        }
        if (!hasNotificationAccess()) {
            toast(R.string.toast_need_nls);
            requestNotificationAccess();
            return;
        }
        try {
            startForegroundService(new Intent(this, OverlayService.class)
                    .setAction(OverlayService.ACTION_START));
            statusView.postDelayed(this::refreshStatus, 600L);
        } catch (Throwable t) {
            toast(R.string.toast_need_overlay);
        }
    }

    private void stopOverlay() {
        if (!OverlayService.running) {
            refreshStatus();
            return;
        }
        try {
            startService(new Intent(this, OverlayService.class)
                    .setAction(OverlayService.ACTION_STOP));
        } catch (Throwable t) {
            try {
                stopService(new Intent(this, OverlayService.class));
            } catch (Throwable ignored) {
                // ignore
            }
        }
        statusView.postDelayed(this::refreshStatus, 600L);
    }

    private void applyLive() {
        if (!OverlayService.running) {
            return;
        }
        try {
            startService(new Intent(this, OverlayService.class)
                    .setAction(OverlayService.ACTION_RELOAD));
        } catch (Throwable ignored) {
            // ignore
        }
    }

    private void requestOverlayPermission() {
        try {
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())));
        } catch (Throwable t) {
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION));
            } catch (Throwable ignored) {
                // ignore
            }
        }
    }

    private void requestNotificationAccess() {
        try {
            startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
        } catch (Throwable ignored) {
            // ignore
        }
    }

    private void requestIgnoreBattery() {
        try {
            startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
        } catch (Throwable ignored) {
            // ignore
        }
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    REQUEST_NOTIFICATIONS);
        }
    }

    private boolean hasLibraryPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return checkSelfPermission(Manifest.permission.READ_MEDIA_AUDIO)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestLibraryPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(new String[]{Manifest.permission.READ_MEDIA_AUDIO}, REQUEST_LIBRARY);
        } else {
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                    REQUEST_LIBRARY);
        }
    }

    private void openAppSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName())));
        } catch (Throwable ignored) {
            // ignore
        }
    }

    private void toast(int res) {
        Toast.makeText(this, res, Toast.LENGTH_SHORT).show();
    }

    // ---- view builders ---------------------------------------------------------

    private void addSection(int titleRes) {
        TextView view = new TextView(this);
        view.setText(titleRes);
        view.setTextSize(16f);
        view.setTextColor(0xFF4FC3F7);
        view.setPadding(0, Prefs.dp(this, 22), 0, Prefs.dp(this, 6));
        root.addView(view);
    }

    private void addNote(int textRes) {
        TextView view = new TextView(this);
        view.setText(textRes);
        view.setTextSize(12f);
        view.setTextColor(0xFF90A4AE);
        view.setPadding(0, Prefs.dp(this, 6), 0, 0);
        root.addView(view);
    }

    private TextView addPermRow(int labelRes, View.OnClickListener listener) {
        TextView row = new TextView(this);
        row.setTextSize(14f);
        row.setPadding(0, Prefs.dp(this, 10), 0, Prefs.dp(this, 10));
        row.setOnClickListener(listener);
        root.addView(row);
        return row;
    }

    private void addButton(int labelRes, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(labelRes);
        button.setOnClickListener(listener);
        root.addView(button, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void addCheckBox(int labelRes, String key, boolean def) {
        CheckBox box = new CheckBox(this);
        box.setText(labelRes);
        box.setTextSize(14f);
        box.setChecked(sp.getBoolean(key, def));
        box.setOnCheckedChangeListener((view, checked) -> {
            sp.edit().putBoolean(key, checked).apply();
            applyLive();
        });
        root.addView(box);
    }

    private void addSlider(int labelRes, String key, int min, int max, int def, String suffix) {
        TextView label = new TextView(this);
        label.setTextSize(14f);
        int value = sp.getInt(key, def);
        label.setText(getString(labelRes) + ": " + value + suffix);
        root.addView(label);

        SeekBar bar = new SeekBar(this);
        bar.setMax(max - min);
        bar.setProgress(Math.max(0, Math.min(max - min, value - min)));
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int current = min + progress;
                label.setText(getString(labelRes) + ": " + current + suffix);
                if (fromUser) {
                    sp.edit().putInt(key, current).apply();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                applyLive();
            }
        });
        root.addView(bar);
    }

    /**
     * Tolerant number parser. Accepts full-width digits and treats a Chinese or
     * ASCII comma as the decimal point, so a Chinese IME cannot silently turn
     * "2.5" into something that fails to parse.
     */
    private static Float parseNumber(String raw) {
        if (raw == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        boolean dot = false;
        for (int i = 0; i < raw.length(); i++) {
            char ch = raw.charAt(i);
            if (ch >= '0' && ch <= '9') {
                sb.append(ch);
            } else if (ch >= '\uFF10' && ch <= '\uFF19') {
                sb.append((char) ('0' + (ch - '\uFF10')));
            } else if (ch == '.' || ch == ',' || ch == '\uFF0E' || ch == '\uFF0C') {
                if (!dot) {
                    sb.append('.');
                    dot = true;
                }
            }
        }
        if (sb.length() == 0) {
            return null;
        }
        try {
            return Float.valueOf(sb.toString());
        } catch (Throwable t) {
            return null;
        }
    }

    private static String formatFloat(float value) {
        if (value == Math.round(value)) {
            return String.valueOf((int) Math.round(value));
        }
        return String.valueOf(value);
    }

    /** Rebuild the overlay a moment after typing stops, so it visibly reacts. */
    private void applyLiveDebounced() {
        if (pendingApply != null) {
            ui.removeCallbacks(pendingApply);
        }
        pendingApply = () -> {
            pendingApply = null;
            applyLive();
        };
        ui.postDelayed(pendingApply, 350L);
    }

    /**
     * Numeric input field. The value is saved on EVERY keystroke, not on focus
     * loss: in touch mode tapping a button or blank space does not move focus
     * away from an EditText, so a focus-based save never fired at all and the
     * setting appeared to do nothing.
     */
    private void addFloatInput(int labelRes, String key, float def) {
        final boolean thickness = Prefs.K_THICKNESS_CM.equals(key);
        final float min = thickness ? 0.6f : 0.8f;
        final float max = thickness ? 12f : 8f;
        final float floorDp = thickness ? Prefs.MIN_BAR_DP : Prefs.MIN_HANDLE_DP;

        TextView label = new TextView(this);
        label.setTextSize(14f);
        label.setText(labelRes);
        label.setPadding(0, Prefs.dp(this, 10), 0, 0);
        root.addView(label);

        final TextView info = new TextView(this);
        info.setTextSize(12f);
        info.setTextColor(0xFF90A4AE);
        root.addView(info);

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setSingleLine(true);
        input.setText(formatFloat(sp.getFloat(key, def)));

        final boolean[] suppress = new boolean[1];

        final Runnable refreshInfo = () -> {
            DisplayMetrics m = Prefs.metrics(this);
            int px = Prefs.cmToPxY(this, sp.getFloat(key, def), m);
            info.setText(getString(R.string.hint_cm_px, px, m.densityDpi,
                    Prefs.dp(this, floorDp)));
        };
        refreshInfo.run();

        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (suppress[0]) {
                    return;
                }
                Float parsed = parseNumber(s.toString());
                if (parsed == null) {
                    info.setText(R.string.hint_bad_number);
                    return;
                }
                float value = Math.max(min, Math.min(max, parsed));
                if (value != sp.getFloat(key, def)) {
                    sp.edit().putFloat(key, value).apply();
                }
                refreshInfo.run();
                applyLiveDebounced();
            }
        });

        input.setOnFocusChangeListener((view, hasFocus) -> {
            if (hasFocus) {
                return;
            }
            suppress[0] = true;
            input.setText(formatFloat(sp.getFloat(key, def)));
            input.setSelection(input.getText().length());
            suppress[0] = false;
            refreshInfo.run();
            applyLive();
        });
        root.addView(input);
    }

    private void addIntInput(int labelRes, String key, int def) {
        TextView label = new TextView(this);
        label.setTextSize(14f);
        label.setText(labelRes);
        label.setPadding(0, Prefs.dp(this, 10), 0, 0);
        root.addView(label);

        final TextView info = new TextView(this);
        info.setTextSize(12f);
        info.setTextColor(0xFF90A4AE);
        root.addView(info);

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setSingleLine(true);
        input.setText(String.valueOf(sp.getInt(key, def)));

        final boolean[] suppress = new boolean[1];

        final Runnable refreshInfo = () -> {
            DisplayMetrics m = Prefs.metrics(this);
            info.setText(getString(R.string.hint_dp_px, Prefs.dp(this, sp.getInt(key, def)),
                    m.densityDpi));
        };
        refreshInfo.run();

        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (suppress[0]) {
                    return;
                }
                Float parsed = parseNumber(s.toString());
                if (parsed == null) {
                    info.setText(R.string.hint_bad_number);
                    return;
                }
                int value = Math.max(0, Math.min(400, Math.round(parsed)));
                if (value != sp.getInt(key, def)) {
                    sp.edit().putInt(key, value).apply();
                }
                refreshInfo.run();
                applyLiveDebounced();
            }
        });

        input.setOnFocusChangeListener((view, hasFocus) -> {
            if (hasFocus) {
                return;
            }
            suppress[0] = true;
            input.setText(String.valueOf(sp.getInt(key, def)));
            input.setSelection(input.getText().length());
            suppress[0] = false;
            refreshInfo.run();
            applyLive();
        });
        root.addView(input);
    }

    private void addTextInput(int labelRes, String key, String def) {
        TextView label = new TextView(this);
        label.setTextSize(14f);
        label.setText(labelRes);
        label.setPadding(0, Prefs.dp(this, 10), 0, 0);
        root.addView(label);

        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setSingleLine(true);
        String value = sp.getString(key, def);
        input.setText(value == null ? def : value);
        input.setHint(def);

        final boolean[] suppress = new boolean[1];
        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (suppress[0]) {
                    return;
                }
                String text = s.toString().trim();
                if (text.isEmpty()) {
                    return;
                }
                if (!text.equals(sp.getString(key, def))) {
                    sp.edit().putString(key, text).apply();
                    applyLiveDebounced();
                }
            }
        });

        input.setOnFocusChangeListener((view, hasFocus) -> {
            if (hasFocus) {
                return;
            }
            String text = input.getText().toString().trim();
            if (text.isEmpty()) {
                text = def;
            }
            suppress[0] = true;
            input.setText(text);
            input.setSelection(input.getText().length());
            suppress[0] = false;
            sp.edit().putString(key, text).apply();
            applyLive();
        });
        root.addView(input);
    }

    private void addEdgePicker() {
        TextView label = new TextView(this);
        label.setTextSize(14f);
        label.setText(R.string.set_edge);
        label.setPadding(0, Prefs.dp(this, 10), 0, 0);
        root.addView(label);

        RadioGroup group = new RadioGroup(this);
        group.setOrientation(RadioGroup.HORIZONTAL);

        RadioButton top = new RadioButton(this);
        top.setId(1);
        top.setText(R.string.edge_top);

        RadioButton bottom = new RadioButton(this);
        bottom.setId(2);
        bottom.setText(R.string.edge_bottom);

        group.addView(top);
        group.addView(bottom);
        group.check(Prefs.EDGE_TOP.equals(Prefs.edge(this)) ? 1 : 2);
        group.setOnCheckedChangeListener((radioGroup, checkedId) -> {
            sp.edit().putString(Prefs.K_EDGE,
                    checkedId == 1 ? Prefs.EDGE_TOP : Prefs.EDGE_BOTTOM).apply();
            applyLive();
        });
        root.addView(group);
    }
}
