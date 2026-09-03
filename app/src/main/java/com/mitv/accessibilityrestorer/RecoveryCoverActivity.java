package com.mitv.accessibilityrestorer;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import java.lang.ref.WeakReference;

public final class RecoveryCoverActivity extends Activity {
    private static final Object LOCK = new Object();
    private static WeakReference<RecoveryCoverActivity> active =
            new WeakReference<RecoveryCoverActivity>(null);

    static boolean show(Context context, String reason) {
        Log.i(AccessibilityRestorer.LOG_TAG,
                "STR COVER requested reason=" + reason);
        Intent intent = new Intent(context, RecoveryCoverActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION
                | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        if (!(context instanceof Activity)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        try {
            context.startActivity(intent);
            if (context instanceof Activity) {
                ((Activity) context).overridePendingTransition(0, 0);
            }
            return true;
        } catch (SecurityException exception) {
            Log.e(AccessibilityRestorer.LOG_TAG,
                    "STR COVER start SecurityException",
                    exception);
            return false;
        } catch (RuntimeException exception) {
            Log.e(AccessibilityRestorer.LOG_TAG,
                    "STR COVER start failed",
                    exception);
            return false;
        }
    }

    static boolean isActive() {
        synchronized (LOCK) {
            RecoveryCoverActivity activity = active.get();
            return activity != null && !activity.isFinishing();
        }
    }

    static void finishCover(final String reason) {
        final RecoveryCoverActivity activity;
        synchronized (LOCK) {
            activity = active.get();
        }
        if (activity == null || activity.isFinishing()) {
            Log.i(AccessibilityRestorer.LOG_TAG,
                    "STR COVER finish requested with no active cover reason=" + reason);
            return;
        }
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (!activity.isFinishing()) {
                    Log.i(AccessibilityRestorer.LOG_TAG,
                            "STR COVER finish reason=" + reason);
                    activity.finish();
                    activity.overridePendingTransition(0, 0);
                }
            }
        });
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);
        View cover = new View(this);
        cover.setBackgroundColor(Color.BLACK);
        setContentView(cover);
        synchronized (LOCK) {
            active = new WeakReference<RecoveryCoverActivity>(this);
        }
        Log.i(AccessibilityRestorer.LOG_TAG, "STR COVER created opaque=true");
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.i(AccessibilityRestorer.LOG_TAG, "STR COVER visible/ready");
    }

    @Override
    protected void onDestroy() {
        synchronized (LOCK) {
            if (active.get() == this) {
                active.clear();
            }
        }
        Log.i(AccessibilityRestorer.LOG_TAG, "STR COVER destroyed");
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        Log.i(AccessibilityRestorer.LOG_TAG,
                "STR COVER back ignored while recovery is active");
    }
}
