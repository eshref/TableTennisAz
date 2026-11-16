package az.tabletennis;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.ScrollView;
import android.widget.TextView;

/** Main destinations share a compact bar and a modal, accessible navigation drawer. */
final class MainScreenChrome {
    final LinearLayout body;
    final Button refresh;
    final TextView updated;
    private final Activity activity;
    private final boolean rankings;
    private final FrameLayout frame;
    private final LinearLayout page;
    private final Button menu;
    private View drawer;
    private android.window.OnBackInvokedCallback backCallback;

    MainScreenChrome(Activity activity, boolean rankings, Runnable refreshAction) {
        this.activity = activity;
        Presentation.systemBars(activity);
        this.rankings = rankings;
        frame = new FrameLayout(activity);
        frame.setFocusableInTouchMode(true);
        frame.setBackgroundColor(activity.getColor(R.color.background));
        frame.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        page = new LinearLayout(activity);
        page.setOrientation(LinearLayout.VERTICAL);
        frame.addView(page, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout bar = new LinearLayout(activity);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(8), dp(4), dp(8), dp(4));
        menu = action("☰", R.string.open_menu, this::openDrawer);
        menu.setId(R.id.open_menu);
        bar.addView(menu, new LinearLayout.LayoutParams(dp(48), -2));
        TextView title = Presentation.label(activity, activity.getString(rankings ? R.string.rankings_title : R.string.results_title), 22);
        if (Build.VERSION.SDK_INT >= 28) title.setAccessibilityHeading(true);
        bar.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        refresh = action("↻", R.string.refresh, refreshAction);
        refresh.setId(R.id.refresh);
        bar.addView(refresh, new LinearLayout.LayoutParams(dp(48), -2));
        page.addView(bar);
        updated = Presentation.label(activity, "", 12);
        updated.setId(R.id.updated);
        updated.setTextColor(activity.getColor(R.color.secondary));
        updated.setPadding(dp(20), 0, dp(20), dp(8));
        page.addView(updated);
        body = new LinearLayout(activity);
        body.setId(R.id.main_body);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(18), 0, dp(18), 0);
        page.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
        activity.setContentView(frame);
        frame.requestFocus();
    }

    boolean closeDrawer() {
        if (drawer == null) return false;
        frame.removeView(drawer);
        drawer = null;
        page.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
        page.setDescendantFocusability(android.view.ViewGroup.FOCUS_AFTER_DESCENDANTS);
        if (Build.VERSION.SDK_INT >= 33 && backCallback != null) {
            activity.getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(backCallback);
            backCallback = null;
        }
        menu.requestFocus();
        return true;
    }

    boolean isDrawerOpen() { return drawer != null; }

    void openDrawer() {
        if (drawer != null) return;
        activity.getSystemService(android.view.inputmethod.InputMethodManager.class)
                .hideSoftInputFromWindow(menu.getWindowToken(), 0);
        FrameLayout overlay = new FrameLayout(activity);
        overlay.setId(R.id.drawer);
        overlay.setBackgroundColor(0x66000000);
        overlay.setOnClickListener(view -> closeDrawer());
        ScrollView scroll = new ScrollView(activity);
        scroll.setBackgroundColor(activity.getColor(R.color.surface));
        scroll.setOnClickListener(view -> { });
        int width = Math.min(dp(320), activity.getResources().getDisplayMetrics().widthPixels - dp(40));
        overlay.addView(scroll, new FrameLayout.LayoutParams(width, -1, Gravity.START));
        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(16), dp(12), dp(16), dp(24));
        panel.setOnClickListener(view -> { });
        scroll.addView(panel);
        LinearLayout heading = new LinearLayout(activity);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        TextView brand = Presentation.label(activity, activity.getString(R.string.app_name), 20);
        heading.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));
        Button close = action("×", R.string.close_menu, this::closeDrawer);
        close.setId(R.id.close_menu);
        heading.addView(close, new LinearLayout.LayoutParams(dp(48), -2));
        panel.addView(heading);
        TextView caption = Presentation.label(activity, activity.getString(R.string.drawer_caption), 14);
        caption.setTextColor(activity.getColor(R.color.secondary));
        panel.addView(caption);
        panel.addView(destination(R.string.rankings_title, R.id.destination_rankings, true));
        panel.addView(destination(R.string.results_title, R.id.destination_results, false));
        drawer = overlay;
        page.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        page.setDescendantFocusability(android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        frame.addView(overlay, new FrameLayout.LayoutParams(-1, -1));
        if (Build.VERSION.SDK_INT >= 28) panel.setAccessibilityPaneTitle(activity.getString(R.string.open_menu));
        close.setFocusableInTouchMode(true);
        close.requestFocus();
        if (Build.VERSION.SDK_INT >= 33) {
            backCallback = this::closeDrawer;
            activity.getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    android.window.OnBackInvokedDispatcher.PRIORITY_OVERLAY, backCallback);
        }
    }

    private RadioButton destination(int title, int id, boolean targetRankings) {
        RadioButton choice = new RadioButton(activity);
        choice.setId(id);
        choice.setText(title);
        Presentation.segment(choice);
        choice.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        choice.setChecked(rankings == targetRankings);
        choice.setOnClickListener(view -> {
            closeDrawer();
            if (rankings == targetRankings) return;
            Intent intent = new Intent(activity, targetRankings ? RankingsActivity.class : ResultsActivity.class);
            if (targetRankings) intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            activity.startActivity(intent);
        });
        return choice;
    }

    private Button action(String symbol, int description, Runnable action) {
        Button button = new Button(activity);
        button.setText(symbol);
        button.setContentDescription(activity.getString(description));
        button.setTooltipText(activity.getString(description));
        button.setTextSize(24);
        button.setPadding(0, 0, 0, 0);
        Presentation.action(button);
        button.setOnClickListener(view -> action.run());
        return button;
    }

    private int dp(int value) { return Presentation.dp(activity, value); }
}
