package com.musicbar.overlay;

import android.content.Context;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Handler;
import android.os.Looper;

/**
 * The short tone played when the track changes.
 *
 * <p>Sound effects are switched off on the bar and its buttons, so this is the only
 * thing that beeps on a track change, and the setting decides when: never, only
 * over Bluetooth, or always. The default is Bluetooth only, which keeps the tablet
 * speaker quiet while still giving feedback through a headset.
 *
 * <p>A headset that beeps on its own when the track changes is beyond reach of any
 * app; that sound comes from the headset's firmware, not from here.
 */
public final class Beep {

    public static final int MODE_OFF = 0;
    public static final int MODE_BLUETOOTH = 1;
    public static final int MODE_ALWAYS = 2;

    private static final int TONE_MS = 120;
    private static final Handler HANDLER = new Handler(Looper.getMainLooper());

    private Beep() {
    }

    /** Play the track-change tone when the current setting asks for one. */
    public static void onTrackChange(Context ctx) {
        int mode = Prefs.beepMode(ctx);
        if (mode == MODE_OFF) {
            return;
        }
        if (mode == MODE_BLUETOOTH && !bluetoothConnected(ctx)) {
            return;
        }
        play();
    }

    /** True while audio is routed to a Bluetooth device. */
    public static boolean bluetoothConnected(Context ctx) {
        try {
            AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
            if (am == null) {
                return false;
            }
            AudioDeviceInfo[] devices = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
            if (devices == null) {
                return false;
            }
            for (AudioDeviceInfo device : devices) {
                int type = device.getType();
                if (type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
                        || type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
            // ignore
        }
        return false;
    }

    private static void play() {
        try {
            final ToneGenerator tone = new ToneGenerator(AudioManager.STREAM_MUSIC, 60);
            tone.startTone(ToneGenerator.TONE_PROP_BEEP, TONE_MS);
            HANDLER.postDelayed(() -> {
                try {
                    tone.release();
                } catch (Throwable ignored) {
                    // ignore
                }
            }, TONE_MS + 250L);
        } catch (Throwable ignored) {
            // Some devices refuse a tone generator; silence is an acceptable failure.
        }
    }
}
