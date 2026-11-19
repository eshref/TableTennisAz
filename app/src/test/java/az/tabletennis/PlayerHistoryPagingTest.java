package az.tabletennis;

import static org.assertj.core.api.Assertions.assertThat;

import android.view.View;
import android.widget.TextView;
import org.jsoup.Jsoup;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 26, qualifiers = "w411dp-h891dp", application = RankingsUiScenario.FixtureApplication.class)
public class PlayerHistoryPagingTest extends ProductUiScenario {
    @Test public void historyReadsAllContainersVerifiesSameNamedTeamParticipantsAndKeepsPagingAndDetailsOffline() throws Exception {
        // arrange
        prepareProfile(12, "İlqar Həsənov");
        var history = Jsoup.parse(html("team-history.html").getBody().readUtf8());
        history.selectFirst(".team-match-main").appendElement("div").addClass("match-box-stage").text("1/2");
        website.put("/matches/results/player/12", history.outerHtml());
        website.put("/matches/results/player/12?page=2", html("history.html").getBody().readUtf8()
                .replace("15 Nov. 2026", "13 Nov. 2026").replace("14 Nov. 2026", "12 Nov. 2026").replace("/matches/502", "/matches/505"));
        publishStage("/matches/results/player/12?page=2", 501, "Play-in");
        var detail = Jsoup.parse(html("singles-detail.html").getBody().readUtf8().replace("16 Nov. 2026", "15 Nov. 2026"));
        detail.select(".match-tournament").remove();
        website.put("/matches/601", detail.outerHtml());
        website.put("/matches/603", detail.outerHtml().replace("/players/12", "/players/99"));
        String teamMatch = "15 Nov. 2026 · İlqar Həsənov — Şəms Quliyeva · 3-1 · Komanda Liqası · Tək · Turnir · 1/2";
        String groupMatch = "13 Nov. 2026 · İlqar Həsənov / Əli Məmmədov — Şəms Quliyeva / Işıl Əliyeva · 3-2 · Kupa · Qoşalar · Turnir · Qrup oyunu";

        // act
        openDirect(ProfileActivity.class, "playerId", 12);

        // assert
        awaitText(teamMatch);
        View teamCard = findText(activity().getWindow().getDecorView(), teamMatch);
        TextView badge = teamCard.findViewById(R.id.match_stage_badge);
        assertThat(badge.getText().toString()).isEqualTo("1/2");
        assertThat(visibleText().stream().filter(text -> text.contains("· Komanda Liqası · Tək · Turnir"))).containsExactly(teamMatch);
        assertThat(visibleText()).contains("14 Nov. 2026 · İlqar Həsənov — Şəms Quliyeva · 3-1 · Liqa · Tək · Turnir");
        assertThat(requestedPaths()).contains("/matches/601", "/matches/603").doesNotContain("/matches/604");
        String updated = ((android.widget.TextView) activity().findViewById(R.id.updated)).getText().toString();

        // arrange
        FixtureApplication.time = java.time.Clock.offset(FixtureApplication.time, java.time.Duration.ofMinutes(2));

        // act
        clickText("Daha çox yüklə");

        // assert
        awaitText(groupMatch);
        assertThat(visibleText()).contains(updated, teamMatch);
        assertThat(requestedPaths()).containsExactly("/matches/results/player/12?page=2");

        // act
        recreateProductScreen();
        activity().findViewById(R.id.refresh).performClick();

        // assert
        awaitText("Məlumat yeniləndi.");
        assertThat(visibleText()).contains(groupMatch, teamMatch);
        assertThat(requestedPaths()).contains("/matches/results/player/12", "/matches/results/player/12?page=2");
        updated = ((android.widget.TextView) activity().findViewById(R.id.updated)).getText().toString();

        // arrange
        closeProductScreens();
        FixtureApplication.online = false;
        server.shutdown();

        // act
        openDirect(ProfileActivity.class, "playerId", 12);

        // assert
        assertThat(visibleText()).contains(updated, groupMatch, teamMatch, "1/2", "Qrup oyunu");

        // act
        clickText(teamMatch);
        followNavigation();

        // assert
        assertThat(visibleText()).contains("Matç haqqında", "3", "1", "15 Nov. 2026");
    }
}
