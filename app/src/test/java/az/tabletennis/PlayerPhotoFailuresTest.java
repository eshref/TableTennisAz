package az.tabletennis;

import static org.assertj.core.api.Assertions.assertThat;

import android.graphics.Color;
import android.widget.TextView;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import okhttp3.mockwebserver.MockResponse;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.ParameterizedRobolectricTestRunner;
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters;
import org.robolectric.annotation.Config;

@RunWith(ParameterizedRobolectricTestRunner.class)
@Config(sdk = 26, qualifiers = "w411dp-h891dp", application = RankingsUiScenario.FixtureApplication.class)
public class PlayerPhotoFailuresTest extends PlayerPhotoScenario {
    private final String markup;
    private final int status;
    private final String body;
    private final String redirect;
    private final List<String> expectedRequests;

    public PlayerPhotoFailuresTest(String scenario, String markup, int status, String body, String redirect, List<String> expectedRequests) {
        this.markup = markup;
        this.status = status;
        this.body = body;
        this.redirect = redirect;
        this.expectedRequests = expectedRequests;
    }

    @Parameters(name = "{0}") public static Collection<Object[]> unavailablePhotos() {
        List<String> noPhoto = List.of("/players?level=amateur", "/portraits/ali.png");
        List<String> attemptedPhoto = List.of("/players?level=amateur", "/portraits/ali.png", "/portraits/bad.png");
        return Arrays.asList(new Object[][] {
                {"missing metadata", "", 200, "", "", noPhoto},
                {"source placeholder", "<img src='/assets/img/person.png'>", 200, "", "", noPhoto},
                {"invalid URL", "<img src='https://['>", 200, "", "", noPhoto},
                {"insecure external URL", "<img src='http://example.com/photo.png'>", 200, "", "", noPhoto},
                {"HTTP failure", "<img src='/portraits/bad.png'>", 503, "Unavailable", "", attemptedPhoto},
                {"invalid image", "<img src='/portraits/bad.png'>", 200, "<html>Sign in</html>", "", attemptedPhoto},
                {"redirect outside origin", "<img src='/portraits/bad.png'>", 302, "", "http://localhost:1/untrusted.png", attemptedPhoto}
        });
    }

    @Test public void unavailablePortraitUsesLocalSilhouetteWhileTheOtherPhotoAndRankingRemainUsable() throws Exception {
        // arrange
        website.put("/players?level=amateur", ranking("/unused.png", "/portraits/ali.png").replace("<img src='/unused.png'>", markup));
        responses.put("/portraits/ali.png", () -> portrait(Color.BLUE));
        responses.put("/portraits/bad.png", () -> new MockResponse().setResponseCode(status).setBody(body).setHeader("Location", redirect));

        // act
        openScreen();

        // assert
        awaitText("Məlumat yeniləndi.");
        String updated = ((TextView) activity().findViewById(R.id.updated)).getText().toString();
        awaitPortrait("Əli Məmmədov", Color.BLUE);
        assertThat(photo("İlqar Həsənov").getDrawable()).isInstanceOf(android.graphics.drawable.VectorDrawable.class);
        assertThat(visibleText()).contains("1. İlqar Həsənov — 800 xal", "2. Əli Məmmədov — 700 xal", updated);
        assertThat(requestedPaths()).containsExactlyInAnyOrderElementsOf(expectedRequests);
        assertThat(activity().findViewById(R.id.refresh).isEnabled()).isTrue();

        // arrange
        FixtureApplication.online = false;

        // act
        restart();

        // assert
        awaitText("İnternet bağlantısı yoxdur. Saxlanmış məlumat göstərilir.");
        awaitPortrait("Əli Məmmədov", Color.BLUE);
        assertThat(photo("İlqar Həsənov").getDrawable()).isInstanceOf(android.graphics.drawable.VectorDrawable.class);
        assertThat(visibleText()).contains(updated);
        assertThat(requestedPaths()).isEmpty();
    }
}
