package az.tabletennis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.robolectric.Shadows.shadowOf;

import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.os.Looper;
import android.view.View;
import android.widget.ImageView;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okio.Buffer;
import org.robolectric.annotation.Config;

@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
abstract class PlayerPhotoScenario extends DoublesUiScenario {
    protected String ranking(String first, String second) {
        return "<div class='ranking-page-list'>"
                + "<a href='/players/12'><img src='" + first + "'><div class='rank-name'>İlqar Həsənov</div><div class='rank-points'>800 Xal</div></a>"
                + "<a href='/players/13'><img src='" + second + "'><div class='rank-name'>Əli Məmmədov</div><div class='rank-points'>700 Xal</div></a></div>";
    }

    protected MockResponse portrait(int color) {
        Bitmap bitmap = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(color);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, bytes);
        bitmap.recycle();
        return new MockResponse().setHeader("Content-Type", "image/png").setBody(new Buffer().write(bytes.toByteArray()));
    }

    protected ImageView photo(String name) {
        View label = findText(activity().getWindow().getDecorView(), name);
        if (label == null) throw new IllegalStateException("No visible player: " + name);
        View row = (View) label.getParent().getParent();
        return row.findViewById(android.R.id.icon);
    }

    protected void awaitPortrait(String name, int color) throws Exception {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle();
            if (photo(name).getDrawable() instanceof BitmapDrawable image
                    && image.getBitmap().getPixel(12, 12) == color) return;
            Thread.sleep(20);
        }
        assertThat(photo(name).getDrawable()).isInstanceOf(BitmapDrawable.class);
        assertThat(((BitmapDrawable) photo(name).getDrawable()).getBitmap().getPixel(12, 12)).isEqualTo(color);
    }
}
