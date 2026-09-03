package com.mitv.accessibilityrestorer;

import android.content.Context;
import android.provider.Settings;
import android.util.Log;

final class VendorStartApp {
    private static final String SETTING_NAME = "start_3rd_app";
    static final String STATUS_ALREADY_ARMED = "ALREADY_ARMED";
    static final String STATUS_MISSING_UNARMED = "MISSING_UNARMED";
    static final String STATUS_WRITE_SUCCESS = "WRITE_SUCCESS";
    static final String STATUS_WRITE_FAILED = "WRITE_FAILED";

    private VendorStartApp() {
    }

    static Result inspect(Context context, String label) {
        String before = null;
        try {
            before = Settings.System.getString(context.getContentResolver(), SETTING_NAME);
            boolean armed = RecoveryTargets.RESTORER_PACKAGE.equals(before);
            String status = armed ? STATUS_ALREADY_ARMED : STATUS_MISSING_UNARMED;
            Log.i(AccessibilityRestorer.LOG_TAG,
                    "start_3rd_app " + label + " BEFORE=" + value(before)
                            + ", decision=ADVISORY_READ_ONLY"
                            + ", write skipped=true"
                            + ", vendorArm final status=" + status);
            return new Result(armed, false, false, before, before, status);
        } catch (SecurityException exception) {
            Log.e(AccessibilityRestorer.LOG_TAG,
                    "start_3rd_app " + label + " advisory read SecurityException",
                    exception);
            return failed(false, false, before, null, label);
        } catch (RuntimeException exception) {
            Log.e(AccessibilityRestorer.LOG_TAG,
                    "start_3rd_app " + label + " advisory read failed",
                    exception);
            return failed(false, false, before, null, label);
        }
    }

    static Result rearm(Context context, String label) {
        String before = null;
        String after = null;
        boolean putResult = false;
        boolean attempted = false;
        try {
            before = Settings.System.getString(context.getContentResolver(), SETTING_NAME);
        } catch (SecurityException exception) {
            Log.e(AccessibilityRestorer.LOG_TAG,
                    "start_3rd_app " + label + " BEFORE SecurityException",
                    exception);
            return failed(attempted, putResult, before, after, label);
        } catch (RuntimeException exception) {
            Log.e(AccessibilityRestorer.LOG_TAG,
                    "start_3rd_app " + label + " BEFORE failed",
                    exception);
            return failed(attempted, putResult, before, after, label);
        }

        Log.i(AccessibilityRestorer.LOG_TAG,
                "start_3rd_app " + label + " BEFORE=" + value(before));
        if (RecoveryTargets.RESTORER_PACKAGE.equals(before)) {
            after = before;
            Log.i(AccessibilityRestorer.LOG_TAG,
                    "start_3rd_app " + label + " decision=ALREADY_ARMED");
            Log.i(AccessibilityRestorer.LOG_TAG,
                    "start_3rd_app " + label + " write skipped: already armed");
            Log.i(AccessibilityRestorer.LOG_TAG,
                    "start_3rd_app " + label + " AFTER=" + value(after));
            Log.i(AccessibilityRestorer.LOG_TAG,
                    "start_3rd_app " + label + " vendorArm final status="
                            + STATUS_ALREADY_ARMED);
            return new Result(
                    true, false, false, before, after, STATUS_ALREADY_ARMED);
        }

        attempted = true;
        Log.i(AccessibilityRestorer.LOG_TAG,
                "start_3rd_app " + label + " decision=WRITE_REQUIRED");
        try {
            putResult = Settings.System.putString(
                    context.getContentResolver(),
                    SETTING_NAME,
                    RecoveryTargets.RESTORER_PACKAGE);
            Log.i(AccessibilityRestorer.LOG_TAG,
                    "start_3rd_app " + label + " putString result=" + putResult);
        } catch (SecurityException exception) {
            Log.e(AccessibilityRestorer.LOG_TAG,
                    "start_3rd_app " + label + " putString SecurityException",
                    exception);
        } catch (RuntimeException exception) {
            Log.e(AccessibilityRestorer.LOG_TAG,
                    "start_3rd_app " + label + " putString failed",
                    exception);
        }

        try {
            after = Settings.System.getString(context.getContentResolver(), SETTING_NAME);
        } catch (SecurityException exception) {
            Log.e(AccessibilityRestorer.LOG_TAG,
                    "start_3rd_app " + label + " AFTER SecurityException",
                    exception);
        } catch (RuntimeException exception) {
            Log.e(AccessibilityRestorer.LOG_TAG,
                    "start_3rd_app " + label + " AFTER failed",
                    exception);
        }
        boolean armed = RecoveryTargets.RESTORER_PACKAGE.equals(after);
        String status = armed ? STATUS_WRITE_SUCCESS : STATUS_WRITE_FAILED;
        Log.i(AccessibilityRestorer.LOG_TAG,
                "start_3rd_app " + label + " AFTER=" + value(after));
        Log.i(AccessibilityRestorer.LOG_TAG,
                "start_3rd_app " + label + " vendorArm final status=" + status);
        return new Result(armed, attempted, putResult, before, after, status);
    }

    private static Result failed(
            boolean attempted,
            boolean putResult,
            String before,
            String after,
            String label) {
        Log.i(AccessibilityRestorer.LOG_TAG,
                "start_3rd_app " + label + " vendorArm final status="
                        + STATUS_WRITE_FAILED);
        return new Result(
                false, attempted, putResult, before, after, STATUS_WRITE_FAILED);
    }

    private static String value(String value) {
        return value == null ? "<null>" : value;
    }

    static final class Result {
        final boolean armed;
        final boolean attempted;
        final boolean putResult;
        final String before;
        final String after;
        final String status;

        Result(
                boolean armed,
                boolean attempted,
                boolean putResult,
                String before,
                String after,
                String status) {
            this.armed = armed;
            this.attempted = attempted;
            this.putResult = putResult;
            this.before = before;
            this.after = after;
            this.status = status;
        }
    }
}
