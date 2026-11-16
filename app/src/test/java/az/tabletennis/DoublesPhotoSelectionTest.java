package az.tabletennis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.robolectric.Shadows.shadowOf;

import android.graphics.Color;
import android.os.Looper;
import android.widget.EditText;
import android.widget.ImageView;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 26, qualifiers = "w411dp-h891dp", application = RankingsUiScenario.FixtureApplication.class)
public class DoublesPhotoSelectionTest extends DoublesPhotoScenario {
    @Test public void latePartnerPortraitCannotLeakIntoAnotherPartnershipAfterSearchModeChangeOrRestart() throws Exception {
        // arrange
        prepareDoubles();
        website.compute("/api/getDuoInfo?id=110", (path, body) -> photos(body, "/portraits/ilqar.png", "/portraits/slow-ali.png"));
        website.compute("/api/getDuoInfo?id=220", (path, body) -> photos(body, "/portraits/ilqar.png", "/portraits/shams.png"));
        responses.put("/portraits/ilqar.png", () -> portrait(Color.RED));
        responses.put("/portraits/shams.png", () -> portrait(Color.GREEN));
        CountDownLatch release = new CountDownLatch(1);
        responses.put("/portraits/slow-ali.png", () -> {
            try { release.await(20, TimeUnit.SECONDS); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            return portrait(Color.BLUE);
        });
        try {
            // act
            openScreen();
            clickText("Qoşalar");

            // assert
            awaitText("1. İlqar Həsənov / Əli Məmmədov — 800 xal");
            ImageView recycled = half("İlqar Həsənov\nƏli Məmmədov", 1);
            awaitHalf("İlqar Həsənov\nŞəms Quliyeva", 1, Color.GREEN);

            // act
            ((EditText) activity().findViewById(R.id.search)).setText("şəms");
            shadowOf(Looper.getMainLooper()).idle();

            // assert
            assertThat(half("İlqar Həsənov\nŞəms Quliyeva", 1)).isSameAs(recycled);

            // act
            release.countDown();
            clickText("Oyunçular");
            clickText("Qoşalar");
            screen.recreate();

            // assert
            awaitHalf("İlqar Həsənov\nŞəms Quliyeva", 0, Color.RED);
            awaitHalf("İlqar Həsənov\nŞəms Quliyeva", 1, Color.GREEN);
            assertThat(((EditText) activity().findViewById(R.id.search)).getText().toString()).isEqualTo("şəms");

            // act
            ((EditText) activity().findViewById(R.id.search)).setText("");

            // assert
            awaitHalf("İlqar Həsənov\nƏli Məmmədov", 1, Color.BLUE);

            // arrange
            FixtureApplication.online = false;
            ((RankingsApplication) activity().getApplication()).onTrimMemory(android.content.ComponentCallbacks2.TRIM_MEMORY_COMPLETE);
            requestedPaths();

            // act
            restart();
            clickText("Qoşalar");

            // assert
            awaitHalf("İlqar Həsənov\nƏli Məmmədov", 1, Color.BLUE);
            awaitHalf("İlqar Həsənov\nŞəms Quliyeva", 1, Color.GREEN);
            awaitHalf("İlqar Həsənov\nŞəms Quliyeva", 0, Color.RED);
            assertThat(requestedPaths()).isEmpty();
        } finally {
            release.countDown();
        }
    }
}
