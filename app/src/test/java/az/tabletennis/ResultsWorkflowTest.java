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
        org.jsoup.nodes.Document duels = org.jsoup.Jsoup.parse(html("duels.html").getBody().readUtf8());
        org.jsoup.nodes.Element singlesDay = duels.selectFirst("a[href='/matches/502']").parent().parent();
        org.jsoup.nodes.Element rematchDay = singlesDay.clone();
        rematchDay.selectFirst(".divs-header").text("16 Nov 2026");
        rematchDay.selectFirst("a").attr("href", "/matches/503");
        rematchDay.selectFirst(".center").text("1-3");
        singlesDay.after(rematchDay);
        website.put("/matches/duellos", duels.outerHtml());
        website.put("/matches/results", "<div id='center'><div class='tournament-list'></div></div>");
        website.put("/matches/502", html("singles-detail.html").getBody().readUtf8());
        prepareProfile(11, "Şəms Quliyeva");
        openScreen();

        // assert
        awaitText("1. İlqar Həsənov — 800 xal");

        // act
        activity().findViewById(R.id.open_menu).performClick();
        clickText("Nəticələr");
        followNavigation();

        // assert
        awaitText("16 Nov. 2026 · İlqar Həsənov — Şəms Quliyeva · 3-1 · Duello · Tək · Duel");
        android.view.ViewGroup rows = activity().findViewById(R.id.result_rows);
        java.util.List<String> labels = new java.util.ArrayList<>();
        for (int i = 0; i < rows.getChildCount(); i++) {
            android.view.View row = rows.getChildAt(i);
            labels.add((row instanceof android.widget.TextView heading ? heading.getText() : row.getContentDescription()).toString());
        }
        assertThat(labels).containsExactly(
                "16 Nov. 2026",
                "16 Nov. 2026 · İlqar Həsənov — Şəms Quliyeva · 3-1 · Duello · Tək · Duel",
                "16 Nov 2026 · İlqar Həsənov — Şəms Quliyeva · 1-3 · Duello · Tək · Duel",
                "15 Nov. 2026",
                "15 Nov. 2026 · İlqar Həsənov / Əli Məmmədov — Şəms Quliyeva / Işıl Əliyeva · 3-2 · Duello · Qoşalar · Duel");
        String updated = ((android.widget.TextView) activity().findViewById(R.id.updated)).getText().toString();

        // act
        clickText("16 Nov. 2026 · İlqar Həsənov — Şəms Quliyeva · 3-1 · Duello · Tək · Duel");
        followNavigation();

        // assert
        awaitText("3");
        assertThat(visibleText()).contains("1");
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
        activity().findViewById(R.id.open_menu).performClick();
        clickText("Nəticələr");
        followNavigation();

        // assert
        assertThat(visibleText()).contains(updated,
                "16 Nov. 2026 · İlqar Həsənov — Şəms Quliyeva · 3-1 · Duello · Tək · Duel",
                "16 Nov 2026 · İlqar Həsənov — Şəms Quliyeva · 1-3 · Duello · Tək · Duel",
                "15 Nov. 2026 · İlqar Həsənov / Əli Məmmədov — Şəms Quliyeva / Işıl Əliyeva · 3-2 · Duello · Qoşalar · Duel");
    }
}
