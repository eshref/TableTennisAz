package az.tabletennis;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 26, qualifiers = "w411dp-h891dp", application = RankingsUiScenario.FixtureApplication.class)
public class ResultsWorkflowTest extends ProductUiScenario {
    @Test public void duelFeedSortsSourceDatesAndOpensSinglesDetailsWithoutInventingTimesThenReopensOffline() throws Exception {
        // arrange
        website.put("/matches/duellos", html("duels.html").getBody().readUtf8());
        website.put("/matches/results", "<div id='center'><div class='tournament-list'></div></div>");
        website.put("/matches/502", html("singles-detail.html").getBody().readUtf8());
        prepareProfile(11, "Şəms Quliyeva");
        openScreen();

        // assert
        awaitText("1. Şəms Quliyeva — 950 xal");

        // act
        clickText("Nəticələr");
        followNavigation();

        // assert
        awaitText("16 Nov. 2026 · İlqar Həsənov — Şəms Quliyeva · 3-1 · Duello");
        assertThat(visibleText()).containsSubsequence(
                "16 Nov. 2026 · İlqar Həsənov — Şəms Quliyeva · 3-1 · Duello",
                "15 Nov. 2026 · İlqar Həsənov / Əli Məmmədov — Şəms Quliyeva / Işıl Əliyeva · 3-2 · Duello");
        String updated = ((android.widget.TextView) activity().findViewById(R.id.updated)).getText().toString();

        // act
        clickText("16 Nov. 2026 · İlqar Həsənov — Şəms Quliyeva · 3-1 · Duello");
        followNavigation();

        // assert
        awaitText("3-1");
        assertThat(visibleText()).contains("16 Nov. 2026", "İlqar Həsənov", "Şəms Quliyeva").doesNotContain("18:00", "Əli Məmmədov", "Işıl Əliyeva");

        // act
        clickText("Şəms Quliyeva");
        followNavigation();

        // assert
        awaitText("950 xal");

        // arrange
        server.shutdown();

        // act
        restart();
        clickText("Nəticələr");
        followNavigation();

        // assert
        assertThat(visibleText()).contains(updated,
                "16 Nov. 2026 · İlqar Həsənov — Şəms Quliyeva · 3-1 · Duello",
                "15 Nov. 2026 · İlqar Həsənov / Əli Məmmədov — Şəms Quliyeva / Işıl Əliyeva · 3-2 · Duello");
    }
}
