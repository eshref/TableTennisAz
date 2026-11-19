package az.tabletennis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.robolectric.Shadows.shadowOf;

import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 26, qualifiers = "w411dp-h891dp", application = RankingsUiScenario.FixtureApplication.class)
public class DoublesWorkflowTest extends DoublesUiScenario {
    @Test public void interruptedNamesKeepSearchIncompleteAllowOtherScreensAndResumeWithoutReplacingASavedRanking() throws Exception {
        // arrange
        prepareDoubles();
        prepareProfile(12, "İlqar Həsənov");
        java.util.concurrent.CountDownLatch finishLookup = new java.util.concurrent.CountDownLatch(1);
        responses.put("/api/getDuoInfo?id=220", () -> {
            try { finishLookup.await(60, java.util.concurrent.TimeUnit.SECONDS); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            return new okhttp3.mockwebserver.MockResponse().setResponseCode(503);
        });
        try {
            // act
            openScreen();

            // assert
            awaitText("1. İlqar Həsənov — 800 xal");

            // act
            clickText("Qoşalar");

            // assert
            awaitText("Qoşaların adları yüklənir: 1 / 3");
            assertThat(activity().findViewById(R.id.search).isEnabled()).isFalse();
            assertThat(visibleText()).doesNotContain("1. İlqar Həsənov / Əli Məmmədov — 800 xal", "Axtarışa uyğun qoşa tapılmadı.");
            assertThat(((android.widget.TextView) activity().findViewById(R.id.updated)).getText().toString()).isEmpty();

            // act
            clickText("Oyunçular");

            // assert
            awaitText("1. İlqar Həsənov — 800 xal");

            // act
            clickText("1. İlqar Həsənov — 800 xal");
            followNavigation();

            // assert
            awaitText("800 xal");

            // act
            back();
            clickText("Qoşalar");
            finishLookup.countDown();

            // assert
            awaitText("Məlumat yüklənmədi. İnternet bağlantısını yoxlayın.");
            assertThat(visibleText()).doesNotContain("1. İlqar Həsənov / Əli Məmmədov — 800 xal");
            assertThat(requestedPaths()).contains("/api/getDuoInfo?id=110", "/api/getDuoInfo?id=220");

            // arrange
            responses.remove("/api/getDuoInfo?id=220");

            // act
            activity().findViewById(R.id.refresh).performClick();

            // assert
            awaitText("1. İlqar Həsənov / Əli Məmmədov — 800 xal");
            assertThat(visibleText()).noneMatch(text -> text.contains("pillə yüksəlib") || text.contains("pillə geriləyib"));
            assertThat(requestedPaths()).contains("/api/getDuoInfo?id=220", "/api/getDuoInfo?id=330")
                    .doesNotContain("/api/getDuoInfo?id=110");
            String updated = ((android.widget.TextView) activity().findViewById(R.id.updated)).getText().toString();

            // arrange
            website.put("/players/duos", "<div id='center'><div class='ranking-page-list'>"
                    + pairRow(220, 1, 800) + pairRow(330, 2, 600) + pairRow(110, 4, 500)
                    + pairRow(440, 5, 400) + "</div></div>");
            website.put("/api/getDuoInfo?id=440", pairInfo(999, 11, "Şəms Quliyeva", 14, "Işıl Əliyeva"));
            FixtureApplication.time = java.time.Clock.offset(FixtureApplication.time, java.time.Duration.ofMinutes(2));

            // act
            activity().findViewById(R.id.refresh).performClick();

            // assert
            awaitText("Yeniləmək mümkün olmadı. Saxlanmış məlumat göstərilir.");
            assertThat(visibleText()).contains(updated, "1. İlqar Həsənov / Əli Məmmədov — 800 xal")
                    .doesNotContain("5. Şəms Quliyeva / Işıl Əliyeva — 400 xal");
            assertThat(visibleText()).noneMatch(text -> text.contains("pillə yüksəlib") || text.contains("pillə geriləyib"));

            // arrange
            website.put("/api/getDuoInfo?id=440", pairInfo(440, 11, "Şəms Quliyeva", 14, "Işıl Əliyeva"));

            // act
            activity().findViewById(R.id.refresh).performClick();

            // assert
        awaitText("4. İlqar Həsənov / Əli Məmmədov — 500 xal, 3 pillə geriləyib");
            assertThat(visibleText()).contains("4. İlqar Həsənov / Əli Məmmədov — 500 xal, 3 pillə geriləyib",
                    "1. İlqar Həsənov / Şəms Quliyeva — 800 xal, 1 pillə yüksəlib", "2. Işıl Əliyeva / Əli Məmmədov — 600 xal, 1 pillə yüksəlib");
            assertThat(((android.widget.TextView) activity().findViewById(R.id.updated)).getText().toString()).isNotEqualTo(updated);

        // act
        ((android.widget.ListView) activity().findViewById(android.R.id.list)).setSelection(4);

        // assert
        awaitText("5. Şəms Quliyeva / Işıl Əliyeva — 400 xal");
        } finally {
            finishLookup.countDown();
        }
    }

    @Test public void websitePositionsFullNamesAndEitherPartnerSearchKeepPartnershipsSeparate() throws Exception {
        // arrange
        prepareDoubles();

        // act
        openScreen();

        // assert
        awaitText("1. İlqar Həsənov — 800 xal");
        assertThat(visibleText()).doesNotContain("1. İlqar Həsənov / Əli Məmmədov — 800 xal");

        // act
        clickText("Qoşalar");

        // assert
        awaitText("1. İlqar Həsənov / Əli Məmmədov — 800 xal");
        assertThat(visibleText()).containsSubsequence("1. İlqar Həsənov / Əli Məmmədov — 800 xal",
                "2. İlqar Həsənov / Şəms Quliyeva — 800 xal", "3. Işıl Əliyeva / Əli Məmmədov — 600 xal");
        assertThat(visibleText()).noneMatch(text -> text.contains("Bonus"));

        // act
        ((EditText) activity().findViewById(R.id.search)).setText("əLi");
        shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(100));

        // assert
        assertThat(visibleText()).contains("1. İlqar Həsənov / Əli Məmmədov — 800 xal",
                "3. Işıl Əliyeva / Əli Məmmədov — 600 xal").doesNotContain("2. İlqar Həsənov / Şəms Quliyeva — 800 xal");

        // act
        ((EditText) activity().findViewById(R.id.search)).setText("ilqar");
        shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(100));

        // assert
        assertThat(visibleText()).contains("1. İlqar Həsənov / Əli Məmmədov — 800 xal",
                "2. İlqar Həsənov / Şəms Quliyeva — 800 xal").doesNotContain("3. Işıl Əliyeva / Əli Məmmədov — 600 xal");
    }

    @Test public void pairHistoryPagesEventsAndDuelsLinksBothPlayersAndRestoresSearchAfterOfflineRelaunch() throws Exception {
        // arrange
        prepareDoubles();
        website.compute("/api/getDuoInfo?id=110", (path, body) -> body.replace("}",
                ",\"firstPlaceCount\":2,\"secondPlaceCount\":1,\"thirdPlaceCount\":0}"));
        prepareProfile(12, "İlqar Həsənov");
        prepareProfile(13, "Əli Məmmədov");
        website.put("/matches/results/duo/110", pairHistory(pairResult(501, "15 Nov. 2026", "Kupa")
                + pairResult(505, "16 Nov. 2026", "Duello"))
                + "<ul class='pagination'><li><a href='/matches/results/duo/110?page=2'>2</a></li></ul>");
        website.put("/matches/results/duo/110?page=2", pairHistory(pairResult(501, "15 Nov. 2026", "")
                + pairResult(506, "14 Nov. 2026", "Liqa") + pairResult(507, "13 Nov. 2026", "Arxiv"))
                + "<ul class='pagination'><li class='disabled'><a href='/matches/results/duo/110?page=3'>Next</a></li></ul>");
        publishStage("/matches/results/duo/110", 501, "Final");
        publishStage("/matches/results/duo/110", 505, "Play-in");
        publishStage("/matches/results/duo/110?page=2", 506, "1/4");
        publishStage("/matches/results/duo/110?page=2", 507, "Unknown stage");
        website.put("/matches/501", html("doubles-detail.html").getBody().readUtf8());
        String sidesAndScore = " · İlqar Həsənov / Əli Məmmədov — Şəms Quliyeva / Işıl Əliyeva · 3-2 · ";
        String duel = "16 Nov. 2026" + sidesAndScore + "Duello · Qoşalar · Duel";
        String cup = "15 Nov. 2026" + sidesAndScore + "Kupa · Qoşalar · Turnir";
        String league = "14 Nov. 2026" + sidesAndScore + "Liqa · Qoşalar · Turnir · 1/4";
        String archived = "13 Nov. 2026" + sidesAndScore + "Arxiv · Qoşalar · Turnir";
        openScreen();

        // assert
        awaitText("1. İlqar Həsənov — 800 xal");

        // act
        clickText("Qoşalar");

        // assert
        awaitText("1. İlqar Həsənov / Əli Məmmədov — 800 xal");

        // act
        ((EditText) activity().findViewById(R.id.search)).setText("məmmədov");
        shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(100));
        clickText("1. İlqar Həsənov / Əli Məmmədov — 800 xal");
        followNavigation();

        // assert
        awaitText(cup + " · Final");
        assertThat(visibleText()).containsSubsequence(duel, cup + " · Final");
        View duelCard = findText(activity().getWindow().getDecorView(), duel);
        assertThat(duelCard.<View>findViewById(R.id.match_stage_badge)).isNull();
        String updated = ((android.widget.TextView) activity().findViewById(R.id.updated)).getText().toString();

        // arrange
        FixtureApplication.time = java.time.Clock.offset(FixtureApplication.time, java.time.Duration.ofMinutes(2));

        // act
        clickText("Daha çox yüklə");

        // assert
        awaitText(league);
        View leagueCard = findText(activity().getWindow().getDecorView(), league);
        TextView leagueBadge = leagueCard.findViewById(R.id.match_stage_badge);
        assertThat(leagueBadge.getText().toString()).isEqualTo("1/4");
        View archivedCard = findText(activity().getWindow().getDecorView(), archived);
        assertThat(archivedCard.<View>findViewById(R.id.match_stage_badge)).isNull();
        assertThat(visibleText()).contains(updated, "Qızıl medal: 2", "Gümüş medal: 1", "Bürünc medal: 0")
                .doesNotContain("Daha çox yüklə");
        assertThat(visibleText().stream().filter(text -> text.startsWith(cup))).containsExactly(cup + " · Final");

        // act
        clickText(cup + " · Final");
        followNavigation();

        // assert
        awaitText("Matç haqqında");
        awaitText("3");
        assertThat(visibleText()).contains("2");

        // act
        back();

        // assert
        assertThat(visibleText()).contains("Qoşanın profili", "Qızıl medal: 2");

        // act
        clickText("İlqar Həsənov");
        followNavigation();

        // assert
        awaitText("800 xal");
        assertThat(visibleText()).contains("İlqar Həsənov", "Matç tarixçəsi");

        // act
        back();
        clickText("Əli Məmmədov");
        followNavigation();

        // assert
        awaitText("800 xal");
        assertThat(visibleText()).contains("Əli Məmmədov", "Matç tarixçəsi");

        // act
        back();
        back();
        screen.recreate();

        // assert
        assertThat(((EditText) activity().findViewById(R.id.search)).getText().toString()).isEqualTo("məmmədov");
        assertThat(((android.widget.RadioButton) activity().findViewById(R.id.doubles_mode)).isChecked()).isTrue();
        assertThat(visibleText()).doesNotContain("2. İlqar Həsənov / Şəms Quliyeva — 800 xal");
        assertThat(requestedPaths()).contains("/matches/results/duo/110", "/matches/results/duo/110?page=2",
                "/players/12", "/players/13").doesNotContain("/matches/results/player/110", "/matches/results/duo/110?page=3");

        // arrange
        FixtureApplication.online = false;
        FixtureApplication.time = java.time.Clock.offset(FixtureApplication.time, java.time.Duration.ofDays(365));
        server.shutdown();

        // act
        restart();
        clickText("Qoşalar");

        // assert
        awaitText("1. İlqar Həsənov / Əli Məmmədov — 800 xal");

        // act
        clickText("1. İlqar Həsənov / Əli Məmmədov — 800 xal");
        followNavigation();

        // assert
        assertThat(visibleText()).contains(updated, "800 xal", "Reytinqdə yer: #1",
                "Qızıl medal: 2", "Gümüş medal: 1", "Bürünc medal: 0", league, cup + " · Final", "1/4", "Final");
        assertThat(activity().findViewById(R.id.refresh).isEnabled()).isFalse();
    }
}
