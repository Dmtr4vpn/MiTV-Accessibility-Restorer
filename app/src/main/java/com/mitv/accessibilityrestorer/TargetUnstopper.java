package com.mitv.accessibilityrestorer;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.SystemClock;
import android.util.Log;

final class TargetUnstopper {
    static final String ACTION_UNSTOP_TARGET =
            "com.mitv.accessibilityrestorer.action.UNSTOP_TARGET";
    static final long STOPPED_POLL_MS = 200L;
    static final long STOPPED_TIMEOUT_MS = 3_000L;

    private TargetUnstopper() {
    }

    static Result unstopMapper(Context context, TargetApps.Status targets) {
        ComponentName[] receivers = new ComponentName[] {
                RecoveryTargets.MAPPER_UNSTOP_PRIMARY,
                RecoveryTargets.MAPPER_UNSTOP_SECONDARY
        };
        Result invisible = unstopWithReceivers(
                context,
                "MAPPER",
                RecoveryTargets.MAPPER_PACKAGE,
                targets.mapperPackage,
                receivers);
        if (invisible.success) {
            return invisible;
        }

        Log.w(AccessibilityRestorer.LOG_TAG,
                "UNSTOP MAPPER VISIBLE FALLBACK REQUIRED component="
                        + RecoveryTargets.MAPPER_ACTIVITY.flattenToString());
        boolean hadCover = RecoveryCoverActivity.isActive();
        boolean launched = TargetApps.startMapper(
                context, targets, "MAPPER VISIBLE FALLBACK REQUIRED");
        if (hadCover) {
            RecoveryCoverActivity.show(context, "MAPPER visible fallback re-cover");
        }
        StopState after = pollUntilRunning(context, RecoveryTargets.MAPPER_PACKAGE);
        return new Result(
                launched && after.known && !after.stopped,
                invisible.beforeStopped,
                after.known && after.stopped,
                RecoveryTargets.MAPPER_ACTIVITY,
                "VISIBLE_FALLBACK",
                true);
    }

    static Result unstopProjectivy(Context context, TargetApps.Status targets) {
        Result invisible = unstopWithReceivers(
                context,
                "PROJECTIVY",
                RecoveryTargets.PROJECTIVY_PACKAGE,
                targets.projectivyPackage,
                new ComponentName[] {RecoveryTargets.PROJECTIVY_UNSTOP_RECEIVER});
        if (invisible.success) {
            return invisible;
        }

        Log.w(AccessibilityRestorer.LOG_TAG,
                "UNSTOP PROJECTIVY VISIBLE FALLBACK REQUIRED component="
                        + RecoveryTargets.PROJECTIVY_ACTIVITY.flattenToString());
        boolean hadCover = RecoveryCoverActivity.isActive();
        boolean launched = TargetApps.startProjectivy(
                context, targets, "PROJECTIVY VISIBLE FALLBACK REQUIRED");
        if (hadCover) {
            RecoveryCoverActivity.show(context, "PROJECTIVY visible fallback re-cover");
        }
        StopState after = pollUntilRunning(context, RecoveryTargets.PROJECTIVY_PACKAGE);
        return new Result(
                launched && after.known && !after.stopped,
                invisible.beforeStopped,
                after.known && after.stopped,
                RecoveryTargets.PROJECTIVY_ACTIVITY,
                "VISIBLE_FALLBACK",
                true);
    }

    private static Result unstopWithReceivers(
            Context context,
            String label,
            String packageName,
            boolean packageAvailable,
            ComponentName[] receivers) {
        long started = SystemClock.elapsedRealtime();
        if (!packageAvailable) {
            Log.e(AccessibilityRestorer.LOG_TAG,
                    "UNSTOP " + label + " failed: package unavailable " + packageName);
            return new Result(false, false, false, null, "PACKAGE_MISSING", false);
        }

        StopState before = readStopped(context, packageName);
        Log.i(AccessibilityRestorer.LOG_TAG,
                "UNSTOP " + label
                        + " beforeStopped=" + stateValue(before)
                        + ", check=ApplicationInfo.FLAG_STOPPED"
                        + ", pollMs=" + STOPPED_POLL_MS
                        + ", timeoutMs=" + STOPPED_TIMEOUT_MS);
        if (before.known && !before.stopped) {
            Log.i(AccessibilityRestorer.LOG_TAG,
                    "UNSTOP " + label
                            + " afterStopped=false, elapsedMs="
                            + (SystemClock.elapsedRealtime() - started)
                            + ", mode=INVISIBLE_ALREADY_RUNNING");
            return new Result(
                    true, false, false, null, "INVISIBLE_ALREADY_RUNNING", false);
        }
        if (!before.known) {
            return new Result(false, false, false, null, "STOPPED_STATE_UNKNOWN", false);
        }

        for (ComponentName receiver : receivers) {
            if (!isExportedReceiver(context, receiver)) {
                Log.w(AccessibilityRestorer.LOG_TAG,
                        "UNSTOP " + label + " receiver unavailable/not exported component="
                                + receiver.flattenToString());
                continue;
            }

            Intent intent = new Intent(ACTION_UNSTOP_TARGET);
            intent.setComponent(receiver);
            intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
            boolean sent = false;
            try {
                context.sendBroadcast(intent);
                sent = true;
                Log.i(AccessibilityRestorer.LOG_TAG,
                        "UNSTOP " + label
                                + " component=" + receiver.flattenToString()
                                + ", action=" + ACTION_UNSTOP_TARGET
                                + ", flag=FLAG_INCLUDE_STOPPED_PACKAGES"
                                + ", broadcastResult=SENT");
            } catch (SecurityException exception) {
                Log.e(AccessibilityRestorer.LOG_TAG,
                        "UNSTOP " + label + " broadcast SecurityException component="
                                + receiver.flattenToString(),
                        exception);
            } catch (RuntimeException exception) {
                Log.e(AccessibilityRestorer.LOG_TAG,
                        "UNSTOP " + label + " broadcast failed component="
                                + receiver.flattenToString(),
                        exception);
            }
            if (!sent) {
                continue;
            }

            StopState after = pollUntilRunning(context, packageName);
            long elapsed = SystemClock.elapsedRealtime() - started;
            Log.i(AccessibilityRestorer.LOG_TAG,
                    "UNSTOP " + label
                            + " component=" + receiver.flattenToString()
                            + ", beforeStopped=true"
                            + ", afterStopped=" + stateValue(after)
                            + ", elapsedMs=" + elapsed
                            + ", mode=INVISIBLE");
            if (after.known && !after.stopped) {
                return new Result(
                        true, true, false, receiver, "INVISIBLE_SUCCESS", false);
            }
        }
        return new Result(false, true, true, null, "INVISIBLE_FAILED", false);
    }

    private static StopState pollUntilRunning(Context context, String packageName) {
        long started = SystemClock.elapsedRealtime();
        long deadline = started + STOPPED_TIMEOUT_MS;
        StopState state = readStopped(context, packageName);
        while (state.known && state.stopped
                && SystemClock.elapsedRealtime() <= deadline) {
            if (!sleep(STOPPED_POLL_MS)) {
                break;
            }
            state = readStopped(context, packageName);
        }
        Log.i(AccessibilityRestorer.LOG_TAG,
                "UNSTOP stopped-state readback package=" + packageName
                        + ", afterStopped=" + stateValue(state)
                        + ", elapsedMs=" + (SystemClock.elapsedRealtime() - started));
        return state;
    }

    private static StopState readStopped(Context context, String packageName) {
        try {
            ApplicationInfo info = context.getPackageManager()
                    .getApplicationInfo(packageName, 0);
            return new StopState(true, (info.flags & ApplicationInfo.FLAG_STOPPED) != 0);
        } catch (PackageManager.NameNotFoundException exception) {
            Log.e(AccessibilityRestorer.LOG_TAG,
                    "UNSTOP package missing while reading FLAG_STOPPED package=" + packageName);
            return new StopState(false, false);
        } catch (RuntimeException exception) {
            Log.e(AccessibilityRestorer.LOG_TAG,
                    "UNSTOP FLAG_STOPPED read failed package=" + packageName,
                    exception);
            return new StopState(false, false);
        }
    }

    private static boolean isExportedReceiver(Context context, ComponentName component) {
        try {
            ActivityInfo info = context.getPackageManager().getReceiverInfo(component, 0);
            return info != null && info.exported;
        } catch (PackageManager.NameNotFoundException exception) {
            return false;
        } catch (RuntimeException exception) {
            Log.e(AccessibilityRestorer.LOG_TAG,
                    "UNSTOP receiver check failed component=" + component.flattenToString(),
                    exception);
            return false;
        }
    }

    private static boolean sleep(long durationMs) {
        try {
            Thread.sleep(durationMs);
            return true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            Log.e(AccessibilityRestorer.LOG_TAG,
                    "UNSTOP polling interrupted",
                    exception);
            return false;
        }
    }

    private static String stateValue(StopState state) {
        return state.known ? Boolean.toString(state.stopped) : "unknown";
    }

    static final class Result {
        final boolean success;
        final boolean beforeStopped;
        final boolean afterStopped;
        final ComponentName component;
        final String mode;
        final boolean visibleFallback;

        Result(
                boolean success,
                boolean beforeStopped,
                boolean afterStopped,
                ComponentName component,
                String mode,
                boolean visibleFallback) {
            this.success = success;
            this.beforeStopped = beforeStopped;
            this.afterStopped = afterStopped;
            this.component = component;
            this.mode = mode;
            this.visibleFallback = visibleFallback;
        }
    }

    private static final class StopState {
        final boolean known;
        final boolean stopped;

        StopState(boolean known, boolean stopped) {
            this.known = known;
            this.stopped = stopped;
        }
    }
}
