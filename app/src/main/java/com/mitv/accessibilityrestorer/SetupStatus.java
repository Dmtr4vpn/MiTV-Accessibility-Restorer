package com.mitv.accessibilityrestorer;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.provider.Settings;

final class SetupStatus {
    private SetupStatus() {
    }

    static Snapshot inspect(Context context) {
        Context appContext = context.getApplicationContext();
        if (appContext == null) {
            appContext = context;
        }
        PackageManager packageManager = appContext.getPackageManager();

        boolean mapperPackage = packageExists(packageManager, RecoveryTargets.MAPPER_PACKAGE);
        boolean mapperService = mapperPackage
                && accessibilityServiceExists(packageManager, RecoveryTargets.MAPPER_SERVICE);
        boolean projectivyPackage = packageExists(
                packageManager, RecoveryTargets.PROJECTIVY_PACKAGE);
        boolean projectivyService = projectivyPackage
                && accessibilityServiceExists(packageManager, RecoveryTargets.PROJECTIVY_SERVICE);
        boolean torrServePackage = packageExists(
                packageManager, TorrServeTarget.PACKAGE_NAME);
        boolean torrServeService = torrServePackage
                && accessibilityServiceExists(
                packageManager, TorrServeTarget.ACCESSIBILITY_SERVICE);
        boolean v2RayPackage = packageExists(packageManager, V2RayVpnAssist.PACKAGE_NAME);
        boolean secureSettingsGranted = hasSecureSettingsPermission(appContext);
        boolean accessibilityEnabled = isAccessibilityEnabled(appContext);
        String enabledServices = readEnabledAccessibilityServices(appContext);
        boolean mapperEnabled = containsExactToken(
                enabledServices, RecoveryTargets.MAPPER_SERVICE.flattenToString());
        boolean projectivyEnabled = containsExactToken(
                enabledServices, RecoveryTargets.PROJECTIVY_SERVICE.flattenToString());
        boolean torrServeEnabled = containsExactToken(
                enabledServices, TorrServeTarget.ACCESSIBILITY_SERVICE_FLAT);
        boolean markedComplete = SetupState.isMarkedComplete(appContext);

        return new Snapshot(
                secureSettingsGranted,
                accessibilityEnabled,
                mapperPackage,
                mapperService,
                mapperEnabled,
                projectivyPackage,
                projectivyService,
                projectivyEnabled,
                torrServePackage,
                torrServeService,
                torrServeEnabled,
                v2RayPackage,
                markedComplete);
    }

    static boolean hasSecureSettingsPermission(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return context.getPackageManager().checkPermission(
                Manifest.permission.WRITE_SECURE_SETTINGS,
                context.getPackageName()) == PackageManager.PERMISSION_GRANTED;
    }

    private static boolean isAccessibilityEnabled(Context context) {
        try {
            return Settings.Secure.getInt(
                    context.getContentResolver(),
                    Settings.Secure.ACCESSIBILITY_ENABLED,
                    0) == 1;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static String readEnabledAccessibilityServices(Context context) {
        try {
            return Settings.Secure.getString(
                    context.getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static boolean containsExactToken(String rawServices, String target) {
        if (rawServices == null || rawServices.isEmpty()) {
            return false;
        }
        String[] tokens = rawServices.split(":", -1);
        for (String token : tokens) {
            if (target.equals(token)) {
                return true;
            }
        }
        return false;
    }

    private static boolean packageExists(PackageManager packageManager, String packageName) {
        try {
            return packageManager.getApplicationInfo(packageName, 0) != null;
        } catch (PackageManager.NameNotFoundException exception) {
            return false;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static boolean accessibilityServiceExists(
            PackageManager packageManager, ComponentName component) {
        try {
            ServiceInfo info = packageManager.getServiceInfo(
                    component, PackageManager.GET_META_DATA);
            return info != null
                    && Manifest.permission.BIND_ACCESSIBILITY_SERVICE.equals(info.permission);
        } catch (PackageManager.NameNotFoundException exception) {
            return false;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    static final class Snapshot {
        final boolean secureSettingsGranted;
        final boolean accessibilityEnabled;
        final boolean mapperPackage;
        final boolean mapperService;
        final boolean mapperEnabled;
        final boolean projectivyPackage;
        final boolean projectivyService;
        final boolean projectivyEnabled;
        final boolean torrServePackage;
        final boolean torrServeService;
        final boolean torrServeEnabled;
        final boolean v2RayPackage;
        final boolean markedComplete;
        final boolean mainComponentsReady;
        final boolean setupComplete;

        Snapshot(
                boolean secureSettingsGranted,
                boolean accessibilityEnabled,
                boolean mapperPackage,
                boolean mapperService,
                boolean mapperEnabled,
                boolean projectivyPackage,
                boolean projectivyService,
                boolean projectivyEnabled,
                boolean torrServePackage,
                boolean torrServeService,
                boolean torrServeEnabled,
                boolean v2RayPackage,
                boolean markedComplete) {
            this.secureSettingsGranted = secureSettingsGranted;
            this.accessibilityEnabled = accessibilityEnabled;
            this.mapperPackage = mapperPackage;
            this.mapperService = mapperService;
            this.mapperEnabled = mapperEnabled;
            this.projectivyPackage = projectivyPackage;
            this.projectivyService = projectivyService;
            this.projectivyEnabled = projectivyEnabled;
            this.torrServePackage = torrServePackage;
            this.torrServeService = torrServeService;
            this.torrServeEnabled = torrServeEnabled;
            this.v2RayPackage = v2RayPackage;
            this.markedComplete = markedComplete;
            this.mainComponentsReady = accessibilityEnabled
                    && mapperPackage
                    && mapperService
                    && mapperEnabled
                    && projectivyPackage
                    && projectivyService
                    && projectivyEnabled;
            this.setupComplete = markedComplete
                    && secureSettingsGranted
                    && mainComponentsReady;
        }
    }
}
