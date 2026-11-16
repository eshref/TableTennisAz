package az.tabletennis;

import android.os.Bundle;
import java.io.IOException;

public class ProfileActivity extends SavedScreen<PlayerProfile> {
    private long playerId;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        playerId = getIntent().getLongExtra("playerId", 0);
        if (playerId <= 0) { finish(); return; }
        select("profile-" + playerId, PlayerProfile.class);
    }

    @Override protected Fetch<PlayerProfile> request(PlayerProfile previous, boolean more) {
        return () -> {
            WebsiteSource.ProfileLoad load = new WebsiteSource(application.websiteUrl()).profile(playerId, previous, more);
            for (MatchDetails details : load.verifiedMatches) {
                new SavedDataStore<>(this, "match-" + details.id, MatchDetails.class)
                        .write(new SavedData<>(details, application.clock().millis()));
            }
            return load.profile;
        };
    }

    @Override protected void render(PlayerProfile profile) {
        android.widget.TextView name = label(profile.name, 26);
        name.setId(R.id.profile_name);
        content.addView(name);
        android.widget.TextView points = label(profile.points == null ? getString(R.string.points_unavailable)
                : getString(R.string.points, profile.points), 20);
        points.setId(R.id.profile_points);
        content.addView(points);
        content.addView(button(getString(R.string.rating_history), () -> startActivity(new android.content.Intent(this, ChartActivity.class)
                .putExtra("playerId", profile.id))));
        content.addView(label(getString(R.string.match_history), 20));
        content.addView(label(getString(R.string.history_coverage), 14));
        if (profile.history.isEmpty()) content.addView(label(getString(R.string.no_history), 16));
        for (MatchSummary match : profile.history) {
            content.addView(button(match.display(), () -> startActivity(new android.content.Intent(this, MatchActivity.class)
                    .putExtra("matchId", match.id))));
        }
        if (profile.nextHistoryPage != null) {
            android.widget.Button more = button(getString(R.string.load_more), () -> refresh(true));
            more.setEnabled(application.isOnline());
            content.addView(more);
        }
    }

    @Override protected long retrievalTime(long previousUpdate, boolean more) {
        return more && previousUpdate > 0 ? previousUpdate : application.clock().millis();
    }
}
