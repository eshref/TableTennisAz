package az.tabletennis;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.drawable.GradientDrawable;
import android.widget.ImageView;
import android.widget.LinearLayout;

/** One partnership circle; each independently bound cell crops its own player's portrait. */
final class DoublesPortraitView extends LinearLayout {
    final ImageView first;
    final ImageView second;
    private final Path circle = new Path();

    DoublesPortraitView(Context context) {
        super(context);
        setOrientation(HORIZONTAL);
        setId(android.R.id.icon);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.OVAL);
        background.setColor(context.getColor(R.color.soft));
        setBackground(background);
        setClipToOutline(true);
        first = half(context);
        second = half(context);
        LayoutParams secondLayout = (LayoutParams) second.getLayoutParams();
        secondLayout.setMarginStart(Presentation.dp(context, 1));
    }

    private ImageView half(Context context) {
        ImageView view = new ImageView(context);
        view.setScaleType(ImageView.ScaleType.CENTER_CROP);
        addView(view, new LayoutParams(0, -1, 1));
        return view;
    }

    @Override protected void dispatchDraw(Canvas canvas) {
        circle.reset();
        circle.addCircle(getWidth() / 2f, getHeight() / 2f, Math.min(getWidth(), getHeight()) / 2f, Path.Direction.CW);
        int saved = canvas.save();
        canvas.clipPath(circle);
        super.dispatchDraw(canvas);
        canvas.restoreToCount(saved);
    }
}
