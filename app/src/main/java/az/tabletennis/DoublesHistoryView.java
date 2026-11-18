package az.tabletennis;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/** A read-only projection of the same saved ranking used by the current standing. */
final class DoublesHistoryView extends LinearLayout {
    private final List<Point> observations = new ArrayList<>();
    private final DateTimeFormatter dates = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss.SSS",
            Locale.forLanguageTag("az-AZ")).withZone(ZoneId.systemDefault());
    private boolean rankMode;
    private int selected;
    private final List<ChartObservationRow> rows = new ArrayList<>();
    private TextView selectedDate;
    private TextView selectedValue;

    DoublesHistoryView(Context context, DoublesRanking ranking, long pairId, boolean rankMode,
                       Consumer<Boolean> selection) {
        super(context);
        this.rankMode = rankMode;
        setOrientation(VERTICAL);
        if (ranking != null) {
            for (DoublesRanking.Observation observation : ranking.observations()) {
                for (DoublesRanking.Entry entry : observation.entries) {
                    if (entry.pairId == pairId) observations.add(new Point(observation.observedAt, entry));
                }
            }
        }
        observations.sort(Comparator.comparingLong(point -> point.time));
        selected = observations.size() - 1;
        addView(label(context.getString(R.string.pair_chart_local)));
        RadioGroup modes = new RadioGroup(context);
        modes.setOrientation(HORIZONTAL);
        modes.setPadding(dp(3), dp(3), dp(3), dp(3));
        modes.setBackground(Presentation.shape(context, R.color.soft));
        modes.addView(mode(R.id.pair_chart_points, R.string.pair_chart_points), new RadioGroup.LayoutParams(0, -2, 1));
        modes.addView(mode(R.id.pair_chart_rank, R.string.pair_chart_rank), new RadioGroup.LayoutParams(0, -2, 1));
        modes.check(rankMode ? R.id.pair_chart_rank : R.id.pair_chart_points);
        addView(modes);
        if (observations.isEmpty()) {
            addView(label(context.getString(R.string.pair_chart_empty)));
            modes.setOnCheckedChangeListener((group, id) -> {
                this.rankMode = id == R.id.pair_chart_rank;
                selection.accept(this.rankMode);
            });
            return;
        }
        Chart chart = new Chart(context);
        LinearLayout panel = new LinearLayout(context);
        panel.setOrientation(VERTICAL);
        panel.setPadding(dp(12), dp(14), dp(12), dp(8));
        android.graphics.drawable.GradientDrawable background = Presentation.shape(context, R.color.surface);
        background.setStroke(dp(1), context.getColor(R.color.line));
        panel.setBackground(background);
        LayoutParams spacing = new LayoutParams(-1, -2);
        spacing.topMargin = dp(14);
        addView(panel, spacing);
        panel.addView(chart, new LayoutParams(-1, dp(140)));
        LinearLayout endpoints = new LinearLayout(context);
        boolean stack = getResources().getConfiguration().screenWidthDp < 360 * getResources().getConfiguration().fontScale;
        endpoints.setOrientation(stack ? VERTICAL : HORIZONTAL);
        TextView first = label(dates.format(Instant.ofEpochMilli(observations.get(0).time)));
        if (observations.size() == 1) first.setGravity(Gravity.CENTER);
        endpoints.addView(first, new LayoutParams(stack ? -1 : 0, -2, stack ? 0 : 1));
        if (observations.size() > 1) {
            TextView last = label(dates.format(Instant.ofEpochMilli(observations.get(observations.size() - 1).time)));
            last.setGravity(Gravity.END);
            endpoints.addView(last, new LayoutParams(stack ? -1 : 0, -2, stack ? 0 : 1));
        }
        // The chart announces every observation; individually actionable values are also available below.
        endpoints.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        panel.addView(endpoints);
        LinearLayout inspected = new LinearLayout(context);
        inspected.setAccessibilityLiveRegion(ACCESSIBILITY_LIVE_REGION_POLITE);
        selectedDate = label("");
        selectedDate.setId(R.id.chart_selected_date);
        selectedValue = Presentation.label(context, "", 16);
        selectedValue.setId(R.id.chart_selected_value);
        selectedValue.setTextColor(context.getColor(R.color.primary));
        selectedValue.setTypeface(null, Typeface.BOLD);
        Presentation.observationColumns(inspected, selectedDate, selectedValue);
        panel.addView(inspected);
        addView(label(context.getString(observations.size() == 1 ? R.string.pair_chart_first : R.string.pair_chart_coverage)));
        LinearLayout values = new LinearLayout(context);
        values.setOrientation(VERTICAL);
        values.setId(R.id.chart_observations);
        addView(values);
        showValues(chart, values);
        modes.setOnCheckedChangeListener((group, id) -> {
            this.rankMode = id == R.id.pair_chart_rank;
            selection.accept(this.rankMode);
            showValues(chart, values);
        });
    }

    private RadioButton mode(int id, int text) {
        RadioButton button = new RadioButton(getContext());
        button.setId(id);
        button.setText(text);
        Presentation.segment(button);
        return button;
    }

    private TextView label(String text) {
        TextView label = Presentation.label(getContext(), text, 13);
        label.setTextColor(getContext().getColor(R.color.secondary));
        return label;
    }

    private void showValues(Chart chart, LinearLayout values) {
        List<String> descriptions = new ArrayList<>();
        for (Point point : observations) {
            String date = dates.format(Instant.ofEpochMilli(point.time));
            descriptions.add(rankMode ? getContext().getString(R.string.pair_chart_rank_point, date, point.entry.position)
                    : getContext().getString(R.string.history_point, date, Integer.toString(point.entry.points)));
        }
        chart.setContentDescription(String.join("; ", descriptions));
        values.removeAllViews();
        rows.clear();
        for (int i = 0; i < descriptions.size(); i++) {
            int index = i;
            Point point = observations.get(i);
            String date = dates.format(Instant.ofEpochMilli(point.time));
            String value = rankMode ? getContext().getString(R.string.rank_number, point.entry.position)
                    : getContext().getString(R.string.points, point.entry.points);
            ChartObservationRow row = new ChartObservationRow(getContext(), date, value, () -> inspect(chart, index));
            values.addView(row, new LayoutParams(-1, -2));
            rows.add(row);
            View divider = new View(getContext());
            divider.setBackgroundColor(getContext().getColor(R.color.line));
            values.addView(divider, new LayoutParams(-1, dp(1)));
        }
        inspect(chart, selected);
    }

    private void inspect(Chart chart, int index) {
        selected = index;
        Point point = observations.get(index);
        selectedDate.setText(dates.format(Instant.ofEpochMilli(point.time)));
        selectedValue.setText(rankMode ? getContext().getString(R.string.rank_number, point.entry.position)
                : getContext().getString(R.string.points, point.entry.points));
        for (int i = 0; i < rows.size(); i++) {
            rows.get(i).showSelected(i == index);
        }
        chart.invalidate();
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private static final class Point {
        final long time;
        final DoublesRanking.Entry entry;

        Point(long time, DoublesRanking.Entry entry) { this.time = time; this.entry = entry; }
    }

    private final class Chart extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        Chart(Context context) {
            super(context);
            setId(R.id.chart);
            setFocusable(true);
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            long minimum = rankMode ? 1 : observations.stream().mapToLong(point -> point.entry.points).min().orElse(0);
            long maximum = observations.stream().mapToLong(point -> rankMode ? point.entry.position : point.entry.points)
                    .max().orElse(1);
            if (maximum == minimum) {
                if (!rankMode) minimum = Math.max(0, minimum - 1);
                maximum++;
            }
            paint.setTextSize(13 * getResources().getDisplayMetrics().scaledDensity);
            paint.setStyle(Paint.Style.FILL);
            float left = Math.max(paint.measureText(tick(minimum)), paint.measureText(tick(maximum))) + dp(12);
            float right = getWidth() - dp(10);
            float top = paint.getTextSize() + dp(8);
            float bottom = getHeight() - dp(10);
            for (long value : new long[] {minimum, maximum}) {
                float y = plotY((value - (double) minimum) / (maximum - (double) minimum), top, bottom);
                paint.setColor(getContext().getColor(R.color.line));
                paint.setStrokeWidth(dp(1));
                canvas.drawLine(left, y, right, y, paint);
                paint.setColor(getContext().getColor(R.color.secondary));
                paint.setTextAlign(Paint.Align.RIGHT);
                canvas.drawText(tick(value), left - dp(8), y + paint.getTextSize() / 3, paint);
            }
            long start = observations.get(0).time;
            long end = observations.get(observations.size() - 1).time;
            Path line = new Path();
            paint.setColor(getContext().getColor(R.color.primary));
            for (int i = 0; i < observations.size(); i++) {
                Point point = observations.get(i);
                float x = end == start ? (left + right) / 2
                        : left + (float) ((double) (point.time - start) / (end - start)) * (right - left);
                double fraction = ((rankMode ? point.entry.position : point.entry.points) - (double) minimum) / (maximum - (double) minimum);
                float y = plotY(fraction, top, bottom);
                if (i == 0) line.moveTo(x, y); else line.lineTo(x, y);
                canvas.drawCircle(x, y, dp(i == selected ? 5 : 3), paint);
            }
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(2));
            canvas.drawPath(line, paint);
        }

        private String tick(long value) { return rankMode ? "#" + value : Long.toString(value); }

        private float plotY(double fraction, float top, float bottom) {
            return rankMode ? top + (float) fraction * (bottom - top) : bottom - (float) fraction * (bottom - top);
        }

    }
}
