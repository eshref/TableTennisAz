package az.tabletennis;

import android.content.Context;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Comfortable partnership row with separate published position and accessible movement. */
final class DoublesRankingRow extends LinearLayout {
    private final DoublesPortraitView photo;
    private final TextView names;
    private final TextView points;
    private final TextView movement;
    private final TextView position;

    DoublesRankingRow(Context context) {
        super(context);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setMinimumHeight(dp(100));
        setPadding(dp(2), dp(15), dp(2), dp(15));
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);
        photo = new DoublesPortraitView(context);
        int size = getResources().getConfiguration().screenWidthDp <= 360 ? 46 : 50;
        LayoutParams imageLayout = new LayoutParams(dp(size), dp(size));
        imageLayout.setMarginEnd(dp(12));
        addView(photo, imageLayout);
        LinearLayout identity = new LinearLayout(context);
        identity.setOrientation(VERTICAL);
        identity.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        names = text(17, R.color.text);
        names.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        names.setId(android.R.id.text1);
        identity.addView(names, new LayoutParams(-1, -2));
        points = text(14, R.color.secondary);
        points.setPadding(0, dp(5), 0, 0);
        points.setId(android.R.id.text2);
        identity.addView(points, new LayoutParams(-1, -2));
        movement = text(12, R.color.secondary);
        identity.addView(movement, new LayoutParams(-1, -2));
        addView(identity, new LayoutParams(0, -2, 1));
        LinearLayout badge = new LinearLayout(context);
        badge.setOrientation(VERTICAL);
        badge.setGravity(Gravity.END);
        badge.setPadding(dp(9), 0, 0, 0);
        badge.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        position = text(20, R.color.text);
        badge.addView(position);
        TextView caption = text(12, R.color.secondary);
        caption.setText(R.string.rank_caption);
        badge.addView(caption);
        addView(badge, new LayoutParams(-2, -2));
    }

    void bind(DoublesRanking.Row row, RankingsApplication application) {
        names.setText(row.pair.first.name + "\n" + row.pair.second.name);
        points.setText(getContext().getString(R.string.points, row.points));
        position.setText(getContext().getString(R.string.rank_number, row.position));
        String description = getContext().getString(R.string.doubles_row, row.position, row.pair.names(), row.points);
        movement.setVisibility(row.placesGained == 0 ? GONE : VISIBLE);
        if (row.placesGained != 0) {
            boolean up = row.placesGained > 0;
            int places = Math.abs(row.placesGained);
            movement.setText((up ? "↑ " : "↓ ") + places);
            movement.setTextColor(getContext().getColor(up ? R.color.movement_up : R.color.movement_down));
            description += ", " + getContext().getString(up ? R.string.movement_up : R.string.movement_down, places);
        }
        setContentDescription(description);
        application.photos().bind(photo, row.pair, application.isOnline());
    }

    private TextView text(int size, int color) {
        TextView view = new TextView(getContext());
        view.setTextSize(size);
        view.setTextColor(getContext().getColor(color));
        return view;
    }

    private int dp(int value) { return Presentation.dp(getContext(), value); }
}
