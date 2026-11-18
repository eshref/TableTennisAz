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
    private final List<Point> ratingHistory = new ArrayList<>();
    private final List<Point> rankingHistory = new ArrayList<>();
    private List<Point> history;
    private final DateTimeFormatter dates = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss.SSS",
            Locale.forLanguageTag("az-AZ")).withZone(ZoneId.systemDefault());
    private boolean rankMode;
    private Long inspectedAt;
    private int selected;
    private final List<ChartObservationRow> rows = new ArrayList<>();
    private TextView firstDate;
    private TextView lastDate;
    private TextView historyNote;
    private TextView selectedDate;
    private TextView selectedValue;

    DoublesHistoryView(Context context, DoublesRanking ranking, long pairId, boolean rankMode,
                       Consumer<Boolean> selection) {
        super(context);
        this.rankMode = rankMode;
        setOrientation(VERTICAL);
        readHistory(ranking, pairId);
        history = rankMode ? rankingHistory : ratingHistory;
        addView(label(context.getString(R.string.pair_chart_local)));
        RadioGroup modes = new RadioGroup(context);
        modes.setOrientation(HORIZONTAL);
        modes.setPadding(dp(3), dp(3), dp(3), dp(3));
        modes.setBackground(Presentation.shape(context, R.color.soft));
        modes.addView(mode(R.id.pair_chart_points, R.string.pair_chart_points), new RadioGroup.LayoutParams(0, -2, 1));
        modes.addView(mode(R.id.pair_chart_rank, R.string.pair_chart_rank), new RadioGroup.LayoutParams(0, -2, 1));
        modes.check(rankMode ? R.id.pair_chart_rank : R.id.pair_chart_points);
        addView(modes);
        if (history.isEmpty()) {
            addView(label(context.getString(R.string.pair_chart_empty)));
            modes.setOnCheckedChangeListener((group, id) -> selection.accept(id == R.id.pair_chart_rank));
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
        firstDate = label("");
        endpoints.addView(firstDate, new LayoutParams(stack ? -1 : 0, -2, stack ? 0 : 1));
        lastDate = label("");
        lastDate.setGravity(Gravity.END);
        endpoints.addView(lastDate, new LayoutParams(stack ? -1 : 0, -2, stack ? 0 : 1));
        // The chart announces every entry, and rows below let users select them individually.
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
        historyNote = label("");
        addView(historyNote);
        LinearLayout values = new LinearLayout(context);
        values.setOrientation(VERTICAL);
        values.setId(R.id.chart_observations);
        addView(values);
        showValues(chart, values);
        modes.setOnCheckedChangeListener((group, id) -> {
            this.rankMode = id == R.id.pair_chart_rank;
            history = this.rankMode ? rankingHistory : ratingHistory;
            selection.accept(this.rankMode);
            showValues(chart, values);
        });
    }

    private void readHistory(DoublesRanking ranking, long pairId) {
        if (ranking == null) return;
        List<DoublesRanking.Observation> observations = new ArrayList<>(ranking.observations());
        observations.sort(Comparator.comparingLong(observation -> observation.observedAt));
        for (DoublesRanking.Observation observation : observations) {
            for (DoublesRanking.Entry entry : observation.entries) {
                if (entry.pairId != pairId) continue;
                addChange(ratingHistory, observation.observedAt, entry.points);
                addChange(rankingHistory, observation.observedAt, entry.position);
                break;
            }
        }
    }

    private void addChange(List<Point> series, long time, int value) {
        if (series.isEmpty() || series.get(series.size() - 1).value != value) {
            series.add(new Point(time, value));
        }
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
        boolean single = history.size() == 1;
        firstDate.setText(dateOf(history.get(0)));
        firstDate.setGravity(single ? Gravity.CENTER : Gravity.START);
        lastDate.setVisibility(single ? GONE : VISIBLE);
        lastDate.setText(dateOf(history.get(history.size() - 1)));
        historyNote.setText(single ? R.string.pair_chart_first : R.string.pair_chart_coverage);

        List<String> descriptions = new ArrayList<>();
        values.removeAllViews();
        rows.clear();
        for (int i = 0; i < history.size(); i++) {
            int index = i;
            Point point = history.get(i);
            ChartObservationRow row = new ChartObservationRow(getContext(), dateOf(point), formattedValue(point), () -> {
                inspectedAt = point.time;
                inspect(chart, index);
            });
            values.addView(row, new LayoutParams(-1, -2));
            rows.add(row);
            descriptions.add(row.getContentDescription().toString());
            View divider = new View(getContext());
            divider.setBackgroundColor(getContext().getColor(R.color.line));
            values.addView(divider, new LayoutParams(-1, dp(1)));
        }
        chart.setContentDescription(String.join("; ", descriptions));
        int index = history.size() - 1;
        // Both modes begin at the same observation. Keep the original inspection time across switches.
        while (index > 0 && inspectedAt != null && history.get(index).time > inspectedAt) index--;
        inspect(chart, index);
    }

    private void inspect(Chart chart, int index) {
        selected = index;
        Point point = history.get(index);
        selectedDate.setText(dateOf(point));
        selectedValue.setText(formattedValue(point));
        for (int i = 0; i < rows.size(); i++) {
            rows.get(i).showSelected(i == index);
        }
        chart.invalidate();
    }

    private String dateOf(Point point) { return dates.format(Instant.ofEpochMilli(point.time)); }

    private String formattedValue(Point point) {
        return getContext().getString(rankMode ? R.string.rank_number : R.string.points, point.value);
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private static final class Point {
        final long time;
        final int value;

        Point(long time, int value) { this.time = time; this.value = value; }
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
            long minimum = rankMode ? 1 : history.stream().mapToLong(point -> point.value).min().orElse(0);
            long maximum = history.stream().mapToLong(point -> point.value).max().orElse(1);
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
            long start = history.get(0).time;
            long end = history.get(history.size() - 1).time;
            Path line = new Path();
            paint.setColor(getContext().getColor(R.color.primary));
            for (int i = 0; i < history.size(); i++) {
                Point point = history.get(i);
                float x = end == start ? (left + right) / 2
                        : left + (float) ((double) (point.time - start) / (end - start)) * (right - left);
                double fraction = (point.value - (double) minimum) / (maximum - (double) minimum);
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
