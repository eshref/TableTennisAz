package az.tabletennis;

import static org.assertj.core.api.Assertions.assertThat;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.VectorDrawable;
import android.widget.TextView;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 26, qualifiers = "w411dp-h891dp", application = RankingsUiScenario.FixtureApplication.class)
public class DoublesPhotoMigrationTest extends DoublesPhotoScenario {
    @Test public void legacyIdentityAcquiresOptionalMetadataWithoutDelayingRankingOrChangingItsTimestamp() throws Exception {
        // arrange
        prepareDoubles();
        try (var input = getClass().getResourceAsStream("/fixtures/legacy-duo-110");
             var output = RuntimeEnvironment.getApplication().openFileOutput("saved-duo-110", Context.MODE_PRIVATE)) {
            input.transferTo(output);
        }
        CountDownLatch release = new CountDownLatch(1);
        responses.put("/api/getDuoInfo?id=110", () -> {
            try { release.await(20, TimeUnit.SECONDS); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            // Metadata can return members in a different order; images still belong beside the displayed names.
            return new okhttp3.mockwebserver.MockResponse().setBody(photos(
                    pairInfo(110, 13, "Əli Məmmədov", 12, "İlqar Həsənov"), "/portraits/ali.png", "/portraits/ilqar.png"));
        });
        responses.put("/portraits/ilqar.png", () -> portrait(Color.RED));
        responses.put("/portraits/ali.png", () -> portrait(Color.BLUE));

        try {
            // act
            openScreen();
            clickText("Qoşalar");

            // assert
            awaitText("1. İlqar Həsənov / Əli Məmmədov — 800 xal");
            awaitText("Məlumat yeniləndi.");
            assertThat(half("İlqar Həsənov\nƏli Məmmədov", 0).getDrawable()).isInstanceOf(VectorDrawable.class);
            String updated = ((TextView) activity().findViewById(R.id.updated)).getText().toString();
            assertThat(requestTo("/api/getDuoInfo?id=110")).isEqualTo("/api/getDuoInfo?id=110");

            // act
            release.countDown();

            // assert
            awaitHalf("İlqar Həsənov\nƏli Məmmədov", 0, Color.RED);
            awaitHalf("İlqar Həsənov\nƏli Məmmədov", 1, Color.BLUE);
            assertThat(((TextView) activity().findViewById(R.id.updated)).getText().toString()).isEqualTo(updated);

            // arrange
            FixtureApplication.online = false;
            ((RankingsApplication) activity().getApplication()).onTrimMemory(android.content.ComponentCallbacks2.TRIM_MEMORY_COMPLETE);
            requestedPaths();

            // act
            restart();
            clickText("Qoşalar");

            // assert
            awaitHalf("İlqar Həsənov\nƏli Məmmədov", 0, Color.RED);
            awaitHalf("İlqar Həsənov\nƏli Məmmədov", 1, Color.BLUE);
            assertThat(visibleText()).contains(updated);
            assertThat(requestedPaths()).isEmpty();
        } finally {
            release.countDown();
        }
    }
}
