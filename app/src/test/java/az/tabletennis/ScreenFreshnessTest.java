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
    private final Duration age;
    private final String expectedRequest;
    private final java.util.List<String> expectedText;
    private final String expectedUpdate;

    public ScreenFreshnessTest(String name, SavedScreenCase journey, Duration age, String expectedRequest,
                               java.util.List<String> expectedText, String expectedUpdate) {
        this.journey = journey;
        this.age = age;
        this.expectedRequest = expectedRequest;
        this.expectedText = expectedText;
        this.expectedUpdate = expectedUpdate;
    }

    @Parameters(name = "{0}") public static Collection<Object[]> screensAndBoundaries() {
        Duration threshold = Duration.ofHours(48);
        String savedUpdate = "Son uğurlu yenilənmə: 15 noy 2026 12:00";
        Collection<Object[]> rows = new ArrayList<>();
        for (SavedScreenCase screen : SavedScreenCase.values()) {
            rows.add(new Object[] {screen + " below 48 hours", screen, threshold.minusMillis(1), null, screen.initialText, savedUpdate});
            rows.add(new Object[] {screen + " exactly 48 hours", screen, threshold, null, screen.initialText, savedUpdate});
            rows.add(new Object[] {screen + " above 48 hours", screen, threshold.plusMillis(1), screen.path, screen.changedText,
                    "Son uğurlu yenilənmə: 17 noy 2026 12:00"});
        }
        return rows;
    }

    @Test public void eachSavedScreenDisplaysCachedDataImmediatelyAndRefreshesOnlyAboveTheBoundary() throws Exception {
        // arrange
        prepareSavedScreens();

        // act
        openDirect(journey.activity, journey.identity, journey.id);

        // assert
        for (String text : journey.initialText) awaitText(text);
        String updated = ((android.widget.TextView) activity().findViewById(R.id.updated)).getText().toString();
        assertThat(updated).startsWith("Son uğurlu yenilənmə: ");
        requestedPaths();

        // arrange
        closeProductScreens();
        FixtureApplication.time = Clock.offset(FixtureApplication.time, age);
        website.compute(journey.path, (path, body) -> body.replace(journey.oldFragment, journey.newFragment));
        responses.put(journey.path, () -> new MockResponse().setBody(website.get(journey.path)).setBodyDelay(1, TimeUnit.SECONDS));

        // act
        openDirect(journey.activity, journey.identity, journey.id);

        // assert
        assertThat(visibleText()).containsAll(journey.initialText).contains(updated);
        if (expectedRequest == null) assertThat(server.takeRequest(1, TimeUnit.SECONDS)).isNull();
        for (String text : expectedText) awaitText(text);
        assertThat(visibleText()).contains(expectedUpdate);
        if (expectedRequest != null) assertThat(requestedPaths()).contains(expectedRequest);
    }
}
