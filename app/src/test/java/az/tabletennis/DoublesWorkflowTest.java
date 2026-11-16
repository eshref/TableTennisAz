package az.tabletennis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.robolectric.Shadows.shadowOf;

import android.widget.EditText;
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
            assertThat(visibleText()).noneMatch(text -> text.contains("↑") || text.contains("↓"));
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
            assertThat(visibleText()).noneMatch(text -> text.contains("↑") || text.contains("↓"));

            // arrange
            website.put("/api/getDuoInfo?id=440", pairInfo(440, 11, "Şəms Quliyeva", 14, "Işıl Əliyeva"));

            // act
            activity().findViewById(R.id.refresh).performClick();

            // assert
            awaitText("5. Şəms Quliyeva / Işıl Əliyeva — 400 xal");
            assertThat(visibleText()).contains("4. İlqar Həsənov / Əli Məmmədov — 500 xal  ↓ 3",
                    "1. İlqar Həsənov / Şəms Quliyeva — 800 xal  ↑ 1", "2. Işıl Əliyeva / Əli Məmmədov — 600 xal  ↑ 1");
            assertThat(((android.widget.TextView) activity().findViewById(R.id.updated)).getText().toString()).isNotEqualTo(updated);
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
        website.put("/matches/results/duo/110?page=2", pairHistory(pairResult(501, "15 Nov. 2026", "Kupa")
                + pairResult(506, "14 Nov. 2026", "Liqa"))
                + "<ul class='pagination'><li class='disabled'><a href='/matches/results/duo/110?page=3'>Next</a></li></ul>");
        website.put("/matches/501", html("doubles-detail.html").getBody().readUtf8());
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
        awaitText("16 Nov. 2026 · İlqar Həsənov / Əli Məmmədov — Şəms Quliyeva / Işıl Əliyeva · 3-2 · Duello");
        assertThat(visibleText()).containsSubsequence(
                "16 Nov. 2026 · İlqar Həsənov / Əli Məmmədov — Şəms Quliyeva / Işıl Əliyeva · 3-2 · Duello",
                "15 Nov. 2026 · İlqar Həsənov / Əli Məmmədov — Şəms Quliyeva / Işıl Əliyeva · 3-2 · Kupa");
        String updated = ((android.widget.TextView) activity().findViewById(R.id.updated)).getText().toString();

        // arrange
        FixtureApplication.time = java.time.Clock.offset(FixtureApplication.time, java.time.Duration.ofMinutes(2));

        // act
        clickText("Daha çox yüklə");

        // assert
        awaitText("14 Nov. 2026 · İlqar Həsənov / Əli Məmmədov — Şəms Quliyeva / Işıl Əliyeva · 3-2 · Liqa");
        assertThat(visibleText()).contains(updated, "Qızıl medal: 2", "Gümüş medal: 1", "Bürünc medal: 0")
                .doesNotContain("Daha çox yüklə");
        assertThat(visibleText().stream().filter(text -> text.endsWith("· Kupa"))).hasSize(1);

        // act
        clickText("15 Nov. 2026 · İlqar Həsənov / Əli Məmmədov — Şəms Quliyeva / Işıl Əliyeva · 3-2 · Kupa");
        followNavigation();

        // assert
        awaitText("Matç haqqında");
        awaitText("3-2");

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
                "Qızıl medal: 2", "Gümüş medal: 1", "Bürünc medal: 0",
                "14 Nov. 2026 · İlqar Həsənov / Əli Məmmədov — Şəms Quliyeva / Işıl Əliyeva · 3-2 · Liqa");
        assertThat(activity().findViewById(R.id.refresh).isEnabled()).isFalse();
    }
}
