package az.tabletennis;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

/** Equal spacing preserves source order without claiming chronology for yearless dates. */
final class RatingChartView extends LinearLayout {
    private final RatingHistory history;
    private final Plot plot;
    private final double minimum;
    private final double maximum;
    private int selected;

    RatingChartView(Context context, RatingHistory history) {
        super(context);
        this.history = history;
        setId(R.id.chart);
        setFocusable(true);
        setOrientation(HORIZONTAL);
        List<String> labels = new ArrayList<>();
        RatingHistory.Observation low = history.observations.get(0);
        RatingHistory.Observation high = low;
        for (RatingHistory.Observation point : history.observations) {
            labels.add(context.getString(R.string.history_point, point.date, point.points));
            if (Double.parseDouble(point.points) < Double.parseDouble(low.points)) low = point;
            if (Double.parseDouble(point.points) > Double.parseDouble(high.points)) high = point;
        }
        minimum = Double.parseDouble(low.points);
        maximum = Double.parseDouble(high.points);
        setContentDescription(String.join("; ", labels));
        LinearLayout scale = new LinearLayout(context);
        scale.setOrientation(VERTICAL);
        scale.setPadding(0, 0, dp(10), 0);
        if (minimum == maximum) scale.setGravity(android.view.Gravity.CENTER_VERTICAL);
        scale.addView(axisLabel(high.points));
        if (minimum != maximum) {
            scale.addView(new View(context), new LayoutParams(1, 0, 1));
            scale.addView(axisLabel(low.points));
        }
        TextView measure = axisLabel(high.points);
        int scaleWidth = (int) Math.ceil(Math.max(measure.getPaint().measureText(high.points),
                measure.getPaint().measureText(low.points))) + dp(12);
        addView(scale, new LayoutParams(scaleWidth, dp(168)));
        LinearLayout graph = new LinearLayout(context);
        graph.setOrientation(VERTICAL);
        plot = new Plot(context);
        graph.addView(plot, new LayoutParams(-1, dp(168)));
        LinearLayout dates = new LinearLayout(context);
        TextView first = axisLabel(history.observations.get(0).date);
        if (history.observations.size() == 1) first.setGravity(android.view.Gravity.CENTER);
        dates.addView(first, new LayoutParams(0, -2, 1));
        if (history.observations.size() > 1) {
            TextView last = axisLabel(history.observations.get(history.observations.size() - 1).date);
            last.setGravity(android.view.Gravity.END);
            dates.addView(last, new LayoutParams(0, -2, 1));
        }
        graph.addView(dates);
        addView(graph, new LayoutParams(0, -2, 1));
        // The complete source observations are announced as one chart, with individually reachable rows below.
        scale.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        graph.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
    }

    void select(int index) {
        selected = index;
        plot.invalidate();
    }

    private TextView axisLabel(String text) {
        TextView label = Presentation.label(getContext(), text, 13);
        label.setTextColor(getContext().getColor(R.color.secondary));
        return label;
    }

    private int dp(int value) { return Presentation.dp(getContext(), value); }

    private final class Plot extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        Plot(Context context) { super(context); }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float margin = dp(8);
            float width = getWidth() - 2 * margin;
            float height = getHeight() - 2 * margin;
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(1));
            paint.setColor(getContext().getColor(R.color.line));
            for (int i = 0; i < 3; i++) {
                float y = margin + height * i / 2;
                canvas.drawLine(margin, y, getWidth() - margin, y, paint);
            }
            Path line = new Path();
            paint.setColor(getContext().getColor(R.color.primary));
            paint.setStrokeWidth(dp(3));
            for (int i = 0; i < history.observations.size(); i++) {
                double value = Double.parseDouble(history.observations.get(i).points);
                float x = history.observations.size() == 1 ? getWidth() / 2f
                        : margin + width * i / (history.observations.size() - 1);
                float y = minimum == maximum ? getHeight() / 2f
                        : margin + (float) ((maximum - value) / (maximum - minimum)) * height;
                if (i == 0) line.moveTo(x, y); else line.lineTo(x, y);
                paint.setStyle(Paint.Style.FILL);
                canvas.drawCircle(x, y, dp(i == selected ? 5 : 3), paint);
                if (i == selected) {
                    paint.setStyle(Paint.Style.STROKE);
                    canvas.drawCircle(x, y, dp(7), paint);
                }
            }
            paint.setStyle(Paint.Style.STROKE);
            canvas.drawPath(line, paint);
        }
    }
}
