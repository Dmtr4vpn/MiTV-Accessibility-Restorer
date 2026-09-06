package com.mitv.accessibilityrestorer;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.provider.Settings;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

final class AccessibilityRestorer {
    static final String LOG_TAG = "MiTVRestorer";

    private static final ComponentName[] REQUIRED_SERVICES = new ComponentName[] {
            RecoveryTargets.PROJECTIVY_SERVICE,
            RecoveryTargets.MAPPER_SERVICE
    };

    private AccessibilityRestorer() {
    }

    static Result restore(Context context, String source) {
        Context appContext = DirectBootContext.get(context);
        boolean hasSecureSettings = appContext.getPackageManager().checkPermission(
                android.Manifest.permission.WRITE_SECURE_SETTINGS,
                appContext.getPackageName()) == PackageManager.PERMISSION_GRANTED;
        Log.i(LOG_TAG,
                "Restore requested: source=" + source
                        + ", WRITE_SECURE_SETTINGS granted=" + hasSecureSettings);

        try {
            String existing = Settings.Secure.getString(
                    appContext.getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            Log.i(LOG_TAG,
                    "enabled_accessibility_services BEFORE="
                            + (existing == null ? "<null>" : existing));

            StringBuilder updated = new StringBuilder(existing == null ? "" : existing);
            List<String> addedComponents = new ArrayList<String>();
            List<String> missingComponents = new ArrayList<String>();

            for (ComponentName requiredService : REQUIRED_SERVICES) {
                String flattened = requiredService.flattenToString();
                if (!isServiceInstalled(appContext, requiredService)) {
                    missingComponents.add(flattened);
                    Log.w(LOG_TAG, "Target AccessibilityService not found: " + flattened);
                    continue;
                }
                if (!containsComponent(existing, requiredService)) {
                    if (updated.length() > 0 && updated.charAt(updated.length() - 1) != ':') {
                        updated.append(':');
                    }
                    updated.append(flattened);
                    addedComponents.add(flattened);
                }
            }

            String updatedValue = updated.toString();
            Log.i(LOG_TAG,
                    "Added AccessibilityService components=" + addedComponents.toString());
            Log.i(LOG_TAG,
                    "enabled_accessibility_services AFTER=" + updatedValue);

            boolean servicesWritten = Settings.Secure.putString(
                    appContext.getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                    updatedValue);
            boolean enabledWritten = Settings.Secure.putInt(
                    appContext.getContentResolver(),
                    Settings.Secure.ACCESSIBILITY_ENABLED,
                    1);
            Log.i(LOG_TAG,
                    "Settings.Secure results: putString=" + servicesWritten
                            + ", putInt=" + enabledWritten);

            if (!servicesWritten || !enabledWritten) {
                String message = "Android отклонил запись Settings.Secure";
                AppStatus.saveResult(appContext, false, message);
                Log.e(LOG_TAG, message);
                return new Result(
                        false,
                        message,
                        updatedValue,
                        addedComponents.size(),
                        missingComponents.size());
            }

            String message = addedComponents.isEmpty()
                    ? "Обе целевые службы уже присутствовали; Accessibility включён"
                    : "Добавлено служб: " + addedComponents.size() + "; Accessibility включён";
            if (!missingComponents.isEmpty()) {
                message += "; не найдены в системе: " + missingComponents.toString();
            }
            AppStatus.saveResult(appContext, true, message);
            Log.i(LOG_TAG,
                    "Restore completed: source=" + source
                            + ", success=true, missing=" + missingComponents.toString());
            return new Result(
                    true,
                    message,
                    updatedValue,
                    addedComponents.size(),
                    missingComponents.size());
        } catch (SecurityException exception) {
            String message = "Нет WRITE_SECURE_SETTINGS: выполните adb shell pm grant";
            AppStatus.saveResult(appContext, false, message);
            Log.e(LOG_TAG, message, exception);
            return new Result(false, message, "", 0, 0);
        } catch (RuntimeException exception) {
            String detail = exception.getMessage();
            String message = "Ошибка восстановления: "
                    + exception.getClass().getSimpleName()
                    + (detail == null ? "" : " — " + detail);
            AppStatus.saveResult(appContext, false, message);
            Log.e(LOG_TAG, message, exception);
            return new Result(false, message, "", 0, 0);
        }
    }

    static int countPresent(Context context) {
        String existing = Settings.Secure.getString(
                context.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        int present = 0;
        for (ComponentName requiredService : REQUIRED_SERVICES) {
            if (containsComponent(existing, requiredService)) {
                present++;
            }
        }
        return present;
    }

    static int requiredCount() {
        return REQUIRED_SERVICES.length;
    }

    private static boolean isServiceInstalled(Context context, ComponentName component) {
        try {
            return context.getPackageManager().getServiceInfo(
                    component, PackageManager.GET_META_DATA) != null;
        } catch (PackageManager.NameNotFoundException exception) {
            return false;
        } catch (RuntimeException exception) {
            Log.w(LOG_TAG,
                    "Could not query target AccessibilityService "
                            + component.flattenToString(),
                    exception);
            return false;
        }
    }

    private static boolean containsComponent(String services, ComponentName expected) {
        if (services == null || services.isEmpty()) {
            return false;
        }

        int start = 0;
        while (start <= services.length()) {
            int separator = services.indexOf(':', start);
            int end = separator >= 0 ? separator : services.length();
            if (end > start) {
                String token = services.substring(start, end);
                ComponentName component = ComponentName.unflattenFromString(token);
                if (expected.equals(component) || expected.flattenToString().equals(token)) {
                    return true;
                }
            }
            if (separator < 0) {
                break;
            }
            start = separator + 1;
        }
        return false;
    }

    static final class Result {
        final boolean success;
        final String message;
        final String enabledServices;
        final int addedCount;
        final int missingCount;

        Result(
                boolean success,
                String message,
                String enabledServices,
                int addedCount,
                int missingCount) {
            this.success = success;
            this.message = message;
            this.enabledServices = enabledServices;
            this.addedCount = addedCount;
            this.missingCount = missingCount;
        }
    }
}
