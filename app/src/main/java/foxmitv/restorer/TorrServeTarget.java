package foxmitv.restorer;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.provider.Settings;
import android.util.Log;

final class TorrServeTarget {
    static final String PACKAGE_NAME = "ru.yourok.torrserve";
    static final String ACCESSIBILITY_SERVICE_FLAT =
            "ru.yourok.torrserve/ru.yourok.torrserve.server.local.services.GlobalTorrService";
    static final ComponentName ACCESSIBILITY_SERVICE =
            component(ACCESSIBILITY_SERVICE_FLAT);

    private TorrServeTarget() {
    }

    static Status inspect(Context context) {
        PackageManager packageManager = context.getPackageManager();
        boolean packageInstalled = packageExists(packageManager);
        boolean accessibilityTargetAvailable = packageInstalled
                && serviceExists(packageManager);
        boolean includedInAllFinal = accessibilityTargetAvailable;

        Log.i(AccessibilityRestorer.LOG_TAG,
                "TORRSERVE packageInstalled=" + packageInstalled);
        Log.i(AccessibilityRestorer.LOG_TAG,
                "TORRSERVE accessibilityTargetAvailable="
                        + accessibilityTargetAvailable);
        Log.i(AccessibilityRestorer.LOG_TAG,
                "TORRSERVE accessibilitySkipped="
                        + !accessibilityTargetAvailable);
        Log.i(AccessibilityRestorer.LOG_TAG,
                "TORRSERVE includedInAllInitial=false");
        Log.i(AccessibilityRestorer.LOG_TAG,
                "TORRSERVE includedInAllFinal=" + includedInAllFinal);
        Log.i(AccessibilityRestorer.LOG_TAG,
                "TORRSERVE expectedAutoStartViaAccessibility="
                        + includedInAllFinal);

        return new Status(packageInstalled, accessibilityTargetAvailable);
    }

    static boolean isPackageInstalled(Context context) {
        return packageExists(context.getPackageManager());
    }

    static RecoveryResult recoverAccessibility(Context context) {
        Status target = inspect(context);
        Log.i(AccessibilityRestorer.LOG_TAG,
                "OPTIONAL TorrServe installed=" + target.packageInstalled);
        Log.i(AccessibilityRestorer.LOG_TAG,
                "OPTIONAL TorrServe componentAvailable="
                        + target.accessibilityTargetAvailable);
        if (!target.packageInstalled) {
            return logResult(RecoveryStatus.SKIPPED_NOT_INSTALLED, null, null);
        }
        if (!target.accessibilityTargetAvailable) {
            return logResult(
                    RecoveryStatus.SKIPPED_COMPONENT_UNAVAILABLE, null, null);
        }

        try {
            String before = Settings.Secure.getString(
                    context.getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            Log.i(AccessibilityRestorer.LOG_TAG,
                    "OPTIONAL TorrServe accessibilityBefore=" + value(before));
            if (containsComponent(before, ACCESSIBILITY_SERVICE)) {
                return logResult(RecoveryStatus.ALREADY_ENABLED, before, before);
            }

            String after = appendRaw(before, ACCESSIBILITY_SERVICE_FLAT);
            boolean written = Settings.Secure.putString(
                    context.getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                    after);
            Log.i(AccessibilityRestorer.LOG_TAG,
                    "OPTIONAL TorrServe putString=" + written
                            + ", accessibilityAfter=" + value(after));
            return logResult(
                    written ? RecoveryStatus.RECOVERED : RecoveryStatus.FAILED,
                    before,
                    after);
        } catch (SecurityException exception) {
            Log.e(AccessibilityRestorer.LOG_TAG,
                    "OPTIONAL TorrServe status=FAILED reason=SecurityException",
                    exception);
            return new RecoveryResult(RecoveryStatus.FAILED, null, null);
        } catch (RuntimeException exception) {
            Log.e(AccessibilityRestorer.LOG_TAG,
                    "OPTIONAL TorrServe status=FAILED reason=RuntimeException",
                    exception);
            return new RecoveryResult(RecoveryStatus.FAILED, null, null);
        }
    }

    private static boolean packageExists(PackageManager packageManager) {
        try {
            return packageManager.getApplicationInfo(PACKAGE_NAME, 0) != null;
        } catch (PackageManager.NameNotFoundException exception) {
            return false;
        } catch (RuntimeException exception) {
            Log.e(AccessibilityRestorer.LOG_TAG,
                    "TORRSERVE package check failed",
                    exception);
            return false;
        }
    }

    private static boolean serviceExists(PackageManager packageManager) {
        try {
            ServiceInfo info = packageManager.getServiceInfo(
                    ACCESSIBILITY_SERVICE,
                    PackageManager.GET_META_DATA);
            return info != null
                    && android.Manifest.permission.BIND_ACCESSIBILITY_SERVICE
                    .equals(info.permission);
        } catch (PackageManager.NameNotFoundException exception) {
            return false;
        } catch (RuntimeException exception) {
            Log.e(AccessibilityRestorer.LOG_TAG,
                    "TORRSERVE AccessibilityService check failed component="
                            + ACCESSIBILITY_SERVICE.flattenToString(),
                    exception);
            return false;
        }
    }

    private static boolean containsComponent(String services, ComponentName expected) {
        if (services == null || services.isEmpty()) {
            return false;
        }
        String[] tokens = services.split(":", -1);
        for (String token : tokens) {
            ComponentName component = ComponentName.unflattenFromString(token);
            if (expected.equals(component)
                    || ACCESSIBILITY_SERVICE_FLAT.equals(token)) {
                return true;
            }
        }
        return false;
    }

    private static String appendRaw(String existing, String token) {
        if (existing == null || existing.isEmpty()) {
            return token;
        }
        return existing.charAt(existing.length() - 1) == ':'
                ? existing + token : existing + ':' + token;
    }

    private static RecoveryResult logResult(
            RecoveryStatus status, String before, String after) {
        Log.i(AccessibilityRestorer.LOG_TAG,
                "OPTIONAL TorrServe status=" + status);
        return new RecoveryResult(status, before, after);
    }

    private static String value(String value) {
        return value == null ? "<null>" : value;
    }

    private static ComponentName component(String flattened) {
        ComponentName component = ComponentName.unflattenFromString(flattened);
        if (component == null) {
            throw new IllegalArgumentException("Invalid component: " + flattened);
        }
        return component;
    }

    static final class Status {
        final boolean packageInstalled;
        final boolean accessibilityTargetAvailable;

        Status(boolean packageInstalled, boolean accessibilityTargetAvailable) {
            this.packageInstalled = packageInstalled;
            this.accessibilityTargetAvailable = accessibilityTargetAvailable;
        }
    }

    enum RecoveryStatus {
        RECOVERED,
        ALREADY_ENABLED,
        SKIPPED_NOT_INSTALLED,
        SKIPPED_COMPONENT_UNAVAILABLE,
        FAILED
    }

    static final class RecoveryResult {
        final RecoveryStatus status;
        final String before;
        final String after;

        RecoveryResult(RecoveryStatus status, String before, String after) {
            this.status = status;
            this.before = before;
            this.after = after;
        }
    }
}
