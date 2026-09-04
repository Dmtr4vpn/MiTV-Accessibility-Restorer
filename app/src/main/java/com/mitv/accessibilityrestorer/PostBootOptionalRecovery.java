package com.mitv.accessibilityrestorer;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

final class PostBootOptionalRecovery {
    private static final String PREFERENCES = "post_boot_optional_recovery";
    private static final String KEY_BOOT_COMPLETED_RECEIVED =
            "boot_completed_received_boot_count";
    private static final String KEY_LAST_EXECUTED =
            "last_post_boot_optional_boot_count";

    private static int inMemoryExecutedBootCount = Integer.MIN_VALUE;

    private PostBootOptionalRecovery() {
    }

    static void onBootCompleted(Context context, int bootCount) {
        Context appContext = DirectBootContext.get(context);
        if (bootCount < 0) {
            Log.w(AccessibilityRestorer.LOG_TAG,
                    "POST_BOOT_OPTIONAL BOOT_COMPLETED not recorded: BOOT_COUNT unavailable");
        } else {
            boolean stored = preferences(appContext).edit()
                    .putInt(KEY_BOOT_COMPLETED_RECEIVED, bootCount)
                    .commit();
            Log.i(AccessibilityRestorer.LOG_TAG,
                    "POST_BOOT_OPTIONAL Android BOOT_COMPLETED_RECEIVED"
                            + ", bootCount=" + bootCount
                            + ", stored=" + stored);
        }
        maybeStartPostBootOptionalRecovery(appContext, bootCount);
    }

    static void maybeStartPostBootOptionalRecovery(Context context, int bootCount) {
        final Context appContext = DirectBootContext.get(context);
        final int currentBootCount = BootSessionState.currentBootCount(appContext);
        final boolean coreBootSuccess = bootCount >= 0
                && currentBootCount == bootCount
                && RecoveryState.isBootCompleted(appContext, bootCount);
        final SharedPreferences preferences = preferences(appContext);
        final boolean bootCompletedReceived = bootCount >= 0
                && preferences.getInt(
                KEY_BOOT_COMPLETED_RECEIVED, Integer.MIN_VALUE) == bootCount;

        synchronized (PostBootOptionalRecovery.class) {
            boolean alreadyExecuted = bootCount >= 0
                    && (inMemoryExecutedBootCount == bootCount
                    || preferences.getInt(KEY_LAST_EXECUTED, Integer.MIN_VALUE) == bootCount);
            Log.i(AccessibilityRestorer.LOG_TAG,
                    "POST_BOOT_OPTIONAL gate check"
                            + ", bootCount=" + bootCount
                            + ", currentBootCount=" + currentBootCount
                            + ", coreBootSuccess=" + coreBootSuccess
                            + ", bootCompletedReceived=" + bootCompletedReceived
                            + ", alreadyExecuted=" + alreadyExecuted);
            if (!coreBootSuccess || !bootCompletedReceived || alreadyExecuted) {
                return;
            }

            boolean stored = preferences.edit()
                    .putInt(KEY_LAST_EXECUTED, bootCount)
                    .commit();
            if (!stored) {
                Log.e(AccessibilityRestorer.LOG_TAG,
                        "POST_BOOT_OPTIONAL gate refused: execution marker store failed"
                                + ", bootCount=" + bootCount);
                return;
            }
            inMemoryExecutedBootCount = bootCount;
        }

        try {
            Thread worker = new Thread(new Runnable() {
                @Override
                public void run() {
                    runOptionalRecovery(appContext, bootCount);
                }
            }, "MiTVRestorer-PostBootOptional");
            worker.start();
        } catch (RuntimeException exception) {
            Log.e(AccessibilityRestorer.LOG_TAG,
                    "POST_BOOT_OPTIONAL_RECOVERY worker start failed"
                            + ", bootCount=" + bootCount,
                    exception);
        }
    }

    private static void runOptionalRecovery(Context context, int bootCount) {
        Log.i(AccessibilityRestorer.LOG_TAG,
                "POST_BOOT_OPTIONAL_RECOVERY started, bootCount=" + bootCount);

        TorrServeTarget.RecoveryStatus torrServeStatus =
                TorrServeTarget.RecoveryStatus.FAILED;
        try {
            torrServeStatus = TorrServeTarget.recoverAccessibility(context).status;
        } catch (RuntimeException exception) {
            Log.e(AccessibilityRestorer.LOG_TAG,
                    "OPTIONAL TorrServe status=FAILED reason=unhandled",
                    exception);
        }

        String v2RayStatus = V2RayVpnAssist.Status.TRIGGER_FAILED.toString();
        try {
            boolean installed = V2RayVpnAssist.isPackageInstalled(context);
            boolean componentAvailable = installed
                    && V2RayVpnAssist.isWidgetReceiverAvailable(context);
            Log.i(AccessibilityRestorer.LOG_TAG,
                    "OPTIONAL v2RayTun installed=" + installed);
            Log.i(AccessibilityRestorer.LOG_TAG,
                    "OPTIONAL v2RayTun componentAvailable=" + componentAvailable);
            if (!installed) {
                v2RayStatus = V2RayVpnAssist.Status.SKIPPED_NOT_INSTALLED.toString();
            } else if (!componentAvailable) {
                v2RayStatus =
                        V2RayVpnAssist.Status.SKIPPED_COMPONENT_UNAVAILABLE.toString();
            } else {
                V2RayVpnAssist.Result result = V2RayVpnAssist.start(
                        context,
                        "post-boot-optional:" + bootCount,
                        "POST_BOOT_OPTIONAL_RECOVERY").awaitCompletion();
                v2RayStatus = normalizeV2RayStatus(result.status);
            }
            Log.i(AccessibilityRestorer.LOG_TAG,
                    "OPTIONAL v2RayTun status=" + v2RayStatus);
        } catch (RuntimeException exception) {
            v2RayStatus = V2RayVpnAssist.Status.TRIGGER_FAILED.toString();
            Log.e(AccessibilityRestorer.LOG_TAG,
                    "OPTIONAL v2RayTun status=" + v2RayStatus + " reason=unhandled",
                    exception);
        }

        Log.i(AccessibilityRestorer.LOG_TAG,
                "POST_BOOT_OPTIONAL_RECOVERY FINISH"
                        + ", bootCount=" + bootCount
                        + ", torrServe=" + torrServeStatus
                        + ", v2Ray=" + v2RayStatus);
    }

    private static String normalizeV2RayStatus(V2RayVpnAssist.Status status) {
        return status == V2RayVpnAssist.Status.SKIPPED_ALREADY_RUNNING_GRACE
                ? V2RayVpnAssist.Status.ALREADY_ACTIVE.toString()
                : status.toString();
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    }
}
