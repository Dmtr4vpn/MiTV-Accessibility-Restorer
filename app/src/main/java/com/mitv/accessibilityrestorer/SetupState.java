package com.mitv.accessibilityrestorer;

import android.content.Context;
import android.content.SharedPreferences;

final class SetupState {
    private static final String PREFERENCES = "first_run_setup";
    private static final String KEY_SETUP_COMPLETED = "setup_completed";

    private SetupState() {
    }

    static boolean isMarkedComplete(Context context) {
        return preferences(context).getBoolean(KEY_SETUP_COMPLETED, false);
    }

    static boolean markComplete(Context context) {
        return preferences(context).edit()
                .putBoolean(KEY_SETUP_COMPLETED, true)
                .commit();
    }

    static void clear(Context context) {
        preferences(context).edit()
                .remove(KEY_SETUP_COMPLETED)
                .commit();
    }

    private static SharedPreferences preferences(Context context) {
        Context appContext = context.getApplicationContext();
        if (appContext == null) {
            appContext = context;
        }
        return appContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    }
}
