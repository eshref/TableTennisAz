package az.tabletennis;

import static org.assertj.core.api.Assertions.assertThat;

import android.app.Activity;
import android.content.Context;
import android.widget.TextView;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.ParameterizedRobolectricTestRunner;
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(ParameterizedRobolectricTestRunner.class)
@Config(sdk = 26, qualifiers = "w411dp-h891dp", application = RankingsUiScenario.FixtureApplication.class)
public class HistoryStageRefreshTest extends DoublesUiScenario {
    private final Class<? extends Activity> screenType;
    private final String intentKey;
    private final long id;
    private final String savedKey;
    private final String historyPath;

    public HistoryStageRefreshTest(Class<? extends Activity> screenType, String intentKey, long id,
                                   String savedKey, String historyPath) {
        this.screenType = screenType;
        this.intentKey = intentKey;
        this.id = id;
        this.savedKey = savedKey;
        this.historyPath = historyPath;
    }

    @Parameters(name = "{0}") public static Object[][] histories() {
        return new Object[][] {
                {ProfileActivity.class, "playerId", 12L, "profile-12", "/matches/results/player/12"},
                {PairResultsActivity.class, "pairId", 110L, "duo-results-110", "/matches/results/duo/110"}
        };
    }

    @Test public void aHistorySavedBeforeStagesOpensAndGainsThePublishedBadgeOnRefresh() throws Exception {
        // arrange
        prepareProfile(12, "İlqar Həsənov");
        prepareDoubles();
        website.put(historyPath, pairHistory(pairResult(501, "15 Nov. 2026", "Kupa")));
        publishStage(historyPath, 501, "Play-in");
        try (var input = getClass().getResourceAsStream("/fixtures/legacy-" + savedKey);
             var output = RuntimeEnvironment.getApplication().openFileOutput("saved-" + savedKey, Context.MODE_PRIVATE)) {
            input.transferTo(output);
        }
        String match = "15 Nov. 2026 · İlqar Həsənov / Əli Məmmədov — Şəms Quliyeva / Işıl Əliyeva · 3-2 · Kupa · Qoşalar · Turnir";

        // act
        openDirect(screenType, intentKey, id);

        // assert
        assertThat(visibleText()).contains(match).doesNotContain("Qrup oyunu");
        assertThat(requestedPaths()).doesNotContain(historyPath);

        // act
        activity().findViewById(R.id.refresh).performClick();

        // assert
        String announcement = match + " · Qrup oyunu";
        awaitText(announcement);
        TextView badge = findText(activity().getWindow().getDecorView(), announcement).findViewById(R.id.match_stage_badge);
        assertThat(badge.getText().toString()).isEqualTo("Qrup oyunu");
        assertThat(requestedPaths()).contains(historyPath);
    }
}
