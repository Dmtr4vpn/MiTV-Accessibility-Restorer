package foxmitv.restorer;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.util.Log;

final class RecoveryState {
    private static final String PREFERENCES = "recovery_sessions";
    private static final String KEY_LAST_BOOT_COUNT = "last_completed_boot_count";
    private static final String KEY_LAST_BOOT_ELAPSED = "last_completed_boot_elapsed";
    private static final String KEY_RUNNING_SESSION = "running_session";
    private static final String KEY_RUNNING_SINCE = "running_since";
    private static final String KEY_LAST_FINISHED = "last_finished";
    private static final String KEY_LAST_SUCCESS = "last_success";

    static final long FIRST_INSTALL_UPTIME_MS = 120_000L;
    static final long BOOT_COMPLETION_GUARD_MS = 30_000L;
    static final long STR_COOLDOWN_MS = 15_000L;
    private static final long STALE_RUNNING_SESSION_MS = 60_000L;

    private RecoveryState() {
    }

    static synchronized MainDecision evaluateMainLaunch(Context context) {
        Context storageContext = DirectBootContext.get(context);
        SharedPreferences preferences = preferences(storageContext);
        int currentBootCount = BootSessionState.currentBootCount(context);
        long elapsed = SystemClock.elapsedRealtime();

        if (!preferences.contains(KEY_LAST_BOOT_COUNT)) {
            if (elapsed >= FIRST_INSTALL_UPTIME_MS) {
                boolean stored = preferences.edit()
                        .putInt(KEY_LAST_BOOT_COUNT, currentBootCount)
                        .putLong(KEY_LAST_BOOT_ELAPSED, 0L)
                        .commit();
                Log.i(AccessibilityRestorer.LOG_TAG,
                        "MAIN DECISION initialized lastCompletedBootCount="
                                + currentBootCount + ", stored=" + stored
                                + ", uptimeMs=" + elapsed);
                return new MainDecision(
                        stored ? MainMode.STR_READY : MainMode.STORAGE_ERROR,
                        currentBootCount,
                        currentBootCount,
                        elapsed,
                        "first_install_uptime");
            }
            return new MainDecision(
                    MainMode.BOOT_PENDING,
                    currentBootCount,
                    Integer.MIN_VALUE,
                    elapsed,
                    "first_install_during_boot");
        }

        int completedBootCount = preferences.getInt(KEY_LAST_BOOT_COUNT, Integer.MIN_VALUE);
        if (currentBootCount != completedBootCount) {
            return new MainDecision(
                    MainMode.BOOT_PENDING,
                    currentBootCount,
                    completedBootCount,
                    elapsed,
                    "boot_count_changed");
        }

        long completedElapsed = preferences.getLong(KEY_LAST_BOOT_ELAPSED, 0L);
        if (completedElapsed > 0L && elapsed < completedElapsed) {
            return new MainDecision(
                    MainMode.BOOT_PENDING,
                    currentBootCount,
                    completedBootCount,
                    elapsed,
                    "elapsed_realtime_reset_boot_count_not_advanced");
        }
        long age = completedElapsed > 0L ? elapsed - completedElapsed : Long.MAX_VALUE;
        if (age >= 0L && age < BOOT_COMPLETION_GUARD_MS) {
            return new MainDecision(
                    MainMode.BOOT_GUARD,
                    currentBootCount,
                    completedBootCount,
                    elapsed,
                    "boot_completed_age_ms=" + age);
        }
        return new MainDecision(
                MainMode.STR_READY,
                currentBootCount,
                completedBootCount,
                elapsed,
                "same_boot_session");
    }

    static synchronized boolean markBootCompleted(Context context, int expectedBootCount) {
        int bootCount = BootSessionState.currentBootCount(context);
        long elapsed = SystemClock.elapsedRealtime();
        if (bootCount != expectedBootCount) {
            Log.w(AccessibilityRestorer.LOG_TAG,
                    "BOOT SESSION completion refused: expectedBootCount="
                            + expectedBootCount + ", currentBootCount=" + bootCount
                            + ", elapsedRealtime=" + elapsed);
            return false;
        }
        boolean stored = preferences(DirectBootContext.get(context)).edit()
                .putInt(KEY_LAST_BOOT_COUNT, bootCount)
                .putLong(KEY_LAST_BOOT_ELAPSED, elapsed)
                .commit();
        Log.i(AccessibilityRestorer.LOG_TAG,
                "BOOT SESSION completed: lastCompletedBootCount=" + bootCount
                        + ", elapsedRealtime=" + elapsed + ", stored=" + stored);
        return stored;
    }

    static synchronized boolean isBootCompleted(Context context, int bootCount) {
        SharedPreferences preferences = preferences(DirectBootContext.get(context));
        return preferences.contains(KEY_LAST_BOOT_COUNT)
                && preferences.getInt(KEY_LAST_BOOT_COUNT, Integer.MIN_VALUE) == bootCount;
    }

    static synchronized boolean hasActiveSession(Context context) {
        SharedPreferences preferences = preferences(DirectBootContext.get(context));
        String runningSession = preferences.getString(KEY_RUNNING_SESSION, null);
        long runningSince = preferences.getLong(KEY_RUNNING_SINCE, 0L);
        long age = runningSince > 0L
                ? SystemClock.elapsedRealtime() - runningSince : Long.MAX_VALUE;
        return runningSession != null && age >= 0L && age < STALE_RUNNING_SESSION_MS;
    }

    static synchronized StartResult tryStart(
            Context context, String trigger, boolean bypassCooldown) {
        SharedPreferences preferences = preferences(DirectBootContext.get(context));
        long now = SystemClock.elapsedRealtime();
        long runningSince = preferences.getLong(KEY_RUNNING_SINCE, 0L);
        long runningAge = runningSince > 0L ? now - runningSince : Long.MAX_VALUE;
        if (runningAge >= 0L && runningAge < STALE_RUNNING_SESSION_MS) {
            return StartResult.rejected("shared_session_running ageMs=" + runningAge);
        }

        long lastFinished = preferences.getLong(KEY_LAST_FINISHED, 0L);
        long cooldownAge = lastFinished > 0L ? now - lastFinished : Long.MAX_VALUE;
        if (!bypassCooldown && cooldownAge >= 0L && cooldownAge < STR_COOLDOWN_MS) {
            return StartResult.rejected("cooldown ageMs=" + cooldownAge);
        }

        int bootCount = BootSessionState.currentBootCount(context);
        String prefix = trigger.startsWith("EARLY_BOOT") ? "early-boot" : "str";
        String sessionId = prefix + ":" + bootCount + ":" + now;
        boolean stored = preferences.edit()
                .putString(KEY_RUNNING_SESSION, sessionId)
                .putLong(KEY_RUNNING_SINCE, now)
                .commit();
        if (!stored) {
            return StartResult.rejected("session_store_failed");
        }
        Log.i(AccessibilityRestorer.LOG_TAG,
                "SESSION MARKER stored session=" + sessionId + ", trigger=" + trigger);
        return StartResult.allowed(sessionId);
    }

    static synchronized void finish(
            Context context, String sessionId, boolean executionCompleted) {
        SharedPreferences preferences = preferences(DirectBootContext.get(context));
        String savedSession = preferences.getString(KEY_RUNNING_SESSION, null);
        if (!sessionId.equals(savedSession)) {
            Log.w(AccessibilityRestorer.LOG_TAG,
                    "SESSION MARKER finish ignored: requested=" + sessionId
                            + ", saved=" + savedSession);
            return;
        }
        boolean stored = preferences.edit()
                .remove(KEY_RUNNING_SESSION)
                .remove(KEY_RUNNING_SINCE)
                .putLong(KEY_LAST_FINISHED, SystemClock.elapsedRealtime())
                .putBoolean(KEY_LAST_SUCCESS, executionCompleted)
                .commit();
        Log.i(AccessibilityRestorer.LOG_TAG,
                "SESSION MARKER cleared session=" + sessionId
                        + ", executionCompleted=" + executionCompleted
                        + ", stored=" + stored);
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    }

    enum MainMode {
        STR_READY,
        BOOT_PENDING,
        BOOT_GUARD,
        STORAGE_ERROR
    }

    static final class MainDecision {
        final MainMode mode;
        final int currentBootCount;
        final int completedBootCount;
        final long elapsedRealtime;
        final String reason;

        MainDecision(
                MainMode mode,
                int currentBootCount,
                int completedBootCount,
                long elapsedRealtime,
                String reason) {
            this.mode = mode;
            this.currentBootCount = currentBootCount;
            this.completedBootCount = completedBootCount;
            this.elapsedRealtime = elapsedRealtime;
            this.reason = reason;
        }
    }

    static final class StartResult {
        final boolean allowed;
        final String sessionId;
        final String reason;

        private StartResult(boolean allowed, String sessionId, String reason) {
            this.allowed = allowed;
            this.sessionId = sessionId;
            this.reason = reason;
        }

        static StartResult allowed(String sessionId) {
            return new StartResult(true, sessionId, "allowed");
        }

        static StartResult rejected(String reason) {
            return new StartResult(false, null, reason);
        }
    }
}
