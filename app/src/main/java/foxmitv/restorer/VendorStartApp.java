package foxmitv.restorer;

import android.content.Context;
import android.provider.Settings;
import android.util.Log;

final class VendorStartApp {
    private static final String SETTING_NAME = "start_3rd_app";
    static final String STATUS_ALREADY_ARMED = "ALREADY_ARMED";
    static final String STATUS_MISSING_UNARMED = "MISSING_UNARMED";
    static final String STATUS_READ_FAILED = "READ_FAILED";

    private VendorStartApp() {
    }

    static Result inspect(Context context, String label) {
        try {
            String value = Settings.System.getString(
                    context.getContentResolver(), SETTING_NAME);
            boolean armed = RecoveryTargets.RESTORER_PACKAGE.equals(value);
            String status = armed ? STATUS_ALREADY_ARMED : STATUS_MISSING_UNARMED;
            Log.i(AccessibilityRestorer.LOG_TAG,
                    "start_3rd_app " + label + " BEFORE=" + value(value)
                            + ", decision=ADVISORY_READ_ONLY"
                            + ", write skipped=true"
                            + ", vendorArm final status=" + status);
            return new Result(armed, false, false, value, value, status);
        } catch (SecurityException exception) {
            Log.e(AccessibilityRestorer.LOG_TAG,
                    "start_3rd_app " + label + " advisory read SecurityException",
                    exception);
            return failed(label);
        } catch (RuntimeException exception) {
            Log.e(AccessibilityRestorer.LOG_TAG,
                    "start_3rd_app " + label + " advisory read failed",
                    exception);
            return failed(label);
        }
    }

    // Kept for cold-boot call compatibility; vendor state is deliberately read-only.
    static Result rearm(Context context, String label) {
        return inspect(context, label + " READ-ONLY");
    }

    private static Result failed(String label) {
        Log.i(AccessibilityRestorer.LOG_TAG,
                "start_3rd_app " + label + " vendorArm final status="
                        + STATUS_READ_FAILED);
        return new Result(false, false, false, null, null, STATUS_READ_FAILED);
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
