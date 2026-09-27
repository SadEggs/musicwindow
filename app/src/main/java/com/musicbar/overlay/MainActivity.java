package com.musicbar.overlay;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.InputType;
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

/**
 * Permission wizard + settings page. Every change is applied to the running
 * overlay immediately.
 */
public class MainActivity extends Activity {

    private static final int REQUEST_NOTIFICATIONS = 100;

    private static boolean notificationAskDone;

    private SharedPreferences sp;
    private LinearLayout root;
    private TextView statusView;
    private TextView overlayRow;
    private TextView nlsRow;
    private TextView batteryRow;
    private TextView notificationRow;

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
        addNote(R.string.hint_poweramp);

        // ---- appearance -------------------------------------------------------
        addSection(R.string.sec_look);
        addSlider(R.string.set_alpha_idle, Prefs.K_ALPHA_IDLE, 5, 100, 35, "%");
        addSlider(R.string.set_alpha_active, Prefs.K_ALPHA_ACTIVE, 20, 100, 90, "%");
        addSlider(R.string.set_fade, Prefs.K_FADE_DELAY_S, 0, 20, 4, "s");
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

        StringBuilder sb = new StringBuilder();
        sb.append(getString(OverlayService.running
                ? R.string.status_running : R.string.status_stopped));
        if (OverlayService.running) {
            String pkg = MediaBridge.get().packageName();
            sb.append('\n');
            sb.append(pkg == null || pkg.isEmpty()
                    ? getString(R.string.status_player_none)
                    : getString(R.string.status_player, pkg));
        }
        statusView.setText(sb.toString());
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

    private void addFloatInput(int labelRes, String key, float def) {
        TextView label = new TextView(this);
        label.setTextSize(14f);
        label.setText(labelRes);
        label.setPadding(0, Prefs.dp(this, 10), 0, 0);
        root.addView(label);

        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setText(String.valueOf(sp.getFloat(key, def)));
        input.setOnFocusChangeListener((view, hasFocus) -> {
            if (!hasFocus) {
                float value;
                try {
                    value = Float.parseFloat(input.getText().toString().trim());
                    if (value <= 0f || value > 20f) {
                        value = def;
                    }
                } catch (Throwable t) {
                    value = def;
                }
                input.setText(String.valueOf(value));
                sp.edit().putFloat(key, value).apply();
                applyLive();
            }
        });
        root.addView(input);
    }

    private void addIntInput(int labelRes, String key, int def) {
        TextView label = new TextView(this);
        label.setTextSize(14f);
        label.setText(labelRes);
        label.setPadding(0, Prefs.dp(this, 10), 0, 0);
        root.addView(label);

        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setText(String.valueOf(sp.getInt(key, def)));
        input.setOnFocusChangeListener((view, hasFocus) -> {
            if (!hasFocus) {
                int value;
                try {
                    value = Integer.parseInt(input.getText().toString().trim());
                    if (value < 0 || value > 400) {
                        value = def;
                    }
                } catch (Throwable t) {
                    value = def;
                }
                input.setText(String.valueOf(value));
                sp.edit().putInt(key, value).apply();
                applyLive();
            }
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
        input.setOnFocusChangeListener((view, hasFocus) -> {
            if (!hasFocus) {
                String text = input.getText().toString().trim();
                if (text.isEmpty()) {
                    text = def;
                }
                input.setText(text);
                sp.edit().putString(key, text).apply();
                applyLive();
            }
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
