package az.tabletennis;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Comfortable row: portrait, wrapping identity with points, and a separate amateur-rank badge. */
final class PlayerRankingRow extends LinearLayout {
    private final ImageView photo;
    private final TextView name;
    private final TextView points;
    private final TextView rank;

    PlayerRankingRow(Context context) {
        super(context);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setMinimumHeight(dp(86));
        setPadding(dp(2), dp(15), dp(2), dp(15));
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);
        photo = new PlayerPortraitView(context);
        photo.setId(android.R.id.icon);
        photo.setScaleType(ImageView.ScaleType.CENTER_CROP);
        photo.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        int portraitSize = getResources().getConfiguration().screenWidthDp <= 360 ? 46 : 50;
        LayoutParams portraitLayout = new LayoutParams(dp(portraitSize), dp(portraitSize));
        portraitLayout.setMarginEnd(dp(12));
        addView(photo, portraitLayout);
        LinearLayout identity = new LinearLayout(context);
        identity.setOrientation(VERTICAL);
        identity.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        name = text(17, R.color.text);
        name.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        name.setId(android.R.id.text1);
        identity.addView(name, new LayoutParams(-1, -2));
        points = text(14, R.color.secondary);
        points.setId(android.R.id.text2);
        points.setPadding(0, dp(5), 0, 0);
        identity.addView(points, new LayoutParams(-1, -2));
        addView(identity, new LayoutParams(0, -2, 1));
        LinearLayout badge = new LinearLayout(context);
        badge.setOrientation(VERTICAL);
        badge.setGravity(Gravity.END);
        badge.setPadding(dp(9), 0, 0, 0);
        badge.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        rank = text(20, R.color.text);
        badge.addView(rank);
        TextView caption = text(12, R.color.secondary);
        caption.setText(R.string.rank_caption);
        badge.addView(caption);
        addView(badge, new LayoutParams(-2, -2));
    }

    void bind(RankingSnapshot.Player player, int position, String accessibleValues, RankingsApplication application) {
        name.setText(player.name);
        points.setText(getContext().getString(R.string.points, player.points));
        rank.setText(getContext().getString(R.string.rank_number, position));
        setContentDescription(accessibleValues);
        application.photos().bind(photo, player.id, player.photoUrl, application.isOnline());
    }

    private TextView text(int size, int color) {
        TextView view = new TextView(getContext());
        view.setTextSize(size);
        view.setTextColor(getContext().getColor(color));
        return view;
    }

    private int dp(int value) { return Presentation.dp(getContext(), value); }
}
