package az.tabletennis;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
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
        RadioGroup modes = new RadioGroup(context);
        modes.setOrientation(HORIZONTAL);
        modes.addView(mode(R.id.pair_chart_points, R.string.pair_chart_points), new RadioGroup.LayoutParams(0, -2, 1));
        modes.addView(mode(R.id.pair_chart_rank, R.string.pair_chart_rank), new RadioGroup.LayoutParams(0, -2, 1));
        modes.check(rankMode ? R.id.pair_chart_rank : R.id.pair_chart_points);
        addView(modes);
        if (observations.isEmpty()) {
            addView(label(context.getString(R.string.pair_chart_empty)));
            modes.setOnCheckedChangeListener((group, id) -> selection.accept(id == R.id.pair_chart_rank));
            return;
        }
        Chart chart = new Chart(context);
        addView(chart, new LayoutParams(-1, dp(220)));
        addView(label(context.getString(observations.size() == 1 ? R.string.pair_chart_first : R.string.pair_chart_coverage)));
        TextView values = label("");
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
        button.setTextSize(16);
        button.setTextColor(getContext().getColor(R.color.text));
        button.setMinHeight(dp(48));
        return button;
    }

    private TextView label(String text) {
        TextView label = new TextView(getContext());
        label.setText(text);
        label.setTextSize(14);
        label.setTextColor(getContext().getColor(R.color.text));
        label.setPadding(0, dp(6), 0, dp(6));
        return label;
    }

    private void showValues(Chart chart, TextView values) {
        List<String> descriptions = new ArrayList<>();
        for (Point point : observations) {
            String date = dates.format(Instant.ofEpochMilli(point.time));
            descriptions.add(rankMode ? getContext().getString(R.string.pair_chart_rank_point, date, point.entry.position)
                    : getContext().getString(R.string.history_point, date, Integer.toString(point.entry.points)));
        }
        chart.setContentDescription(String.join("; ", descriptions));
        values.setText(String.join("\n", descriptions));
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
            paint.setTextSize(14 * getResources().getDisplayMetrics().scaledDensity);
            paint.setStyle(Paint.Style.FILL);
            float left = Math.max(paint.measureText(tick(minimum)), paint.measureText(tick(maximum))) + dp(12);
            float right = getWidth() - dp(10);
            float top = paint.getTextSize() + dp(8);
            float bottom = getHeight() - 2 * paint.getTextSize() - dp(18);
            for (long value : new long[] {minimum, maximum}) {
                float y = plotY((value - (double) minimum) / (maximum - (double) minimum), top, bottom);
                paint.setColor(getContext().getColor(R.color.line));
                paint.setStrokeWidth(dp(1));
                canvas.drawLine(left, y, right, y, paint);
                paint.setColor(getContext().getColor(R.color.text));
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
                canvas.drawCircle(x, y, dp(4), paint);
            }
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(2));
            canvas.drawPath(line, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(getContext().getColor(R.color.text));
            drawDate(canvas, start, 0, bottom, Paint.Align.LEFT);
            if (end != start) drawDate(canvas, end, getWidth(), bottom, Paint.Align.RIGHT);
        }

        private String tick(long value) { return rankMode ? "#" + value : Long.toString(value); }

        private float plotY(double fraction, float top, float bottom) {
            return rankMode ? top + (float) fraction * (bottom - top) : bottom - (float) fraction * (bottom - top);
        }

        private void drawDate(Canvas canvas, long time, float x, float bottom, Paint.Align alignment) {
            String[] date = dates.format(Instant.ofEpochMilli(time)).split(" ");
            paint.setTextAlign(alignment);
            canvas.drawText(date[0], x, bottom + paint.getTextSize() + dp(8), paint);
            canvas.drawText(date[1], x, bottom + 2 * paint.getTextSize() + dp(10), paint);
        }
    }
}
