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
    private final Duration age;
    private final String expectedRequest;
    private final String expectedRow;
    private final String expectedUpdate;

    public RankingsFreshnessTest(String scenario, Duration age, String expectedRequest, String expectedRow, String expectedUpdate) {
        this.age = age;
        this.expectedRequest = expectedRequest;
        this.expectedRow = expectedRow;
        this.expectedUpdate = expectedUpdate;
    }

    @Parameters(name = "{0}") public static Collection<Object[]> boundaries() {
        Duration threshold = Duration.ofHours(48);
        String savedUpdate = "Son uğurlu yenilənmə: 15 noy 2026 12:00";
        return Arrays.asList(new Object[][] {
                {"below 48 hours", threshold.minusMillis(1), null, "1. İlqar Həsənov — 800 xal", savedUpdate},
                {"exactly 48 hours", threshold, null, "1. İlqar Həsənov — 800 xal", savedUpdate},
                {"above 48 hours", threshold.plusMillis(1), "/players?level=amateur", "1. Yeni ad — 800 xal",
                        "Son uğurlu yenilənmə: 17 noy 2026 12:00"}
        });
    }

    @Test public void reopeningShowsSavedRankingsBeforeRefreshingOnlyAboveTheBoundary() throws Exception {
        // arrange
        server.enqueue(html("amateur.html"));

        // act
        openScreen();

        // assert
        awaitText("1. İlqar Həsənov — 800 xal");
        assertThat(server.takeRequest(1, TimeUnit.SECONDS)).isNotNull();
        String updated = ((android.widget.TextView) activity().findViewById(R.id.updated)).getText().toString();
        assertThat(updated).startsWith("Son uğurlu yenilənmə: ");

        // arrange
        screen.pause().stop().destroy();
        FixtureApplication.time = java.time.Clock.offset(FixtureApplication.time, age);
        String changed = html("amateur.html").getBody().readUtf8().replace("İlqar Həsənov", "Yeni ad");
        server.enqueue(new okhttp3.mockwebserver.MockResponse().setBody(changed).setBodyDelay(1, TimeUnit.SECONDS));

        // act
        openScreen();

        // assert
        assertThat(visibleText()).contains("1. İlqar Həsənov — 800 xal", updated);
        assertThat(Optional.ofNullable(server.takeRequest(1, TimeUnit.SECONDS)).map(RecordedRequest::getPath).orElse(null))
                .isEqualTo(expectedRequest);
        awaitText(expectedRow);
        assertThat(visibleText()).contains(expectedUpdate);
    }
}
