package az.tabletennis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.ParameterizedRobolectricTestRunner;
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters;
import org.robolectric.annotation.Config;

@RunWith(ParameterizedRobolectricTestRunner.class)
@Config(sdk = 26, qualifiers = "w411dp-h891dp", application = RankingsUiScenario.FixtureApplication.class)
public class RankingsFreshnessTest extends RankingsUiScenario {
    private final long age;
    private final String expectedRequest;
    private final String expectedRow;

    public RankingsFreshnessTest(String scenario, long age, String expectedRequest, String expectedRow) {
        this.age = age;
        this.expectedRequest = expectedRequest;
        this.expectedRow = expectedRow;
    }

    @Parameters(name = "{0}") public static Collection<Object[]> boundaries() {
        return Arrays.asList(new Object[][] {
                {"below 15 minutes", 899_999L, null, "1. Şəms Quliyeva — 950 xal"},
                {"exactly 15 minutes", 900_000L, null, "1. Şəms Quliyeva — 950 xal"},
                {"above 15 minutes", 900_001L, "/players?level=amateur", "1. Yeni ad — 950 xal"}
        });
    }

    @Test public void reopeningShowsSavedRankingsBeforeRefreshingOnlyAboveTheBoundary() throws Exception {
        // arrange
        server.enqueue(html("amateur.html"));
        server.enqueue(html("pro.html"));
        openScreen();

        // assert
        awaitText("1. Şəms Quliyeva — 950 xal");
        assertThat(server.takeRequest(1, TimeUnit.SECONDS)).isNotNull();
        assertThat(server.takeRequest(1, TimeUnit.SECONDS)).isNotNull();

        // arrange
        screen.pause().stop().destroy();
        FixtureApplication.time = java.time.Clock.offset(FixtureApplication.time, Duration.ofMillis(age));
        server.enqueue(html("amateur.html").setBodyDelay(1, TimeUnit.SECONDS));
        String changed = html("pro.html").getBody().readUtf8().replace("Şəms Quliyeva", "Yeni ad");
        server.enqueue(new okhttp3.mockwebserver.MockResponse().setBody(changed));

        // act
        openScreen();

        // assert
        assertThat(visibleText()).contains("1. Şəms Quliyeva — 950 xal");
        assertThat(Optional.ofNullable(server.takeRequest(1, TimeUnit.SECONDS)).map(RecordedRequest::getPath).orElse(null))
                .isEqualTo(expectedRequest);
        awaitText(expectedRow);
    }
}
