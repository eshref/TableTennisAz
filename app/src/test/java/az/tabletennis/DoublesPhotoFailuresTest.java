package az.tabletennis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.robolectric.Shadows.shadowOf;

import android.graphics.Color;
import android.graphics.drawable.VectorDrawable;
import android.os.Looper;
import android.widget.TextView;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.ParameterizedRobolectricTestRunner;
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters;
import org.robolectric.annotation.Config;

@RunWith(ParameterizedRobolectricTestRunner.class)
@Config(sdk = 26, qualifiers = "w411dp-h891dp", application = RankingsUiScenario.FixtureApplication.class)
public class DoublesPhotoFailuresTest extends DoublesPhotoScenario {
    private final int status;
    private final String body;

    public DoublesPhotoFailuresTest(String scenario, int status, String body) { this.status = status; this.body = body; }

    @Parameters(name = "{0}") public static Collection<Object[]> failures() {
        return Arrays.asList(new Object[][] {{"HTTP failure", 503, ""}, {"unusable image", 200, "not an image"}});
    }

    @Test public void oneFailedImageKeepsItsPartnerUsableAndCannotReplaceRankingOrAdvanceTime() throws Exception {
        // arrange
        prepareDoubles();
        website.compute("/api/getDuoInfo?id=110", (path, identity) -> photos(identity, "/portraits/ilqar.png", "/portraits/ali.png"));
        CountDownLatch release = new CountDownLatch(1);
        responses.put("/portraits/ilqar.png", () -> portrait(Color.RED));
        responses.put("/portraits/ali.png", () -> {
            try { release.await(20, TimeUnit.SECONDS); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            return new MockResponse().setResponseCode(status).setBody(body);
        });

        try {
            // act
            openScreen();
            clickText("Qoşalar");

            // assert
            awaitText("1. İlqar Həsənov / Əli Məmmədov — 800 xal");
            awaitHalf("İlqar Həsənov\nƏli Məmmədov", 0, Color.RED);
            String updated = ((TextView) activity().findViewById(R.id.updated)).getText().toString();

            // arrange
            FixtureApplication.time = java.time.Clock.offset(FixtureApplication.time, java.time.Duration.ofMinutes(2));
            responses.put("/players/duos", () -> new MockResponse().setResponseCode(503));

            // act
            release.countDown();
            activity().findViewById(R.id.refresh).performClick();

            // assert
            awaitText("Yeniləmək mümkün olmadı. Saxlanmış məlumat göstərilir.");
            awaitHalf("İlqar Həsənov\nƏli Məmmədov", 0, Color.RED);
            assertThat(half("İlqar Həsənov\nƏli Məmmədov", 1).getDrawable()).isInstanceOf(VectorDrawable.class);
            assertThat(visibleText()).contains(updated, "1. İlqar Həsənov / Əli Məmmədov — 800 xal");

            // arrange
            FixtureApplication.online = false;
            ((RankingsApplication) activity().getApplication()).onTrimMemory(android.content.ComponentCallbacks2.TRIM_MEMORY_COMPLETE);

            // act
            restart();
            clickText("Qoşalar");

            // assert
            awaitHalf("İlqar Həsənov\nƏli Məmmədov", 0, Color.RED);
            assertThat(half("İlqar Həsənov\nƏli Məmmədov", 1).getDrawable()).isInstanceOf(VectorDrawable.class);
            assertThat(visibleText()).contains(updated);
        } finally {
            release.countDown();
        }
    }
}
