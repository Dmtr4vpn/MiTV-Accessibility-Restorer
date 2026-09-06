package com.mitv.accessibilityrestorer;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Date;

public final class ControlActivity extends Activity {
    private TextView titleView;
    private TextView headingView;
    private TextView statusView;
    private Button restoreButton;
    private boolean manualRecoveryRunning;
    private String notice;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(Color.rgb(20, 24, 30));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.setPadding(dp(24), dp(16), dp(24), dp(16));

        titleView = new TextView(this);
        titleView.setTextColor(Color.WHITE);
        titleView.setTextSize(27f);
        content.addView(titleView, matchWrap());

        headingView = new TextView(this);
        headingView.setTextColor(Color.WHITE);
        headingView.setTextSize(18f);
        headingView.setPadding(0, dp(8), 0, 0);
        content.addView(headingView, matchWrap());

        statusView = new TextView(this);
        statusView.setTextColor(Color.LTGRAY);
        statusView.setTextSize(16f);
        statusView.setPadding(0, dp(8), 0, dp(8));
        content.addView(statusView, matchWrap());

        TextView homeNote = new TextView(this);
        homeNote.setText("Для выхода нажмите HOME.");
        homeNote.setTextColor(Color.WHITE);
        homeNote.setTextSize(16f);
        homeNote.setPadding(0, dp(4), 0, dp(8));
        content.addView(homeNote, matchWrap());

        restoreButton = createButton("Восстановить спецвозможности");
        restoreButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startManualRecovery();
            }
        });
        content.addView(restoreButton, buttonParams());

        scrollView.addView(content);
        setContentView(scrollView);
        refreshStatus();
        restoreButton.requestFocus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
    }

    private void startManualRecovery() {
        manualRecoveryRunning = true;
        restoreButton.setEnabled(false);
        notice = "Выполняется восстановление…";
        refreshStatus();
        boolean started = RecoveryEngine.startStrRecovery(
                getApplicationContext(),
                "MANUAL",
                true,
                SystemClock.elapsedRealtime(),
                new RecoveryEngine.Callback() {
                    @Override
                    public void onFinished(boolean executionCompleted, String message) {
                        manualRecoveryRunning = false;
                        notice = executionCompleted
                                ? "Восстановление завершено."
                                : "Восстановление завершено частично или с ошибкой.";
                        refreshStatus();
                        returnToControlActivity();
                    }
                });
        if (!started) {
            manualRecoveryRunning = false;
            notice = "Восстановление уже выполняется или не смогло запуститься.";
            refreshStatus();
        }
    }

    private void returnToControlActivity() {
        Intent intent = new Intent(getApplicationContext(), ControlActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        try {
            getApplicationContext().startActivity(intent);
        } catch (RuntimeException exception) {
            Log.e(AccessibilityRestorer.LOG_TAG,
                    "MANUAL could not return to ControlActivity", exception);
        }
    }

    private void refreshStatus() {
        SetupStatus.Snapshot snapshot = SetupStatus.inspect(this);
        AppVersion.Info version = AppVersion.read(this);
        titleView.setText(getApplicationInfo().loadLabel(getPackageManager())
                + " " + version.name);

        boolean systemReady = snapshot.secureSettingsGranted && snapshot.mainComponentsReady;
        String vpnState = snapshot.v2RayPackage
                ? V2RayVpnAssist.getVpnStateForStatus(this)
                : null;
        boolean optionalIssue = (snapshot.torrServePackage
                && (!snapshot.accessibilityEnabled
                || !snapshot.torrServeService
                || !snapshot.torrServeEnabled))
                || (snapshot.v2RayPackage && !"true".equals(vpnState));
        headingView.setText(systemReady
                ? optionalIssue
                        ? "Система готова к работе.\nНекоторые необязательные компоненты не активны."
                        : "Система готова к работе."
                : "Требуется восстановление.");

        StringBuilder text = new StringBuilder();
        text.append(snapshot.secureSettingsGranted ? "✓ " : "✗ ")
                .append("Разрешение управления спецвозможностями — ")
                .append(snapshot.secureSettingsGranted ? "выдано" : "не выдано")
                .append("\n  WRITE_SECURE_SETTINGS")
                .append('\n');
        appendMainComponent(
                text,
                "Button Mapper",
                snapshot.accessibilityEnabled,
                snapshot.mapperPackage,
                snapshot.mapperService,
                snapshot.mapperEnabled);
        appendMainComponent(
                text,
                "Projectivy Launcher",
                snapshot.accessibilityEnabled,
                snapshot.projectivyPackage,
                snapshot.projectivyService,
                snapshot.projectivyEnabled);
        appendOptionalComponent(
                text,
                "TorrServe",
                snapshot.accessibilityEnabled,
                snapshot.torrServePackage,
                snapshot.torrServeService,
                snapshot.torrServeEnabled);
        appendV2RayStatus(text, snapshot.v2RayPackage, vpnState);

        long lastTime = AppStatus.getLastTime(this);
        if (lastTime > 0L) {
            text.append("\nПоследнее восстановление: ")
                    .append(AppStatus.getLastSuccess(this) ? "успешно" : "ошибка/частично")
                    .append(", ")
                    .append(DateFormat.getMediumDateFormat(this).format(new Date(lastTime)))
                    .append(' ')
                    .append(DateFormat.getTimeFormat(this).format(new Date(lastTime)))
                    .append('\n');
        }

        if (!snapshot.secureSettingsGranted) {
            text.append("\nЗапустите INSTALL.cmd на ПК для выдачи разрешения.\n");
        }
        if (notice != null && !notice.isEmpty()) {
            text.append("\n").append(notice).append('\n');
        }

        statusView.setText(text.toString());
        restoreButton.setEnabled(
                !manualRecoveryRunning && snapshot.secureSettingsGranted);
    }

    private static void appendMainComponent(
            StringBuilder text,
            String label,
            boolean accessibilityEnabled,
            boolean packageFound,
            boolean serviceFound,
            boolean serviceEnabled) {
        if (!packageFound) {
            text.append("✗ ").append(label).append(" — не установлен\n");
        } else if (!serviceFound) {
            text.append("✗ ").append(label)
                    .append(" — служба спецвозможностей не найдена\n");
        } else if (!accessibilityEnabled || !serviceEnabled) {
            text.append("✗ ").append(label)
                    .append(" — спецвозможности выключены\n");
        } else {
            text.append("✓ ").append(label)
                    .append(" — спецвозможности включены\n");
        }
    }

    private static void appendOptionalComponent(
            StringBuilder text,
            String label,
            boolean accessibilityEnabled,
            boolean packageFound,
            boolean serviceFound,
            boolean serviceEnabled) {
        if (!packageFound) {
            text.append("○ ").append(label).append(" — не установлен (необязательно)\n");
        } else if (!serviceFound || !accessibilityEnabled || !serviceEnabled) {
            text.append("! ").append(label)
                    .append(" — спецвозможности выключены (необязательно)\n");
        } else {
            text.append("✓ ").append(label)
                    .append(" — спецвозможности включены (необязательно)\n");
        }
    }

    private static void appendV2RayStatus(
            StringBuilder text, boolean packageFound, String vpnState) {
        if (!packageFound) {
            text.append("○ v2RayTun — не установлен (необязательно)\n");
            return;
        }
        if ("true".equals(vpnState)) {
            text.append("✓ v2RayTun — VPN подключён (необязательно)\n");
        } else if ("false".equals(vpnState)) {
            text.append("! v2RayTun — VPN не подключён (необязательно)\n");
        } else {
            text.append("! v2RayTun — состояние VPN недоступно (необязательно)\n");
        }
    }

    private Button createButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(16f);
        button.setFocusable(true);
        button.setMinHeight(dp(52));
        button.setId(View.generateViewId());
        return button;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private LinearLayout.LayoutParams buttonParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(52));
        params.topMargin = dp(4);
        return params;
    }

    private static LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }
}
