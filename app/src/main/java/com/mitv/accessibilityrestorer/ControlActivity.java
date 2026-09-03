package com.mitv.accessibilityrestorer;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Date;

public final class ControlActivity extends Activity {
    private TextView statusView;
    private Button restoreButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(Color.rgb(20, 24, 30));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        int padding = dp(32);
        content.setPadding(padding, padding, padding, padding);

        TextView title = new TextView(this);
        title.setText("MiTV Accessibility Restorer");
        title.setTextColor(Color.WHITE);
        title.setTextSize(28f);
        content.addView(title, matchWrap());

        statusView = new TextView(this);
        statusView.setTextColor(Color.LTGRAY);
        statusView.setTextSize(18f);
        statusView.setPadding(0, dp(24), 0, dp(24));
        content.addView(statusView, matchWrap());

        restoreButton = new Button(this);
        restoreButton.setText("Восстановить сейчас");
        restoreButton.setTextSize(18f);
        restoreButton.setFocusable(true);
        restoreButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                restoreButton.setEnabled(false);
                statusView.setText("Выполняется диагностическое восстановление…");
                boolean started = RecoveryEngine.startStrRecovery(
                        getApplicationContext(),
                        "MANUAL",
                        true,
                        SystemClock.elapsedRealtime(),
                        new RecoveryEngine.Callback() {
                            @Override
                            public void onFinished(
                                    boolean executionCompleted, String message) {
                                restoreButton.setEnabled(true);
                                refreshStatus();
                            }
                        });
                if (!started) {
                    restoreButton.setEnabled(true);
                    refreshStatus();
                }
            }
        });
        content.addView(restoreButton, wrapWrap());

        TextView note = new TextView(this);
        note.setText("Bound/Binding/Crashed проверяются через ADB. Подробности выполнения доступны в logcat по тегу MiTVRestorer.");
        note.setTextColor(Color.GRAY);
        note.setTextSize(15f);
        note.setPadding(0, dp(24), 0, 0);
        content.addView(note, matchWrap());

        scrollView.addView(content);
        setContentView(scrollView);
        restoreButton.requestFocus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
    }

    private void refreshStatus() {
        boolean hasSecureSettings = getPackageManager().checkPermission(
                Manifest.permission.WRITE_SECURE_SETTINGS,
                getPackageName()) == PackageManager.PERMISSION_GRANTED;
        int present = AccessibilityRestorer.countPresent(this);

        StringBuilder text = new StringBuilder();
        text.append("Версия: ")
                .append(RecoveryEngine.VERSION_NAME)
                .append(" (")
                .append(RecoveryEngine.VERSION_CODE)
                .append(")\n");
        text.append("WRITE_SECURE_SETTINGS: ")
                .append(hasSecureSettings ? "выдано" : "НЕ ВЫДАНО")
                .append('\n');
        text.append("Основные службы: ")
                .append(present)
                .append('/')
                .append(AccessibilityRestorer.requiredCount())
                .append('\n');
        text.append("TorrServe: ")
                .append(TorrServeTarget.isPackageInstalled(this)
                        ? "установлен" : "не установлен")
                .append('\n');
        text.append("v2RayTun: ")
                .append(V2RayVpnAssist.isPackageInstalled(this)
                        ? "установлен" : "не установлен")
                .append('\n');
        text.append("VPN: ")
                .append(V2RayVpnAssist.isVpnActive(this)
                        ? "активен" : "не обнаружен")
                .append('\n');

        long lastTime = AppStatus.getLastTime(this);
        if (lastTime > 0L) {
            text.append("Последнее восстановление: ")
                    .append(DateFormat.getMediumDateFormat(this).format(new Date(lastTime)))
                    .append(' ')
                    .append(DateFormat.getTimeFormat(this).format(new Date(lastTime)))
                    .append('\n');
            text.append("Результат: ")
                    .append(AppStatus.getLastSuccess(this)
                            ? "операции выполнены" : "ошибка/частично")
                    .append(" — ")
                    .append(AppStatus.getLastMessage(this));
        } else {
            text.append(AppStatus.getLastMessage(this));
        }
        statusView.setText(text.toString());
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private static LinearLayout.LayoutParams wrapWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }
}
