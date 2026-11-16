package az.tabletennis;

import android.content.Intent;
import android.os.Bundle;
import java.io.IOException;
import java.util.List;

public class MatchActivity extends SavedScreen<MatchDetails> {
    private long matchId;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        matchId = getIntent().getLongExtra("matchId", 0);
        if (matchId <= 0) { finish(); return; }
        select("match-" + matchId, MatchDetails.class);
    }

    @Override protected Fetch<MatchDetails> request(MatchDetails previous, boolean more) {
        return () -> new WebsiteSource(application.websiteUrl()).match(matchId);
    }

    @Override protected void render(MatchDetails match) {
        content.addView(label(getString(R.string.match_details), 26));
        content.addView(label(match.eventName, 18));
        content.addView(label(match.date, 16));
        if (!match.time.isEmpty()) content.addView(label(match.time, 16));
        addParticipants(match.left, R.id.participants_left);
        android.widget.TextView score = label(match.score, 28);
        score.setId(R.id.match_score);
        content.addView(score);
        addParticipants(match.right, R.id.participants_right);
    }

    private void addParticipants(List<MatchDetails.PlayerLink> players, int id) {
        android.widget.LinearLayout side = new android.widget.LinearLayout(this);
        side.setOrientation(android.widget.LinearLayout.VERTICAL);
        side.setId(id);
        for (MatchDetails.PlayerLink player : players) {
            side.addView(button(player.name, () -> startActivity(new Intent(this, ProfileActivity.class).putExtra("playerId", player.id))));
        }
        content.addView(side);
    }
}
