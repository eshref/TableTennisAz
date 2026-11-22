package az.tabletennis;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import com.google.android.gms.tasks.Task;

public final class TournamentAlertsActivity extends Activity {
    private static final int NOTIFICATION_PERMISSION = 1;
    private TournamentAlerts alerts;
    private Switch toggle;
    private TextView status;
    private Task<Boolean> observed;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        Presentation.systemBars(this);
        alerts = ((RankingsApplication) getApplication()).tournamentAlerts();

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        ScrollView page = new ScrollView(this);
        page.setBackgroundColor(getColor(R.color.background));
        page.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(Presentation.dp(this, 16) + insets.getSystemWindowInsetLeft(),
                    Presentation.dp(this, 8) + insets.getSystemWindowInsetTop(),
                    Presentation.dp(this, 16) + insets.getSystemWindowInsetRight(),
                    insets.getSystemWindowInsetBottom());
            return insets;
        });

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        Button back = new Button(this);
        back.setContentDescription(getString(R.string.back));
        back.setText("‹");
        back.setTextSize(28);
        back.setPadding(0, 0, 0, 0);
        Presentation.action(back);
        back.setOnClickListener(view -> finish());
        bar.addView(back, new LinearLayout.LayoutParams(Presentation.dp(this, 48), -2));
        TextView title = Presentation.label(this, getString(R.string.tournament_alerts), 22);
        if (Build.VERSION.SDK_INT >= 28) title.setAccessibilityHeading(true);
        bar.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        panel.addView(bar);

        toggle = new Switch(this);
        toggle.setId(R.id.tournament_alerts_switch);
        toggle.setSaveEnabled(false);
        toggle.setText(R.string.tournament_alert_formats);
        toggle.setMinHeight(Presentation.dp(this, 56));
        toggle.setOnClickListener(view -> changePreference());
        panel.addView(toggle);
        status = Presentation.label(this, "", 14);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        panel.addView(status);
        page.addView(panel);
        setContentView(page);
    }

    @Override protected void onResume() {
        super.onResume();
        observe(alerts.pendingEnable());
        render();
    }

    private void changePreference() {
        boolean shouldEnable = toggle.isChecked();
        toggle.setChecked(alerts.isEnabled());
        if (!shouldEnable) {
            alerts.disable();
            render();
        } else if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] {Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_PERMISSION);
        } else {
            observe(alerts.enable());
            render();
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(requestCode, permissions, grants);
        if (requestCode != NOTIFICATION_PERMISSION || grants.length == 0) return;
        if (grants[0] == PackageManager.PERMISSION_GRANTED) {
            observe(alerts.enable());
            render();
        } else {
            status.setText(R.string.tournament_alert_permission_required);
        }
    }

    private void observe(Task<Boolean> task) {
        if (task == null || task == observed) return;
        observed = task;
        task.addOnCompleteListener(result -> {
            if (isDestroyed() || isFinishing()) return;
            render();
            if (!result.isSuccessful() || !result.getResult()) {
                status.setText(alerts.isAllowed() ? R.string.tournament_alert_subscribe_failed
                        : R.string.tournament_alert_permission_required);
            }
        });
    }

    private void render() {
        boolean pending = alerts.pendingEnable() != null;
        toggle.setChecked(alerts.isEnabled());
        toggle.setEnabled(alerts.isAvailable() && !pending);
        if (!alerts.isAvailable()) status.setText(R.string.tournament_alert_unavailable);
        else if (pending) status.setText(R.string.tournament_alert_subscribing);
        else status.setText("");
    }
}
