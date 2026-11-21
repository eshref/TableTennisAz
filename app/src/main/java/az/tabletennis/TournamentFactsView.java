package az.tabletennis;

import android.content.Context;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Keeps each tournament fact and its icon together when a row runs out of space. */
final class TournamentFactsView extends ViewGroup {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMMM uuuu", Locale.forLanguageTag("az"));

    private final TextView date;
    private final TextView entrants;
    private final TextView prize;
    private final Rect[] positions = {new Rect(), new Rect(), new Rect()};
    private final int columnGap;
    private final int rowGap;

    TournamentFactsView(Context context) {
        super(context);
        columnGap = Presentation.dp(context, 12);
        rowGap = Presentation.dp(context, 5);
        date = addFact(R.drawable.ic_calendar);
        entrants = addFact(R.drawable.ic_pair);
        prize = addFact(R.drawable.ic_trophy);
    }

    void bind(TournamentCatalogue.Tournament tournament, boolean doubles) {
        Context context = getContext();
        String unknown = context.getString(R.string.tournament_unknown_value);
        Long count = doubles ? tournament.pairs : tournament.players;
        date.setText(dateRange(tournament.start, tournament.end));
        entrants.setText(context.getString(doubles ? R.string.tournament_pairs : R.string.tournament_players,
                count == null ? unknown : count.toString()));
        String amount = tournament.prize == null ? unknown : tournament.prize;
        prize.setText(amount);
        prize.setContentDescription(context.getString(R.string.tournament_prize, amount));
    }

    private String dateRange(LocalDate start, LocalDate end) {
        if (start.equals(end)) return DATE.format(start);
        if (start.getYear() == end.getYear() && start.getMonth() == end.getMonth()) {
            return getContext().getString(R.string.tournament_day_range,
                    Integer.toString(start.getDayOfMonth()), DATE.format(end));
        }
        return getContext().getString(R.string.tournament_date_range, DATE.format(start), DATE.format(end));
    }

    private TextView addFact(int icon) {
        Context context = getContext();
        TextView fact = Presentation.label(context, "", 13);
        fact.setPadding(0, 0, 0, 0);
        fact.setTextColor(context.getColor(R.color.secondary));
        Drawable drawable = context.getDrawable(icon).mutate();
        drawable.setTint(fact.getCurrentTextColor());
        int size = Presentation.dp(context, 14);
        drawable.setBounds(0, 0, size, size);
        fact.setCompoundDrawablesRelative(drawable, null, null, null);
        fact.setCompoundDrawablePadding(Presentation.dp(context, 5));
        addView(fact);
        return fact;
    }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        boolean bounded = MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.UNSPECIFIED;
        int childWidthSpec = bounded ? MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST) : widthMeasureSpec;
        int rowTop = 0;
        int rowHeight = 0;
        int rowWidth = 0;
        int contentWidth = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View fact = getChildAt(i);
            fact.measure(childWidthSpec, MeasureSpec.UNSPECIFIED);
            int start = rowWidth == 0 ? 0 : rowWidth + columnGap;
            if (bounded && start > 0 && start + fact.getMeasuredWidth() > width) {
                rowTop += rowHeight + rowGap;
                rowHeight = 0;
                start = 0;
            }
            rowWidth = start + fact.getMeasuredWidth();
            rowHeight = Math.max(rowHeight, fact.getMeasuredHeight());
            contentWidth = Math.max(contentWidth, rowWidth);
            positions[i].set(start, rowTop, rowWidth, rowTop + fact.getMeasuredHeight());
        }
        setMeasuredDimension(resolveSize(contentWidth, widthMeasureSpec),
                resolveSize(rowTop + rowHeight, heightMeasureSpec));
    }

    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        int width = right - left;
        for (int i = 0; i < getChildCount(); i++) {
            Rect box = positions[i];
            int start = rtl ? width - box.right : box.left;
            getChildAt(i).layout(start, box.top, start + box.width(), box.bottom);
        }
    }
}
