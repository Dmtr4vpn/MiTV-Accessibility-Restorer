package com.mitv.accessibilityrestorer;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;

import java.util.Set;

public final class MainActivity extends Activity {
    private static final String ACTION_STR_BOOT_COMPLETED = "mitv.action.STR_BOOT_COMPLETED";
    private static final long MAX_INTERACTIVE_WAIT_MS = 600_000L;

    private final Handler handler = new Handler();
    private final Runnable interactivePoll = new Runnable() {
        @Override
        public void run() {
            evaluateTrigger("IS_INTERACTIVE_POLL");
        }
    };
    private final BroadcastReceiver wakeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent == null ? null : intent.getAction();
            String trigger = Intent.ACTION_SCREEN_ON.equals(action)
                    ? "SCREEN_ON" : "STR_BOOT_COMPLETED";
            strRecoveryRequired = true;
            Log.i(AccessibilityRestorer.LOG_TAG,
                    "TRIGGER broadcast received action=" + action);
            evaluateTrigger(trigger);
        }
    };

    private boolean receiverRegistered;
    private boolean recoveryScheduled;
    private boolean strCoverRequested;
    private boolean strRecoveryRequired;
    private long createdElapsed;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        createdElapsed = SystemClock.elapsedRealtime();
        logIntent("MAIN_ACTIVITY onCreate", getIntent());
        registerWakeReceiver();
        evaluateTrigger("MAIN_ACTIVITY");
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        logIntent("MAIN_ACTIVITY onNewIntent", intent);
        evaluateTrigger("MAIN_ACTIVITY_NEW_INTENT");
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (receiverRegistered) {
            try {
                unregisterReceiver(wakeReceiver);
            } catch (RuntimeException exception) {
                Log.w(AccessibilityRestorer.LOG_TAG,
                        "Wake receiver unregister failed",
                        exception);
            }
            receiverRegistered = false;
        }
        super.onDestroy();
    }

    private void registerWakeReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(ACTION_STR_BOOT_COMPLETED);
        try {
            registerReceiver(wakeReceiver, filter);
            receiverRegistered = true;
            Log.i(AccessibilityRestorer.LOG_TAG,
                    "Wake receiver registered for SCREEN_ON and " + ACTION_STR_BOOT_COMPLETED);
        } catch (RuntimeException exception) {
            Log.e(AccessibilityRestorer.LOG_TAG,
                    "Wake receiver registration failed; isInteractive polling remains active",
                    exception);
        }
    }

    private void evaluateTrigger(final String trigger) {
        if (isFinishing() || recoveryScheduled) {
            Log.i(AccessibilityRestorer.LOG_TAG,
                    "TRIGGER ignored locally trigger=" + trigger
                            + ", finishing=" + isFinishing()
                            + ", recoveryScheduled=" + recoveryScheduled);
            return;
        }

        RecoveryState.MainDecision decision = RecoveryState.evaluateMainLaunch(this);
        boolean interactive = RecoveryEngine.isInteractive(this);
        Log.i(AccessibilityRestorer.LOG_TAG,
                "MAIN DECISION trigger=" + trigger
                        + ", mode=" + decision.mode
                        + ", currentBootCount=" + decision.currentBootCount
                        + ", lastCompletedBootCount=" + decision.completedBootCount
                        + ", elapsedRealtime=" + decision.elapsedRealtime
                        + ", isInteractive=" + interactive
                        + ", reason=" + decision.reason);

        if (decision.mode == RecoveryState.MainMode.BOOT_PENDING) {
            Log.i(AccessibilityRestorer.LOG_TAG,
                    "EARLY BOOT candidate currentBootCount=" + decision.currentBootCount
                            + ", lastCompletedBootCount=" + decision.completedBootCount
                            + ", isInteractive=" + interactive
                            + ", elapsedRealtime=" + decision.elapsedRealtime
                            + ", reason=" + decision.reason
                            + ", EARLY_BOOT_SETTLE_MS="
                            + RecoveryEngine.EARLY_BOOT_SETTLE_MS);
            if (!hasAdvancedBootCount(decision)) {
                Log.i(AccessibilityRestorer.LOG_TAG,
                        "EARLY BOOT skipped for now: BOOT_COUNT is unavailable or has not advanced; "
                                + "stale LOCKED_BOOT_COMPLETED session cannot start recovery");
                waitForStateChange(trigger, interactive);
                return;
            }
            if (!interactive) {
                Log.i(AccessibilityRestorer.LOG_TAG,
                        "EARLY BOOT waiting: screen is not interactive");
                waitForStateChange(trigger, false);
                return;
            }
            scheduleEarlyBoot(trigger);
            return;
        }

        if (decision.mode != RecoveryState.MainMode.STR_READY) {
            Log.i(AccessibilityRestorer.LOG_TAG,
                    "Recovery not started because mode=" + decision.mode);
            RecoveryCoverActivity.finishCover("MainActivity mode=" + decision.mode);
            finish();
            return;
        }

        if (!interactive) {
            strRecoveryRequired = true;
            prepareStrCover(trigger);
            waitForStateChange(trigger, false);
            return;
        }

        if (!strRecoveryRequired && !RecoveryState.hasActiveSession(this)) {
            routeToControlActivity(trigger, decision);
            return;
        }

        prepareStrCover(trigger);
        scheduleStrRecovery(trigger);
    }

    private void routeToControlActivity(
            String trigger, RecoveryState.MainDecision decision) {
        Log.i(AccessibilityRestorer.LOG_TAG,
                "MAIN DECISION trigger=" + trigger
                        + ", mode=USER_UI"
                        + ", currentBootCount=" + decision.currentBootCount
                        + ", lastCompletedBootCount=" + decision.completedBootCount
                        + ", elapsedRealtime=" + decision.elapsedRealtime
                        + ", isInteractive=true"
                        + ", reason=no_recovery_required");
        Log.i(AccessibilityRestorer.LOG_TAG, "MAIN ROUTE -> ControlActivity");
        try {
            startActivity(new Intent(this, ControlActivity.class));
        } catch (RuntimeException exception) {
            Log.e(AccessibilityRestorer.LOG_TAG,
                    "MAIN ROUTE ControlActivity failed",
                    exception);
        }
        finish();
    }

    private static boolean hasAdvancedBootCount(RecoveryState.MainDecision decision) {
        return decision.currentBootCount >= 0
                && decision.currentBootCount != decision.completedBootCount;
    }

    private void waitForStateChange(String trigger, boolean interactive) {
        long waitingMs = SystemClock.elapsedRealtime() - createdElapsed;
        if (waitingMs >= MAX_INTERACTIVE_WAIT_MS) {
            Log.e(AccessibilityRestorer.LOG_TAG,
                    "Interactive/BOOT_COUNT wait timeout trigger=" + trigger
                            + ", elapsedMs=" + waitingMs);
            RecoveryCoverActivity.finishCover("MainActivity wait timeout");
            finish();
            return;
        }
        Log.i(AccessibilityRestorer.LOG_TAG,
                "Waiting for wake or current BOOT_COUNT trigger=" + trigger
                        + ", isInteractive=" + interactive
                        + ", elapsedMs=" + waitingMs
                        + ", pollMs=" + RecoveryEngine.INTERACTIVE_POLL_MS);
        handler.removeCallbacks(interactivePoll);
        handler.postDelayed(interactivePoll, RecoveryEngine.INTERACTIVE_POLL_MS);
    }

    private void prepareStrCover(String trigger) {
        if (strCoverRequested) {
            if (RecoveryEngine.isInteractive(this)) {
                boolean reasserted = RecoveryCoverActivity.show(
                        this, trigger + " ensure top on wake");
                Log.i(AccessibilityRestorer.LOG_TAG,
                        "STR COVER wake reassert result=" + reasserted
                                + ", trigger=" + trigger);
            }
            return;
        }
        strCoverRequested = true;
        boolean requested = RecoveryCoverActivity.show(this, trigger);
        Log.i(AccessibilityRestorer.LOG_TAG,
                "STR COVER request result=" + requested
                        + ", trigger=" + trigger
                        + ", isInteractive=" + RecoveryEngine.isInteractive(this));
    }

    private void scheduleEarlyBoot(final String trigger) {
        recoveryScheduled = true;
        handler.removeCallbacks(interactivePoll);
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!RecoveryEngine.isInteractive(MainActivity.this)) {
                    recoveryScheduled = false;
                    Log.i(AccessibilityRestorer.LOG_TAG,
                            "EARLY BOOT skipped: interactive state lost before start");
                    evaluateTrigger("EARLY_BOOT_INTERACTIVE_LOST");
                    return;
                }

                RecoveryState.MainDecision recheck =
                        RecoveryState.evaluateMainLaunch(MainActivity.this);
                if (recheck.mode != RecoveryState.MainMode.BOOT_PENDING
                        || !hasAdvancedBootCount(recheck)) {
                    recoveryScheduled = false;
                    Log.i(AccessibilityRestorer.LOG_TAG,
                            "EARLY BOOT skipped after settle: mode=" + recheck.mode
                                    + ", currentBootCount=" + recheck.currentBootCount
                                    + ", lastCompletedBootCount=" + recheck.completedBootCount
                                    + ", reason=" + recheck.reason);
                    evaluateTrigger("EARLY_BOOT_SETTLE_RECHECK");
                    return;
                }

                boolean started = RecoveryEngine.startEarlyColdBootRecovery(
                        getApplicationContext(),
                        trigger,
                        recheck.currentBootCount,
                        new RecoveryEngine.Callback() {
                            @Override
                            public void onFinished(boolean success, String message) {
                                Log.i(AccessibilityRestorer.LOG_TAG,
                                        "MainActivity early boot callback success=" + success
                                                + ", message=" + message);
                                finish();
                            }
                        });
                if (!started) {
                    Log.i(AccessibilityRestorer.LOG_TAG,
                            "EARLY BOOT fallback retained because early worker did not start");
                    finish();
                }
            }
        }, RecoveryEngine.EARLY_BOOT_SETTLE_MS);
        Log.i(AccessibilityRestorer.LOG_TAG,
                "EARLY BOOT scheduled settleMs=" + RecoveryEngine.EARLY_BOOT_SETTLE_MS
                        + ", trigger=" + trigger
                        + ", fallback retained until successful completion");
    }

    private void scheduleStrRecovery(final String trigger) {
        final long wakeElapsed = SystemClock.elapsedRealtime();
        recoveryScheduled = true;
        handler.removeCallbacks(interactivePoll);
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!RecoveryEngine.isInteractive(MainActivity.this)) {
                    recoveryScheduled = false;
                    evaluateTrigger("INTERACTIVE_LOST_BEFORE_START");
                    return;
                }
                boolean started = RecoveryEngine.startStrRecovery(
                        getApplicationContext(),
                        trigger,
                        false,
                        wakeElapsed,
                        new RecoveryEngine.Callback() {
                            @Override
                            public void onFinished(
                                    boolean executionCompleted, String message) {
                                Log.i(AccessibilityRestorer.LOG_TAG,
                                        "MainActivity recovery callback executionCompleted="
                                                + executionCompleted
                                                + ", message=" + message);
                                RecoveryCoverActivity.finishCover(
                                        "MainActivity recovery callback executionCompleted="
                                                + executionCompleted);
                                finish();
                            }
                        });
                if (!started) {
                    RecoveryCoverActivity.finishCover(
                            "STR worker did not start");
                    finish();
                }
            }
        }, RecoveryEngine.STR_INTERACTIVE_SETTLE_MS);
        Log.i(AccessibilityRestorer.LOG_TAG,
                "STR recovery scheduled after interactive settleMs="
                        + RecoveryEngine.STR_INTERACTIVE_SETTLE_MS
                        + ", wakeElapsed=" + wakeElapsed
                        + ", trigger=" + trigger);
    }

    private static void logIntent(String label, Intent intent) {
        if (intent == null) {
            Log.i(AccessibilityRestorer.LOG_TAG, label + " intent=<null>");
            return;
        }
        Set<String> categories = intent.getCategories();
        Log.i(AccessibilityRestorer.LOG_TAG,
                label + " action=" + intent.getAction()
                        + ", categories=" + (categories == null ? "[]" : categories.toString())
                        + ", flags=0x" + Integer.toHexString(intent.getFlags()));
    }
}
