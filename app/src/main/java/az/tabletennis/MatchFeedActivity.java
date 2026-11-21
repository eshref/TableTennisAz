package az.tabletennis;

import android.content.Intent;
import android.os.Build;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.time.format.DateTimeParseException;

abstract class MatchFeedActivity extends SavedScreen<ResultsFeed> {
    @Override protected void render(ResultsFeed feed) {
        LinearLayout rows = new LinearLayout(this);
        rows.setId(R.id.result_rows);
        rows.setOrientation(LinearLayout.VERTICAL);
        String previousDay = null;
        for (MatchSummary match : feed.matches) {
            String day = dateGroupKey(match.date);
            if (!day.isEmpty() && !day.equals(previousDay)) {
                TextView date = label(match.date, 12);
                date.setTextColor(getColor(R.color.secondary));
                date.setPadding(dp(2), dp(12), dp(2), dp(8));
                if (Build.VERSION.SDK_INT >= 28) date.setAccessibilityHeading(true);
                rows.addView(date);
            }
            previousDay = day;
            rows.addView(new MatchCard(this, match,
                    () -> startActivity(new Intent(this, MatchActivity.class).putExtra("matchId", match.id))));
        }
        content.addView(rows);
        if (feed.hasMore()) {
            Button more = button(getString(R.string.load_more), () -> refresh(true));
            more.setEnabled(application.isOnline());
            content.addView(more);
        }
    }

    private String dateGroupKey(String date) {
        if (date.isEmpty()) return date;
        try { return MatchSummary.sourceDay(date).toString(); }
        catch (DateTimeParseException legacyDate) { return date; }
    }
}
