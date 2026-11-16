package az.tabletennis;

import android.content.Intent;
import android.os.Bundle;

public class PairResultsActivity extends SavedScreen<PairHistory> {
    private long pairId;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        pairId = getIntent().getLongExtra("pairId", 0);
        if (pairId <= 0) { finish(); return; }
        select("duo-results-" + pairId, PairHistory.class);
    }

    @Override protected Fetch<PairHistory> request(PairHistory previous, boolean more) {
        return () -> {
            DoublesRepository repository = new DoublesRepository(this, application.websiteUrl());
            DoublesPair pair = previous == null ? repository.pair(pairId) : previous.pair;
            PairMedals medals = more && previous != null ? previous.medals : repository.medals(pair);
            PairHistory history = new WebsiteSource(application.websiteUrl()).pairHistory(pair, previous, more);
            return new PairHistory(pair, history.matches, history.pages, history.nextPage, medals);
        };
    }

    private void showStanding() {
        SavedData<DoublesRanking> ranking = new SavedDataStore<>(this, "doubles-ranking", DoublesRanking.class).read();
        DoublesRanking.Row standing = null;
        if (ranking != null) {
            for (DoublesRanking.Row row : ranking.value.rows) {
                if (row.pair.id == pairId) { standing = row; break; }
            }
        }
        android.widget.TextView points = label(standing == null ? getString(R.string.points_unavailable)
                : getString(R.string.points, standing.points), 20);
        points.setId(R.id.profile_points);
        content.addView(points);
        android.widget.TextView position = label(standing == null ? getString(R.string.pair_position_unavailable)
                : getString(R.string.pair_position, standing.position), 20);
        position.setId(R.id.pair_position);
        content.addView(position);
        if (standing != null) {
            java.text.DateFormat format = java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM,
                    java.text.DateFormat.SHORT, java.util.Locale.forLanguageTag("az-AZ"));
            content.addView(label(getString(R.string.pair_ranking_updated_at,
                    format.format(new java.util.Date(ranking.updatedAt))), 13));
        }
    }

    private void showMedal(int id, int description, Integer count) {
        android.widget.TextView medal = label(getString(description,
                count == null ? getString(R.string.medal_unavailable) : Integer.toString(count)), 16);
        medal.setId(id);
        content.addView(medal);
    }

    @Override protected void render(PairHistory history) {
        content.addView(label(getString(R.string.pair_profile), 26));
        for (MatchDetails.PlayerLink player : new MatchDetails.PlayerLink[] {history.pair.first, history.pair.second}) {
            content.addView(button(player.name, () -> startActivity(new Intent(this, ProfileActivity.class)
                    .putExtra("playerId", player.id))));
        }
        showStanding();
        content.addView(label(getString(R.string.pair_medals), 20));
        showMedal(R.id.pair_gold, R.string.pair_gold, history.medals == null ? null : history.medals.gold);
        showMedal(R.id.pair_silver, R.string.pair_silver, history.medals == null ? null : history.medals.silver);
        showMedal(R.id.pair_bronze, R.string.pair_bronze, history.medals == null ? null : history.medals.bronze);
        content.addView(label(getString(R.string.match_history), 20));
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
