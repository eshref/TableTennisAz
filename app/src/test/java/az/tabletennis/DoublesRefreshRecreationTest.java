package az.tabletennis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
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
public class DoublesRefreshRecreationTest extends DoublesUiScenario {
    private final int ageMinutes;

    public DoublesRefreshRecreationTest(String name, int ageMinutes) { this.ageMinutes = ageMinutes; }

    @Parameters(name = "{0}") public static Collection<Object[]> ages() {
        return Arrays.asList(new Object[][] {{"fresh manual refresh", 2}, {"stale refresh", 16}});
    }

    @Test public void recreationFollowsTheOutstandingRefreshWithoutLosingMovementOrSavingAnotherObservation() throws Exception {
        // arrange
        prepareDoubles();
        website.put("/players/duos", ranking(pairRow(110, 12, 800)));
        CountDownLatch requested = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        // act
        openScreen();
        clickText("Qoşalar");

        // assert
        awaitText("12. İlqar Həsənov / Əli Məmmədov — 800 xal");
        requestedPaths();

        // arrange
        FixtureApplication.time = Clock.offset(FixtureApplication.time, Duration.ofMinutes(ageMinutes));
        responses.put("/players/duos", () -> {
            requested.countDown();
            try { release.await(30, TimeUnit.SECONDS); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            return new MockResponse().setBody(ranking(pairRow(110, 9, 800)));
        });
        try {
            // act
            activity().findViewById(R.id.refresh).performClick();

            // assert
            assertThat(requested.await(5, TimeUnit.SECONDS)).isTrue();

            // act
            screen.recreate();
            release.countDown();

            // assert
            awaitText("9. İlqar Həsənov / Əli Məmmədov — 800 xal, 3 pillə yüksəlib");
            assertThat(requestedPaths()).containsExactly("/players/duos");

            // arrange
            responses.remove("/players/duos");
            website.put("/players/duos", ranking(pairRow(110, 11, 800)));

            // act
            activity().findViewById(R.id.refresh).performClick();

            // assert
            awaitText("11. İlqar Həsənov / Əli Məmmədov — 800 xal, 2 pillə geriləyib");
        } finally {
            release.countDown();
        }
    }

    private static String ranking(String rows) {
        return "<div id='center'><div class='ranking-page-list'>" + rows + "</div></div>";
    }
}
