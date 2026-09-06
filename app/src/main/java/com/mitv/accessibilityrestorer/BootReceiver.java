package com.mitv.accessibilityrestorer;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;

public final class BootReceiver extends BroadcastReceiver {
    private static final long RESTORE_DELAY_MILLIS = 10_000L;
    private static final int RESTORE_REQUEST_CODE = 3003;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) {
            return;
        }

        String action = intent.getAction();
        if (!Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(action)
                && !Intent.ACTION_BOOT_COMPLETED.equals(action)) {
            return;
        }

        Context appContext = DirectBootContext.get(context);
        int currentBootCount = BootSessionState.currentBootCount(appContext);
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)) {
            PostBootOptionalRecovery.onBootCompleted(appContext, currentBootCount);
        }
        if (currentBootCount >= 0
                && RecoveryState.isBootCompleted(appContext, currentBootCount)) {
            Log.i(AccessibilityRestorer.LOG_TAG,
                    "BOOT fallback skipped before alarm: boot already completed"
                            + ", action=" + action
                            + ", BOOT_COUNT=" + currentBootCount);
            return;
        }
        BootSessionState.Claim claim = BootSessionState.claim(appContext);
        boolean hasSecureSettings = appContext.getPackageManager().checkPermission(
                android.Manifest.permission.WRITE_SECURE_SETTINGS,
                appContext.getPackageName()) == PackageManager.PERMISSION_GRANTED;

        Log.i(AccessibilityRestorer.LOG_TAG,
                "Boot event=" + action
                        + ", BOOT_COUNT=" + claim.session.bootCount
                        + ", session=" + claim.session.id
                        + ", alreadyScheduled=" + claim.alreadyScheduled
                        + ", state=" + claim.state);
        Log.i(AccessibilityRestorer.LOG_TAG,
                "WRITE_SECURE_SETTINGS granted=" + hasSecureSettings);

        if (claim.alreadyScheduled) {
            Log.i(AccessibilityRestorer.LOG_TAG,
                    "Restore already scheduled or completed for this boot; ignoring " + action);
            return;
        }
        if (!claim.claimed) {
            AppStatus.saveResult(
                    appContext,
                    false,
                    "Не удалось сохранить Direct Boot-состояние планирования");
            return;
        }

        Intent restoreIntent = new Intent(appContext, RestoreReceiver.class);
        restoreIntent.setAction(RestoreReceiver.ACTION_RESTORE_AFTER_BOOT);
        restoreIntent.putExtra(RestoreReceiver.EXTRA_BOOT_SESSION, claim.session.id);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                appContext,
                RESTORE_REQUEST_CODE,
                restoreIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | immutableFlag());

        AlarmManager alarmManager = (AlarmManager) appContext.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            alarmFailed(appContext, claim.session.id, "AlarmManager недоступен", null);
            return;
        }

        long triggerAt = SystemClock.elapsedRealtime() + RESTORE_DELAY_MILLIS;
        long scheduledAtWall = System.currentTimeMillis();
        boolean exact = true;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        triggerAt,
                        pendingIntent);
            } else {
                alarmManager.setExact(
                        AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        triggerAt,
                        pendingIntent);
            }
        } catch (SecurityException exception) {
            exact = false;
            Log.w(AccessibilityRestorer.LOG_TAG,
                    "Exact alarm rejected; using one-shot inexact fallback", exception);
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setAndAllowWhileIdle(
                            AlarmManager.ELAPSED_REALTIME_WAKEUP,
                            triggerAt,
                            pendingIntent);
                } else {
                    alarmManager.set(
                            AlarmManager.ELAPSED_REALTIME_WAKEUP,
                            triggerAt,
                            pendingIntent);
                }
            } catch (RuntimeException fallbackException) {
                alarmFailed(
                        appContext,
                        claim.session.id,
                        "Не удалось установить одноразовый alarm",
                        fallbackException);
                return;
            }
        } catch (RuntimeException exception) {
            alarmFailed(
                    appContext,
                    claim.session.id,
                    "Не удалось установить одноразовый alarm",
                    exception);
            return;
        }

        BootSessionState.markAlarmScheduled(appContext, claim.session.id, triggerAt);
        Log.i(AccessibilityRestorer.LOG_TAG,
                "One-shot alarm scheduled: session=" + claim.session.id
                        + ", scheduledAtWall=" + scheduledAtWall
                        + ", triggerElapsed=" + triggerAt
                        + ", delayMs=" + RESTORE_DELAY_MILLIS
                        + ", exact=" + exact
                        + ", fallback retained=true");
    }

    private static void alarmFailed(
            Context context, String sessionId, String message, RuntimeException exception) {
        BootSessionState.markAlarmFailed(context, sessionId);
        if (exception == null) {
            Log.e(AccessibilityRestorer.LOG_TAG, message);
        } else {
            Log.e(AccessibilityRestorer.LOG_TAG, message, exception);
        }
        AppStatus.saveResult(context, false, message);
    }

    private static int immutableFlag() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                ? PendingIntent.FLAG_IMMUTABLE
                : 0;
    }
}
