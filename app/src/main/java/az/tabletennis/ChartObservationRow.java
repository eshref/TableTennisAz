package az.tabletennis;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.RippleDrawable;
import android.widget.LinearLayout;
import android.widget.TextView;

/** A selectable observation keeps the source date and its value in separate wrapping columns. */
@android.annotation.SuppressLint("ViewConstructor") // Created with observation data in code, never inflated from XML.
final class ChartObservationRow extends LinearLayout {
    private final TextView date;
    private final TextView value;

    ChartObservationRow(Context context, String dateText, String valueText, Runnable inspect) {
        super(context);
        setId(R.id.chart_observation);
        setMinimumHeight(Presentation.dp(context, 48));
        setPadding(Presentation.dp(context, 4), Presentation.dp(context, 6),
                Presentation.dp(context, 4), Presentation.dp(context, 6));
        date = Presentation.label(context, dateText, 14);
        date.setId(android.R.id.text1);
        value = Presentation.label(context, valueText, 14);
        value.setId(android.R.id.text2);
        Presentation.observationColumns(this, date, value);
        setContentDescription(dateText + " — " + valueText);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        date.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        value.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setFocusable(true);
        setOnClickListener(view -> inspect.run());
        setBackground(new RippleDrawable(ColorStateList.valueOf(context.getColor(R.color.soft)),
                null, new ColorDrawable(android.graphics.Color.WHITE)));
        showSelected(false);
    }

    void showSelected(boolean selected) {
        setSelected(selected);
        date.setTextColor(getContext().getColor(selected ? R.color.primary : R.color.secondary));
        value.setTextColor(getContext().getColor(selected ? R.color.primary : R.color.text));
        date.setTypeface(null, selected ? Typeface.BOLD : Typeface.NORMAL);
        value.setTypeface(null, Typeface.BOLD);
    }

    @Override public CharSequence getAccessibilityClassName() { return android.widget.Button.class.getName(); }
}
