package com.mitv.accessibilityrestorer;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

final class BootSessionState {
    private static final String PREFERENCES = "direct_boot_state";
    private static final String KEY_SESSION_ID = "session_id";
    private static final String KEY_STATE = "state";
    private static final String KEY_CLAIMED_AT = "claimed_at";
    private static final String KEY_TRIGGER_ELAPSED = "trigger_elapsed";
    private static final String KEY_FINISHED_AT = "finished_at";
    private static final String KEY_SUCCESS = "success";

    private static final String STATE_SCHEDULED = "scheduled";
    private static final String STATE_RESTORING = "restoring";
    private static final String STATE_COMPLETED = "completed";
    private static final int UNKNOWN_BOOT_COUNT = -1;

    private BootSessionState() {
    }

    static int currentBootCount(Context context) {
        int bootCount = UNKNOWN_BOOT_COUNT;
        try {
            bootCount = Settings.Global.getInt(
                    context.getContentResolver(), "boot_count", UNKNOWN_BOOT_COUNT);
        } catch (RuntimeException exception) {
            Log.w(AccessibilityRestorer.LOG_TAG,
                    "Could not read Settings.Global.BOOT_COUNT",
                    exception);
        }
        return bootCount;
    }

    static synchronized Claim claim(Context context) {
        Context storageContext = DirectBootContext.get(context);
        Session session = readSession(context);
        SharedPreferences preferences = preferences(storageContext);
        String savedSession = preferences.getString(KEY_SESSION_ID, null);
        String savedState = preferences.getString(KEY_STATE, "none");
        boolean alreadyScheduled = session.id.equals(savedSession)
                && (STATE_SCHEDULED.equals(savedState)
                || STATE_RESTORING.equals(savedState)
                || STATE_COMPLETED.equals(savedState));

        if (alreadyScheduled) {
            return new Claim(session, false, true, savedState);
        }

        boolean stored = preferences.edit()
                .putString(KEY_SESSION_ID, session.id)
                .putString(KEY_STATE, STATE_SCHEDULED)
                .putLong(KEY_CLAIMED_AT, System.currentTimeMillis())
                .remove(KEY_TRIGGER_ELAPSED)
                .remove(KEY_FINISHED_AT)
                .remove(KEY_SUCCESS)
                .commit();
        if (!stored) {
            Log.e(AccessibilityRestorer.LOG_TAG,
                    "Could not persist Direct Boot schedule claim for " + session.id);
        }
        return new Claim(session, stored, false, stored ? STATE_SCHEDULED : "store_failed");
    }

    static synchronized void markAlarmScheduled(
            Context context, String sessionId, long triggerElapsed) {
        SharedPreferences preferences = preferences(DirectBootContext.get(context));
        if (!sessionId.equals(preferences.getString(KEY_SESSION_ID, null))) {
            Log.w(AccessibilityRestorer.LOG_TAG,
                    "Refusing to update alarm time for stale session " + sessionId);
            return;
        }
        boolean stored = preferences.edit()
                .putLong(KEY_TRIGGER_ELAPSED, triggerElapsed)
                .commit();
        if (!stored) {
            Log.e(AccessibilityRestorer.LOG_TAG,
                    "Could not persist alarm time for " + sessionId);
        }
    }

    static synchronized void markAlarmFailed(Context context, String sessionId) {
        SharedPreferences preferences = preferences(DirectBootContext.get(context));
        if (!sessionId.equals(preferences.getString(KEY_SESSION_ID, null))) {
            return;
        }
        boolean cleared = preferences.edit()
                .remove(KEY_SESSION_ID)
                .remove(KEY_STATE)
                .remove(KEY_TRIGGER_ELAPSED)
                .commit();
        if (!cleared) {
            Log.e(AccessibilityRestorer.LOG_TAG,
                    "Could not clear failed alarm claim for " + sessionId);
        }
    }

    static synchronized boolean beginRestore(Context context, String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) {
            Log.e(AccessibilityRestorer.LOG_TAG, "Restore alarm has no boot session ID");
            return false;
        }

        SharedPreferences preferences = preferences(DirectBootContext.get(context));
        String savedSession = preferences.getString(KEY_SESSION_ID, null);
        String state = preferences.getString(KEY_STATE, "none");
        if (!sessionId.equals(savedSession) || !STATE_SCHEDULED.equals(state)) {
            Log.w(AccessibilityRestorer.LOG_TAG,
                    "Skipping duplicate or stale restore: requested=" + sessionId
                            + ", saved=" + savedSession + ", state=" + state);
            return false;
        }

        boolean stored = preferences.edit()
                .putString(KEY_STATE, STATE_RESTORING)
                .commit();
        if (!stored) {
            Log.e(AccessibilityRestorer.LOG_TAG,
                    "Could not mark restore as started for " + sessionId);
        }
        return stored;
    }

    static synchronized void finishRestore(Context context, String sessionId, boolean success) {
        SharedPreferences preferences = preferences(DirectBootContext.get(context));
        if (!sessionId.equals(preferences.getString(KEY_SESSION_ID, null))) {
            Log.w(AccessibilityRestorer.LOG_TAG,
                    "Refusing to finish restore for stale session " + sessionId);
            return;
        }
        boolean stored = preferences.edit()
                .putString(KEY_STATE, STATE_COMPLETED)
                .putLong(KEY_FINISHED_AT, System.currentTimeMillis())
                .putBoolean(KEY_SUCCESS, success)
                .commit();
        if (!stored) {
            Log.e(AccessibilityRestorer.LOG_TAG,
                    "Could not persist restore completion for " + sessionId);
        }
    }

    private static Session readSession(Context context) {
        int bootCount = currentBootCount(context);
        if (bootCount >= 0) {
            return new Session(bootCount, "boot-count:" + bootCount);
        }

        String kernelBootId = readKernelBootId();
        if (kernelBootId != null && !kernelBootId.isEmpty()) {
            return new Session(UNKNOWN_BOOT_COUNT, "kernel:" + kernelBootId);
        }

        long estimatedBootEpoch = System.currentTimeMillis() - SystemClock.elapsedRealtime();
        return new Session(
                UNKNOWN_BOOT_COUNT,
                "boot-epoch-minute:" + (estimatedBootEpoch / 60_000L));
    }

    private static String readKernelBootId() {
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader("/proc/sys/kernel/random/boot_id"));
            String value = reader.readLine();
            return value == null ? null : value.trim();
        } catch (IOException | RuntimeException exception) {
            Log.w(AccessibilityRestorer.LOG_TAG, "Could not read kernel boot ID", exception);
            return null;
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException exception) {
                    Log.w(AccessibilityRestorer.LOG_TAG, "Could not close boot ID reader", exception);
                }
            }
        }
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    }

    static final class Claim {
        final Session session;
        final boolean claimed;
        final boolean alreadyScheduled;
        final String state;

        Claim(Session session, boolean claimed, boolean alreadyScheduled, String state) {
            this.session = session;
            this.claimed = claimed;
            this.alreadyScheduled = alreadyScheduled;
            this.state = state;
        }
    }

    static final class Session {
        final int bootCount;
        final String id;

        Session(int bootCount, String id) {
            this.bootCount = bootCount;
            this.id = id;
        }
    }
}
