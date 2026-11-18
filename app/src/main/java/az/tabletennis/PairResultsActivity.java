package az.tabletennis;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

public class PairResultsActivity extends SavedScreen<PairHistory> {
    private long pairId;
    private boolean rankMode;

    @Override protected int detailTitle() { return R.string.pair_profile; }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        rankMode = state != null && state.getBoolean("rankMode");
        pairId = getIntent().getLongExtra("pairId", 0);
        if (pairId <= 0) { finish(); return; }
        int side = Math.max(0, (getResources().getDisplayMetrics().widthPixels - dp(32) - dp(640)) / 2);
        content.setPadding(side, 0, side, 0);
        select("duo-results-" + pairId, PairHistory.class);
    }

    @Override protected Fetch<PairHistory> request(PairHistory previous, boolean more) {
        if (!more) application.photos().refreshed();
        return () -> {
            DoublesRepository repository = new DoublesRepository(this, application.websiteUrl());
            DoublesPair pair = previous == null ? repository.pair(pairId) : previous.pair;
            PairMedals medals = more && previous != null ? previous.medals : repository.medals(pair);
            PairHistory history = new WebsiteSource(application.websiteUrl()).pairHistory(pair, previous, more);
            return new PairHistory(pair, history.matches, history.pages, history.nextPage, medals);
        };
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putBoolean("rankMode", rankMode);
        super.onSaveInstanceState(state);
    }

    @Override protected void render(PairHistory history) {
        LinearLayout identity = new LinearLayout(this);
        identity.setGravity(Gravity.CENTER_VERTICAL);
        identity.setPadding(0, dp(12), 0, dp(18));
        DoublesPortraitView portrait = new DoublesPortraitView(this);
        LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(dp(84), dp(84));
        size.setMarginEnd(dp(15));
        identity.addView(portrait, size);
        LinearLayout title = new LinearLayout(this);
        title.setOrientation(LinearLayout.VERTICAL);
        title.addView(heading(getString(R.string.pair_identity_title)));
        title.addView(secondary(getString(R.string.pair_identity_subtitle)));
        identity.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        content.addView(identity);
        application.photos().bind(portrait, history.pair, application.isOnline());
        for (MatchDetails.PlayerLink player : new MatchDetails.PlayerLink[] {history.pair.first, history.pair.second}) {
            Button link = button(player.name, () -> startActivity(new Intent(this, ProfileActivity.class)
                    .putExtra("playerId", player.id)));
            link.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            link.setTextSize(16);
            link.setMaxLines(Integer.MAX_VALUE);
            link.setEllipsize(null);
            link.setPadding(0, dp(12), dp(4), dp(12));
            link.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_chevron, 0);
            link.setCompoundDrawablePadding(dp(8));
            content.addView(link, new LinearLayout.LayoutParams(-1, -2));
            View divider = new View(this);
            divider.setBackgroundColor(getColor(R.color.line));
            content.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));
        }
        SavedData<DoublesRanking> ranking = new SavedDataStore<>(this, "doubles-ranking", DoublesRanking.class).read();
        showStanding(ranking);
        content.addView(section(R.string.pair_medals));
        LinearLayout medals = new LinearLayout(this);
        medals.addView(medal(R.id.pair_gold, R.string.pair_gold, R.string.medal_gold,
                history.medals == null ? null : history.medals.gold), medalSize());
        medals.addView(medal(R.id.pair_silver, R.string.pair_silver, R.string.medal_silver,
                history.medals == null ? null : history.medals.silver), medalSize());
        medals.addView(medal(R.id.pair_bronze, R.string.pair_bronze, R.string.medal_bronze,
                history.medals == null ? null : history.medals.bronze), new LinearLayout.LayoutParams(0, -1, 1));
        content.addView(medals);
        content.addView(section(R.string.pair_chart_title));
        content.addView(new DoublesHistoryView(this, ranking == null ? null : ranking.value, pairId,
                rankMode, selected -> rankMode = selected));
        content.addView(section(R.string.match_history));
        content.addView(secondary(getString(R.string.pair_history_coverage)));
        if (history.matches.isEmpty()) content.addView(label(getString(R.string.no_history), 16));
        for (MatchSummary match : history.matches) {
            content.addView(new MatchCard(this, match, () -> startActivity(new Intent(this, MatchActivity.class)
                    .putExtra("matchId", match.id))));
        }
        if (history.nextPage != null) {
            Button more = button(getString(R.string.load_more), () -> refresh(true));
            more.setEnabled(application.isOnline());
            content.addView(more, new LinearLayout.LayoutParams(-1, -2));
        }
    }

    private void showStanding(SavedData<DoublesRanking> ranking) {
        DoublesRanking.Row standing = null;
        if (ranking != null) {
            for (DoublesRanking.Row row : ranking.value.rows) {
                if (row.pair.id == pairId) { standing = row; break; }
            }
        }
        LinearLayout stats = new LinearLayout(this);
        stats.setPadding(dp(16), dp(16), dp(4), dp(16));
        stats.setBackground(Presentation.shape(this, R.color.soft));
        TextView points = label(standing == null ? "—" : Integer.toString(standing.points), 28);
        points.setId(R.id.profile_points);
        points.setContentDescription(standing == null ? getString(R.string.points_unavailable)
                : getString(R.string.points, standing.points));
        stats.addView(stat(points, R.string.pair_points), new LinearLayout.LayoutParams(0, -2, 1));
        TextView position = label(standing == null ? "—" : getString(R.string.rank_number, standing.position), 28);
        position.setId(R.id.pair_position);
        position.setContentDescription(standing == null ? getString(R.string.pair_position_unavailable)
                : getString(R.string.pair_position, standing.position));
        stats.addView(stat(position, R.string.pair_published_position), new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout.LayoutParams spacing = new LinearLayout.LayoutParams(-1, -2);
        spacing.topMargin = dp(16);
        spacing.bottomMargin = dp(8);
        content.addView(stats, spacing);
        if (standing != null) {
            java.text.DateFormat format = java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM,
                    java.text.DateFormat.SHORT, java.util.Locale.forLanguageTag("az-AZ"));
            content.addView(secondary(getString(R.string.pair_ranking_updated_at,
                    format.format(new java.util.Date(ranking.updatedAt)))));
        }
    }

    private LinearLayout stat(TextView value, int caption) {
        LinearLayout stat = new LinearLayout(this);
        stat.setOrientation(LinearLayout.VERTICAL);
        stat.setPadding(0, 0, dp(12), 0);
        value.setTypeface(null, Typeface.BOLD);
        stat.addView(value);
        stat.addView(secondary(getString(caption)));
        return stat;
    }

    private LinearLayout medal(int id, int description, int caption, Integer count) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER_HORIZONTAL);
        tile.setPadding(dp(6), dp(12), dp(6), dp(12));
        android.graphics.drawable.GradientDrawable background = Presentation.shape(this, R.color.surface);
        background.setStroke(dp(1), getColor(R.color.line));
        tile.setBackground(background);
        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_medal);
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        tile.addView(icon, new LinearLayout.LayoutParams(dp(24), dp(24)));
        TextView value = label(count == null ? "—" : Integer.toString(count), 22);
        value.setId(id);
        value.setGravity(Gravity.CENTER);
        value.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        value.setContentDescription(getString(description,
                count == null ? getString(R.string.medal_unavailable) : Integer.toString(count)));
        tile.addView(value, new LinearLayout.LayoutParams(-1, -2));
        TextView name = secondary(getString(caption));
        name.setGravity(Gravity.CENTER);
        tile.addView(name, new LinearLayout.LayoutParams(-1, -2));
        return tile;
    }

    private LinearLayout.LayoutParams medalSize() {
        LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(0, -1, 1);
        size.setMarginEnd(dp(8));
        return size;
    }

    private TextView section(int title) {
        TextView heading = heading(getString(title));
        heading.setPadding(0, dp(24), 0, dp(8));
        return heading;
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
