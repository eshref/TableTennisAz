package az.tabletennis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.robolectric.Shadows.shadowOf;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import java.util.concurrent.TimeUnit;

abstract class DoublesPhotoScenario extends PlayerPhotoScenario {
    protected String photos(String identity, String first, String second) {
        return identity.replace("}", ",\"player1PictureUrl\":\"" + first
                + "\",\"player2PictureUrl\":\"" + second + "\"}");
    }

    protected ViewGroup avatar(String names) {
        View name = findText(activity().getWindow().getDecorView(), names);
        if (name == null) throw new IllegalStateException("No visible partnership: " + names);
        return ((View) name.getParent().getParent()).findViewById(android.R.id.icon);
    }

    protected ImageView half(String names, int index) { return (ImageView) avatar(names).getChildAt(index); }

    protected void awaitHalf(String names, int index, int color) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle();
            if (half(names, index).getDrawable() instanceof BitmapDrawable image
                    && image.getBitmap().getPixel(12, 12) == color) return;
            Thread.sleep(20);
        }
        assertThat(half(names, index).getDrawable()).isInstanceOf(BitmapDrawable.class);
        assertThat(((BitmapDrawable) half(names, index).getDrawable()).getBitmap().getPixel(12, 12)).isEqualTo(color);
    }

    protected Bitmap renderedAvatar(String names) {
        View avatar = avatar(names);
        Bitmap rendered = Bitmap.createBitmap(avatar.getWidth(), avatar.getHeight(), Bitmap.Config.ARGB_8888);
        avatar.draw(new Canvas(rendered));
        return rendered;
    }
}
