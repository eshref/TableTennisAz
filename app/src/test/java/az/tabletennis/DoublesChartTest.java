package az.tabletennis;

import static org.assertj.core.api.Assertions.assertThat;

import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 26, qualifiers = "w411dp-h891dp", application = RankingsUiScenario.FixtureApplication.class)
public class DoublesChartTest extends DoublesUiScenario {
    @Test public void historyModesKeepOnlyTheirDatedValueChangesAcrossRefreshesAndOfflineRestart() throws Exception {
        // arrange
        prepareDoubles();
        ranking(pairRow(110, 9, 800));
        website.put("/matches/results/duo/110", pairHistory("<div class='no-info'>Matç yoxdur</div>"));
        openScreen();

        // act
        clickText("Qoşalar");

        // assert
        awaitText("Son uğurlu yenilənmə: 15 noy 2026 12:00");

        // arrange
        nextRanking(pairRow(110, 9, 800));

        // act
        activity().findViewById(R.id.refresh).performClick();

        // assert
        awaitText("Son uğurlu yenilənmə: 15 noy 2026 12:01");

        // act
        clickText("9. İlqar Həsənov / Əli Məmmədov — 800 xal");
        followNavigation();

        // assert
        awaitText("Matç tarixçəsi yoxdur.");
        assertThat(visibleText()).contains("Reytinqin son uğurlu yenilənməsi: 15 noy 2026 12:01");
        assertHistory("15.11.2026 12:00:00.000 — 800 xal");

        // act
        clickText("Mövqe");

        // assert
        assertHistory("15.11.2026 12:00:00.000 — #9");

        // arrange
        record Refresh(String rows, String expectedRow) {}
        List<Refresh> refreshes = List.of(
                new Refresh(pairRow(110, 8, 800), "8. İlqar Həsənov / Əli Məmmədov — 800 xal, 1 pillə yüksəlib"),
                new Refresh(pairRow(110, 8, 820), "8. İlqar Həsənov / Əli Məmmədov — 820 xal"),
                new Refresh(pairRow(220, 1, 900), "1. İlqar Həsənov / Şəms Quliyeva — 900 xal"),
                new Refresh(pairRow(110, 8, 820), "8. İlqar Həsənov / Əli Məmmədov — 820 xal"),
                new Refresh(pairRow(220, 1, 900), "1. İlqar Həsənov / Şəms Quliyeva — 900 xal"),
                new Refresh(pairRow(110, 6, 800), "6. İlqar Həsənov / Əli Məmmədov — 800 xal"),
                new Refresh(pairRow(110, 7, 800), "7. İlqar Həsənov / Əli Məmmədov — 800 xal, 1 pillə geriləyib"));

        // act
        back();
        for (Refresh refresh : refreshes) {
            // arrange
            nextRanking(refresh.rows());

            // act
            activity().findViewById(R.id.refresh).performClick();

            // assert
            awaitText(refresh.expectedRow());
        }

        // arrange
        FixtureApplication.online = false;
        server.shutdown();

        // act
        restart();
        clickText("Qoşalar");

        // assert
        awaitText("7. İlqar Həsənov / Əli Məmmədov — 800 xal, 1 pillə geriləyib");

        // act
        clickText("7. İlqar Həsənov / Əli Məmmədov — 800 xal, 1 pillə geriləyib");
        followNavigation();

        // assert
        assertThat(visibleText()).contains("Reytinqin son uğurlu yenilənməsi: 15 noy 2026 12:08", "Reytinqdə yer: #7");
        assertHistory("15.11.2026 12:00:00.000 — 800 xal", "15.11.2026 12:03:00.000 — 820 xal",
                "15.11.2026 12:07:00.000 — 800 xal");
        assertSelection("15.11.2026 12:07:00.000", "800 xal");

        // act
        clickText("Mövqe");

        // assert
        assertHistory("15.11.2026 12:00:00.000 — #9", "15.11.2026 12:02:00.000 — #8",
                "15.11.2026 12:07:00.000 — #6", "15.11.2026 12:08:00.000 — #7");
        assertSelection("15.11.2026 12:08:00.000", "#7");

        // act
        clickText("15.11.2026 12:02:00.000 — #8");
        clickText("Xal");

        // assert
        assertSelection("15.11.2026 12:00:00.000", "800 xal");

        // act
        clickText("Mövqe");

        // assert
        assertSelection("15.11.2026 12:02:00.000", "#8");
    }

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
        awaitText("3. İlqar Həsənov / Əli Məmmədov — 900 xal, 6 pillə yüksəlib");

        // arrange
        FixtureApplication.time = java.time.Clock.offset(FixtureApplication.time, java.time.Duration.ofDays(2));
        ranking(pairRow(110, 1, 1000) + pairRow(220, 7, 750));

        // act
        activity().findViewById(R.id.refresh).performClick();

        // assert
        awaitText("1. İlqar Həsənov / Əli Məmmədov — 1000 xal, 2 pillə yüksəlib");

        // arrange
        FixtureApplication.time = java.time.Clock.offset(FixtureApplication.time, java.time.Duration.ofHours(24));

        // act
        restart();
        clickText("Qoşalar");

        // assert
        awaitText("1. İlqar Həsənov / Əli Məmmədov — 1000 xal, 2 pillə yüksəlib");

        // act
        clickText("1. İlqar Həsənov / Əli Məmmədov — 1000 xal, 2 pillə yüksəlib");
        followNavigation();

        // assert
        awaitText("Matç tarixçəsi yoxdur.");
        String points = "15.11.2026 12:00:00.000 — 800 xal; 15.11.2026 12:02:00.000 — 900 xal; 17.11.2026 12:02:00.000 — 1000 xal";
        assertThat(activity().findViewById(R.id.chart).getContentDescription().toString()).isEqualTo(points);
        assertThat(visibleText()).contains("1000 xal", "Reytinqdə yer: #1",
                "İlk müşahidə və seçilmiş göstəricinin dəyişdiyi yenilənmələr göstərilir. Yenilənmələr arasındakı dövr üçün məlumat yoxdur.")
                .doesNotContain("İlk müşahidə göstərilir. Seçilmiş göstərici dəyişdikdə tarixçəyə yeni qeyd əlavə olunur.");

        // act
        clickText("15.11.2026 12:00:00.000 — 800 xal");

        // assert
        assertThat(activity().<android.widget.TextView>findViewById(R.id.chart_selected_date).getText().toString())
                .isEqualTo("15.11.2026 12:00:00.000");
        assertThat(activity().<android.widget.TextView>findViewById(R.id.chart_selected_value).getText().toString())
                .isEqualTo("800 xal");

        // act
        clickText("Mövqe");

        // assert
        String ranks = "15.11.2026 12:00:00.000 — #9; 15.11.2026 12:02:00.000 — #3; 17.11.2026 12:02:00.000 — #1";
        assertThat(activity().findViewById(R.id.chart).getContentDescription().toString()).isEqualTo(ranks);
        assertThat(activity().<android.widget.TextView>findViewById(R.id.chart_selected_value).getText().toString())
                .isEqualTo("#9");

        // act
        back();
        clickText("7. İlqar Həsənov / Şəms Quliyeva — 750 xal, 6 pillə geriləyib");
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
        clickText("1. İlqar Həsənov / Əli Məmmədov — 1000 xal, 2 pillə yüksəlib");
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
        awaitText("1. İlqar Həsənov / Əli Məmmədov — 1000 xal, 2 pillə yüksəlib");

        // act
        clickText("1. İlqar Həsənov / Əli Məmmədov — 1000 xal, 2 pillə yüksəlib");
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
        assertThat(visibleText()).contains("İlk müşahidə göstərilir. Seçilmiş göstərici dəyişdikdə tarixçəyə yeni qeyd əlavə olunur.");
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
        recreateProductScreen();

        // assert
        assertThat(activity().<android.widget.RadioButton>findViewById(R.id.pair_chart_rank).isChecked()).isTrue();
        assertThat(activity().findViewById(R.id.chart).getContentDescription().toString())
                .isEqualTo("15.11.2026 12:00:00.000 — #1");
        assertThat(requestedPaths()).isEmpty();

        // act
        clickText("Xal");

        // assert
        assertThat(activity().findViewById(R.id.chart).getContentDescription().toString()).isEqualTo(point);
    }

    private void nextRanking(String rows) {
        FixtureApplication.time = Clock.offset(FixtureApplication.time, Duration.ofMinutes(1));
        ranking(rows);
    }

    private void assertHistory(String... entries) {
        assertThat(activity().findViewById(R.id.chart).getContentDescription().toString())
                .isEqualTo(String.join("; ", entries));
        ViewGroup rows = activity().findViewById(R.id.chart_observations);
        List<String> descriptions = new ArrayList<>();
        List<String> datedRows = new ArrayList<>();
        for (int i = 0; i < rows.getChildCount(); i++) {
            View row = rows.getChildAt(i);
            if (!row.isClickable()) continue;
            TextView date = row.findViewById(android.R.id.text1);
            TextView value = row.findViewById(android.R.id.text2);
            datedRows.add(date.getText() + " — " + value.getText());
            descriptions.add(row.getContentDescription().toString());
        }
        assertThat(datedRows).containsExactly(entries);
        assertThat(descriptions).containsExactly(entries);
    }

    private void assertSelection(String date, String value) {
        assertThat(activity().<TextView>findViewById(R.id.chart_selected_date).getText().toString()).isEqualTo(date);
        assertThat(activity().<TextView>findViewById(R.id.chart_selected_value).getText().toString()).isEqualTo(value);
    }

    private void ranking(String rows) {
        website.put("/players/duos", "<div id='center'><div class='ranking-page-list'>" + rows + "</div></div>");
    }
}
