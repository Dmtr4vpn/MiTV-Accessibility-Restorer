package com.mitv.accessibilityrestorer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public final class RestoreReceiver extends BroadcastReceiver {
    static final String ACTION_RESTORE_AFTER_BOOT =
            "com.mitv.accessibilityrestorer.action.RESTORE_AFTER_BOOT";
    static final String EXTRA_BOOT_SESSION = "boot_session";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent == null ? null : intent.getAction();
        String sessionId = intent == null ? null : intent.getStringExtra(EXTRA_BOOT_SESSION);
        Log.i(AccessibilityRestorer.LOG_TAG,
                "RestoreReceiver started: action=" + action + ", session=" + sessionId);

        if (!ACTION_RESTORE_AFTER_BOOT.equals(action)) {
            Log.w(AccessibilityRestorer.LOG_TAG, "Ignoring unexpected RestoreReceiver action");
            return;
        }

        Context appContext = DirectBootContext.get(context);
        int currentBootCount = BootSessionState.currentBootCount(appContext);
        if (currentBootCount >= 0
                && RecoveryState.isBootCompleted(appContext, currentBootCount)) {
            Log.i(AccessibilityRestorer.LOG_TAG,
                    "BOOT fallback skipped in RestoreReceiver: early boot already completed"
                            + ", currentBootCount=" + currentBootCount
                            + ", requestedSession=" + sessionId);
            return;
        }
        if (!BootSessionState.beginRestore(appContext, sessionId)) {
            return;
        }

        final PendingResult pendingResult = goAsync();
        final Context workerContext = appContext;
        final String workerSessionId = sessionId;
        try {
            Thread worker = new Thread(new Runnable() {
                @Override
                public void run() {
                    boolean success = false;
                    try {
                        success = RecoveryEngine.runColdBoot(
                                workerContext, workerSessionId);
                    } catch (RuntimeException exception) {
                        Log.e(AccessibilityRestorer.LOG_TAG,
                                "RestoreReceiver cold boot flow failed",
                                exception);
                    } finally {
                        BootSessionState.finishRestore(
                                workerContext, workerSessionId, success);
                        Log.i(AccessibilityRestorer.LOG_TAG,
                                "RestoreReceiver finished: session=" + workerSessionId
                                        + ", success=" + success);
                        pendingResult.finish();
                    }
                }
            }, "MiTVRestorer-Boot");
            worker.start();
        } catch (RuntimeException exception) {
            Log.e(AccessibilityRestorer.LOG_TAG,
                    "Could not start cold boot recovery thread",
                    exception);
            BootSessionState.finishRestore(appContext, sessionId, false);
            pendingResult.finish();
        }
    }
}
