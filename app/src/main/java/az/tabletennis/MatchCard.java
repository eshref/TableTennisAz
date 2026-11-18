package az.tabletennis;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/** A source-backed match summary. Callers own the destination opened by the card. */
final class MatchCard extends LinearLayout {
    MatchCard(Context context, MatchSummary match, Runnable open) {
        super(context);
        setId(R.id.match_card);
        setOrientation(VERTICAL);
        setPadding(dp(14), dp(10), dp(14), dp(10));
        GradientDrawable surface = Presentation.shape(context, R.color.surface);
        surface.setCornerRadius(dp(15));
        surface.setStroke(dp(1), context.getColor(R.color.line));
        setBackground(new RippleDrawable(ColorStateList.valueOf(context.getColor(R.color.soft)), surface, null));
        LinearLayout.LayoutParams spacing = new LinearLayout.LayoutParams(-1, -2);
        spacing.bottomMargin = dp(11);
        setLayoutParams(spacing);
        setMinimumHeight(dp(48));
        setFocusable(true);
        setOnClickListener(view -> open.run());
        String kind = context.getString(match.left.size() == 2 ? R.string.match_doubles : R.string.match_singles);
        // Profile histories, including older snapshots, identify duels by the website's reserved event label.
        boolean duel = match.duel || (match.eventId == 0 && "Duello".equalsIgnoreCase(match.eventName));
        String source = context.getString(match.eventId != 0 && duel ? R.string.match_event_and_duel
                : duel ? R.string.match_duel : R.string.match_event);
        String type = kind + " · " + source;
        setContentDescription(match.display() + " · " + type);
        // One actionable TalkBack announcement retains both full sides, score, date, event and type.
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        LinearLayout metadata = new LinearLayout(context);
        boolean stack = getResources().getConfiguration().screenWidthDp < 360 * getResources().getConfiguration().fontScale;
        metadata.setOrientation(stack ? VERTICAL : HORIZONTAL);
        metadata.setGravity(Gravity.CENTER_VERTICAL);
        metadata.addView(secondary(match.date), new LayoutParams(stack ? -1 : 0, -2, stack ? 0 : 1));
        TextView badge = secondary(type);
        badge.setBackground(Presentation.shape(context, R.color.soft));
        badge.setPadding(dp(7), dp(3), dp(7), dp(3));
        metadata.addView(badge, new LayoutParams(-2, -2));
        addView(metadata);
        String[] scores = match.score.split("\\s*-\\s*", -1);
        boolean usableScore = scores.length == 2 && scores[0].matches("\\d+") && scores[1].matches("\\d+");
        int comparison = usableScore ? new java.math.BigInteger(scores[0]).compareTo(new java.math.BigInteger(scores[1])) : 0;
        addView(side(String.join("\n", match.left), usableScore ? scores[0] : match.score, comparison > 0));
        addView(side(String.join("\n", match.right), usableScore ? scores[1] : "", comparison < 0));
        if (!match.eventName.isEmpty()) {
            View divider = new View(context);
            divider.setBackgroundColor(context.getColor(R.color.line));
            LayoutParams line = new LayoutParams(-1, dp(1));
            line.topMargin = dp(8);
            addView(divider, line);
            addView(secondary(match.eventName));
        }
    }

    @Override public CharSequence getAccessibilityClassName() { return android.widget.Button.class.getName(); }

    private LinearLayout side(String names, String score, boolean winner) {
        LinearLayout side = new LinearLayout(getContext());
        side.setGravity(Gravity.CENTER_VERTICAL);
        TextView participants = Presentation.label(getContext(), names, 16);
        participants.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        participants.setTypeface(Typeface.DEFAULT, winner ? Typeface.BOLD : Typeface.NORMAL);
        participants.setPadding(0, dp(6), dp(12), dp(6));
        side.addView(participants, new LayoutParams(0, -2, 1));
        TextView points = Presentation.label(getContext(), score, 22);
        points.setTypeface(null, Typeface.BOLD);
        points.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        side.addView(points, new LayoutParams(-2, -2));
        return side;
    }

    private TextView secondary(String text) {
        TextView label = Presentation.label(getContext(), text, 12);
        label.setTextColor(getContext().getColor(R.color.secondary));
        label.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        return label;
    }

    private int dp(int value) { return Presentation.dp(getContext(), value); }
}
