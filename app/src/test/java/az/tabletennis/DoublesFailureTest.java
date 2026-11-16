package az.tabletennis;

import static org.assertj.core.api.Assertions.assertThat;

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
public class DoublesFailureTest extends DoublesUiScenario {
    private final int status;
    private final String body;

    public DoublesFailureTest(String name, int status, String body) { this.status = status; this.body = body; }

    @Parameters(name = "{0}") public static Collection<Object[]> failures() {
        return Arrays.asList(new Object[][] {
                {"HTTP failure", 403, "Forbidden"},
                {"missing ranking", 200, "<html>Sign in</html>"},
                {"partially unusable ranking", 200, ranking(pairRow(110, 1, 800) + pairRow(220, 2, 800).replace("rank-points", "changed"))},
                {"conflicting pair identity", 200, ranking(pairRow(110, 1, 800) + pairRow(110, 2, 800))},
                {"conflicting source positions", 200, ranking(pairRow(110, 1, 800) + pairRow(220, 1, 800))}
        });
    }

    private static String ranking(String rows) { return "<div id='center'><div class='ranking-page-list'>" + rows + "</div></div>"; }

    @Test public void unusableFirstLoadsCanRetryAndFailedRefreshesPreserveCompleteRowsAndUpdateTime() throws Exception {
        // arrange
        prepareDoubles();
        responses.put("/players/duos", () -> new MockResponse().setResponseCode(status).setBody(body));
        openScreen();

        // assert
        awaitText("1. Şəms Quliyeva — 950 xal");

        // act
        clickText("Qoşalar");

        // assert
        awaitText("Məlumat yüklənmədi. İnternet bağlantısını yoxlayın.");
        assertThat(visibleText()).doesNotContain("1. İlqar Həsənov / Əli Məmmədov — 800 xal", "Axtarışa uyğun qoşa tapılmadı.");
        assertThat(((android.widget.TextView) activity().findViewById(R.id.updated)).getText().toString()).isEmpty();

        // arrange
        responses.remove("/players/duos");

        // act
        activity().findViewById(R.id.refresh).performClick();

        // assert
        awaitText("1. İlqar Həsənov / Əli Məmmədov — 800 xal");
        String updated = ((android.widget.TextView) activity().findViewById(R.id.updated)).getText().toString();

        // arrange
        responses.put("/players/duos", () -> new MockResponse().setResponseCode(status).setBody(body));
        FixtureApplication.time = java.time.Clock.offset(FixtureApplication.time, java.time.Duration.ofMinutes(2));

        // act
        activity().findViewById(R.id.refresh).performClick();

        // assert
        awaitText("Yeniləmək mümkün olmadı. Saxlanmış məlumat göstərilir.");
        assertThat(visibleText()).contains(updated, "1. İlqar Həsənov / Əli Məmmədov — 800 xal",
                "2. İlqar Həsənov / Şəms Quliyeva — 800 xal", "3. Işıl Əliyeva / Əli Məmmədov — 600 xal");
    }
}
