package az.tabletennis;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.GradientDrawable;
import android.widget.ImageView;

/** Circular drawing also works on software renderers; the upper crop keeps faces in view. */
final class PlayerPortraitView extends ImageView {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

    PlayerPortraitView(Context context) {
        super(context);
        setScaleType(ScaleType.CENTER_CROP);
        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.OVAL);
        background.setColor(context.getColor(R.color.soft));
        setBackground(background);
        setClipToOutline(true);
    }

    @Override protected void onDraw(Canvas canvas) {
        if (!(getDrawable() instanceof BitmapDrawable portrait)) {
            super.onDraw(canvas);
            return;
        }
        Bitmap bitmap = portrait.getBitmap();
        float scale = Math.max((float) getWidth() / bitmap.getWidth(), (float) getHeight() / bitmap.getHeight());
        Matrix crop = new Matrix();
        crop.setScale(scale, scale);
        crop.postTranslate((getWidth() - bitmap.getWidth() * scale) * 0.5f,
                (getHeight() - bitmap.getHeight() * scale) * 0.2f);
        BitmapShader shader = new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        shader.setLocalMatrix(crop);
        paint.setShader(shader);
        canvas.drawCircle(getWidth() / 2f, getHeight() / 2f, Math.min(getWidth(), getHeight()) / 2f, paint);
    }
}
