package az.tabletennis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {26, 36}, qualifiers = "w411dp-h891dp", application = RankingsUiScenario.FixtureApplication.class)
public class DuelsWorkflowTest extends ProductUiScenario {
    private static final String SINGLES = "16 Nov. 2026 · İlqar Həsənov — Şəms Quliyeva · 3-1 · Duello · Tək · Duel";
    private static final String DOUBLES = "15 Nov. 2026 · İlqar Həsənov / Əli Məmmədov — Şəms Quliyeva / Işıl Əliyeva · 3-2 · Duello · Qoşalar · Duel";
    private static final String OLDER = "14 Nov. 2026 · Yeni A — Yeni B · 4-1 · Duello · Tək · Duel";

    @Test public void menuOpensMixedDuelsWithPaginationAndPreviouslyOpenedDetailsOffline() throws Exception {
        // arrange
        prepareResults();
        website.compute("/matches/duellos?page=2", (path, body) -> body.replace("17 Nov. 2026", "14 Nov. 2026"));
        website.put("/matches/501", html("doubles-detail.html").getBody().readUtf8().replace(">Kupa<", ">Duello<"));
        openScreen();
        awaitText("1. İlqar Həsənov — 800 xal");
        requestedPaths();

        // act
        clickText("☰");
        clickText("Duellər");
        followNavigation();

        // assert
        awaitText(SINGLES);
        assertThat(visibleText()).contains("Duellər", "Tək · Duel", "Qoşalar · Duel", "Daha çox yüklə")
                .doesNotContain("Hamısı", "Turnirlər", "Tədbir seç", OLDER);
        assertThat(duelCards()).containsExactly(SINGLES, DOUBLES);
        assertThat(requestedPaths()).containsExactly("/matches/duellos");

        // act
        clickText("Daha çox yüklə");

        // assert
        awaitText(OLDER);
        assertThat(duelCards()).containsExactly(SINGLES, DOUBLES, OLDER);
        assertThat(visibleText()).doesNotContain("Daha çox yüklə");
        assertThat(requestedPaths()).containsExactly("/matches/duellos?page=2");

        // act
        clickText(DOUBLES);
        followNavigation();

        // assert
        awaitText("15 Nov. 2026 · 18:00");
        assertThat(visibleText()).contains("Matç haqqında", "Duello", "Qoşalar", "Əli Məmmədov", "Işıl Əliyeva", "3", "2");
        assertThat(requestedPaths()).containsExactly("/matches/501");

        // arrange
        FixtureApplication.online = false;

        // act
        restart();
        clickText("☰");
        clickText("Duellər");
        followNavigation();

        // assert
        assertThat(visibleText()).contains("İnternet bağlantısı yoxdur. Saxlanmış məlumat göstərilir.");
        assertThat(duelCards()).containsExactly(SINGLES, DOUBLES, OLDER);

        // act
        clickText(DOUBLES);
        followNavigation();

        // assert
        assertThat(visibleText()).contains("Matç haqqında", "15 Nov. 2026 · 18:00", "Əli Məmmədov", "Işıl Əliyeva", "3", "2",
                "İnternet bağlantısı yoxdur. Saxlanmış məlumat göstərilir.");
        assertThat(requestedPaths()).isEmpty();
    }

    @Test public void aVerifiedEmptyDuelFeedShowsAnEmptyState() throws Exception {
        // arrange
        website.put("/matches/duellos", "<div id='center'><div class='matches-box'>"
                + "<div class='no-info'>Matç yoxdur</div></div></div>");
        openScreen();

        // act
        clickText("☰");
        clickText("Duellər");
        followNavigation();

        // assert
        awaitText("Mənbədə duel tapılmadı.");
        assertThat(visibleText()).doesNotContain("Daha çox yüklə", "Filtrləri təmizlə");
    }

    @Test public void refreshingDuelsRemovesLoadMoreWhenTheSourceNoLongerHasANextPage() throws Exception {
        // arrange
        prepareResults();
        openScreen();
        clickText("☰");
        clickText("Duellər");
        followNavigation();
        awaitText("Daha çox yüklə");
        website.put("/matches/duellos", html("duels.html").getBody().readUtf8());

        // act
        clickText("↻");

        // assert
        awaitText("Məlumat yeniləndi.");
        assertThat(duelCards()).containsExactly(SINGLES, DOUBLES);
        assertThat(visibleText()).doesNotContain("Daha çox yüklə");
    }

    private List<String> duelCards() {
        return visibleText().stream().filter(text -> text.contains(" — ") && text.endsWith(" · Duel"))
                .collect(java.util.stream.Collectors.toList());
    }
}
