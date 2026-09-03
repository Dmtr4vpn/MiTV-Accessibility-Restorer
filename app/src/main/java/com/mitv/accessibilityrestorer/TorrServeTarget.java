package com.mitv.accessibilityrestorer;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
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
}
