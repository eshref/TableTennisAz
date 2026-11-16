package az.tabletennis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
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
    private final long age;
    private final String expectedRequest;
    private final String expectedRow;
    private final boolean unchangedTime;

    public DoublesFreshnessTest(String name, long age, String expectedRequest, String expectedRow, boolean unchangedTime) {
        this.age = age;
        this.expectedRequest = expectedRequest;
        this.expectedRow = expectedRow;
        this.unchangedTime = unchangedTime;
    }

    @Parameters(name = "{0}") public static Collection<Object[]> boundaries() {
        return Arrays.asList(new Object[][] {
                {"below", 899_999L, null, "1. İlqar Həsənov / Əli Məmmədov — 800 xal", true},
                {"exactly", 900_000L, null, "1. İlqar Həsənov / Əli Məmmədov — 800 xal", true},
                {"above", 900_001L, "/players/duos", "1. İlqar Həsənov / Əli Məmmədov — 801 xal", false}
        });
    }

    @Test public void openingDoublesShowsSavedRowsBeforeRefreshingOnlyAboveFifteenMinutesAndReusesNames() throws Exception {
        // arrange
        prepareDoubles();
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
        FixtureApplication.time = Clock.offset(FixtureApplication.time, Duration.ofMillis(age));
        website.compute("/players/duos", (path, body) -> body.replace("800 Xal", "801 Xal"));
        responses.put("/players/duos", () -> new MockResponse().setBody(website.get("/players/duos")).setBodyDelay(1, TimeUnit.SECONDS));

        // act
        openScreen();
        clickText("Qoşalar");
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(100));

        // assert
        assertThat(visibleText()).contains("1. İlqar Həsənov / Əli Məmmədov — 800 xal", updated);
        assertThat(requestTo("/players/duos")).isEqualTo(expectedRequest);
        awaitText(expectedRow);
        assertThat(((android.widget.TextView) activity().findViewById(R.id.updated)).getText().toString().equals(updated))
                .isEqualTo(unchangedTime);
        assertThat(requestedPaths()).noneMatch(path -> path.startsWith("/api/getDuoInfo"));
    }
}
