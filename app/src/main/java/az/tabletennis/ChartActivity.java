package az.tabletennis;

import android.os.Bundle;
import android.graphics.Typeface;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

public class ChartActivity extends SavedScreen<RatingHistory> {
    private long playerId;
    private int selected = -1;
    private final List<Button> observations = new ArrayList<>();
    private RatingChartView chart;
    private TextView selectedDate;
    private TextView selectedValue;

    @Override protected int detailTitle() { return R.string.rating_history; }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        playerId = getIntent().getLongExtra("playerId", 0);
        if (playerId <= 0) { finish(); return; }
        if (state != null) selected = state.getInt("observation", -1);
        int side = Math.max(0, (getResources().getDisplayMetrics().widthPixels - dp(32) - dp(640)) / 2);
        content.setPadding(side, 0, side, 0);
        select("chart-" + playerId, RatingHistory.class);
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putInt("observation", selected);
        super.onSaveInstanceState(state);
    }

    @Override protected Fetch<RatingHistory> request(RatingHistory previous, boolean more) {
        return () -> new WebsiteSource(application.websiteUrl()).ratingHistory(playerId);
    }

    @Override protected void render(RatingHistory history) {
        observations.clear();
        String name = getIntent().getStringExtra("playerName");
        if (name == null) {
            SavedData<PlayerProfile> profile = new SavedDataStore<>(this, "profile-" + playerId, PlayerProfile.class).read();
            if (profile != null) name = profile.value.name;
        }
        if (name != null) content.addView(heading(name));
        content.addView(secondary(getString(R.string.rating_history_subtitle)));
        content.addView(secondary(getString(R.string.chart_explanation)));
        if (history.observations.isEmpty()) {
            content.addView(heading(getString(R.string.no_rating_history)));
            return;
        }
        selected = selected < 0 ? history.observations.size() - 1 : Math.min(selected, history.observations.size() - 1);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(12), dp(14), dp(12), dp(8));
        android.graphics.drawable.GradientDrawable background = Presentation.shape(this, R.color.surface);
        background.setStroke(dp(1), getColor(R.color.line));
        panel.setBackground(background);
        chart = new RatingChartView(this, history);
        panel.addView(chart, new LinearLayout.LayoutParams(-1, -2));
        selectedDate = secondary("");
        selectedDate.setId(R.id.chart_selected_date);
        selectedValue = label("", 18);
        selectedValue.setId(R.id.chart_selected_value);
        selectedValue.setTextColor(getColor(R.color.primary));
        selectedValue.setTypeface(null, Typeface.BOLD);
        LinearLayout selection = new LinearLayout(this);
        selection.setOrientation(LinearLayout.VERTICAL);
        selection.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        selection.addView(selectedDate);
        selection.addView(selectedValue);
        panel.addView(selection);
        content.addView(panel);
        if (history.observations.size() == 1) content.addView(secondary(getString(R.string.single_rating_observation)));
        TextView title = heading(getString(R.string.observations));
        title.setPadding(0, dp(22), 0, dp(8));
        content.addView(title);
        LinearLayout rows = new LinearLayout(this);
        rows.setId(R.id.chart_observations);
        rows.setOrientation(LinearLayout.VERTICAL);
        content.addView(rows);
        for (int i = 0; i < history.observations.size(); i++) {
            RatingHistory.Observation point = history.observations.get(i);
            int index = i;
            Button row = button(getString(R.string.history_point, point.date, point.points), () -> inspect(history, index));
            row.setStateListAnimator(null);
            row.setTextSize(16);
            row.setGravity(android.view.Gravity.START | android.view.Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12), dp(12), dp(12), dp(12));
            rows.addView(row, new LinearLayout.LayoutParams(-1, -2));
            observations.add(row);
            View divider = new View(this);
            divider.setBackgroundColor(getColor(R.color.line));
            rows.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));
        }
        inspect(history, selected);
    }

    private void inspect(RatingHistory history, int index) {
        selected = index;
        RatingHistory.Observation point = history.observations.get(index);
        selectedDate.setText(point.date);
        selectedValue.setText(getString(R.string.observation_points, point.points));
        chart.select(index);
        for (int i = 0; i < observations.size(); i++) {
            Button row = observations.get(i);
            row.setSelected(i == index);
            row.setTypeface(null, i == index ? Typeface.BOLD : Typeface.NORMAL);
            row.setBackgroundTintList(android.content.res.ColorStateList.valueOf(getColor(i == index ? R.color.soft : R.color.surface)));
        }
    }

    private TextView heading(String text) {
        TextView label = label(text, 20);
        label.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        if (android.os.Build.VERSION.SDK_INT >= 28) label.setAccessibilityHeading(true);
        return label;
    }

    private TextView secondary(String text) {
        TextView label = label(text, 13);
        label.setTextColor(getColor(R.color.secondary));
        return label;
    }
}
