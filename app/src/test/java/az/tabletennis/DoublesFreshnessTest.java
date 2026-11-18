package az.tabletennis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.ParameterizedRobolectricTestRunner;
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters;
import org.robolectric.annotation.Config;

@RunWith(ParameterizedRobolectricTestRunner.class)
@Config(sdk = 26, qualifiers = "w411dp-h891dp", application = RankingsUiScenario.FixtureApplication.class)
public class DoublesFreshnessTest extends DoublesUiScenario {
    private final Duration age;
    private final List<String> expectedRequests;
    private final String expectedRow;
    private final String expectedUpdate;

    public DoublesFreshnessTest(String name, Duration age, List<String> expectedRequests, String expectedRow, String expectedUpdate) {
        this.age = age;
        this.expectedRequests = expectedRequests;
        this.expectedRow = expectedRow;
        this.expectedUpdate = expectedUpdate;
    }

    @Parameters(name = "{0}") public static Collection<Object[]> boundaries() {
        Duration threshold = Duration.ofHours(48);
        String savedRow = "1. İlqar Həsənov / Əli Məmmədov — 800 xal";
        String savedUpdate = "Son uğurlu yenilənmə: 15 noy 2026 12:00";
        return Arrays.asList(new Object[][] {
                {"below 48 hours", threshold.minusMillis(1), List.of(), savedRow, savedUpdate},
                {"exactly 48 hours", threshold, List.of(), savedRow, savedUpdate},
                {"above 48 hours", threshold.plusMillis(1), List.of("/players/duos"),
                        "1. İlqar Həsənov / Əli Məmmədov — 801 xal", "Son uğurlu yenilənmə: 17 noy 2026 12:00"}
        });
    }

    @Test public void openingDoublesShowsSavedRowsBeforeRefreshingOnlyAboveTheBoundaryAndReusesNames() throws Exception {
        // arrange
        prepareDoubles();

        // act
        openScreen();

        // assert
        awaitText("1. İlqar Həsənov — 800 xal");

        // act
        clickText("Qoşalar");

        // assert
        awaitText("1. İlqar Həsənov / Əli Məmmədov — 800 xal");
        String updated = ((android.widget.TextView) activity().findViewById(R.id.updated)).getText().toString();
        requestedPaths();

        // arrange
        screen.pause().stop().destroy();
        FixtureApplication.time = Clock.offset(FixtureApplication.time, age);
        website.compute("/players/duos", (path, body) -> body.replace("800 Xal", "801 Xal"));
        responses.put("/players/duos", () -> new MockResponse().setBody(website.get("/players/duos")).setBodyDelay(1, TimeUnit.SECONDS));

        // act
        openScreen();
        clickText("Qoşalar");
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(100));

        // assert
        assertThat(visibleText()).contains("1. İlqar Həsənov / Əli Məmmədov — 800 xal", updated);
        if (expectedRequests.isEmpty()) assertThat(server.takeRequest(1, TimeUnit.SECONDS)).isNull();
        awaitText(expectedRow);
        assertThat(visibleText()).contains(expectedUpdate);
        assertThat(requestedPaths()).filteredOn(path -> !path.equals("/players?level=amateur"))
                .containsExactlyElementsOf(expectedRequests);
    }
}
