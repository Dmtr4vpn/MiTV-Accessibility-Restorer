package foxmitv.restorer;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Build;

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
        boolean v2RayComponent = v2RayPackage
                && V2RayVpnAssist.isWidgetReceiverAvailable(appContext);
        boolean secureSettingsGranted = hasSecureSettingsPermission(appContext);
        boolean markedComplete = SetupState.isMarkedComplete(appContext);

        return new Snapshot(
                secureSettingsGranted,
                mapperPackage,
                mapperService,
                projectivyPackage,
                projectivyService,
                torrServePackage,
                torrServeService,
                v2RayPackage,
                v2RayComponent,
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
        final boolean mapperPackage;
        final boolean mapperService;
        final boolean projectivyPackage;
        final boolean projectivyService;
        final boolean torrServePackage;
        final boolean torrServeService;
        final boolean v2RayPackage;
        final boolean v2RayComponent;
        final boolean markedComplete;
        final boolean mainComponentsReady;
        final boolean setupComplete;

        Snapshot(
                boolean secureSettingsGranted,
                boolean mapperPackage,
                boolean mapperService,
                boolean projectivyPackage,
                boolean projectivyService,
                boolean torrServePackage,
                boolean torrServeService,
                boolean v2RayPackage,
                boolean v2RayComponent,
                boolean markedComplete) {
            this.secureSettingsGranted = secureSettingsGranted;
            this.mapperPackage = mapperPackage;
            this.mapperService = mapperService;
            this.projectivyPackage = projectivyPackage;
            this.projectivyService = projectivyService;
            this.torrServePackage = torrServePackage;
            this.torrServeService = torrServeService;
            this.v2RayPackage = v2RayPackage;
            this.v2RayComponent = v2RayComponent;
            this.markedComplete = markedComplete;
            this.mainComponentsReady = mapperPackage
                    && mapperService
                    && projectivyPackage
                    && projectivyService;
            this.setupComplete = markedComplete
                    && secureSettingsGranted
                    && mainComponentsReady;
        }
    }
}
