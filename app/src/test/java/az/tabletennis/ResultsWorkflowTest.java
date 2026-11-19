package az.tabletennis;

import static org.assertj.core.api.Assertions.assertThat;

import android.content.Context;
import org.jsoup.Jsoup;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 26, qualifiers = "w411dp-h891dp", application = RankingsUiScenario.FixtureApplication.class)
public class ResultsWorkflowTest extends ProductUiScenario {
    @Test public void aLaterPageCanAddTheTeamContestStageToAnAlreadyVisibleChildResult() throws Exception {
        // arrange
        prepareResults();
        website.compute("/matches/results", (path, body) -> body.replace("Payız Kuboku", "Komanda Liqası"));
        var contest = Jsoup.parse(html("team-history.html").getBody().readUtf8());
        contest.selectFirst(".pagination a").attr("href", "/matches/results/event/901?page=2");
        website.put("/matches/results/event/901", contest.outerHtml());
        contest.selectFirst(".team-match-main").appendElement("div").addClass("match-box-stage").text("Final");
        contest.select(".pagination").remove();
        website.put("/matches/results/event/901?page=2", contest.outerHtml());
        org.robolectric.RuntimeEnvironment.getApplication().getSharedPreferences("results-selection", Context.MODE_PRIVATE)
                .edit().putString("type", "EVENTS").putLong("event", 901).commit();
        String child = "15 Nov. 2026 · Əli Məmmədov — Işıl Əliyeva · 3-0 · Komanda Liqası · Tək · Turnir";

        // act
        openDirect(ResultsActivity.class, "", 0);

        // assert
        awaitText(child);
        assertThat(visibleText()).doesNotContain("Final");

        // act
        clickText("Daha çox yüklə");

        // assert
        awaitText(child + " · Final");
        assertThat(visibleText().stream().filter(text -> text.startsWith(child))).containsExactly(child + " · Final");
        assertThat(visibleText()).contains("14 Nov. 2026 · İlqar Həsənov — Şəms Quliyeva · 3-1 · Komanda Liqası · Tək · Turnir");
    }

    @Test public void resultsSavedBeforeStageBadgesOpenAndGainThePublishedStageOnRefresh() throws Exception {
        // arrange
        prepareResults();
        publishStage("/matches/results/event/901", 601, "Play-in");
        Context context = org.robolectric.RuntimeEnvironment.getApplication();
        try (var input = getClass().getResourceAsStream("/fixtures/legacy-results-events-901");
             var output = context.openFileOutput("saved-results-events-901", Context.MODE_PRIVATE)) {
            input.transferTo(output);
        }
        context.getSharedPreferences("results-selection", Context.MODE_PRIVATE).edit()
                .putString("type", "EVENTS").putLong("event", 901).commit();

        // act
        openDirect(ResultsActivity.class, "", 0);

        // assert
        assertThat(visibleText()).contains("14 Nov. 2026 · Kubok A — Kubok B · 3-0 · Payız Kuboku · Tək · Turnir")
                .doesNotContain("Qrup oyunu");
        assertThat(requestedPaths()).isEmpty();

        // act
        activity().findViewById(R.id.refresh).performClick();

        // assert
        awaitText("14 Nov. 2026 · Kubok A — Kubok B · 3-0 · Payız Kuboku · Tək · Turnir · Qrup oyunu");
        assertThat(visibleText()).contains("Qrup oyunu");
        assertThat(requestedPaths()).contains("/matches/results/event/901");
    }

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
