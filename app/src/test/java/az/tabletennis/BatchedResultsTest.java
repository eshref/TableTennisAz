package az.tabletennis;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 26, qualifiers = "w411dp-h891dp", application = RankingsUiScenario.FixtureApplication.class)
public class BatchedResultsTest extends ProductUiScenario {
    @Test public void boundedMixedBatchKeepsEventProvenanceAndResumesBothSourcesAfterRestart() throws Exception {
        // arrange
        prepareResults();
        openScreen();

        // act
        activity().findViewById(R.id.open_menu).performClick();
        clickText("Nəticələr");
        followNavigation();

        // assert
        awaitText("14 Nov. 2026 · Kubok A — Kubok B · 3-0 · Payız Kuboku");
        assertThat(visibleText()).contains("13 Nov. 2026 · Liqa A — Liqa B · 3-1 · Şəhər Liqası")
                .contains("16 Nov. 2026 · İlqar Həsənov — Şəms Quliyeva · 3-1 · Şəhər Liqası")
                .contains("15 Nov. 2026 · İlqar Həsənov / Əli Məmmədov — Şəms Quliyeva / Işıl Əliyeva · 3-2 · Duello")
                .noneMatch(text -> text.contains("Uzaq A") || text.contains("Səhifə 2"));
        assertThat(visibleText().stream().filter(text -> text.contains("İlqar Həsənov — Şəms Quliyeva · 3-1"))).hasSize(1);
        assertThat(requestedPaths()).contains("/matches/results/event/901", "/matches/results/event/902", "/matches/duellos")
                .doesNotContain("/matches/results/event/903", "/matches/results/event/901?page=2", "/matches/duellos?page=2");

        // arrange
        String updated = ((android.widget.TextView) activity().findViewById(R.id.updated)).getText().toString();
        FixtureApplication.time = java.time.Clock.offset(FixtureApplication.time, java.time.Duration.ofMinutes(2));
        responses.put("/matches/results/event/903", () -> new okhttp3.mockwebserver.MockResponse().setResponseCode(503));

        // act
        restart();
        activity().findViewById(R.id.open_menu).performClick();
        clickText("Nəticələr");
        followNavigation();
        clickText("Daha çox yüklə");

        // assert
        awaitText("Yeniləmək mümkün olmadı. Saxlanmış məlumat göstərilir.");
        assertThat(visibleText()).contains(updated, "14 Nov. 2026 · Kubok A — Kubok B · 3-0 · Payız Kuboku")
                .noneMatch(text -> text.contains("Səhifə 2") || text.contains("Yeni A"));
        assertThat(requestedPaths()).contains("/matches/results/event/901?page=2", "/matches/results/event/903", "/matches/duellos?page=2");

        // arrange
        responses.remove("/matches/results/event/903");

        // act
        clickText("Daha çox yüklə");

        // assert
        awaitText("16 Nov. 2026 · Səhifə 2 A — Səhifə 2 B · 3-2 · Payız Kuboku");
        awaitText("12 Nov. 2026 · Uzaq A — Uzaq B · 2-0 · Uzaq Kubok");
        assertThat(requestedPaths()).contains("/matches/results/event/901?page=2", "/matches/results/event/903", "/matches/duellos?page=2")
                .doesNotContain("/matches/results/event/901", "/matches/results/event/902", "/matches/duellos");
        assertThat(visibleText()).containsSubsequence(
                "17 Nov. 2026 · Yeni A — Yeni B · 4-1 · Duello",
                "16 Nov. 2026 · İlqar Həsənov — Şəms Quliyeva · 3-1 · Şəhər Liqası",
                "16 Nov. 2026 · Səhifə 2 A — Səhifə 2 B · 3-2 · Payız Kuboku");
        assertThat(visibleText().stream().filter(text -> text.contains("İlqar Həsənov / Əli Məmmədov"))).hasSize(1);
        assertThat(visibleText()).contains(updated);

        // arrange
        server.shutdown();

        // act
        restart();
        activity().findViewById(R.id.open_menu).performClick();
        clickText("Nəticələr");
        followNavigation();

        // assert
        assertThat(visibleText()).contains("16 Nov. 2026 · Səhifə 2 A — Səhifə 2 B · 3-2 · Payız Kuboku",
                "17 Nov. 2026 · Yeni A — Yeni B · 4-1 · Duello", "12 Nov. 2026 · Uzaq A — Uzaq B · 2-0 · Uzaq Kubok");
    }
}
