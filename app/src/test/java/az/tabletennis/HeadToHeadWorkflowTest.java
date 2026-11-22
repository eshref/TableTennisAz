package az.tabletennis;

import static az.tabletennis.HeadToHeadFixtures.ASHRAF;
import static az.tabletennis.HeadToHeadFixtures.ALIKHAN;
import static az.tabletennis.HeadToHeadFixtures.DOUBLES;
import static az.tabletennis.HeadToHeadFixtures.DOUBLES_NEWER;
import static az.tabletennis.HeadToHeadFixtures.ELCHIN;
import static az.tabletennis.HeadToHeadFixtures.ERHAN;
import static az.tabletennis.HeadToHeadFixtures.FIRDOVSI;
import static az.tabletennis.HeadToHeadFixtures.HISTORICAL;
import static az.tabletennis.HeadToHeadFixtures.ILQAR;
import static az.tabletennis.HeadToHeadFixtures.KHAYAL;
import static az.tabletennis.HeadToHeadFixtures.MIDDLE;
import static az.tabletennis.HeadToHeadFixtures.NEWER;
import static az.tabletennis.HeadToHeadFixtures.OLDER;
import static az.tabletennis.HeadToHeadFixtures.SHAKIR;
import static az.tabletennis.HeadToHeadFixtures.VAQIF;
import static az.tabletennis.HeadToHeadFixtures.laterDoublesPage;
import static az.tabletennis.HeadToHeadFixtures.laterPage;
import static az.tabletennis.HeadToHeadFixtures.publish;
import static org.assertj.core.api.Assertions.assertThat;
import static org.robolectric.Shadows.shadowOf;

import android.os.Looper;
import android.view.View;
import android.widget.EditText;
import android.widget.ScrollView;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36, qualifiers = "w411dp-h500dp", application = RankingsUiScenario.FixtureApplication.class)
public class HeadToHeadWorkflowTest extends ProductUiScenario {
    @Test public void singlesSearchFindsVerifiedMatchesAcrossPagesAndKeepsItsPlaceAfterDetails() throws Exception {
        // arrange
        publish(website);
        CountDownLatch laterPage = new CountDownLatch(1);
        responses.put("/matches/results/player/13?page=2", () -> {
            try {
                if (laterPage.await(15, TimeUnit.SECONDS)) return new MockResponse().setBody(laterPage());
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            return new MockResponse().setResponseCode(503);
        });
        openScreen();

        // act
        clickText("☰");

        // assert
        assertThat(visibleText()).containsSequence("Duellər", "Üz-üzə", "Məxfilik siyasəti");

        // act
        clickText("Üz-üzə");
        followNavigation();
        pickPlayer(R.id.head_to_head_first, "Səf", ASHRAF);
        pickPlayer(R.id.head_to_head_second, "Səf", ASHRAF);

        // assert
        assertThat(visibleText()).contains("Tək", "İki fərqli oyunçu seçin.");
        assertThat(searchButton().isEnabled()).isFalse();

        // act
        field(R.id.head_to_head_second).setText(ILQAR);

        // assert
        assertThat(searchButton().isEnabled()).isFalse();

        // act
        pickPlayer(R.id.head_to_head_second, "İlq", ILQAR);
        clickText("Axtar");

        // act & assert
        try {
            awaitText(OLDER);
            assertThat(visibleText()).contains("Axtarış davam edir").doesNotContain(NEWER);
        } finally {
            laterPage.countDown();
        }

        // assert
        awaitText(NEWER);
        awaitText("Axtarış tamamlandı");
        assertThat(matchCards()).containsExactly(NEWER, MIDDLE, OLDER);

        // act
        ScrollView scroll = activity().findViewById(R.id.main_scroll);
        scroll.scrollTo(0, 125);
        int position = scroll.getScrollY();
        clickText(OLDER);
        followNavigation();

        // assert
        awaitText("Matç haqqında");
        awaitText("10 Aug. 2021");
        assertThat(visibleText()).contains(ASHRAF, ILQAR, "3", "2");

        // act
        back();
        shadowOf(Looper.getMainLooper()).idle();

        // assert
        assertThat(position).isPositive();
        assertThat(scroll.getScrollY()).isEqualTo(position);
        assertThat(field(R.id.head_to_head_first).getText().toString()).isEqualTo(ASHRAF);
        assertThat(field(R.id.head_to_head_second).getText().toString()).isEqualTo(ILQAR);
        assertThat(matchCards()).containsExactly(NEWER, MIDDLE, OLDER);

        // act
        pickPlayer(R.id.head_to_head_first, "İlq", ILQAR);
        pickPlayer(R.id.head_to_head_second, "Səf", ASHRAF);
        clickText("Axtar");

        // assert
        awaitText("Axtarış tamamlandı");
        assertThat(matchCards()).containsExactly(NEWER, MIDDLE, OLDER);
    }

    @Test public void keepsVerifiedMatchesWhenALaterHistoryPageFails() throws Exception {
        // arrange
        publish(website);
        responses.put("/matches/results/player/13?page=2", () -> new MockResponse().setResponseCode(503));
        openScreen();

        // act
        clickText("☰");
        clickText("Üz-üzə");
        followNavigation();
        pickPlayer(R.id.head_to_head_first, "Səf", ASHRAF);
        pickPlayer(R.id.head_to_head_second, "İlq", ILQAR);
        clickText("Axtar");

        // assert
        awaitText("Axtarış tamamlanmadı");
        assertThat(visibleText()).doesNotContain("Matç tapılmadı");
        assertThat(matchCards()).containsExactly(OLDER);
        assertThat(searchButton().isEnabled()).isTrue();
    }

    @Test public void doublesSearchFindsTheSameMeetingsAcrossTeammateAndSideOrders() throws Exception {
        // arrange
        HeadToHeadFixtures.publishDoubles(website);
        CountDownLatch laterPage = new CountDownLatch(1);
        responses.put("/matches/results/player/7?page=2", () -> {
            try {
                if (laterPage.await(15, TimeUnit.SECONDS)) return new MockResponse().setBody(laterDoublesPage());
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            return new MockResponse().setResponseCode(503);
        });
        openScreen();

        // act
        openDoubles();
        pickPlayer(R.id.head_to_head_left_first, "Erh", ERHAN);
        pickPlayer(R.id.head_to_head_left_second, "Elç", ELCHIN);
        pickPlayer(R.id.head_to_head_right_first, "Əli", ALIKHAN);
        pickPlayer(R.id.head_to_head_right_second, "Erh", ERHAN);

        // assert
        assertThat(visibleText()).contains("Qoşa 1", "Qoşa 2", "Dörd fərqli oyunçu seçin.");
        assertThat(searchButton().isEnabled()).isFalse();

        // act
        field(R.id.head_to_head_right_second).setText(KHAYAL);

        // assert
        assertThat(searchButton().isEnabled()).isFalse();

        // act
        pickPlayer(R.id.head_to_head_right_second, "Xəy", KHAYAL);
        clickText("Axtar");

        // act & assert
        try {
            awaitText(DOUBLES);
            assertThat(visibleText()).contains("Axtarış davam edir").doesNotContain(DOUBLES_NEWER);
        } finally {
            laterPage.countDown();
        }

        // assert
        awaitText("Axtarış tamamlandı");
        assertThat(doublesCards()).containsExactly(DOUBLES_NEWER, DOUBLES);

        // act
        pickPlayer(R.id.head_to_head_left_first, "Elç", ELCHIN);
        pickPlayer(R.id.head_to_head_left_second, "Erh", ERHAN);
        clickText("Axtar");

        // assert
        awaitText("Axtarış tamamlandı");
        assertThat(doublesCards()).containsExactly(DOUBLES_NEWER, DOUBLES);

        // act
        pickPlayer(R.id.head_to_head_left_first, "Əli", ALIKHAN);
        pickPlayer(R.id.head_to_head_left_second, "Xəy", KHAYAL);
        pickPlayer(R.id.head_to_head_right_first, "Erh", ERHAN);
        pickPlayer(R.id.head_to_head_right_second, "Elç", ELCHIN);
        clickText("Axtar");

        // assert
        awaitText("Axtarış tamamlandı");
        assertThat(doublesCards()).containsExactly(DOUBLES_NEWER, DOUBLES);
    }

    @Test public void historicalDoublesRequireTheSelectedProfilesOnTheirExactSides() throws Exception {
        // arrange
        HeadToHeadFixtures.publishHistoricalDoubles(website);
        openScreen();

        // act
        openDoubles();
        pickPlayer(R.id.head_to_head_left_first, "Xəy", KHAYAL);
        pickPlayer(R.id.head_to_head_left_second, "Fir", FIRDOVSI);
        pickPlayer(R.id.head_to_head_right_first, "Vaq", VAQIF);
        pickPlayer(R.id.head_to_head_right_second, "Şak", SHAKIR);
        clickText("Axtar");

        // assert
        awaitText("Axtarış tamamlandı");
        assertThat(doublesCards()).containsExactly(HISTORICAL);

        // act
        clickText(HISTORICAL);
        followNavigation();

        // assert
        awaitText("25 Mar. 2026");
        assertThat(visibleText()).contains(KHAYAL, FIRDOVSI, VAQIF, SHAKIR, "3", "1");

        // act
        back();
        recreateProductScreen();

        // assert
        assertThat(field(R.id.head_to_head_left_first).isShown()).isTrue();
        assertThat(visibleText()).contains(KHAYAL, FIRDOVSI, VAQIF, SHAKIR);
        assertThat(doublesCards()).containsExactly(HISTORICAL);
    }

    @Test public void playerDirectoryDistinguishesAFailedLookupFromNoMatchingPlayers() throws Exception {
        // arrange
        publish(website);
        responses.put("/search?query=s%C9%99f", () -> new MockResponse().setResponseCode(503));
        website.put("/search?query=nam%C9%99lum", "<div id='center'><div class='no-info'>"
                + "Axtarış kriteriyalarına uyğun oyunçu tapılmadı.</div></div>");
        openScreen();

        // act
        clickText("☰");
        clickText("Üz-üzə");
        followNavigation();
        field(R.id.head_to_head_first).requestFocus();
        field(R.id.head_to_head_first).setText("Səf");

        advanceDebounce();

        // assert
        awaitText("Oyunçu siyahısı yüklənmədi.");
        assertThat(visibleText()).contains("Yenidən cəhd et");

        // arrange
        responses.remove("/search?query=s%C9%99f");

        // act
        clickText("Yenidən cəhd et");

        advanceDebounce();

        // assert
        awaitText(ASHRAF);

        // act
        field(R.id.head_to_head_first).setText("Naməlum");

        advanceDebounce();

        // assert
        awaitText("Axtarışa uyğun oyunçu tapılmadı.");
        assertThat(visibleText()).doesNotContain(ASHRAF, "Yenidən cəhd et");
        assertThat(searchButton().isEnabled()).isFalse();
    }

    private void advanceDebounce() {
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(300));
    }

    private void openDoubles() throws Exception {
        clickText("☰");
        clickText("Üz-üzə");
        followNavigation();
        clickText("Qoşalar");
    }

    private EditText field(int id) { return activity().findViewById(id); }

    private View searchButton() { return findText(activity().getWindow().getDecorView(), "Axtar"); }

    private void pickPlayer(int id, String query, String fullName) throws InterruptedException {
        field(id).requestFocus();
        field(id).setText(query);
        View suggestions = ((android.view.ViewGroup) field(id).getParent()).getChildAt(2);
        View suggestion = null;
        for (int attempt = 0; attempt < 250 && suggestion == null; attempt++) {
            shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(20));
            suggestion = findText(suggestions, fullName);
            if (suggestion == null) Thread.sleep(20);
        }
        if (suggestion == null) throw new IllegalStateException("No player suggestion for " + fullName);
        suggestion.performClick();
    }

    private java.util.List<String> matchCards() {
        return visibleText().stream().filter(text -> text.endsWith(" · Tək · Duel") || text.endsWith(" · Tək · Turnir")).toList();
    }

    private List<String> doublesCards() {
        return visibleText().stream().filter(text -> text.endsWith(" · Qoşalar · Turnir")).toList();
    }

}
