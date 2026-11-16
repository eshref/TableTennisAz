package az.tabletennis;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 26, qualifiers = "w411dp-h891dp", application = RankingsUiScenario.FixtureApplication.class)
public class DoublesChartTest extends DoublesUiScenario {
    @Test public void unvisitedPartnershipsKeepSeparateDatedHistoriesAcrossRefreshFailureAndOfflineRestart() throws Exception {
        // arrange
        prepareDoubles();
        ranking(pairRow(110, 9, 800) + pairRow(220, 12, 700));
        website.put("/matches/results/duo/110", pairHistory("<div class='no-info'>Matç yoxdur</div>"));
        website.put("/matches/results/duo/220", pairHistory("<div class='no-info'>Matç yoxdur</div>"));
        openScreen();

        // act
        clickText("Qoşalar");

        // assert
        awaitText("9. İlqar Həsənov / Əli Məmmədov — 800 xal");

        // arrange
        FixtureApplication.time = java.time.Clock.offset(FixtureApplication.time, java.time.Duration.ofMinutes(2));
        ranking(pairRow(220, 1, 950) + pairRow(110, 3, 900));

        // act
        activity().findViewById(R.id.refresh).performClick();

        // assert
        awaitText("3. İlqar Həsənov / Əli Məmmədov — 900 xal  ↑ 6");

        // arrange
        FixtureApplication.time = java.time.Clock.offset(FixtureApplication.time, java.time.Duration.ofDays(2));
        ranking(pairRow(110, 1, 1000) + pairRow(220, 7, 750));

        // act
        activity().findViewById(R.id.refresh).performClick();

        // assert
        awaitText("1. İlqar Həsənov / Əli Məmmədov — 1000 xal  ↑ 2");

        // act
        clickText("1. İlqar Həsənov / Əli Məmmədov — 1000 xal  ↑ 2");
        followNavigation();

        // assert
        awaitText("Matç tarixçəsi yoxdur.");
        String points = "15.11.2026 12:00:00.000 — 800 xal; 15.11.2026 12:02:00.000 — 900 xal; 17.11.2026 12:02:00.000 — 1000 xal";
        assertThat(activity().findViewById(R.id.chart).getContentDescription().toString()).isEqualTo(points);
        assertThat(visibleText()).contains("1000 xal", "Reytinqdə yer: #1",
                "Yalnız bu cihazda uğurla saxlanmış reytinq yenilənmələri göstərilir. Yenilənmələr arasındakı dövr üçün məlumat yoxdur.")
                .doesNotContain("Növbəti reytinq yenilənmələri tarixçəyə yeni müşahidələr əlavə edəcək.");

        // act
        clickText("Mövqe");

        // assert
        String ranks = "15.11.2026 12:00:00.000 — #9; 15.11.2026 12:02:00.000 — #3; 17.11.2026 12:02:00.000 — #1";
        assertThat(activity().findViewById(R.id.chart).getContentDescription().toString()).isEqualTo(ranks);

        // act
        back();
        clickText("7. İlqar Həsənov / Şəms Quliyeva — 750 xal  ↓ 6");
        followNavigation();

        // assert
        awaitText("Matç tarixçəsi yoxdur.");
        assertThat(activity().findViewById(R.id.chart).getContentDescription().toString()).isEqualTo(
                "15.11.2026 12:00:00.000 — 700 xal; 15.11.2026 12:02:00.000 — 950 xal; 17.11.2026 12:02:00.000 — 750 xal");

        // act
        clickText("Mövqe");

        // assert
        assertThat(activity().findViewById(R.id.chart).getContentDescription().toString()).isEqualTo(
                "15.11.2026 12:00:00.000 — #12; 15.11.2026 12:02:00.000 — #1; 17.11.2026 12:02:00.000 — #7");

        // arrange
        responses.put("/players/duos", () -> new okhttp3.mockwebserver.MockResponse().setResponseCode(500));
        FixtureApplication.time = java.time.Clock.offset(FixtureApplication.time, java.time.Duration.ofMinutes(1));

        // act
        back();
        activity().findViewById(R.id.refresh).performClick();

        // assert
        awaitText("Yeniləmək mümkün olmadı. Saxlanmış məlumat göstərilir.");

        // act
        clickText("1. İlqar Həsənov / Əli Məmmədov — 1000 xal  ↑ 2");
        followNavigation();

        // assert
        assertThat(activity().findViewById(R.id.chart).getContentDescription().toString()).isEqualTo(points);
        String rankingUpdated = visibleText().stream().filter(text -> text.startsWith("Reytinqin son uğurlu yenilənməsi:")).findFirst().orElseThrow();

        // arrange
        FixtureApplication.online = false;
        FixtureApplication.time = java.time.Clock.offset(FixtureApplication.time, java.time.Duration.ofDays(365));
        server.shutdown();

        // act
        restart();
        clickText("Qoşalar");

        // assert
        awaitText("1. İlqar Həsənov / Əli Məmmədov — 1000 xal  ↑ 2");

        // act
        clickText("1. İlqar Həsənov / Əli Məmmədov — 1000 xal  ↑ 2");
        followNavigation();

        // assert
        assertThat(activity().findViewById(R.id.chart).getContentDescription().toString()).isEqualTo(points);
        assertThat(visibleText()).contains(rankingUpdated, "1000 xal", "Reytinqdə yer: #1");
        assertThat(activity().findViewById(R.id.refresh).isEnabled()).isFalse();

        // act
        clickText("Mövqe");

        // assert
        assertThat(activity().findViewById(R.id.chart).getContentDescription().toString()).isEqualTo(ranks);
    }

    @Test public void firstObservationIsDatedInBothModesAndProfileRefreshDoesNotCollectHistory() throws Exception {
        // arrange
        prepareDoubles();
        website.put("/matches/results/duo/110", pairHistory("<div class='no-info'>Matç yoxdur</div>"));
        openScreen();

        // act
        clickText("Qoşalar");

        // assert
        awaitText("1. İlqar Həsənov / Əli Məmmədov — 800 xal");

        // act
        clickText("1. İlqar Həsənov / Əli Məmmədov — 800 xal");
        followNavigation();

        // assert
        awaitText("Matç tarixçəsi yoxdur.");
        String point = "15.11.2026 12:00:00.000 — 800 xal";
        assertThat(activity().<android.view.View>findViewById(R.id.chart)).isNotNull();
        assertThat(activity().findViewById(R.id.chart).getContentDescription().toString()).isEqualTo(point);
        assertThat(visibleText()).contains("Növbəti reytinq yenilənmələri tarixçəyə yeni müşahidələr əlavə edəcək.");
        requestedPaths();

        // act
        clickText("Mövqe");

        // assert
        assertThat(activity().findViewById(R.id.chart).getContentDescription().toString())
                .isEqualTo("15.11.2026 12:00:00.000 — #1");
        assertThat(org.robolectric.Shadows.shadowOf(activity()).getNextStartedActivity()).isNull();
        assertThat(requestedPaths()).isEmpty();

        // arrange
        FixtureApplication.time = java.time.Clock.offset(FixtureApplication.time, java.time.Duration.ofMinutes(1));

        // act
        activity().findViewById(R.id.refresh).performClick();

        // assert
        awaitText("Matç tarixçəsi yoxdur.");
        // Wait for the HTTP refresh to complete, not just the already-saved matches.
        awaitText("Son uğurlu yenilənmə: 15 noy 2026 12:01");
        assertThat(activity().findViewById(R.id.chart).getContentDescription().toString())
                .isEqualTo("15.11.2026 12:00:00.000 — #1");
        assertThat(requestedPaths()).containsExactly("/api/getDuoInfo?id=110", "/matches/results/duo/110");

        // act
        clickText("Xal");

        // assert
        assertThat(activity().findViewById(R.id.chart).getContentDescription().toString()).isEqualTo(point);
    }

    private void ranking(String rows) {
        website.put("/players/duos", "<div id='center'><div class='ranking-page-list'>" + rows + "</div></div>");
    }
}
