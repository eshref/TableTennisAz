package az.tabletennis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import okhttp3.mockwebserver.MockResponse;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.ParameterizedRobolectricTestRunner;
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters;
import org.robolectric.annotation.Config;

@RunWith(ParameterizedRobolectricTestRunner.class)
@Config(sdk = 26, qualifiers = "w411dp-h891dp", application = RankingsUiScenario.FixtureApplication.class)
public class SavedScreenFailureTest extends ProductUiScenario {
    private final SavedScreenCase journey;
    private final int status;
    private final String body;

    public SavedScreenFailureTest(String name, SavedScreenCase journey, int status, String body) {
        this.journey = journey;
        this.status = status;
        this.body = body;
    }

    @Parameters(name = "{0}") public static Collection<Object[]> failures() throws Exception {
        Collection<Object[]> rows = new ArrayList<>();
        for (SavedScreenCase screen : SavedScreenCase.values()) {
            rows.add(new Object[] {screen + " HTTP failure", screen, 403, "Forbidden"});
            rows.add(new Object[] {screen + " changed markup", screen, 200, "<html>Sign in</html>"});
        }
        String pair = fixture("pair-info.json");
        rows.add(new Object[] {"PAIR wrong results identity", SavedScreenCase.PAIR, 200,
                pair.replace("duo/110", "duo/220")});
        rows.add(new Object[] {"PAIR changed partnership", SavedScreenCase.PAIR, 200,
                pair.replace("\"player2Id\":13", "\"player2Id\":11")});
        rows.add(new Object[] {"PAIR invalid medal count", SavedScreenCase.PAIR, 200,
                pair.replace("\"firstPlaceCount\":4", "\"firstPlaceCount\":-1")});
        rows.add(new Object[] {"PROFILE wrong source identity", SavedScreenCase.PROFILE, 200,
                "<div id='center'><div class='player-block'><div class='name-div'>Başqa oyunçu</div></div><div id='chart-user-id'>11</div></div>"});
        rows.add(new Object[] {"MATCH incomplete doubles side", SavedScreenCase.MATCH, 200,
                fixture("doubles-detail.html").replace("<a href=\"/players/14\"><div class=\"match-player-name\">Işıl Əliyeva</div></a>", "")});
        rows.add(new Object[] {"CHART invalid numeric value", SavedScreenCase.CHART, 200, "[{\"day\":\"31.12\",\"point\":\"?\"}]"});
        rows.add(new Object[] {"RESULTS partially unlinked row", SavedScreenCase.RESULTS, 200,
                fixture("history.html").replace("href=\"/matches/501\"", "data-missing=\"/matches/501\"").replace("/matches/502", "/matches/606")});
        rows.add(new Object[] {"RESULTS malformed source identity", SavedScreenCase.RESULTS, 200,
                fixture("history.html").replace("/matches/501", "/matches/999999999999999999999999999")});
        return rows;
    }

    private static String fixture(String file) throws Exception {
        try (var input = SavedScreenFailureTest.class.getResourceAsStream("/fixtures/" + file)) {
            return new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    @Test public void unavailableFirstLoadCanRecoverManuallyAndLaterFailuresPreserveSavedDataAndTimeForLongOfflineReuse() throws Exception {
        // arrange
        prepareSavedScreens();
        responses.put(journey.path, () -> new MockResponse().setResponseCode(status).setBody(body));

        // act
        openDirect(journey.activity, journey.identity, journey.id);

        // assert
        awaitText("Məlumat yüklənmədi. İnternet bağlantısını yoxlayın.");
        assertThat(visibleText()).doesNotContain(journey.initialText, "Bu seçim üzrə mənbədə nəticə tapılmadı.");
        assertThat(((android.widget.TextView) activity().findViewById(R.id.updated)).getText().toString()).isEmpty();

        // arrange
        responses.remove(journey.path);

        // act
        activity().findViewById(R.id.refresh).performClick();

        // assert
        awaitText(journey.initialText);
        String updated = ((android.widget.TextView) activity().findViewById(R.id.updated)).getText().toString();
        assertThat(updated).startsWith("Son uğurlu yenilənmə: ");

        // arrange
        FixtureApplication.time = Clock.offset(FixtureApplication.time, Duration.ofMinutes(2));
        responses.put(journey.path, () -> new MockResponse().setResponseCode(status).setBody(body));

        // act
        activity().findViewById(R.id.refresh).performClick();

        // assert
        assertThat(visibleText()).contains(journey.initialText, updated);
        awaitText("Yeniləmək mümkün olmadı. Saxlanmış məlumat göstərilir.");
        assertThat(visibleText()).contains(journey.initialText, updated);

        // arrange
        closeProductScreens();
        FixtureApplication.time = Clock.offset(FixtureApplication.time, Duration.ofDays(365));
        FixtureApplication.online = false;
        server.shutdown();

        // act
        openDirect(journey.activity, journey.identity, journey.id);

        // assert
        assertThat(visibleText()).contains(journey.initialText, updated);
        assertThat(activity().findViewById(R.id.refresh).isEnabled()).isFalse();
    }
}
