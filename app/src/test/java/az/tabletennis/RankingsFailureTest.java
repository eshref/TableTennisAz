package az.tabletennis;

import static org.assertj.core.api.Assertions.assertThat;

import android.widget.TextView;
import java.util.Arrays;
import java.util.Collection;
import okhttp3.mockwebserver.MockResponse;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.ParameterizedRobolectricTestRunner;
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters;
import org.robolectric.annotation.Config;

@RunWith(ParameterizedRobolectricTestRunner.class)
@Config(sdk = 26, qualifiers = "w411dp-h891dp", application = RankingsUiScenario.FixtureApplication.class)
public class RankingsFailureTest extends RankingsUiScenario {
    private final int statusCode;
    private final String responseBody;

    public RankingsFailureTest(String scenario, int statusCode, String responseBody) {
        this.statusCode = statusCode;
        this.responseBody = responseBody;
    }

    @Parameters(name = "{0}") public static Collection<Object[]> unusableResponses() {
        return Arrays.asList(new Object[][] {
                {"access denied", 403, "Forbidden"},
                {"changed markup", 200, "<html><body>Sign in to continue</body></html>"},
                {"partially malformed list", 200, "<div class='ranking-page-list'>"
                        + "<a href='/players/11'><div class='rank-name'>Şəms Quliyeva</div><div class='rank-points'>950 Xal</div></a>"
                        + "<a href='/players/99'><div class='rank-name'>Natamam</div><div class='rank-points'>?</div></a></div>"},
                {"conflicting source identity", 200, "<div class='ranking-page-list'>"
                        + "<a href='/players/12'><div class='rank-name'>İlqar Həsənov</div><div class='rank-points'>801 Xal</div></a></div>"}
        });
    }

    @Test public void unavailableFirstVisitAndFailedRefreshNeverInventOrReplaceSavedRankings() throws Exception {
        // arrange
        server.enqueue(html("amateur.html"));
        server.enqueue(new MockResponse().setResponseCode(statusCode).setBody(responseBody));

        // act
        openScreen();

        // assert
        awaitText("Reytinq yüklənmədi. İnternet bağlantısını yoxlayıb yenidən cəhd edin.");
        assertThat(((TextView) screen.get().findViewById(R.id.updated)).getText().toString()).isEmpty();
        assertThat(visibleText()).noneMatch(text -> text.matches("\\d+\\. .* — \\d+ xal"));

        // arrange
        server.enqueue(html("amateur.html"));
        server.enqueue(html("pro.html"));

        // act
        screen.get().findViewById(R.id.refresh).performClick();

        // assert
        awaitText("1. Şəms Quliyeva — 950 xal");
        String successfulUpdate = ((TextView) screen.get().findViewById(R.id.updated)).getText().toString();
        assertThat(successfulUpdate).startsWith("Son uğurlu yenilənmə: ");

        // arrange
        server.enqueue(html("amateur.html"));
        server.enqueue(new MockResponse().setResponseCode(statusCode).setBody(responseBody));

        // act
        screen.get().findViewById(R.id.refresh).performClick();

        // assert
        awaitText("Yeniləmək mümkün olmadı. Saxlanmış reytinq göstərilir.");
        assertThat(visibleText()).contains("1. Şəms Quliyeva — 950 xal", "4. Işıl Əliyeva — 600 xal");
        assertThat(((TextView) screen.get().findViewById(R.id.updated)).getText().toString()).isEqualTo(successfulUpdate);

        // act
        screen.pause().stop().destroy();
        FixtureApplication.online = false;
        server.shutdown();
        openScreen();

        // assert
        awaitText("Yeniləmək mümkün olmadı. Saxlanmış reytinq göstərilir.");
        assertThat(visibleText()).contains("1. Şəms Quliyeva — 950 xal", "4. Işıl Əliyeva — 600 xal");
        assertThat(((TextView) screen.get().findViewById(R.id.updated)).getText().toString()).isEqualTo(successfulUpdate);
    }
}
