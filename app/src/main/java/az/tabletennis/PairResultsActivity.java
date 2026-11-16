package az.tabletennis;

import android.content.Intent;
import android.os.Bundle;

public class PairResultsActivity extends SavedScreen<PairHistory> {
    private long pairId;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        pairId = getIntent().getLongExtra("pairId", 0);
        select("duo-results-" + pairId, PairHistory.class);
    }

    @Override protected Fetch<PairHistory> request(PairHistory previous, boolean more) {
        return () -> {
            DoublesPair pair = previous == null
                    ? new DoublesRepository(this, application.websiteUrl()).pair(pairId) : previous.pair;
            return new WebsiteSource(application.websiteUrl()).pairHistory(pair, previous, more);
        };
    }

    @Override protected void render(PairHistory history) {
        content.addView(label(getString(R.string.pair_results), 26));
        for (MatchDetails.PlayerLink player : new MatchDetails.PlayerLink[] {history.pair.first, history.pair.second}) {
            content.addView(button(player.name, () -> startActivity(new Intent(this, ProfileActivity.class)
                    .putExtra("playerId", player.id))));
        }
        content.addView(label(getString(R.string.pair_history_coverage), 14));
        if (history.matches.isEmpty()) content.addView(label(getString(R.string.no_history), 16));
        for (MatchSummary match : history.matches) {
            content.addView(button(match.display(), () -> startActivity(new Intent(this, MatchActivity.class)
                    .putExtra("matchId", match.id))));
        }
        if (history.nextPage != null) {
            android.widget.Button more = button(getString(R.string.load_more), () -> refresh(true));
            more.setEnabled(application.isOnline());
            content.addView(more);
        }
    }
}
