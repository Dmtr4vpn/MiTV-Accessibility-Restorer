package com.mitv.accessibilityrestorer;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

final class AppVersion {
    private AppVersion() {
    }

    static Info read(Context context) {
        try {
            PackageInfo packageInfo = context.getPackageManager().getPackageInfo(
                    context.getPackageName(), 0);
            String name = packageInfo.versionName == null
                    ? "unknown" : packageInfo.versionName;
            long code = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                    ? packageInfo.getLongVersionCode() : packageInfo.versionCode;
            return new Info(name, code);
        } catch (PackageManager.NameNotFoundException exception) {
            Log.e(AccessibilityRestorer.LOG_TAG,
                    "Unable to read installed package version",
                    exception);
            return new Info("unknown", -1L);
        }
    }

    static final class Info {
        final String name;
        final long code;

        Info(String name, long code) {
            this.name = name;
            this.code = code;
        }
    }
}
