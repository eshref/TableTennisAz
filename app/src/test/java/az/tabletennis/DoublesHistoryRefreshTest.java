package az.tabletennis;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 26, qualifiers = "w411dp-h891dp", application = RankingsUiScenario.FixtureApplication.class)
public class DoublesHistoryRefreshTest extends DoublesUiScenario {
    @Test public void loadedHistoryRefreshesAtomicallyAcrossPagesAndRejectsAnotherPartnershipDespiteASharedPlayer() throws Exception {
        // arrange
        prepareDoubles();
        website.put("/api/getDuoInfo?id=110", pairInfo(110, 12, "İlqar  Həsənov", 13, "Əli Məmmədov"));
        website.put("/matches/results/duo/110", pairHistory(pairResult(501, "15 Nov. 2026", "Kupa"))
                + "<ul class='pagination'><li><a href='/matches/results/duo/110?page=2'>2</a></li></ul>");
        website.put("/matches/results/duo/110?page=2", pairHistory(pairResult(506, "14 Nov. 2026", "Duello")));
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
        awaitText("15 Nov. 2026 · İlqar Həsənov / Əli Məmmədov — Şəms Quliyeva / Işıl Əliyeva · 3-2 · Kupa · Qoşalar · Turnir");

        // act
        clickText("Daha çox yüklə");

        // assert
        awaitText("14 Nov. 2026 · İlqar Həsənov / Əli Məmmədov — Şəms Quliyeva / Işıl Əliyeva · 3-2 · Duello · Qoşalar · Duel");
        String updated = ((android.widget.TextView) activity().findViewById(R.id.updated)).getText().toString();
        requestedPaths();

        // arrange
        FixtureApplication.time = java.time.Clock.offset(FixtureApplication.time, java.time.Duration.ofHours(49));
        website.compute("/matches/results/duo/110", (path, body) -> body.replace("3-2", "3-0"));
        responses.put("/matches/results/duo/110?page=2", () -> new okhttp3.mockwebserver.MockResponse().setResponseCode(503));

        // act
        back();
        clickText("1. İlqar Həsənov / Əli Məmmədov — 800 xal");
        followNavigation();

        // assert
        assertThat(visibleText()).contains(updated, "15 Nov. 2026 · İlqar Həsənov / Əli Məmmədov — Şəms Quliyeva / Işıl Əliyeva · 3-2 · Kupa · Qoşalar · Turnir");
        awaitText("Yeniləmək mümkün olmadı. Saxlanmış məlumat göstərilir.");
        assertThat(visibleText()).contains(updated).noneMatch(text -> text.contains("3-0"));
        assertThat(requestedPaths()).containsExactly("/api/getDuoInfo?id=110", "/matches/results/duo/110", "/matches/results/duo/110?page=2");

        // arrange
        responses.remove("/matches/results/duo/110?page=2");

        // act
        activity().findViewById(R.id.refresh).performClick();

        // assert
        awaitText("15 Nov. 2026 · İlqar Həsənov / Əli Məmmədov — Şəms Quliyeva / Işıl Əliyeva · 3-0 · Kupa · Qoşalar · Turnir");
        assertThat(visibleText()).contains("14 Nov. 2026 · İlqar Həsənov / Əli Məmmədov — Şəms Quliyeva / Işıl Əliyeva · 3-2 · Duello · Qoşalar · Duel")
                .doesNotContain("Daha çox yüklə");
        assertThat(requestedPaths()).containsExactly("/api/getDuoInfo?id=110", "/matches/results/duo/110", "/matches/results/duo/110?page=2");
        String refreshed = ((android.widget.TextView) activity().findViewById(R.id.updated)).getText().toString();
        assertThat(refreshed).isNotEqualTo(updated);

        // arrange
        website.compute("/matches/results/duo/110?page=2", (path, body) -> body.replace("Əli Məmmədov", "Başqa tərəfdaş"));

        // act
        activity().findViewById(R.id.refresh).performClick();

        // assert
        awaitText("Yeniləmək mümkün olmadı. Saxlanmış məlumat göstərilir.");
        assertThat(visibleText()).contains(refreshed,
                "14 Nov. 2026 · İlqar Həsənov / Əli Məmmədov — Şəms Quliyeva / Işıl Əliyeva · 3-2 · Duello · Qoşalar · Duel")
                .noneMatch(text -> text.contains("Başqa tərəfdaş"));
    }
}
