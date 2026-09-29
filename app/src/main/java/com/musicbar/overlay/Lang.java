package com.musicbar.overlay;

import android.app.LocaleManager;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;
import android.os.LocaleList;

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

    /**
     * Hand the chosen language to the system as well, so that the parts of the interface Android
     * draws for us follow it too: the launcher label, the app-info page and the installer all read
     * the system locale rather than our resources, which is why the app could be in English while
     * the icon under it still carried the Chinese name.
     *
     * <p>Android 13 and later; below that the label simply follows the system language. Only sent
     * when it differs, because the system recreates the app's activities when it changes.
     */
    public static void applyToSystem(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return;
        }
        try {
            LocaleManager manager = context.getSystemService(LocaleManager.class);
            if (manager == null) {
                return;
            }
            int mode = Prefs.lang(context);
            LocaleList want;
            if (mode == ENGLISH) {
                want = LocaleList.forLanguageTags("en");
            } else if (mode == CHINESE) {
                want = LocaleList.forLanguageTags("zh-Hans");
            } else {
                want = LocaleList.getEmptyLocaleList();
            }
            if (!want.equals(manager.getApplicationLocales())) {
                manager.setApplicationLocales(want);
            }
        } catch (Throwable ignored) {
            // A system without the service, or one that refuses: the app still follows the setting.
        }
    }
}
