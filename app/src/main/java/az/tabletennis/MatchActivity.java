package az.tabletennis;

import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.List;

public class MatchActivity extends SavedScreen<MatchDetails> {
    private long matchId;

    @Override protected int detailTitle() { return R.string.match_details; }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        matchId = getIntent().getLongExtra("matchId", 0);
        if (matchId <= 0) { finish(); return; }
        int margin = Math.max(0, (getResources().getDisplayMetrics().widthPixels - dp(32) - dp(640)) / 2);
        content.setPadding(margin, 0, margin, 0);
        select("match-" + matchId, MatchDetails.class);
    }

    @Override protected Fetch<MatchDetails> request(MatchDetails previous, boolean more) {
        application.photos().refreshed();
        return () -> new WebsiteSource(application.websiteUrl()).match(matchId);
    }

    @Override protected void render(MatchDetails match) {
        content.addView(secondary(getString(match.left.size() == 2 ? R.string.match_doubles : R.string.match_singles)));
        if (!match.eventName.isEmpty()) {
            TextView event = label(match.eventName, 22);
            event.setTypeface(null, Typeface.BOLD);
            if (android.os.Build.VERSION.SDK_INT >= 28) event.setAccessibilityHeading(true);
            content.addView(event);
        }
        String when = match.date + (match.time.isEmpty() ? "" : " · " + match.time);
        if (!when.isEmpty()) content.addView(secondary(when));
        LinearLayout scoreboard = new LinearLayout(this);
        scoreboard.setId(R.id.match_score);
        scoreboard.setOrientation(LinearLayout.VERTICAL);
        scoreboard.setPadding(dp(16), dp(2), dp(16), dp(2));
        GradientDrawable panel = Presentation.shape(this, R.color.surface);
        panel.setCornerRadius(dp(18));
        panel.setStroke(dp(1), getColor(R.color.line));
        scoreboard.setBackground(panel);
        LinearLayout.LayoutParams spacing = new LinearLayout.LayoutParams(-1, -2);
        spacing.topMargin = dp(18);
        spacing.bottomMargin = dp(18);
        content.addView(scoreboard, spacing);
        String[] scores = match.score.split("\\s*-\\s*", -1);
        boolean valid = scores.length == 2 && scores[0].matches("\\d+") && scores[1].matches("\\d+");
        int comparison = valid ? new java.math.BigInteger(scores[0]).compareTo(new java.math.BigInteger(scores[1])) : 0;
        scoreboard.addView(side(match.left, R.id.participants_left, R.id.match_score_left,
                valid ? scores[0] : match.score, comparison > 0));
        View divider = new View(this);
        divider.setBackgroundColor(getColor(R.color.line));
        scoreboard.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));
        scoreboard.addView(side(match.right, R.id.participants_right, R.id.match_score_right,
                valid ? scores[1] : "", comparison < 0));
        content.addView(secondary(getString(R.string.match_profile_hint)));
    }

    private LinearLayout side(List<MatchDetails.PlayerLink> players, int participantsId, int scoreId, String score, boolean winner) {
        LinearLayout side = new LinearLayout(this);
        side.setGravity(Gravity.CENTER_VERTICAL);
        side.setPadding(0, dp(17), 0, dp(17));
        LinearLayout people = new LinearLayout(this);
        people.setOrientation(LinearLayout.VERTICAL);
        if (winner) {
            TextView caption = secondary(getString(R.string.match_winner));
            caption.setTextColor(getColor(R.color.primary));
            caption.setTypeface(null, Typeface.BOLD);
            people.addView(caption);
        }
        LinearLayout participants = new LinearLayout(this);
        participants.setOrientation(LinearLayout.VERTICAL);
        participants.setId(participantsId);
        for (MatchDetails.PlayerLink player : players) participants.addView(participant(player, winner));
        people.addView(participants);
        side.addView(people, new LinearLayout.LayoutParams(0, -2, 1));
        TextView points = label(score, 38);
        points.setId(scoreId);
        points.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        points.setGravity(Gravity.CENTER);
        points.setPadding(dp(12), 0, 0, 0);
        points.setContentDescription(getString(R.string.match_side_score,
                String.join(" / ", players.stream().map(player -> player.name).toArray(String[]::new)), score));
        side.addView(points, new LinearLayout.LayoutParams(-2, -2));
        return side;
    }

    private LinearLayout participant(MatchDetails.PlayerLink player, boolean winner) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        Runnable open = () -> startActivity(new Intent(this, ProfileActivity.class).putExtra("playerId", player.id));
        PlayerPortraitView portrait = new PlayerPortraitView(this);
        portrait.setId(android.R.id.icon);
        portrait.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams photoSize = new LinearLayout.LayoutParams(dp(34), dp(34));
        photoSize.setMarginEnd(dp(9));
        row.addView(portrait, photoSize);
        Button name = button(player.name, open);
        name.setTextSize(16);
        name.setTypeface(null, winner ? Typeface.BOLD : Typeface.NORMAL);
        name.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        name.setPadding(0, dp(7), 0, dp(7));
        name.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_chevron, 0);
        name.setCompoundDrawablePadding(dp(6));
        row.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
        row.setOnClickListener(view -> open.run());
        // The named button is the single accessible profile action; the portrait is decorative.
        row.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        application.photos().bind(portrait, player.id, player.photoUrl, application.isOnline());
        return row;
    }

    private TextView secondary(String text) {
        TextView label = label(text, 13);
        label.setTextColor(getColor(R.color.secondary));
        return label;
    }
}
