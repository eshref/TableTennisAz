package az.tabletennis;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import az.tabletennis.TournamentCatalogue.Medal;
import az.tabletennis.TournamentCatalogue.Medalist;
import java.util.Collections;
import java.util.List;
import java.util.Map;

final class TournamentPodiumView extends LinearLayout {
    private enum Step {
        SILVER(Medal.SILVER, R.string.medal_silver, R.color.podium_silver, R.color.podium_on_silver, 6),
        GOLD(Medal.GOLD, R.string.medal_gold, R.color.podium_gold, R.color.podium_on_gold, 10),
        BRONZE(Medal.BRONZE, R.string.medal_bronze, R.color.podium_bronze, R.color.podium_on_bronze, 2);

        final Medal medal;
        final int label;
        final int background;
        final int textColor;
        final int verticalPadding;

        Step(Medal medal, int label, int background, int textColor, int verticalPadding) {
            this.medal = medal;
            this.label = label;
            this.background = background;
            this.textColor = textColor;
            this.verticalPadding = verticalPadding;
        }
    }

    private Map<Medal, List<Medalist>> placements = Collections.emptyMap();

    TournamentPodiumView(Context context) {
        super(context);
        setId(R.id.tournament_podium);
        setOrientation(HORIZONTAL);
        setLayoutDirection(LAYOUT_DIRECTION_LTR);
        setGravity(Gravity.BOTTOM);
        setPadding(dp(8), dp(8), dp(8), dp(8));
        setBackground(Presentation.shape(context, R.color.background));
        setVisibility(GONE);
    }

    void bind(Map<Medal, List<Medalist>> podium, RankingsApplication application) {
        if (podium.equals(placements)) return;
        placements = podium;
        removeAllViews();
        setVisibility(podium.isEmpty() ? GONE : VISIBLE);
        if (podium.isEmpty()) return;
        for (Step style : Step.values()) {
            addStep(style, podium.get(style.medal), application);
        }
    }

    private void addStep(Step style, List<Medalist> medalists, RankingsApplication application) {
        Context context = getContext();
        Configuration configuration = getResources().getConfiguration();
        boolean narrow = configuration.screenWidthDp <= 360;
        boolean compactLargeText = narrow && configuration.fontScale >= 1.3f;
        boolean center = style == Step.GOLD;
        LinearLayout step = new LinearLayout(context);
        step.setOrientation(VERTICAL);
        LayoutParams column = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        if (center) {
            column.weight = compactLargeText ? 1.75f : 1.06f;
            column.setMarginStart(dp(4));
            column.setMarginEnd(dp(4));
        }
        addView(step, column);
        if (medalists == null) return;

        LinearLayout portraits = new LinearLayout(context);
        portraits.setGravity(Gravity.CENTER);
        step.addView(portraits, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        boolean compactPair = narrow && medalists.size() == 2;
        int standardSize = center ? 42 : 36;
        int compactSize = center ? 34 : 29;
        int size = dp(compactPair ? compactSize : standardSize);
        int overlap = dp(compactPair ? 5 : 7);
        boolean online = application.isOnline();
        for (Medalist medalist : medalists) {
            PlayerPortraitView portrait = new PlayerPortraitView(context);
            portrait.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            GradientDrawable ring = new GradientDrawable();
            ring.setShape(GradientDrawable.OVAL);
            ring.setColor(Color.TRANSPARENT);
            ring.setStroke(dp(2), context.getColor(R.color.surface));
            portrait.setForeground(ring);
            LayoutParams portraitSize = new LayoutParams(size, size);
            if (portraits.getChildCount() > 0) portraitSize.setMarginStart(-overlap);
            portraits.addView(portrait, portraitSize);
            application.photos().bind(portrait, medalist.id, medalist.photo, online);
        }
        LinearLayout names = new LinearLayout(context);
        names.setOrientation(VERTICAL);
        names.setMinimumHeight(dp(medalists.size() == 2 ? 37 : 23));
        names.setPadding(0, dp(4), 0, dp(2));
        step.addView(names, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        for (Medalist medalist : medalists) {
            TextView name = Presentation.label(context, medalist.name, compactLargeText ? 11 : 12);
            name.setTypeface(Typeface.DEFAULT_BOLD);
            name.setGravity(Gravity.CENTER);
            name.setTextDirection(TEXT_DIRECTION_FIRST_STRONG);
            name.setIncludeFontPadding(false);
            name.setPadding(dp(2), dp(1), dp(2), dp(1));
            names.addView(name, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        }

        TextView base = Presentation.label(context, Integer.toString(style.medal.place), 16);
        base.setContentDescription(context.getString(style.label));
        base.setGravity(Gravity.CENTER);
        base.setTypeface(Typeface.DEFAULT_BOLD);
        base.setIncludeFontPadding(false);
        base.setTextColor(context.getColor(style.textColor));
        int padding = dp(style.verticalPadding);
        base.setPadding(0, padding, 0, padding);
        GradientDrawable background = Presentation.shape(context, style.background);
        float topRadius = dp(8);
        float bottomRadius = dp(4);
        background.setCornerRadii(new float[] {
                topRadius, topRadius, topRadius, topRadius, bottomRadius, bottomRadius, bottomRadius, bottomRadius
        });
        base.setBackground(background);
        step.addView(base, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }

    private int dp(int value) { return Presentation.dp(getContext(), value); }
}
