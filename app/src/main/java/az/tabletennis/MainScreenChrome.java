package az.tabletennis;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.net.Uri;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.function.Consumer;

/** Main destinations share a compact bar and a modal, accessible navigation drawer. */
final class MainScreenChrome {
    enum Destination {
        RANKINGS(R.string.rankings_title, R.id.destination_rankings, R.drawable.ic_list, RankingsActivity.class),
        SINGLES_TOURNAMENTS(R.string.singles_tournaments, R.id.destination_singles_tournaments,
                R.drawable.ic_trophy, SinglesTournamentsActivity.class),
        DOUBLES_TOURNAMENTS(R.string.doubles_tournaments, R.id.destination_doubles_tournaments,
                R.drawable.ic_trophy, DoublesTournamentsActivity.class),
        DUELS(R.string.duel_results, R.id.destination_duels, R.drawable.ic_trophy, DuelsActivity.class);

        final int title;
        final int id;
        final int icon;
        final Class<? extends Activity> screen;

        Destination(int title, int id, int icon, Class<? extends Activity> screen) {
            this.title = title;
            this.id = id;
            this.icon = icon;
            this.screen = screen;
        }
    }

    final LinearLayout body;
    final Button refresh;
    final TextView updated;
    private final Activity activity;
    private final Destination currentDestination;
    private final FrameLayout frame;
    private final LinearLayout page;
    private final Button menu;
    private final View menuDot;
    private final TournamentCatalogueRepository catalogues;
    private final Consumer<SavedData<TournamentCatalogue>> tournamentDots = this::updateTournamentDots;
    private SavedData<TournamentCatalogue> catalogue;
    private View drawer;
    private android.window.OnBackInvokedCallback backCallback;

    MainScreenChrome(Activity activity, Destination currentDestination, Runnable refreshAction) {
        this.activity = activity;
        catalogues = ((RankingsApplication) activity.getApplication()).tournamentCatalogues();
        Presentation.systemBars(activity);
        this.currentDestination = currentDestination;
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
        FrameLayout menuControl = new FrameLayout(activity);
        menuControl.addView(menu, new FrameLayout.LayoutParams(-1, -2));
        menuDot = redDot(R.id.menu_new_tournaments_dot);
        FrameLayout.LayoutParams menuDotPosition = new FrameLayout.LayoutParams(dp(8), dp(8), Gravity.TOP | Gravity.END);
        menuDotPosition.topMargin = dp(9);
        menuDotPosition.setMarginEnd(dp(9));
        menuControl.addView(menuDot, menuDotPosition);
        bar.addView(menuControl, new LinearLayout.LayoutParams(dp(48), -2));
        TextView title = Presentation.label(activity, activity.getString(currentDestination.title), 22);
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

    void observeTournamentDots() { catalogues.observe(tournamentDots); }

    void stopObservingTournamentDots() { catalogues.removeObserver(tournamentDots); }

    private void updateTournamentDots(SavedData<TournamentCatalogue> snapshot) {
        catalogue = snapshot;
        boolean singles = catalogues.hasNew(snapshot, TournamentCatalogue.Format.SINGLES);
        boolean doubles = catalogues.hasNew(snapshot, TournamentCatalogue.Format.DOUBLES);
        boolean hasNewTournaments = singles || doubles;
        menuDot.setVisibility(hasNewTournaments ? View.VISIBLE : View.GONE);
        menu.setContentDescription(hasNewTournaments ? newTournamentsDescription(R.string.open_menu)
                : activity.getString(R.string.open_menu));
        if (drawer != null) {
            updateDestinationDot(Destination.SINGLES_TOURNAMENTS, singles);
            updateDestinationDot(Destination.DOUBLES_TOURNAMENTS, doubles);
        }
    }

    private void updateDestinationDot(Destination target, boolean visible) {
        RadioButton choice = drawer.findViewById(target.id);
        View dot = ((View) choice.getParent()).findViewById(R.id.new_tournaments_dot);
        dot.setVisibility(visible ? View.VISIBLE : View.GONE);
        choice.setContentDescription(visible ? newTournamentsDescription(target.title) : null);
    }

    private String newTournamentsDescription(int label) {
        return activity.getString(R.string.new_tournaments_description, activity.getString(label));
    }

    private View redDot(int id) {
        View dot = new View(activity);
        dot.setId(id);
        dot.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(activity.getColor(R.color.new_tournament_dot));
        dot.setBackground(circle);
        dot.setVisibility(View.GONE);
        return dot;
    }

    void openDrawer() {
        if (drawer != null) return;
        activity.getSystemService(android.view.inputmethod.InputMethodManager.class)
                .hideSoftInputFromWindow(menu.getWindowToken(), 0);
        FrameLayout overlay = new FrameLayout(activity);
        overlay.setId(R.id.drawer);
        overlay.setBackgroundColor(0x5500190d);
        overlay.setOnClickListener(view -> closeDrawer());
        ScrollView scroll = new ScrollView(activity);
        GradientDrawable surface = Presentation.shape(activity, R.color.surface);
        surface.setCornerRadii(new float[] {0, 0, dp(24), dp(24), dp(24), dp(24), 0, 0});
        scroll.setBackground(surface);
        scroll.setElevation(dp(12));
        scroll.setFillViewport(true);
        scroll.setOnClickListener(view -> { });
        int available = frame.getWidth() > 0 ? frame.getWidth() - frame.getPaddingLeft() - frame.getPaddingRight()
                : activity.getResources().getDisplayMetrics().widthPixels;
        int width = Math.min(dp(310), Math.round(available * .85f));
        overlay.addView(scroll, new FrameLayout.LayoutParams(width, -1, Gravity.START));
        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(16), dp(18), dp(16), dp(18));
        panel.setOnClickListener(view -> { });
        scroll.addView(panel);
        LinearLayout heading = new LinearLayout(activity);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        TextView brand = Presentation.label(activity, activity.getString(R.string.app_name), 16);
        brand.setTextColor(activity.getColor(R.color.primary));
        brand.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        brand.setPadding(dp(9), 0, 0, 0);
        if (Build.VERSION.SDK_INT >= 28) brand.setAccessibilityHeading(true);
        heading.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));
        Button close = action("×", R.string.close_menu, this::closeDrawer);
        close.setId(R.id.close_menu);
        heading.addView(close, new LinearLayout.LayoutParams(dp(48), -2));
        LinearLayout.LayoutParams headingSize = new LinearLayout.LayoutParams(-1, -2);
        headingSize.bottomMargin = dp(26);
        panel.addView(heading, headingSize);
        TextView caption = Presentation.label(activity, activity.getString(R.string.drawer_caption), 12);
        caption.setTextColor(activity.getColor(R.color.secondary));
        caption.setPadding(dp(12), 0, dp(12), dp(12));
        panel.addView(caption);
        for (Destination target : Destination.values()) {
            LinearLayout.LayoutParams destinationSize = new LinearLayout.LayoutParams(-1, -2);
            destinationSize.bottomMargin = dp(6);
            panel.addView(destinationRow(target), destinationSize);
        }
        panel.addView(privacyPolicyLink());
        panel.addView(new View(activity), new LinearLayout.LayoutParams(1, 0, 1));
        TextView footer = Presentation.label(activity, activity.getString(R.string.drawer_footer), 12);
        footer.setTextColor(activity.getColor(R.color.secondary));
        footer.setPadding(dp(12), dp(20), dp(12), dp(6));
        panel.addView(footer);
        drawer = overlay;
        updateTournamentDots(catalogue);
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

    private View destinationRow(Destination target) {
        RadioButton choice = destination(target);
        if (target != Destination.SINGLES_TOURNAMENTS && target != Destination.DOUBLES_TOURNAMENTS) return choice;
        choice.setPaddingRelative(dp(16), dp(13), dp(32), dp(13));
        FrameLayout row = new FrameLayout(activity);
        row.addView(choice, new FrameLayout.LayoutParams(-1, -2));
        FrameLayout.LayoutParams position = new FrameLayout.LayoutParams(dp(8), dp(8), Gravity.CENTER_VERTICAL | Gravity.END);
        position.setMarginEnd(dp(16));
        row.addView(redDot(R.id.new_tournaments_dot), position);
        return row;
    }

    private Button privacyPolicyLink() {
        Button link = new Button(activity);
        link.setId(R.id.privacy_policy);
        link.setText(R.string.privacy_policy);
        link.setTextSize(14);
        link.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        link.setPadding(dp(16), dp(8), dp(16), dp(8));
        Presentation.action(link);
        link.setOnClickListener(view -> {
            closeDrawer();
            Uri policy = Uri.parse(activity.getString(R.string.privacy_policy_url));
            try {
                activity.startActivity(new Intent(Intent.ACTION_VIEW, policy));
            } catch (ActivityNotFoundException unavailable) {
                Toast.makeText(activity, R.string.privacy_policy_browser_unavailable, Toast.LENGTH_SHORT).show();
            }
        });
        return link;
    }

    private RadioButton destination(Destination target) {
        RadioButton choice = new RadioButton(activity);
        choice.setId(target.id);
        choice.setText(target.title);
        choice.setButtonDrawable(null);
        choice.setTextSize(14);
        choice.setPadding(dp(16), dp(13), dp(16), dp(13));
        choice.setMinHeight(dp(48));
        ColorStateList colors = new ColorStateList(new int[][] {new int[] {android.R.attr.state_checked}, new int[] {}},
                new int[] {activity.getColor(R.color.primary), activity.getColor(R.color.text)});
        choice.setTextColor(colors);
        StateListDrawable backgrounds = new StateListDrawable();
        GradientDrawable selected = Presentation.shape(activity, R.color.soft);
        selected.setCornerRadius(dp(14));
        backgrounds.addState(new int[] {android.R.attr.state_checked}, selected);
        backgrounds.addState(new int[] {}, new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        GradientDrawable mask = Presentation.shape(activity, R.color.surface);
        mask.setCornerRadius(dp(14));
        choice.setBackground(new RippleDrawable(ColorStateList.valueOf(activity.getColor(R.color.soft)), backgrounds, mask));
        choice.setCompoundDrawablesRelativeWithIntrinsicBounds(target.icon, 0, 0, 0);
        choice.setCompoundDrawablePadding(dp(14));
        choice.setCompoundDrawableTintList(colors);
        choice.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        boolean selectedDestination = currentDestination == target;
        choice.setChecked(selectedDestination);
        choice.setTypeface(Typeface.create(selectedDestination ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
        choice.setOnClickListener(view -> navigateTo(target));
        return choice;
    }

    private void navigateTo(Destination target) {
        closeDrawer();
        if (currentDestination == target) return;
        Intent intent = new Intent(activity, target.screen);
        if (target == Destination.RANKINGS) intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        activity.startActivity(intent);
        if (currentDestination != Destination.RANKINGS && target != Destination.RANKINGS) activity.finish();
    }

    private Button action(String symbol, int description, Runnable action) {
        Button button = new Button(activity);
        button.setText(symbol);
        button.setContentDescription(activity.getString(description));
        button.setTooltipText(activity.getString(description));
        button.setTextSize(24);
        button.setPadding(0, 0, 0, 0);
        Presentation.action(button);
        GradientDrawable mask = Presentation.shape(activity, R.color.surface);
        mask.setCornerRadius(dp(24));
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(activity.getColor(R.color.soft)), null, mask));
        button.setOnClickListener(view -> action.run());
        return button;
    }

    private int dp(int value) { return Presentation.dp(activity, value); }
}
