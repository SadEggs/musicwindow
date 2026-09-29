package com.musicbar.overlay;

import android.content.Context;
import android.content.res.Configuration;

import java.util.Locale;

/**
 * The language the interface is shown in.
 *
 * <p>Applied by overriding {@code attachBaseContext} in the components that show text, so a
 * wrapped context is what every {@code getString} in them resolves against. The default
 * resources are Chinese and {@code values-en} holds the English translation, so "follow the
 * system" needs no wrapping at all.
 */
public final class Lang {

    public static final int SYSTEM = 0;
    public static final int CHINESE = 1;
    public static final int ENGLISH = 2;

    private Lang() {
    }

    /** Wrap a context in the chosen language, or return it untouched for "follow the system". */
    public static Context wrap(Context base) {
        int mode = Prefs.lang(base);
        if (mode != CHINESE && mode != ENGLISH) {
            return base;
        }
        Configuration config = new Configuration(base.getResources().getConfiguration());
        config.setLocale(mode == ENGLISH ? Locale.ENGLISH : Locale.SIMPLIFIED_CHINESE);
        return base.createConfigurationContext(config);
    }
}
