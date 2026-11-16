package az.tabletennis;

import android.os.Bundle;
import java.io.IOException;

public class ChartActivity extends SavedScreen<RatingHistory> {
    private long playerId;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        playerId = getIntent().getLongExtra("playerId", 0);
        if (playerId <= 0) { finish(); return; }
        select("chart-" + playerId, RatingHistory.class);
    }

    @Override protected Fetch<RatingHistory> request(RatingHistory previous, boolean more) {
        return () -> new WebsiteSource(application.websiteUrl()).ratingHistory(playerId);
    }

    @Override protected void render(RatingHistory history) {
        content.addView(label(getString(R.string.rating_history), 26));
        content.addView(label(getString(R.string.chart_explanation), 14));
        if (history.observations.isEmpty()) content.addView(label(getString(R.string.no_rating_history), 16));
        else {
            content.addView(new RatingChartView(this, history), new android.widget.LinearLayout.LayoutParams(-1, dp(200)));
            for (RatingHistory.Observation point : history.observations) {
                content.addView(label(getString(R.string.history_point, point.date, point.points), 16));
            }
        }
    }
}
