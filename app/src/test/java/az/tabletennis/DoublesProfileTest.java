package az.tabletennis;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 26, qualifiers = "w411dp-h891dp", application = RankingsUiScenario.FixtureApplication.class)
public class DoublesProfileTest extends DoublesUiScenario {
    @Test public void selectedPartnershipShowsPublishedStandingAndOwnMedalsAfterOfflineRestart() throws Exception {
        // arrange
        prepareDoubles();
        website.compute("/api/getDuoInfo?id=110", (path, body) -> body.replace("}",
                ",\"firstPlaceCount\":9,\"secondPlaceCount\":8,\"thirdPlaceCount\":7}"));
        website.compute("/api/getDuoInfo?id=220", (path, body) -> body.replace("}",
                ",\"firstPlaceCount\":3,\"secondPlaceCount\":0,\"thirdPlaceCount\":1}"));
        website.put("/matches/results/duo/220", pairHistory("<div class='no-info'>Matç yoxdur</div>"));
        openScreen();

        // assert
        awaitText("1. İlqar Həsənov — 800 xal");

        // act
        clickText("Qoşalar");

        // assert
        awaitText("2. İlqar Həsənov / Şəms Quliyeva — 800 xal");

        // act
        clickText("2. İlqar Həsənov / Şəms Quliyeva — 800 xal");
        followNavigation();

        // assert
        awaitText("Matç tarixçəsi yoxdur.");
        assertThat(visibleText()).contains("Qoşanın profili", "İlqar Həsənov", "Şəms Quliyeva", "800 xal",
                "Reytinqdə yer: #2", "Qızıl medal: 3", "Gümüş medal: 0", "Bürünc medal: 1");
        String updated = ((android.widget.TextView) activity().findViewById(R.id.updated)).getText().toString();

        // arrange
        FixtureApplication.online = false;
        FixtureApplication.time = java.time.Clock.offset(FixtureApplication.time, java.time.Duration.ofDays(365));
        server.shutdown();

        // act
        restart();
        clickText("Qoşalar");

        // assert
        awaitText("2. İlqar Həsənov / Şəms Quliyeva — 800 xal");

        // act
        clickText("2. İlqar Həsənov / Şəms Quliyeva — 800 xal");
        followNavigation();

        // assert
        assertThat(visibleText()).contains(updated, "800 xal", "Reytinqdə yer: #2",
                "Qızıl medal: 3", "Gümüş medal: 0", "Bürünc medal: 1", "Matç tarixçəsi yoxdur.");
        assertThat(activity().findViewById(R.id.refresh).isEnabled()).isFalse();
    }

    @Test public void cachedIdentityRefreshesMissingAndZeroMedalsWhileStandingFollowsTheLatestSavedRanking() throws Exception {
        // arrange
        prepareDoubles();
        website.put("/matches/results/duo/110", pairHistory("<div class='no-info'>Matç yoxdur</div>"));
        openScreen();

        // assert
        awaitText("1. İlqar Həsənov — 800 xal");

        // act
        clickText("Qoşalar");

        // assert
        awaitText("1. İlqar Həsənov / Əli Məmmədov — 800 xal");

        // act
        clickText("1. İlqar Həsənov / Əli Məmmədov — 800 xal");
        followNavigation();

        // assert
        awaitText("Matç tarixçəsi yoxdur.");
        assertThat(visibleText()).contains("Qızıl medal: məlumat mövcud deyil",
                "Gümüş medal: məlumat mövcud deyil", "Bürünc medal: məlumat mövcud deyil");
        requestedPaths();

        // arrange
        website.compute("/api/getDuoInfo?id=110", (path, body) -> body.replace("}",
                ",\"firstPlaceCount\":0,\"secondPlaceCount\":0,\"thirdPlaceCount\":0}"));
        FixtureApplication.time = java.time.Clock.offset(FixtureApplication.time, java.time.Duration.ofMinutes(1));

        // act
        activity().findViewById(R.id.refresh).performClick();

        // assert
        awaitText("Qızıl medal: 0");
        assertThat(visibleText()).contains("Gümüş medal: 0", "Bürünc medal: 0");
        assertThat(requestedPaths()).contains("/api/getDuoInfo?id=110").doesNotContain("/players/duos");

        // arrange
        website.put("/api/getDuoInfo?id=110", pairInfo(110, 13, "Əli Məmmədov", 12, "İlqar Həsənov")
                .replace("}", ",\"firstPlaceCount\":4,\"secondPlaceCount\":null}"));
        FixtureApplication.time = java.time.Clock.offset(FixtureApplication.time, java.time.Duration.ofMinutes(1));

        // act
        activity().findViewById(R.id.refresh).performClick();

        // assert
        awaitText("Qızıl medal: 4");
        assertThat(visibleText()).contains("Gümüş medal: məlumat mövcud deyil", "Bürünc medal: məlumat mövcud deyil");
        String profileUpdated = ((android.widget.TextView) activity().findViewById(R.id.updated)).getText().toString();

        // arrange
        website.put("/players/duos", "<div id='center'><div class='ranking-page-list'>"
                + pairRow(220, 1, 900) + pairRow(330, 2, 850) + pairRow(110, 4, 801) + "</div></div>");
        FixtureApplication.time = java.time.Clock.offset(FixtureApplication.time, java.time.Duration.ofMinutes(1));

        // act
        back();
        activity().findViewById(R.id.refresh).performClick();

        // assert
        awaitText("4. İlqar Həsənov / Əli Məmmədov — 801 xal  ↓ 3");
        requestedPaths();

        // act
        clickText("4. İlqar Həsənov / Əli Məmmədov — 801 xal  ↓ 3");
        followNavigation();

        // assert
        assertThat(visibleText()).contains(profileUpdated, "801 xal", "Reytinqdə yer: #4", "Qızıl medal: 4")
                .doesNotContain("800 xal", "Reytinqdə yer: #1");
        assertThat(requestedPaths()).isEmpty();
    }

    @Test public void preProfileSavedIdentitiesRankingsAndResultsRemainReadableAndGainMedalsWithoutLosingPages() throws Exception {
        // arrange
        for (String key : new String[] {"duo-110", "doubles-ranking", "duo-results-110"}) {
            try (var input = getClass().getResourceAsStream("/fixtures/legacy-" + key);
                 var output = org.robolectric.RuntimeEnvironment.getApplication()
                         .openFileOutput("saved-" + key, android.content.Context.MODE_PRIVATE)) {
                input.transferTo(output);
            }
        }
        FixtureApplication.online = false;
        openScreen();

        // act
        clickText("Qoşalar");

        // assert
        awaitText("2. İlqar Həsənov / Əli Məmmədov — 800 xal");

        // act
        clickText("2. İlqar Həsənov / Əli Məmmədov — 800 xal");
        followNavigation();

        // assert
        assertThat(visibleText()).contains("800 xal", "Reytinqdə yer: #2", "Qızıl medal: məlumat mövcud deyil",
                "15 Nov. 2026 · İlqar Həsənov / Əli Məmmədov — Şəms Quliyeva / Işıl Əliyeva · 3-2 · Kupa",
                "Daha çox yüklə");
        java.util.ArrayList<android.view.View> pagination = new java.util.ArrayList<>();
        activity().getWindow().getDecorView().findViewsWithText(pagination, "Daha çox yüklə", android.view.View.FIND_VIEWS_WITH_TEXT);
        assertThat(pagination).singleElement().satisfies(view -> assertThat(view.isEnabled()).isFalse());

        // arrange
        FixtureApplication.online = true;
        website.put("/api/getDuoInfo?id=110", pairInfo(110, 12, "İlqar Həsənov", 13, "Əli Məmmədov")
                .replace("}", ",\"firstPlaceCount\":5,\"secondPlaceCount\":2,\"thirdPlaceCount\":1}"));
        website.put("/matches/results/duo/110", pairHistory(pairResult(501, "15 Nov. 2026", "Kupa"))
                + "<ul class='pagination'><li><a href='/matches/results/duo/110?page=2'>2</a></li></ul>");
        website.put("/matches/results/duo/110?page=2", pairHistory(pairResult(502, "14 Nov. 2026", "Duello")));

        // act
        closeProductScreens();
        openDirect(PairResultsActivity.class, "pairId", 110);
        activity().findViewById(R.id.refresh).performClick();

        // assert
        awaitText("Qızıl medal: 5");
        assertThat(visibleText()).contains("15 Nov. 2026 · İlqar Həsənov / Əli Məmmədov — Şəms Quliyeva / Işıl Əliyeva · 3-2 · Kupa",
                "Daha çox yüklə");
        assertThat(requestedPaths()).containsExactly("/api/getDuoInfo?id=110", "/matches/results/duo/110");

        // act
        clickText("Daha çox yüklə");

        // assert
        awaitText("14 Nov. 2026 · İlqar Həsənov / Əli Məmmədov — Şəms Quliyeva / Işıl Əliyeva · 3-2 · Duello");
        assertThat(visibleText()).contains("Qızıl medal: 5", "Gümüş medal: 2", "Bürünc medal: 1")
                .doesNotContain("Daha çox yüklə");
        assertThat(requestedPaths()).containsExactly("/matches/results/duo/110?page=2");
    }
}
