package com.mitv.accessibilityrestorer;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.util.Log;

final class TargetApps {
    private TargetApps() {
    }

    static Status inspect(Context context) {
        PackageManager packageManager = context.getPackageManager();
        Status status = new Status(
                packageExists(packageManager, RecoveryTargets.MAPPER_PACKAGE),
                activityExists(packageManager, RecoveryTargets.MAPPER_ACTIVITY),
                serviceExists(packageManager, RecoveryTargets.MAPPER_SERVICE),
                packageExists(packageManager, RecoveryTargets.PROJECTIVY_PACKAGE),
                activityExists(packageManager, RecoveryTargets.PROJECTIVY_ACTIVITY),
                serviceExists(packageManager, RecoveryTargets.PROJECTIVY_SERVICE));
        Log.i(AccessibilityRestorer.LOG_TAG,
                "PACKAGE CHECK mapperPackage=" + status.mapperPackage
                        + ", mapperActivity=" + status.mapperActivity
                        + ", mapperService=" + status.mapperService
                        + ", projectivyPackage=" + status.projectivyPackage
                        + ", projectivyActivity=" + status.projectivyActivity
                        + ", projectivyService=" + status.projectivyService);
        if (!status.mapperPackage) {
            Log.e(AccessibilityRestorer.LOG_TAG,
                    "PACKAGE_NOT_INSTALLED " + RecoveryTargets.MAPPER_PACKAGE);
        }
        if (!status.projectivyPackage) {
            Log.e(AccessibilityRestorer.LOG_TAG,
                    "PACKAGE_NOT_INSTALLED " + RecoveryTargets.PROJECTIVY_PACKAGE);
        }
        return status;
    }

    static boolean startMapper(Context context, Status status, String label) {
        if (!status.mapperActivity) {
            Log.w(AccessibilityRestorer.LOG_TAG,
                    "ACTIVITY START " + label + " skipped: mapper Activity unavailable");
            return false;
        }
        return startExplicit(context, RecoveryTargets.MAPPER_ACTIVITY, label);
    }

    static boolean startProjectivy(Context context, Status status, String label) {
        if (!status.projectivyActivity) {
            Log.w(AccessibilityRestorer.LOG_TAG,
                    "ACTIVITY START " + label + " skipped: Projectivy Activity unavailable");
            return false;
        }
        return startExplicit(context, RecoveryTargets.PROJECTIVY_ACTIVITY, label);
    }

    private static boolean startExplicit(Context context, ComponentName component, String label) {
        Intent intent = new Intent();
        intent.setComponent(component);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        try {
            context.startActivity(intent);
            Log.i(AccessibilityRestorer.LOG_TAG,
                    "ACTIVITY START " + label + " component="
                            + component.flattenToString() + " result=success");
            return true;
        } catch (SecurityException exception) {
            Log.e(AccessibilityRestorer.LOG_TAG,
                    "ACTIVITY START " + label + " SecurityException component="
                            + component.flattenToString(),
                    exception);
            return false;
        } catch (RuntimeException exception) {
            Log.e(AccessibilityRestorer.LOG_TAG,
                    "ACTIVITY START " + label + " failed component="
                            + component.flattenToString(),
                    exception);
            return false;
        }
    }

    private static boolean packageExists(PackageManager packageManager, String packageName) {
        try {
            return packageManager.getPackageInfo(packageName, 0) != null;
        } catch (PackageManager.NameNotFoundException exception) {
            return false;
        } catch (RuntimeException exception) {
            Log.e(AccessibilityRestorer.LOG_TAG,
                    "PACKAGE CHECK failed for " + packageName,
                    exception);
            return false;
        }
    }

    private static boolean activityExists(PackageManager packageManager, ComponentName component) {
        try {
            return packageManager.getActivityInfo(component, 0) != null;
        } catch (PackageManager.NameNotFoundException exception) {
            return false;
        } catch (RuntimeException exception) {
            Log.e(AccessibilityRestorer.LOG_TAG,
                    "PACKAGE CHECK Activity failed for " + component.flattenToString(),
                    exception);
            return false;
        }
    }

    private static boolean serviceExists(PackageManager packageManager, ComponentName component) {
        try {
            return packageManager.getServiceInfo(component, PackageManager.GET_META_DATA) != null;
        } catch (PackageManager.NameNotFoundException exception) {
            return false;
        } catch (RuntimeException exception) {
            Log.e(AccessibilityRestorer.LOG_TAG,
                    "PACKAGE CHECK service failed for " + component.flattenToString(),
                    exception);
            return false;
        }
    }

    static final class Status {
        final boolean mapperPackage;
        final boolean mapperActivity;
        final boolean mapperService;
        final boolean projectivyPackage;
        final boolean projectivyActivity;
        final boolean projectivyService;

        Status(
                boolean mapperPackage,
                boolean mapperActivity,
                boolean mapperService,
                boolean projectivyPackage,
                boolean projectivyActivity,
                boolean projectivyService) {
            this.mapperPackage = mapperPackage;
            this.mapperActivity = mapperActivity;
            this.mapperService = mapperService;
            this.projectivyPackage = projectivyPackage;
            this.projectivyActivity = projectivyActivity;
            this.projectivyService = projectivyService;
        }

        boolean allAvailable() {
            return mapperActivity && mapperService && projectivyActivity && projectivyService;
        }
    }
}
