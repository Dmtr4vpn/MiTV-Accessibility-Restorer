package com.mitv.accessibilityrestorer;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.SystemClock;
import android.util.Log;

import java.util.concurrent.atomic.AtomicReference;

final class V2RayVpnAssist {
    static final String PACKAGE_NAME = "com.v2raytun.android";
    static final String ACTION_WIDGET_CLICK =
            "com.v2raytun.android.action.widget.click";
    static final String WIDGET_RECEIVER_FLAT =
            "com.v2raytun.android/.receiver.WidgetProvider1x1";
    static final ComponentName WIDGET_RECEIVER = component(WIDGET_RECEIVER_FLAT);

    static final long RUNNING_GRACE_MS = 2_000L;
    static final long VPN_POLL_MS = 250L;
    static final long VPN_TIMEOUT_MS = 5_000L;

    private V2RayVpnAssist() {
    }

    static Handle start(Context context, final String sessionId) {
        final Context appContext = context.getApplicationContext() == null
                ? context : context.getApplicationContext();
        final Handle handle = new Handle(SystemClock.elapsedRealtime());
        try {
            Thread worker = new Thread(new Runnable() {
                @Override
                public void run() {
                    Result result;
                    try {
                        result = V2RayVpnAssist.run(appContext);
                    } catch (RuntimeException exception) {
                        Log.e(AccessibilityRestorer.LOG_TAG,
                                "V2RAY unhandled VPN assist failure session=" + sessionId,
                                exception);
                        result = result(
                                Status.TRIGGER_FAILED,
                                false,
                                false,
                                handle.startedElapsed);
                    }
                    handle.complete(result, sessionId);
                }
            }, "MiTVRestorer-VPN");
            worker.start();
        } catch (RuntimeException exception) {
            Log.e(AccessibilityRestorer.LOG_TAG,
                    "V2RAY could not start VPN assist thread session=" + sessionId,
                    exception);
            handle.complete(
                    result(Status.TRIGGER_FAILED, false, false, handle.startedElapsed),
                    sessionId);
        }
        return handle;
    }

    static boolean isPackageInstalled(Context context) {
        return readPackageState(context).installed;
    }

    static boolean isVpnActive(Context context) {
        VpnState state = readVpnState(context);
        return state.known && state.active;
    }

    private static Result run(Context context) {
        long started = SystemClock.elapsedRealtime();
        PackageState packageState = readPackageState(context);
        Log.i(AccessibilityRestorer.LOG_TAG,
                "V2RAY packageInstalled=" + stateValue(
                        packageState.packageKnown, packageState.installed));
        if (!packageState.packageKnown) {
            Log.e(AccessibilityRestorer.LOG_TAG,
                    "V2RAY VPN trigger decision=TRIGGER_FAILED"
                            + " reason=package_state_unknown");
            return result(Status.TRIGGER_FAILED, false, false, started);
        }
        if (!packageState.installed) {
            Log.i(AccessibilityRestorer.LOG_TAG,
                    "V2RAY VPN trigger decision=SKIPPED_PACKAGE_MISSING");
            return result(
                    Status.SKIPPED_PACKAGE_MISSING, false, false, started);
        }

        Log.i(AccessibilityRestorer.LOG_TAG,
                "V2RAY stoppedBefore=" + stateValue(
                        packageState.stoppedKnown, packageState.stopped));
        Log.i(AccessibilityRestorer.LOG_TAG,
                "VPN detection check=NetworkCapabilities.TRANSPORT_VPN");
        VpnState before = readVpnState(context);
        Log.i(AccessibilityRestorer.LOG_TAG,
                "VPN BEFORE active=" + stateValue(before.known, before.active));
        if (!before.known) {
            Log.e(AccessibilityRestorer.LOG_TAG,
                    "V2RAY VPN trigger decision=TRIGGER_FAILED reason=vpn_state_unknown");
            return result(Status.TRIGGER_FAILED, false, false, started);
        }
        if (before.active) {
            Log.i(AccessibilityRestorer.LOG_TAG,
                    "V2RAY VPN trigger decision=VPN_ALREADY_ACTIVE");
            return result(Status.ALREADY_ACTIVE, false, true, started);
        }

        if (!packageState.stoppedKnown) {
            Log.e(AccessibilityRestorer.LOG_TAG,
                    "V2RAY VPN trigger decision=TRIGGER_FAILED reason=stopped_state_unknown");
            return result(Status.TRIGGER_FAILED, false, false, started);
        }

        if (!packageState.stopped) {
            Log.i(AccessibilityRestorer.LOG_TAG,
                    "V2RAY VPN trigger decision=GRACE_ALREADY_RUNNING"
                            + ", graceMs=" + RUNNING_GRACE_MS);
            if (!sleep(RUNNING_GRACE_MS, "V2RAY running grace")) {
                return result(Status.TRIGGER_FAILED, false, false, started);
            }

            VpnState afterGrace = readVpnState(context);
            Log.i(AccessibilityRestorer.LOG_TAG,
                    "VPN AFTER GRACE active="
                            + stateValue(afterGrace.known, afterGrace.active));
            if (!afterGrace.known) {
                return result(Status.TRIGGER_FAILED, false, false, started);
            }
            if (afterGrace.active) {
                Log.i(AccessibilityRestorer.LOG_TAG,
                        "V2RAY VPN trigger decision=SKIPPED_ALREADY_RUNNING_GRACE");
                return result(
                        Status.SKIPPED_ALREADY_RUNNING_GRACE,
                        false,
                        true,
                        started);
            }

            PackageState afterGracePackage = readPackageState(context);
            Log.i(AccessibilityRestorer.LOG_TAG,
                    "V2RAY stoppedAfterGrace=" + stateValue(
                            afterGracePackage.stoppedKnown,
                            afterGracePackage.stopped));
            if (!afterGracePackage.packageKnown
                    || !afterGracePackage.installed
                    || !afterGracePackage.stoppedKnown) {
                return result(Status.TRIGGER_FAILED, false, false, started);
            }
        }

        VpnState immediatelyBeforeTrigger = readVpnState(context);
        Log.i(AccessibilityRestorer.LOG_TAG,
                "VPN IMMEDIATELY BEFORE TRIGGER active=" + stateValue(
                        immediatelyBeforeTrigger.known,
                        immediatelyBeforeTrigger.active));
        if (!immediatelyBeforeTrigger.known) {
            return result(Status.TRIGGER_FAILED, false, false, started);
        }
        if (immediatelyBeforeTrigger.active) {
            Log.i(AccessibilityRestorer.LOG_TAG,
                    "V2RAY VPN trigger decision=VPN_ALREADY_ACTIVE");
            return result(Status.ALREADY_ACTIVE, false, true, started);
        }

        Log.i(AccessibilityRestorer.LOG_TAG,
                "V2RAY VPN trigger decision=SEND_ONCE"
                        + ", stopped=" + packageState.stopped);
        if (!sendTrigger(context)) {
            return result(Status.TRIGGER_FAILED, false, false, started);
        }

        long pollStarted = SystemClock.elapsedRealtime();
        long deadline = pollStarted + VPN_TIMEOUT_MS;
        while (SystemClock.elapsedRealtime() <= deadline) {
            VpnState current = readVpnState(context);
            long elapsed = SystemClock.elapsedRealtime() - started;
            Log.i(AccessibilityRestorer.LOG_TAG,
                    "V2RAY VPN poll active=" + stateValue(current.known, current.active)
                            + ", elapsedMs=" + elapsed);
            if (current.known && current.active) {
                Log.i(AccessibilityRestorer.LOG_TAG,
                        "VPN AFTER active=true, elapsedMs=" + elapsed);
                return result(Status.CONNECTED, true, true, started);
            }
            if (!sleep(VPN_POLL_MS, "V2RAY VPN polling")) {
                return result(Status.TRIGGER_FAILED, true, false, started);
            }
        }

        Log.w(AccessibilityRestorer.LOG_TAG,
                "VPN AFTER active=false, timeoutMs=" + VPN_TIMEOUT_MS
                        + ", elapsedMs=" + (SystemClock.elapsedRealtime() - started));
        return result(Status.TIMEOUT, true, false, started);
    }

    private static boolean sendTrigger(Context context) {
        Intent intent = new Intent(ACTION_WIDGET_CLICK);
        intent.setComponent(WIDGET_RECEIVER);
        intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        Log.i(AccessibilityRestorer.LOG_TAG,
                "V2RAY trigger component=" + WIDGET_RECEIVER.flattenToShortString()
                        + ", action=" + ACTION_WIDGET_CLICK
                        + ", flag=FLAG_INCLUDE_STOPPED_PACKAGES");
        try {
            context.sendBroadcast(intent);
            Log.i(AccessibilityRestorer.LOG_TAG,
                    "V2RAY broadcast sent/result=SENT");
            return true;
        } catch (SecurityException exception) {
            Log.e(AccessibilityRestorer.LOG_TAG,
                    "V2RAY broadcast sent/result=SECURITY_EXCEPTION",
                    exception);
            return false;
        } catch (RuntimeException exception) {
            Log.e(AccessibilityRestorer.LOG_TAG,
                    "V2RAY broadcast sent/result=FAILED",
                    exception);
            return false;
        }
    }

    private static PackageState readPackageState(Context context) {
        try {
            ApplicationInfo info = context.getPackageManager()
                    .getApplicationInfo(PACKAGE_NAME, 0);
            return new PackageState(
                    true,
                    true,
                    true,
                    (info.flags & ApplicationInfo.FLAG_STOPPED) != 0);
        } catch (PackageManager.NameNotFoundException exception) {
            return new PackageState(true, false, false, false);
        } catch (RuntimeException exception) {
            Log.e(AccessibilityRestorer.LOG_TAG,
                    "V2RAY package/FLAG_STOPPED check failed",
                    exception);
            return new PackageState(false, false, false, false);
        }
    }

    private static VpnState readVpnState(Context context) {
        try {
            ConnectivityManager manager = (ConnectivityManager) context.getSystemService(
                    Context.CONNECTIVITY_SERVICE);
            if (manager == null) {
                Log.e(AccessibilityRestorer.LOG_TAG,
                        "V2RAY ConnectivityManager unavailable");
                return new VpnState(false, false);
            }
            Network[] networks = manager.getAllNetworks();
            if (networks == null) {
                return new VpnState(true, false);
            }
            for (Network network : networks) {
                NetworkCapabilities capabilities = manager.getNetworkCapabilities(network);
                if (capabilities != null
                        && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                    return new VpnState(true, true);
                }
            }
            return new VpnState(true, false);
        } catch (SecurityException exception) {
            Log.e(AccessibilityRestorer.LOG_TAG,
                    "V2RAY VPN detection denied; ACCESS_NETWORK_STATE unavailable",
                    exception);
            return new VpnState(false, false);
        } catch (RuntimeException exception) {
            Log.e(AccessibilityRestorer.LOG_TAG,
                    "V2RAY VPN detection failed",
                    exception);
            return new VpnState(false, false);
        }
    }

    private static ComponentName component(String flattened) {
        ComponentName component = ComponentName.unflattenFromString(flattened);
        if (component == null) {
            throw new IllegalArgumentException("Invalid component: " + flattened);
        }
        return component;
    }

    private static Result result(
            Status status,
            boolean triggerSent,
            boolean vpnDetected,
            long startedElapsed) {
        return new Result(
                status,
                triggerSent,
                vpnDetected,
                SystemClock.elapsedRealtime() - startedElapsed);
    }

    private static boolean sleep(long durationMs, String label) {
        try {
            Thread.sleep(durationMs);
            return true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            Log.e(AccessibilityRestorer.LOG_TAG, label + " interrupted", exception);
            return false;
        }
    }

    private static String stateValue(boolean known, boolean value) {
        return known ? Boolean.toString(value) : "unknown";
    }

    enum Status {
        IN_PROGRESS,
        CONNECTED,
        TIMEOUT,
        ALREADY_ACTIVE,
        SKIPPED_PACKAGE_MISSING,
        SKIPPED_ALREADY_RUNNING_GRACE,
        TRIGGER_FAILED
    }

    static final class Handle {
        private final AtomicReference<Result> result =
                new AtomicReference<Result>();
        private final long startedElapsed;

        Handle(long startedElapsed) {
            this.startedElapsed = startedElapsed;
        }

        Result snapshot() {
            Result current = result.get();
            if (current != null) {
                return current;
            }
            return new Result(
                    Status.IN_PROGRESS,
                    false,
                    false,
                    SystemClock.elapsedRealtime() - startedElapsed);
        }

        void complete(Result completed, String sessionId) {
            result.set(completed);
            Log.i(AccessibilityRestorer.LOG_TAG,
                    "V2RAY final vpnAssist status=" + completed.status
                            + ", vpnTriggerSent=" + completed.triggerSent
                            + ", vpnDetected=" + completed.vpnDetected
                            + ", vpnElapsedMs=" + completed.elapsedMs
                            + ", session=" + sessionId);
        }
    }

    static final class Result {
        final Status status;
        final boolean triggerSent;
        final boolean vpnDetected;
        final long elapsedMs;

        Result(Status status, boolean triggerSent, boolean vpnDetected, long elapsedMs) {
            this.status = status;
            this.triggerSent = triggerSent;
            this.vpnDetected = vpnDetected;
            this.elapsedMs = elapsedMs;
        }
    }

    private static final class PackageState {
        final boolean packageKnown;
        final boolean installed;
        final boolean stoppedKnown;
        final boolean stopped;

        PackageState(
                boolean packageKnown,
                boolean installed,
                boolean stoppedKnown,
                boolean stopped) {
            this.packageKnown = packageKnown;
            this.installed = installed;
            this.stoppedKnown = stoppedKnown;
            this.stopped = stopped;
        }
    }

    private static final class VpnState {
        final boolean known;
        final boolean active;

        VpnState(boolean known, boolean active) {
            this.known = known;
            this.active = active;
        }
    }
}
