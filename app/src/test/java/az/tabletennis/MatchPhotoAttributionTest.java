package az.tabletennis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.robolectric.Shadows.shadowOf;

import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.os.Looper;
import android.view.View;
import android.widget.ImageView;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.ParameterizedRobolectricTestRunner;
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters;
import org.robolectric.annotation.Config;

@RunWith(ParameterizedRobolectricTestRunner.class)
@Config(sdk = 26, qualifiers = "w411dp-h891dp", application = RankingsUiScenario.FixtureApplication.class)
public class MatchPhotoAttributionTest extends PlayerPhotoScenario {
    private final String fixture;
    private final Integer expectedColor;
    private final List<String> expectedRequests;

    public MatchPhotoAttributionTest(String fixture, Integer expectedColor, List<String> expectedRequests) {
        this.fixture = fixture;
        this.expectedColor = expectedColor;
        this.expectedRequests = expectedRequests;
    }

    @Parameters(name = "{0}") public static Collection<Object[]> sources() {
        return Arrays.asList(new Object[][] {
                {"singles-detail.html", Color.RED, List.of("/matches/501", "/portraits/one.png")},
                {"doubles-detail.html", null, List.of("/matches/501")}
        });
    }

    @Test public void unlinkedImagesAreUsedOnlyWhenTheSideHasOneUnambiguousParticipant() throws Exception {
        // arrange
        org.jsoup.nodes.Document source = org.jsoup.Jsoup.parse(html(fixture).getBody().readUtf8());
        source.selectFirst(".match-player-box").prepend("<div class='match-player-image-div'>"
                + "<img src='/portraits/one.png'><img src='/portraits/two.png' class='match-player-para-image'></div>");
        website.put("/matches/501", source.outerHtml());
        responses.put("/portraits/one.png", () -> portrait(Color.RED));
        responses.put("/portraits/two.png", () -> portrait(Color.BLUE));

        // act
        openDirect(MatchActivity.class, "matchId", 501);

        // assert
        awaitText("Məlumat yeniləndi.");
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
        Integer renderedColor;
        do {
            shadowOf(Looper.getMainLooper()).idle();
            ImageView image = ((View) findText(activity().getWindow().getDecorView(), "İlqar Həsənov").getParent()).findViewById(android.R.id.icon);
            renderedColor = image.getDrawable() instanceof BitmapDrawable bitmap ? bitmap.getBitmap().getPixel(12, 12) : null;
            if (java.util.Objects.equals(renderedColor, expectedColor)) break;
            Thread.sleep(20);
        } while (System.nanoTime() < deadline);
        assertThat(renderedColor).isEqualTo(expectedColor);
        assertThat(requestedPaths()).containsExactlyInAnyOrderElementsOf(expectedRequests);
        assertThat(visibleText()).contains("İlqar Həsənov", "Şəms Quliyeva");
    }
}
