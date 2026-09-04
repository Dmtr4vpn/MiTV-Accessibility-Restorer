package com.mitv.accessibilityrestorer;

import android.app.Activity;
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
    private TextView headingView;
    private TextView statusView;
    private Button finishSetupButton;
    private Button checkAgainButton;
    private Button diagnosticsButton;
    private Button restoreButton;
    private boolean diagnosticsExpanded;
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
        int padding = dp(32);
        content.setPadding(padding, padding, padding, padding);

        TextView title = new TextView(this);
        title.setText("MiTV Accessibility Restorer");
        title.setTextColor(Color.WHITE);
        title.setTextSize(28f);
        content.addView(title, matchWrap());

        headingView = new TextView(this);
        headingView.setTextColor(Color.WHITE);
        headingView.setTextSize(21f);
        headingView.setPadding(0, dp(14), 0, 0);
        content.addView(headingView, matchWrap());

        statusView = new TextView(this);
        statusView.setTextColor(Color.LTGRAY);
        statusView.setTextSize(18f);
        statusView.setPadding(0, dp(20), 0, dp(14));
        content.addView(statusView, matchWrap());

        finishSetupButton = createButton("Завершить настройку");
        finishSetupButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finishSetup();
            }
        });
        content.addView(finishSetupButton, buttonParams());

        checkAgainButton = createButton("Проверить снова");
        checkAgainButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                notice = null;
                refreshStatus();
            }
        });
        content.addView(checkAgainButton, buttonParams());

        diagnosticsButton = createButton("Диагностика");
        diagnosticsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                diagnosticsExpanded = !diagnosticsExpanded;
                diagnosticsButton.setText(
                        diagnosticsExpanded ? "Скрыть диагностику" : "Диагностика");
                refreshStatus();
            }
        });
        content.addView(diagnosticsButton, buttonParams());

        restoreButton = createButton("Восстановить сейчас");
        restoreButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startManualRecovery();
            }
        });
        content.addView(restoreButton, buttonParams());

        TextView note = new TextView(this);
        note.setText("Bound/Binding/Crashed проверяются через ADB. Подробности выполнения доступны в logcat по тегу MiTVRestorer.");
        note.setTextColor(Color.GRAY);
        note.setTextSize(15f);
        note.setPadding(0, dp(20), 0, 0);
        content.addView(note, matchWrap());

        scrollView.addView(content);
        setContentView(scrollView);
        refreshStatus();
        requestInitialFocus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
    }

    private void finishSetup() {
        SetupStatus.Snapshot snapshot = SetupStatus.inspect(this);
        if (!snapshot.secureSettingsGranted) {
            SetupState.clear(this);
            notice = "Разрешение ещё не выдано. Запустите INSTALL.cmd на ПК и затем выберите «Проверить снова».";
        } else if (!snapshot.mainComponentsReady) {
            SetupState.clear(this);
            notice = "Настройка не завершена: установите Button Mapper и Projectivy Launcher с указанными Accessibility-службами.";
        } else if (SetupState.markComplete(this)) {
            notice = "Система готова к работе. Необязательные интеграции не влияют на завершение настройки.";
        } else {
            notice = "Не удалось сохранить маркер настройки. Повторите проверку.";
        }
        Log.i(AccessibilityRestorer.LOG_TAG,
                "FIRST RUN finish permissionGranted=" + snapshot.secureSettingsGranted
                        + ", mainComponentsReady=" + snapshot.mainComponentsReady
                        + ", torrServePackage=" + snapshot.torrServePackage
                        + ", torrServeService=" + snapshot.torrServeService
                        + ", v2RayPackage=" + snapshot.v2RayPackage);
        refreshStatus();
    }

    private void startManualRecovery() {
        manualRecoveryRunning = true;
        restoreButton.setEnabled(false);
        notice = "Выполняется диагностическое восстановление…";
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
                        notice = null;
                        refreshStatus();
                    }
                });
        if (!started) {
            manualRecoveryRunning = false;
            notice = "Восстановление уже выполняется или не смогло запуститься.";
            refreshStatus();
        }
    }

    private void refreshStatus() {
        SetupStatus.Snapshot snapshot = SetupStatus.inspect(this);
        headingView.setText(snapshot.setupComplete
                ? "Настройка завершена" : "Первичная настройка");

        StringBuilder text = new StringBuilder();
        text.append("Версия: ")
                .append(RecoveryEngine.VERSION_NAME)
                .append(" (")
                .append(RecoveryEngine.VERSION_CODE)
                .append(")\n\n");
        appendMainComponent(
                text, "Button Mapper", snapshot.mapperPackage, snapshot.mapperService);
        appendMainComponent(
                text, "Projectivy Launcher",
                snapshot.projectivyPackage,
                snapshot.projectivyService);
        appendOptionalComponent(
                text, "TorrServe", snapshot.torrServePackage, snapshot.torrServeService);
        appendOptionalPackage(text, "v2RayTun", snapshot.v2RayPackage);
        text.append('\n');
        text.append(snapshot.secureSettingsGranted ? "✓ " : "✗ ")
                .append("WRITE_SECURE_SETTINGS ")
                .append(snapshot.secureSettingsGranted ? "активирован" : "НЕ АКТИВИРОВАН")
                .append('\n');

        if (!snapshot.secureSettingsGranted) {
            text.append("\nТребуется однократная инициализация с ПК.\n")
                    .append("Используйте INSTALL.cmd из комплекта релиза.\n\n")
                    .append("Расширенная команда:\n")
                    .append("adb shell pm grant com.mitv.accessibilityrestorer android.permission.WRITE_SECURE_SETTINGS\n");
        } else if (!snapshot.mainComponentsReady) {
            text.append("\nРазрешение активно, но отсутствует основной пакет или AccessibilityService.\n");
        } else if (snapshot.setupComplete) {
            text.append("\nСистема готова к работе.\n");
        } else {
            text.append("\nОсновные компоненты готовы. Выберите «Завершить настройку».\n");
        }

        if (notice != null && !notice.isEmpty()) {
            text.append("\n").append(notice).append('\n');
        }

        if (diagnosticsExpanded) {
            appendDiagnostics(text, snapshot);
        }

        statusView.setText(text.toString());
        finishSetupButton.setVisibility(
                snapshot.secureSettingsGranted ? View.VISIBLE : View.GONE);
        finishSetupButton.setEnabled(!manualRecoveryRunning);
        checkAgainButton.setEnabled(!manualRecoveryRunning);
        diagnosticsButton.setEnabled(!manualRecoveryRunning);
        restoreButton.setEnabled(
                !manualRecoveryRunning
                        && snapshot.secureSettingsGranted);
    }

    private void appendDiagnostics(StringBuilder text, SetupStatus.Snapshot snapshot) {
        text.append("\nДиагностика:\n");
        text.append("Маркер setup_completed: ")
                .append(snapshot.markedComplete ? "true" : "false")
                .append('\n');
        text.append("Основные службы включены: ")
                .append(AccessibilityRestorer.countPresent(this))
                .append('/')
                .append(AccessibilityRestorer.requiredCount())
                .append('\n');
        text.append("VPN transport: ")
                .append(V2RayVpnAssist.getVpnStateForStatus(this))
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
                    .append(AppStatus.getLastMessage(this))
                    .append('\n');
        } else {
            text.append(AppStatus.getLastMessage(this)).append('\n');
        }
    }

    private static void appendMainComponent(
            StringBuilder text, String label, boolean packageFound, boolean serviceFound) {
        if (packageFound && serviceFound) {
            text.append("✓ ").append(label).append(" найден\n");
        } else if (packageFound) {
            text.append("✗ ").append(label).append(": AccessibilityService не найден\n");
        } else {
            text.append("✗ ").append(label).append(" не установлен\n");
        }
    }

    private static void appendOptionalComponent(
            StringBuilder text, String label, boolean packageFound, boolean serviceFound) {
        if (packageFound && serviceFound) {
            text.append("✓ ").append(label).append(" найден (необязательно)\n");
        } else if (packageFound) {
            text.append("○ ").append(label)
                    .append(": AccessibilityService не найден (необязательно)\n");
        } else {
            text.append("○ ").append(label).append(" не установлен (необязательно)\n");
        }
    }

    private static void appendOptionalPackage(
            StringBuilder text, String label, boolean packageFound) {
        text.append(packageFound ? "✓ " : "○ ")
                .append(label)
                .append(packageFound ? " найден" : " не установлен")
                .append(" (необязательно)\n");
    }

    private Button createButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(18f);
        button.setFocusable(true);
        button.setMinHeight(dp(52));
        return button;
    }

    private void requestInitialFocus() {
        SetupStatus.Snapshot snapshot = SetupStatus.inspect(this);
        if (!snapshot.secureSettingsGranted) {
            checkAgainButton.requestFocus();
        } else if (!snapshot.setupComplete) {
            finishSetupButton.requestFocus();
        } else {
            restoreButton.requestFocus();
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private LinearLayout.LayoutParams buttonParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(8);
        return params;
    }

    private static LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }
}
