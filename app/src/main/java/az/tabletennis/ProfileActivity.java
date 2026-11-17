package az.tabletennis;

import android.os.Bundle;
import android.content.Intent;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

public class ProfileActivity extends SavedScreen<PlayerProfile> {
    private long playerId;

    @Override protected int detailTitle() { return R.string.player_profile; }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        playerId = getIntent().getLongExtra("playerId", 0);
        if (playerId <= 0) { finish(); return; }
        int side = Math.max(0, (getResources().getDisplayMetrics().widthPixels - dp(32) - dp(640)) / 2);
        content.setPadding(side, 0, side, 0);
        select("profile-" + playerId, PlayerProfile.class);
    }

    @Override protected Fetch<PlayerProfile> request(PlayerProfile previous, boolean more) {
        if (!more) application.photos().refreshed();
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
        LinearLayout identity = new LinearLayout(this);
        identity.setGravity(Gravity.CENTER_VERTICAL);
        identity.setPadding(0, dp(12), 0, dp(18));
        PlayerPortraitView portrait = new PlayerPortraitView(this);
        portrait.setId(android.R.id.icon);
        portrait.setImportantForAccessibility(android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams portraitSize = new LinearLayout.LayoutParams(dp(76), dp(76));
        portraitSize.setMarginEnd(dp(15));
        identity.addView(portrait, portraitSize);
        LinearLayout names = new LinearLayout(this);
        names.setOrientation(LinearLayout.VERTICAL);
        TextView name = label(profile.name, 20);
        name.setId(R.id.profile_name);
        name.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        heading(name);
        names.addView(name);
        names.addView(secondary(getString(profile.amateurPosition == null ? R.string.player : R.string.amateur_player)));
        identity.addView(names, new LinearLayout.LayoutParams(0, -2, 1));
        content.addView(identity);
        application.photos().bind(portrait, profile.id, profile.photoUrl, application.isOnline());

        LinearLayout stats = new LinearLayout(this);
        stats.setPadding(dp(16), dp(12), dp(4), dp(12));
        stats.setBackground(Presentation.shape(this, R.color.soft));
        TextView points = label(profile.points == null ? getString(R.string.points_unavailable)
                : getString(R.string.points, profile.points), profile.points == null ? 16 : 28);
        points.setId(R.id.profile_points);
        stats.addView(stat(points, getString(R.string.current_points)), new LinearLayout.LayoutParams(0, -2, 1));
        TextView position = label(profile.amateurPosition == null ? "—"
                : getString(R.string.rank_number, profile.amateurPosition), 28);
        position.setId(R.id.profile_position);
        stats.addView(stat(position, getString(profile.amateurPosition == null
                ? R.string.position_unavailable : R.string.amateur_position)), new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout.LayoutParams statsSpacing = new LinearLayout.LayoutParams(-1, -2);
        statsSpacing.bottomMargin = dp(8);
        content.addView(stats, statsSpacing);
        android.widget.Button chart = button(getString(R.string.rating_history), () -> startActivity(new Intent(this, ChartActivity.class)
                .putExtra("playerId", profile.id)));
        chart.setId(R.id.profile_history_action);
        chart.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        chart.setPadding(dp(14), dp(10), dp(14), dp(10));
        chart.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_chart, 0, R.drawable.ic_chevron, 0);
        chart.setCompoundDrawablePadding(dp(10));
        content.addView(chart, new LinearLayout.LayoutParams(-1, -2));
        android.view.View divider = new android.view.View(this);
        divider.setBackgroundColor(getColor(R.color.line));
        content.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));
        TextView history = label(getString(R.string.match_history), 20);
        history.setPadding(0, dp(24), 0, dp(4));
        history.setTypeface(null, Typeface.BOLD);
        heading(history);
        content.addView(history);
        content.addView(secondary(getString(R.string.history_coverage)));
        if (profile.history.isEmpty()) content.addView(label(getString(R.string.no_history), 16));
        for (MatchSummary match : profile.history) {
            content.addView(new MatchCard(this, match, () -> startActivity(new Intent(this, MatchActivity.class)
                    .putExtra("matchId", match.id))));
        }
        if (profile.nextHistoryPage != null) {
            android.widget.Button more = button(getString(R.string.load_more), () -> refresh(true));
            more.setEnabled(application.isOnline());
            content.addView(more);
        }
    }

    private LinearLayout stat(TextView value, String caption) {
        LinearLayout stat = new LinearLayout(this);
        stat.setOrientation(LinearLayout.VERTICAL);
        stat.setPadding(0, 0, dp(12), 0);
        value.setTypeface(null, Typeface.BOLD);
        stat.addView(value);
        stat.addView(secondary(caption));
        return stat;
    }

    private TextView secondary(String text) {
        TextView label = label(text, 13);
        label.setTextColor(getColor(R.color.secondary));
        return label;
    }

    private void heading(TextView label) {
        if (android.os.Build.VERSION.SDK_INT >= 28) label.setAccessibilityHeading(true);
    }
}
