package az.tabletennis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
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
public class ScreenFreshnessTest extends ProductUiScenario {
    private final SavedScreenCase journey;
    private final long age;
    private final String expectedRequest;
    private final String expectedText;
    private final boolean unchangedTime;

    public ScreenFreshnessTest(String name, SavedScreenCase journey, long age, String expectedRequest, String expectedText, boolean unchangedTime) {
        this.journey = journey;
        this.age = age;
        this.expectedRequest = expectedRequest;
        this.expectedText = expectedText;
        this.unchangedTime = unchangedTime;
    }

    @Parameters(name = "{0}") public static Collection<Object[]> screensAndBoundaries() {
        Collection<Object[]> rows = new ArrayList<>();
        for (SavedScreenCase screen : SavedScreenCase.values()) {
            rows.add(new Object[] {screen + " below", screen, 899_999L, null, screen.initialText, true});
            rows.add(new Object[] {screen + " exactly", screen, 900_000L, null, screen.initialText, true});
            rows.add(new Object[] {screen + " above", screen, 900_001L, screen.path, screen.changedText, false});
        }
        return rows;
    }

    @Test public void eachSavedScreenDisplaysCachedDataImmediatelyAndRefreshesOnlyAboveFifteenMinutes() throws Exception {
        // arrange
        prepareSavedScreens();
        openDirect(journey.activity, journey.identity, journey.id);

        // assert
        awaitText(journey.initialText);
        String updated = ((android.widget.TextView) activity().findViewById(R.id.updated)).getText().toString();
        assertThat(updated).startsWith("Son uğurlu yenilənmə: ");
        requestedPaths();

        // arrange
        closeProductScreens();
        FixtureApplication.time = Clock.offset(FixtureApplication.time, Duration.ofMillis(age));
        website.compute(journey.path, (path, body) -> body.replace(journey.oldFragment, journey.newFragment));
        responses.put(journey.path, () -> new MockResponse().setBody(website.get(journey.path)).setBodyDelay(1, TimeUnit.SECONDS));

        // act
        openDirect(journey.activity, journey.identity, journey.id);

        // assert
        assertThat(visibleText()).contains(journey.initialText, updated);
        assertThat(requestTo(journey.path)).isEqualTo(expectedRequest);
        awaitText(expectedText);
        assertThat(((android.widget.TextView) activity().findViewById(R.id.updated)).getText().toString().equals(updated)).isEqualTo(unchangedTime);
    }
}
