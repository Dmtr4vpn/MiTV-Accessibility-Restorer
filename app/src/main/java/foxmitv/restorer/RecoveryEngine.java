package foxmitv.restorer;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;

import java.util.concurrent.atomic.AtomicBoolean;

final class RecoveryEngine {
    static final long STR_INTERACTIVE_SETTLE_MS = 500L;
    static final long EARLY_BOOT_SETTLE_MS = 2_500L;
    static final long INTERACTIVE_POLL_MS = 1_000L;

    private static final long WRITE_TIMEOUT_MS = 3_000L;
    private static final long WRITE_POLL_MS = 200L;
    private static final long NORMAL_ALL_INITIAL_SETTLE_MS = 2_500L;
    private static final long NORMAL_BASE_SETTLE_MS = 1_500L;
    private static final long NORMAL_MAPPER_SETTLE_MS = 2_500L;
    private static final long NORMAL_ALL_FINAL_SETTLE_MS = 1_500L;
    private static final long CONSERVATIVE_ALL_INITIAL_SETTLE_MS = 4_000L;
    private static final long CONSERVATIVE_BASE_SETTLE_MS = 3_000L;
    private static final long CONSERVATIVE_MAPPER_SETTLE_MS = 3_000L;
    private static final long CONSERVATIVE_ALL_FINAL_SETTLE_MS = 3_000L;
    private static final long RETRY_PAUSE_MS = 1_000L;
    private static final long COLD_BOOT_STABILIZE_MS = 1_000L;
    private static final long FINAL_REARM_DELAY_MS = 2_000L;
    private static final int MAX_STR_ATTEMPTS = 2;

    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);

    private RecoveryEngine() {
    }

    interface Callback {
        void onFinished(boolean executionCompleted, String message);
    }

    static boolean startStrRecovery(
            Context context,
            final String trigger,
            boolean manual,
            final long wakeElapsed,
            final Callback callback) {
        final Context appContext = DirectBootContext.get(context);
        final long appliedInteractiveSettleMs = manual ? 0L : STR_INTERACTIVE_SETTLE_MS;
        Log.i(AccessibilityRestorer.LOG_TAG,
                "TRIGGER received trigger=" + trigger
                        + ", boot_count=" + BootSessionState.currentBootCount(appContext)
                        + ", wakeElapsed=" + wakeElapsed
                        + ", elapsedRealtime=" + SystemClock.elapsedRealtime());

        if (!RUNNING.compareAndSet(false, true)) {
            Log.w(AccessibilityRestorer.LOG_TAG,
                    "TRIGGER ignored because already running trigger=" + trigger);
            return false;
        }

        final RecoveryState.StartResult start =
                RecoveryState.tryStart(appContext, trigger, manual);
        if (!start.allowed) {
            RUNNING.set(false);
            Log.w(AccessibilityRestorer.LOG_TAG,
                    "TRIGGER ignored trigger=" + trigger + ", reason=" + start.reason);
            return false;
        }

        try {
            Thread worker = new Thread(new Runnable() {
                @Override
                public void run() {
                    V2RayVpnAssist.Handle vpnAssist =
                            V2RayVpnAssist.start(appContext, start.sessionId);
                    Outcome outcome;
                    try {
                        outcome = runStrSession(
                                appContext,
                                start.sessionId,
                                trigger,
                                wakeElapsed,
                                appliedInteractiveSettleMs,
                                vpnAssist);
                    } catch (RuntimeException exception) {
                        Log.e(AccessibilityRestorer.LOG_TAG,
                                "SESSION unhandled RuntimeException session=" + start.sessionId,
                                exception);
                        String message = failedStrMessage(
                                "unhandled_" + exception.getClass().getSimpleName())
                                + vpnFields(vpnAssist.snapshot());
                        AppStatus.saveResult(appContext, false, message);
                        RecoveryCoverActivity.finishCover(
                                "Unhandled STR RuntimeException");
                        outcome = new Outcome(false, message);
                    } finally {
                        RUNNING.set(false);
                    }
                    RecoveryState.finish(
                            appContext, start.sessionId, outcome.executionCompleted);
                    final Outcome deliveredOutcome = outcome;
                    if (callback != null) {
                        new Handler(Looper.getMainLooper()).post(new Runnable() {
                            @Override
                            public void run() {
                                callback.onFinished(
                                        deliveredOutcome.executionCompleted,
                                        deliveredOutcome.message);
                            }
                        });
                    }
                }
            }, "MiTVRestorer-STR");
            worker.start();
            return true;
        } catch (RuntimeException exception) {
            RUNNING.set(false);
            RecoveryState.finish(appContext, start.sessionId, false);
            Log.e(AccessibilityRestorer.LOG_TAG,
                    "Could not start recovery thread",
                    exception);
            RecoveryCoverActivity.finishCover("Could not start STR worker");
            return false;
        }
    }

    static boolean startEarlyColdBootRecovery(
            Context context,
            final String sourceTrigger,
            final int expectedBootCount,
            final Callback callback) {
        final Context appContext = DirectBootContext.get(context);
        final String trigger = "EARLY_BOOT:" + sourceTrigger;
        Log.i(AccessibilityRestorer.LOG_TAG,
                "EARLY BOOT started request trigger=" + sourceTrigger
                        + ", expectedBootCount=" + expectedBootCount
                        + ", currentBootCount=" + BootSessionState.currentBootCount(appContext)
                        + ", elapsedRealtime=" + SystemClock.elapsedRealtime());

        if (RecoveryState.isBootCompleted(appContext, expectedBootCount)) {
            Log.i(AccessibilityRestorer.LOG_TAG,
                    "EARLY BOOT skipped: boot already completed boot_count="
                            + expectedBootCount);
            return false;
        }
        if (BootSessionState.currentBootCount(appContext) != expectedBootCount) {
            Log.w(AccessibilityRestorer.LOG_TAG,
                    "EARLY BOOT skipped: BOOT_COUNT changed before start expected="
                            + expectedBootCount + ", current="
                            + BootSessionState.currentBootCount(appContext));
            return false;
        }
        if (!RUNNING.compareAndSet(false, true)) {
            Log.w(AccessibilityRestorer.LOG_TAG,
                    "EARLY BOOT skipped because recovery already running");
            return false;
        }

        final RecoveryState.StartResult start =
                RecoveryState.tryStart(appContext, trigger, true);
        if (!start.allowed) {
            RUNNING.set(false);
            Log.w(AccessibilityRestorer.LOG_TAG,
                    "EARLY BOOT skipped reason=" + start.reason);
            return false;
        }

        try {
            Thread worker = new Thread(new Runnable() {
                @Override
                public void run() {
                    boolean success = false;
                    String message;
                    try {
                        success = runColdBoot(
                                appContext,
                                start.sessionId,
                                "EARLY_BOOT",
                                expectedBootCount);
                        message = success
                                ? "early boot completed"
                                : "early boot partial/failure; fallback retained";
                    } catch (RuntimeException exception) {
                        Log.e(AccessibilityRestorer.LOG_TAG,
                                "EARLY BOOT unhandled RuntimeException session="
                                        + start.sessionId,
                                exception);
                        message = "early boot failure: "
                                + exception.getClass().getSimpleName();
                    } finally {
                        RUNNING.set(false);
                    }
                    RecoveryState.finish(appContext, start.sessionId, success);
                    Log.i(AccessibilityRestorer.LOG_TAG,
                            "EARLY BOOT completed boot_count=" + expectedBootCount
                                    + ", success=" + success
                                    + ", fallback="
                                    + (success ? "will be skipped" : "retained"));
                    final boolean deliveredSuccess = success;
                    final String deliveredMessage = message;
                    if (callback != null) {
                        new Handler(Looper.getMainLooper()).post(new Runnable() {
                            @Override
                            public void run() {
                                callback.onFinished(deliveredSuccess, deliveredMessage);
                            }
                        });
                    }
                }
            }, "MiTVRestorer-EarlyBoot");
            worker.start();
            return true;
        } catch (RuntimeException exception) {
            RUNNING.set(false);
            RecoveryState.finish(appContext, start.sessionId, false);
            Log.e(AccessibilityRestorer.LOG_TAG,
                    "Could not start early boot recovery thread",
                    exception);
            return false;
        }
    }

    static boolean runColdBoot(Context context, String bootSessionId) {
        int bootCount = BootSessionState.currentBootCount(context);
        if (RecoveryState.isBootCompleted(context, bootCount)) {
            Log.i(AccessibilityRestorer.LOG_TAG,
                    "BOOT fallback skipped in engine: boot already completed boot_count="
                            + bootCount + ", session=" + bootSessionId);
            return true;
        }
        return runColdBoot(context, bootSessionId, "BOOT_FALLBACK", bootCount);
    }

    private static boolean runColdBoot(
            Context context,
            String bootSessionId,
            String trigger,
            int expectedBootCount) {
        Context appContext = DirectBootContext.get(context);
        long started = SystemClock.elapsedRealtime();
        logSessionStart(appContext, bootSessionId, trigger);
        VendorStartApp.Result precheck =
                VendorStartApp.rearm(appContext, trigger + " PRECHECK");
        TargetApps.Status targets = TargetApps.inspect(appContext);

        AccessibilityRestorer.Result restoreResult =
                AccessibilityRestorer.restore(appContext, trigger.toLowerCase());
        boolean finalLaunch = false;
        VendorStartApp.Result rearmOne = precheck;
        VendorStartApp.Result rearmTwo = precheck;
        if (restoreResult.success) {
            rearmOne = VendorStartApp.rearm(
                    appContext, trigger + " REARM #1");
            sleep(COLD_BOOT_STABILIZE_MS, "BOOT projectivy stabilization");
            finalLaunch = TargetApps.startProjectivy(
                    appContext, targets, trigger + " PROJECTIVY FINAL LAUNCH");
            sleep(FINAL_REARM_DELAY_MS, "BOOT final re-arm delay");
            rearmTwo = VendorStartApp.rearm(
                    appContext, trigger + " REARM #2");
        }

        boolean coreRecoverySuccess = restoreResult.success
                && targets.mapperService
                && targets.projectivyService
                && finalLaunch;
        boolean bootMarked = coreRecoverySuccess
                && RecoveryState.markBootCompleted(appContext, expectedBootCount);
        boolean fullSuccess = coreRecoverySuccess && bootMarked;
        String result = fullSuccess ? "success"
                : (restoreResult.success || finalLaunch) ? "partial" : "failure";
        Log.i(AccessibilityRestorer.LOG_TAG,
                "SESSION FINISH session=" + bootSessionId
                        + ", trigger=" + trigger
                        + ", boot_count=" + expectedBootCount
                        + ", success=" + fullSuccess
                        + ", result=" + result
                        + ", coreRecoverySuccess=" + coreRecoverySuccess
                        + ", accessibilitySuccess=" + restoreResult.success
                        + ", projectivyLaunch=" + finalLaunch
                        + ", vendorPrecheck=" + precheck.status
                        + ", vendorRearm1=" + rearmOne.status
                        + ", vendorArm=" + rearmTwo.status
                        + ", bootMarked=" + bootMarked
                        + ", durationMs=" + (SystemClock.elapsedRealtime() - started));
        if (fullSuccess) {
            PostBootOptionalRecovery.maybeStartPostBootOptionalRecovery(
                    appContext, expectedBootCount);
        }
        return fullSuccess;
    }

    private static Outcome runStrSession(
            Context context,
            String sessionId,
            String trigger,
            long wakeElapsed,
            long interactiveSettleMs,
            V2RayVpnAssist.Handle vpnAssist) {
        long recoveryStartElapsed = SystemClock.elapsedRealtime();
        logSessionStart(context, sessionId, trigger);
        Log.i(AccessibilityRestorer.LOG_TAG,
                "STR TIMING wakeElapsed=" + wakeElapsed
                        + ", recoveryStartElapsed=" + recoveryStartElapsed
                        + ", interactiveSettleMs=" + interactiveSettleMs);
        boolean coverActive = RecoveryCoverActivity.isActive();
        VendorStartApp.Result vendor = VendorStartApp.inspect(context, "STR ADVISORY");
        TargetApps.Status targets = TargetApps.inspect(context);
        TorrServeTarget.Status torrServe = TorrServeTarget.inspect(context);

        String existing;
        try {
            existing = Settings.Secure.getString(
                    context.getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        } catch (SecurityException exception) {
            Log.e(AccessibilityRestorer.LOG_TAG,
                    "ACCESSIBILITY BEFORE SecurityException",
                    exception);
            String message = failedStrMessage("settings_read_denied")
                    + vpnFields(vpnAssist.snapshot());
            AppStatus.saveResult(context, false, message);
            RecoveryCoverActivity.finishCover("STR settings read denied");
            return finishOutcome(
                    sessionId, trigger, wakeElapsed, recoveryStartElapsed,
                    -1L, false, message);
        } catch (RuntimeException exception) {
            Log.e(AccessibilityRestorer.LOG_TAG,
                    "ACCESSIBILITY BEFORE failed",
                    exception);
            String message = failedStrMessage("settings_read_error")
                    + vpnFields(vpnAssist.snapshot());
            AppStatus.saveResult(context, false, message);
            RecoveryCoverActivity.finishCover("STR settings read failed");
            return finishOutcome(
                    sessionId, trigger, wakeElapsed, recoveryStartElapsed,
                    -1L, false, message);
        }

        AccessibilityPlan plan = AccessibilityPlan.create(
                existing,
                targets.mapperService,
                targets.projectivyService,
                torrServe.accessibilityTargetAvailable);
        Log.i(AccessibilityRestorer.LOG_TAG, "ACCESSIBILITY BEFORE raw=" + value(existing));
        Log.i(AccessibilityRestorer.LOG_TAG, "BASE=" + plan.base);
        Log.i(AccessibilityRestorer.LOG_TAG, "MAPPER target=" + plan.mapper);
        Log.i(AccessibilityRestorer.LOG_TAG, "PROJECTIVY target=" + plan.projectivy);
        Log.i(AccessibilityRestorer.LOG_TAG, "ALL INITIAL=" + plan.allInitial);
        Log.i(AccessibilityRestorer.LOG_TAG, "ALL FINAL=" + plan.allFinal);

        boolean settingsRecovery = false;
        boolean targetUnstop = false;
        TargetUnstopper.Result mapperUnstop = null;
        TargetUnstopper.Result projectivyUnstop = null;
        TimingProfile completedProfile = TimingProfile.NORMAL_9S;
        int completedAttempt = 0;
        for (int attempt = 1; attempt <= MAX_STR_ATTEMPTS; attempt++) {
            TimingProfile profile = attempt == 1
                    ? TimingProfile.NORMAL_9S : TimingProfile.CONSERVATIVE;
            completedProfile = profile;
            completedAttempt = attempt;
            Log.i(AccessibilityRestorer.LOG_TAG,
                    "STR RESET attempt=" + attempt
                            + ", timingProfile=" + profile.label
                            + ", allInitialSettleMs=" + profile.allInitialSettleMs
                            + ", baseSettleMs=" + profile.baseSettleMs
                            + ", mapperSettleMs=" + profile.mapperSettleMs
                            + ", allFinalSettleMs=" + profile.allFinalSettleMs);

            mapperUnstop = TargetUnstopper.unstopMapper(context, targets);
            projectivyUnstop = TargetUnstopper.unstopProjectivy(context, targets);
            targetUnstop = mapperUnstop.success && projectivyUnstop.success;
            Log.i(AccessibilityRestorer.LOG_TAG,
                    "STR target unstop attempt=" + attempt
                            + ", targetUnstop=" + targetUnstop
                            + ", mapper=" + mapperUnstop.mode
                            + ", projectivy=" + projectivyUnstop.mode);

            settingsRecovery = runStrResetPhases(context, plan, targets, profile);
            if (settingsRecovery && targetUnstop) {
                break;
            }
            if (attempt < MAX_STR_ATTEMPTS) {
                String retryReason = !settingsRecovery && !targetUnstop
                        ? "settings_and_target_unstop"
                        : !settingsRecovery ? "settings_recovery" : "target_unstop";
                Log.w(AccessibilityRestorer.LOG_TAG,
                        "STR RESET attempt=" + attempt
                                + " failed measurable error"
                                + ", reason=" + retryReason
                                + ", settingsRecovery=" + settingsRecovery
                                + ", targetUnstop=" + targetUnstop
                                + ", retryPauseMs=" + RETRY_PAUSE_MS);
                Log.i(AccessibilityRestorer.LOG_TAG,
                        "RETRY timingProfile=CONSERVATIVE");
                sleep(RETRY_PAUSE_MS, "STR conservative retry pause");
            }
        }

        boolean targetsAvailable = targets.mapperPackage
                && targets.mapperService
                && targets.projectivyPackage
                && targets.projectivyActivity
                && targets.projectivyService;
        boolean coreExecutionReady = settingsRecovery && targetUnstop && targetsAvailable;
        boolean finalLaunch = false;
        long finalLaunchElapsed = -1L;
        if (coreExecutionReady) {
            finalLaunch = TargetApps.startProjectivy(
                    context, targets, "FINAL PROJECTIVY launch");
            finalLaunchElapsed = SystemClock.elapsedRealtime();
            Log.i(AccessibilityRestorer.LOG_TAG,
                    "FINAL PROJECTIVY finalLaunch=" + finalLaunch
                            + ", finalLaunchElapsed=" + finalLaunchElapsed);
        } else {
            Log.e(AccessibilityRestorer.LOG_TAG,
                    "FINAL PROJECTIVY launch skipped: measurable recovery incomplete"
                            + ", settingsRecovery=" + settingsRecovery
                            + ", targetUnstop=" + targetUnstop
                            + ", targetsAvailable=" + targetsAvailable);
        }

        boolean executionCompleted = coreExecutionReady && finalLaunch;
        boolean partial = settingsRecovery || targetUnstop || finalLaunch;
        String execution = executionCompleted ? "COMPLETED" : partial ? "PARTIAL" : "FAILED";
        V2RayVpnAssist.Result vpn = vpnAssist.snapshot();
        String message = "execution=" + execution
                + ", settingsRecovery=" + settingsRecovery
                + ", targetUnstop=" + targetUnstop
                + ", mapperUnstop=" + mode(mapperUnstop)
                + ", projectivyUnstop=" + mode(projectivyUnstop)
                + ", torrServeIncluded="
                + torrServe.accessibilityTargetAvailable
                + ", targetsAvailable=" + targetsAvailable
                + ", finalLaunch=" + finalLaunch
                + ", bindingVerification=UNAVAILABLE_IN_APP"
                + vpnFields(vpn)
                + ", attempt=" + completedAttempt
                + ", timingProfile=" + completedProfile.label
                + ", cover=" + coverActive
                + ", vendorArm=" + vendor.status
                + ", vendorAdvisory=true";
        AppStatus.saveResult(context, executionCompleted, message);
        RecoveryCoverActivity.finishCover(
                executionCompleted
                        ? "STR execution completed after final Projectivy launch"
                        : "STR execution partial or failed");
        return finishOutcome(
                sessionId, trigger, wakeElapsed, recoveryStartElapsed,
                finalLaunchElapsed, executionCompleted, message);
    }

    private static boolean runStrResetPhases(
            Context context,
            AccessibilityPlan plan,
            TargetApps.Status targets,
            TimingProfile profile) {
        if (!targets.mapperService || !targets.projectivyService) {
            Log.e(AccessibilityRestorer.LOG_TAG,
                    "STR RESET unavailable: one or more target services are missing");
            return false;
        }

        boolean allInitial = writeAndConfirmSecureServices(
                context,
                "PHASE ALL_INITIAL",
                plan.allInitial,
                true,
                profile.allInitialSettleMs);
        boolean base = writeAndConfirmSecureServices(
                context,
                "PHASE BASE",
                plan.base,
                false,
                profile.baseSettleMs);
        boolean mapper = writeAndConfirmSecureServices(
                context,
                "PHASE MAPPER",
                plan.mapper,
                true,
                profile.mapperSettleMs);
        boolean allFinal = writeAndConfirmSecureServices(
                context,
                "PHASE ALL_FINAL",
                plan.allFinal,
                true,
                profile.allFinalSettleMs);
        return allInitial && base && mapper && allFinal;
    }

    private static boolean writeAndConfirmSecureServices(
            Context context,
            String label,
            String target,
            boolean ensureAccessibilityEnabled,
            long settleMs) {
        long started = SystemClock.elapsedRealtime();
        try {
            boolean putResult = Settings.Secure.putString(
                    context.getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                    target);
            Log.i(AccessibilityRestorer.LOG_TAG,
                    label + " putString result=" + putResult
                            + ", writeElapsedMs="
                            + (SystemClock.elapsedRealtime() - started)
                            + ", target=" + target);
            boolean enabledResult = true;
            if (ensureAccessibilityEnabled) {
                enabledResult = Settings.Secure.putInt(
                        context.getContentResolver(),
                        Settings.Secure.ACCESSIBILITY_ENABLED,
                        1);
                Log.i(AccessibilityRestorer.LOG_TAG,
                        label + " accessibility_enabled putInt result=" + enabledResult);
            }

            long deadline = started + WRITE_TIMEOUT_MS;
            String readback = null;
            while (SystemClock.elapsedRealtime() <= deadline) {
                readback = Settings.Secure.getString(
                        context.getContentResolver(),
                        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
                long elapsed = SystemClock.elapsedRealtime() - started;
                Log.i(AccessibilityRestorer.LOG_TAG,
                        label + " readback elapsedMs=" + elapsed + ", value=" + value(readback));
                if (AccessibilityPlan.sameComponentSet(target, readback)) {
                    Log.i(AccessibilityRestorer.LOG_TAG,
                            label + " settings readback matched elapsedMs=" + elapsed
                                    + ", bindingVerification=UNAVAILABLE_IN_APP");
                    if (!putResult || !enabledResult) {
                        Log.e(AccessibilityRestorer.LOG_TAG,
                                label + " measurable write failure"
                                        + ", putString=" + putResult
                                        + ", putInt=" + enabledResult);
                        return false;
                    }
                    if (!sleep(settleMs, label + " framework settle")) {
                        return false;
                    }
                    Log.i(AccessibilityRestorer.LOG_TAG,
                            label + " settle complete settleMs=" + settleMs);
                    return true;
                }
                if (!sleep(WRITE_POLL_MS, label + " polling")) {
                    return false;
                }
            }
            Log.e(AccessibilityRestorer.LOG_TAG,
                    label + " TIMEOUT timeoutMs=" + WRITE_TIMEOUT_MS
                            + ", expected=" + target + ", lastReadback=" + value(readback));
            return false;
        } catch (SecurityException exception) {
            Log.e(AccessibilityRestorer.LOG_TAG,
                    label + " SecurityException",
                    exception);
            return false;
        } catch (RuntimeException exception) {
            Log.e(AccessibilityRestorer.LOG_TAG,
                    label + " RuntimeException",
                    exception);
            return false;
        }
    }

    static boolean isInteractive(Context context) {
        PowerManager powerManager =
                (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        return powerManager == null || powerManager.isInteractive();
    }

    private static void logSessionStart(Context context, String sessionId, String trigger) {
        AppVersion.Info version = AppVersion.read(context);
        boolean hasSecureSettings = context.getPackageManager().checkPermission(
                android.Manifest.permission.WRITE_SECURE_SETTINGS,
                context.getPackageName()) == PackageManager.PERMISSION_GRANTED;
        Log.i(AccessibilityRestorer.LOG_TAG,
                "SESSION START session=" + sessionId
                        + ", versionName=" + version.name
                        + ", versionCode=" + version.code
                        + ", trigger=" + trigger
                        + ", boot_count=" + BootSessionState.currentBootCount(context)
                        + ", elapsedRealtime=" + SystemClock.elapsedRealtime()
                        + ", isInteractive=" + isInteractive(context)
                        + ", WRITE_SECURE_SETTINGS=" + hasSecureSettings);
    }

    private static Outcome finishOutcome(
            String sessionId,
            String trigger,
            long wakeElapsed,
            long recoveryStartElapsed,
            long finalLaunchElapsed,
            boolean executionCompleted,
            String message) {
        long finishedElapsed = SystemClock.elapsedRealtime();
        long timingEndElapsed = finalLaunchElapsed >= 0L
                ? finalLaunchElapsed : finishedElapsed;
        Log.i(AccessibilityRestorer.LOG_TAG,
                "SESSION FINISH session=" + sessionId
                        + ", trigger=" + trigger
                        + ", executionCompleted=" + executionCompleted
                        + ", result=" + message
                        + ", wakeElapsed=" + wakeElapsed
                        + ", recoveryStartElapsed=" + recoveryStartElapsed
                        + ", finalLaunchElapsed=" + finalLaunchElapsed
                        + ", durationFromWakeMs=" + (timingEndElapsed - wakeElapsed)
                        + ", durationRecoveryMs="
                        + (timingEndElapsed - recoveryStartElapsed));
        return new Outcome(executionCompleted, message);
    }

    private static String failedStrMessage(String reason) {
        return "execution=FAILED"
                + ", settingsRecovery=false"
                + ", targetUnstop=false"
                + ", torrServeIncluded=false"
                + ", finalLaunch=false"
                + ", bindingVerification=UNAVAILABLE_IN_APP"
                + ", reason=" + reason;
    }

    private static String vpnFields(V2RayVpnAssist.Result vpn) {
        return ", vpnAssist=" + vpn.status
                + ", vpnTriggerSent=" + vpn.triggerSent
                + ", vpnDetected=" + vpn.vpnDetected
                + ", vpnElapsedMs=" + vpn.elapsedMs;
    }

    private static boolean sleep(long durationMs, String label) {
        try {
            Thread.sleep(durationMs);
            return true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            Log.e(AccessibilityRestorer.LOG_TAG,
                    label + " interrupted",
                    exception);
            return false;
        }
    }

    private static String value(String value) {
        return value == null ? "<null>" : value;
    }

    private static String mode(TargetUnstopper.Result result) {
        return result == null ? "NOT_RUN" : result.mode;
    }

    private enum TimingProfile {
        NORMAL_9S(
                "NORMAL_9S",
                NORMAL_ALL_INITIAL_SETTLE_MS,
                NORMAL_BASE_SETTLE_MS,
                NORMAL_MAPPER_SETTLE_MS,
                NORMAL_ALL_FINAL_SETTLE_MS),
        CONSERVATIVE(
                "CONSERVATIVE",
                CONSERVATIVE_ALL_INITIAL_SETTLE_MS,
                CONSERVATIVE_BASE_SETTLE_MS,
                CONSERVATIVE_MAPPER_SETTLE_MS,
                CONSERVATIVE_ALL_FINAL_SETTLE_MS);

        final String label;
        final long allInitialSettleMs;
        final long baseSettleMs;
        final long mapperSettleMs;
        final long allFinalSettleMs;

        TimingProfile(
                String label,
                long allInitialSettleMs,
                long baseSettleMs,
                long mapperSettleMs,
                long allFinalSettleMs) {
            this.label = label;
            this.allInitialSettleMs = allInitialSettleMs;
            this.baseSettleMs = baseSettleMs;
            this.mapperSettleMs = mapperSettleMs;
            this.allFinalSettleMs = allFinalSettleMs;
        }
    }

    private static final class Outcome {
        final boolean executionCompleted;
        final String message;

        Outcome(boolean executionCompleted, String message) {
            this.executionCompleted = executionCompleted;
            this.message = message;
        }
    }
}
