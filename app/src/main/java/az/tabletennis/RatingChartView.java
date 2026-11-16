package az.tabletennis;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;
import java.util.List;

/** Source order is intentional: day/month-only labels cannot establish calendar chronology. */
final class RatingChartView extends View {
    private final RatingHistory history;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    RatingChartView(Context context, RatingHistory history) {
        super(context);
        this.history = history;
        setId(R.id.chart);
        setFocusable(true);
        java.util.List<String> labels = new java.util.ArrayList<>();
        for (RatingHistory.Observation observation : history.observations) {
            labels.add(context.getString(R.string.history_point, observation.date, observation.points));
        }
        setContentDescription(String.join("; ", labels));
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        List<RatingHistory.Observation> points = history.observations;
        if (points.isEmpty()) return;
        double minimum = Double.POSITIVE_INFINITY;
        double maximum = Double.NEGATIVE_INFINITY;
        for (RatingHistory.Observation point : points) {
            double value = Double.parseDouble(point.points);
            minimum = Math.min(minimum, value);
            maximum = Math.max(maximum, value);
        }
        double range = Math.max(1, maximum - minimum);
        float margin = 20 * getResources().getDisplayMetrics().density;
        Path line = new Path();
        paint.setColor(android.graphics.Color.rgb(0, 107, 94));
        paint.setStrokeWidth(3 * getResources().getDisplayMetrics().density);
        for (int i = 0; i < points.size(); i++) {
            float x = margin + (getWidth() - 2 * margin) * i / Math.max(1, points.size() - 1);
            float y = getHeight() - margin - (float) ((Double.parseDouble(points.get(i).points) - minimum) / range) * (getHeight() - 2 * margin);
            if (i == 0) line.moveTo(x, y); else line.lineTo(x, y);
            paint.setStyle(Paint.Style.FILL);
            canvas.drawCircle(x, y, 4 * getResources().getDisplayMetrics().density, paint);
        }
        paint.setStyle(Paint.Style.STROKE);
        canvas.drawPath(line, paint);
    }
}
