package az.tabletennis;

import static az.tabletennis.HeadToHeadFixtures.ASHRAF;
import static az.tabletennis.HeadToHeadFixtures.ALIKHAN;
import static az.tabletennis.HeadToHeadFixtures.ARCHIVE_FINAL_DUEL;
import static az.tabletennis.HeadToHeadFixtures.ARCHIVE_PAGE_TWO_DUEL;
import static az.tabletennis.HeadToHeadFixtures.ARCHIVE_TEAM_GAME;
import static az.tabletennis.HeadToHeadFixtures.ARCHIVE_TOURNAMENT;
import static az.tabletennis.HeadToHeadFixtures.DOUBLES;
import static az.tabletennis.HeadToHeadFixtures.DOUBLES_NEWER;
import static az.tabletennis.HeadToHeadFixtures.DOUBLES_TEAM_GAME;
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
import static az.tabletennis.HeadToHeadFixtures.directory;
import static az.tabletennis.HeadToHeadFixtures.directoryPlayer;
import static az.tabletennis.HeadToHeadFixtures.laterDoublesPage;
import static az.tabletennis.HeadToHeadFixtures.laterPage;
import static az.tabletennis.HeadToHeadFixtures.publish;
import static org.assertj.core.api.Assertions.assertThat;
import static org.robolectric.Shadows.shadowOf;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.VectorDrawable;
import android.os.Looper;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ScrollView;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import okhttp3.Call;
import okhttp3.EventListener;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okio.Buffer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36, qualifiers = "w411dp-h500dp", application = RankingsUiScenario.FixtureApplication.class)
public class HeadToHeadWorkflowTest extends ProductUiScenario {
    private static final String MISSING_PORTRAIT = "/portraits/player-115.png";

    public static class PortraitFixtureApplication extends RankingsUiScenario.FixtureApplication {
        private final CountDownLatch missingPortraitResponse = new CountDownLatch(1);

        @Override WebsiteClient websiteClient() {
            return new WebsiteClient(websiteUrl(), new OkHttpClient.Builder().eventListener(new EventListener() {
                @Override public void callEnd(Call call) {
                    if (MISSING_PORTRAIT.equals(call.request().url().encodedPath())) missingPortraitResponse.countDown();
                }
            }));
        }
    }

    @Test
    @Config(application = PortraitFixtureApplication.class)
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    public void namesakeSuggestionsKeepTheirPortraitsAndSelectionsWhenTheQueryChanges() throws Exception {
        // arrange
        HeadToHeadFixtures.publishNamesakeMeeting(website);
        String available = "/portraits/player-15.png";
        String player = directoryPlayer(15, ASHRAF, available);
        String withPhoto = ASHRAF + " · Profil 15";
        String withoutPhoto = ASHRAF + " · Profil 115";
        website.put("/search?query=s%C9%99f", directory(player, directoryPlayer(115, ASHRAF, "/assets/img/person.png")));
        website.put("/search?query=s%C9%99f%C9%99r", directory(directoryPlayer(115, ASHRAF, MISSING_PORTRAIT), player));
        CountDownLatch releasePhoto = new CountDownLatch(1);
        Bitmap portrait = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888);
        portrait.eraseColor(Color.BLUE);
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        portrait.compress(Bitmap.CompressFormat.PNG, 100, png);
        portrait.recycle();
        responses.put(available, () -> awaitPage(releasePhoto,
                new MockResponse().setHeader("Content-Type", "image/png").setBody(new Buffer().write(png.toByteArray()))));
        responses.put(MISSING_PORTRAIT, () -> new MockResponse().setResponseCode(404));
        var application = (PortraitFixtureApplication) RuntimeEnvironment.getApplication();
        openDirect(HeadToHeadActivity.class, "", 0);

        try {
            // act
            field(R.id.head_to_head_first).setText("Səf");
            advanceDebounce();

            // assert
            awaitText(withPhoto);
            assertThat(requestTo(available)).isEqualTo(available);
            assertThat(visibleText()).containsSequence(withPhoto, withoutPhoto);
            assertThat(suggestionPortrait(withPhoto).getDrawable()).isInstanceOf(VectorDrawable.class);

            // act
            clickText(withPhoto);

            // assert
            assertThat(field(R.id.head_to_head_first).getText().toString()).isEqualTo(ASHRAF);

            // act
            field(R.id.head_to_head_second).setText("Səf");
            advanceDebounce();
            awaitText(withPhoto);
            field(R.id.head_to_head_second).setText("Səfər");
            advanceDebounce();

            // assert
            awaitText(withoutPhoto);
            assertThat(visibleText()).containsSequence(withoutPhoto, withPhoto);
            assertThat(application.missingPortraitResponse.await(5, TimeUnit.SECONDS))
                    .as("the unavailable portrait HTTP response was received").isTrue();
        } finally {
            releasePhoto.countDown();
        }

        // assert
        awaitPortrait(withPhoto);
        assertThat(suggestionPortrait(withoutPhoto).getDrawable()).isInstanceOf(VectorDrawable.class);

        // act
        clickText(withoutPhoto);
        clickText("Axtar");

        // assert
        awaitText("Axtarış tamamlandı");
        assertThat(visibleText()).containsSequence("Qələbə sayı", "1–0", ASHRAF, ASHRAF);
        assertThat(matchCards()).containsExactly("18 Nov. 2026 · Əşrəf Səfərov — Əşrəf Səfərov · 3-1 · Liqa · Tək · Turnir");
    }

    @Test public void scrollingToASuggestionRequestsItsPortrait() throws Exception {
        // arrange
        String portrait = "/portraits/player-15.png";
        List<String> directoryRows = new ArrayList<>();
        for (int id = 101; id <= 120; id++) {
            directoryRows.add(directoryPlayer(id, "Səfərov " + id, null));
        }
        directoryRows.add(directoryPlayer(15, ASHRAF, portrait));
        website.put("/search?query=s%C9%99f", directory(directoryRows.toArray(String[]::new)));
        openDirect(HeadToHeadActivity.class, "", 0);

        // act
        field(R.id.head_to_head_first).setText("Səf");
        advanceDebounce();

        // assert
        awaitText(ASHRAF);
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(32));
        assertThat(requestTo(portrait)).isNull();

        // act
        ScrollView scroll = activity().findViewById(R.id.main_scroll);
        scroll.scrollTo(0, scroll.getChildAt(0).getHeight());
        // Robolectric updates the scroll offset without dispatching a new frame.
        scroll.getViewTreeObserver().dispatchOnPreDraw();

        // assert
        assertThat(requestTo(portrait)).isEqualTo(portrait);
    }

    @Test public void singlesSearchIncludesTournamentResultsAndOnlyTheSelectedPlayersTeamGame() throws Exception {
        // arrange
        publish(website);
        HeadToHeadFixtures.publishTournamentArchive(website);
        openScreen();

        // act
        clickText("☰");
        clickText("Üz-üzə");
        followNavigation();
        pickPlayer(R.id.head_to_head_first, "Səf", ASHRAF);
        pickPlayer(R.id.head_to_head_second, "İlq", ILQAR);
        clickText("Axtar");

        // assert
        awaitText("Axtarış tamamlandı");
        assertThat(visibleText()).containsSequence("Qələbə sayı", "3–4", ASHRAF, ILQAR);
        assertThat(matchCards()).containsExactly(ARCHIVE_TOURNAMENT, NEWER, ARCHIVE_PAGE_TWO_DUEL, MIDDLE,
                ARCHIVE_FINAL_DUEL, ARCHIVE_TEAM_GAME, OLDER);

        // act
        clickText(ARCHIVE_TEAM_GAME);
        followNavigation();

        // assert
        awaitText("Matç haqqında");
        awaitText("12 Nov. 2026");
        assertThat(visibleText()).contains(ASHRAF, ILQAR, "2", "3", "Komanda Liqası");
    }

    @Test public void singlesSearchMergesVerifiedHistoryAndDuelsAndKeepsItsPlaceAfterDetails() throws Exception {
        // arrange
        publish(website);
        CountDownLatch laterPage = new CountDownLatch(1);
        responses.put("/matches/results/player/13?page=2", () -> awaitPage(laterPage, new MockResponse().setBody(laterPage())));
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
            assertThat(visibleText()).contains("Axtarış davam edir").doesNotContain(NEWER, "Qələbə sayı", "Matç tapılmadı");
        } finally {
            laterPage.countDown();
        }

        // assert
        awaitText(NEWER);
        awaitText("Axtarış tamamlandı");
        assertThat(visibleText()).containsSequence("Qələbə sayı", "1–4", ASHRAF, ILQAR);
        assertThat(matchCards()).containsExactly(NEWER, ARCHIVE_PAGE_TWO_DUEL, MIDDLE, ARCHIVE_FINAL_DUEL, OLDER);

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
        assertThat(matchCards()).containsExactly(NEWER, ARCHIVE_PAGE_TWO_DUEL, MIDDLE, ARCHIVE_FINAL_DUEL, OLDER);

        // act
        pickPlayer(R.id.head_to_head_first, "İlq", ILQAR);
        pickPlayer(R.id.head_to_head_second, "Səf", ASHRAF);
        clickText("Axtar");

        // assert
        awaitText("Axtarış tamamlandı");
        assertThat(visibleText()).containsSequence("Qələbə sayı", "4–1", ILQAR, ASHRAF);
        assertThat(matchCards()).containsExactly(NEWER, ARCHIVE_PAGE_TWO_DUEL, MIDDLE, ARCHIVE_FINAL_DUEL, OLDER);
    }

    @Test public void retriesSinglesAfterALaterHistoryPageFails() throws Exception {
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
        assertThat(visibleText()).doesNotContain("Qələbə sayı", "Matç tapılmadı");
        assertThat(matchCards()).containsExactly(OLDER);

        // arrange
        CountDownLatch laterPage = new CountDownLatch(1);
        CountDownLatch duelArchive = new CountDownLatch(1);
        // A previously verified match is still usable if its details cannot be fetched on retry.
        responses.put("/matches/46", () -> new MockResponse().setResponseCode(503));
        responses.put("/matches/results/player/13?page=2", () -> awaitPage(laterPage, new MockResponse().setBody(laterPage())));
        responses.put("/matches/duellos", () -> awaitPage(duelArchive, new MockResponse().setBody(website.get("/matches/duellos"))));

        // act & assert
        try {
            clickText("Yenidən cəhd et");
            assertThat(visibleText()).contains("Axtarış davam edir").doesNotContain("Yenidən cəhd et");
            assertThat(field(R.id.head_to_head_first).getText().toString()).isEqualTo(ASHRAF);
            assertThat(field(R.id.head_to_head_second).getText().toString()).isEqualTo(ILQAR);
            assertThat(matchCards()).containsExactly(OLDER);
            laterPage.countDown();
            awaitText(MIDDLE);
            assertThat(visibleText()).contains("Axtarış davam edir").doesNotContain("Qələbə sayı");
            assertThat(matchCards()).containsExactly(NEWER, MIDDLE, OLDER);
        } finally {
            laterPage.countDown();
            duelArchive.countDown();
        }

        // assert
        awaitText("Axtarış tamamlandı");
        assertThat(visibleText()).containsSequence("Qələbə sayı", "1–4", ASHRAF, ILQAR);
        assertThat(matchCards()).containsExactly(NEWER, ARCHIVE_PAGE_TWO_DUEL, MIDDLE, ARCHIVE_FINAL_DUEL, OLDER);
    }

    @Test public void keepsVerifiedMatchesWhenTheFinalDuelPageFails() throws Exception {
        // arrange
        publish(website);
        responses.put("/matches/duellos?page=3", () -> new MockResponse().setResponseCode(503));
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
        assertThat(matchCards()).containsExactly(NEWER, ARCHIVE_PAGE_TWO_DUEL, MIDDLE, OLDER);
        assertThat(visibleText()).contains("Yenidən cəhd et");
    }

    @Test public void keepsVerifiedMatchesWhenALaterTournamentPageFails() throws Exception {
        // arrange
        publish(website);
        HeadToHeadFixtures.publishTournamentArchive(website);
        responses.put("/matches/results/event/912?page=2", () -> new MockResponse().setResponseCode(503));
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
        assertThat(visibleText()).doesNotContain("Qələbə sayı", "Matç tapılmadı");
        assertThat(matchCards()).containsExactly(ARCHIVE_TOURNAMENT, NEWER, ARCHIVE_PAGE_TWO_DUEL, MIDDLE,
                ARCHIVE_FINAL_DUEL, OLDER);
    }

    @Test public void showsAnEmptyScoreOnlyAfterTheSearchCompletes() throws Exception {
        // arrange
        HeadToHeadFixtures.publishEmptySingles(website);
        CountDownLatch firstPage = new CountDownLatch(1);
        responses.put("/matches/results/player/13", () -> awaitPage(firstPage, new MockResponse().setResponseCode(503)));
        openScreen();

        // act
        clickText("☰");
        clickText("Üz-üzə");
        followNavigation();
        pickPlayer(R.id.head_to_head_first, "Səf", ASHRAF);
        pickPlayer(R.id.head_to_head_second, "İlq", ILQAR);

        // act & assert
        try {
            clickText("Axtar");
            assertThat(visibleText()).contains("Axtarış davam edir").doesNotContain("Qələbə sayı", "0–0", "Matç tapılmadı");
        } finally {
            firstPage.countDown();
        }

        // assert
        awaitText("Axtarış tamamlanmadı");
        assertThat(visibleText()).doesNotContain("Qələbə sayı", "0–0", "Matç tapılmadı");

        // arrange
        responses.remove("/matches/results/player/13");

        // act
        clickText("Yenidən cəhd et");

        // assert
        awaitText("Matç tapılmadı");
        assertThat(visibleText()).containsSubsequence("Qələbə sayı", "0–0", "Matç tapılmadı");
        assertThat(matchCards()).isEmpty();
    }

    @Test public void doublesSearchFindsTheSameMeetingsAcrossTeammateAndSideOrders() throws Exception {
        // arrange
        HeadToHeadFixtures.publishDoubles(website);
        HeadToHeadFixtures.publishTournamentArchive(website);
        CountDownLatch laterPage = new CountDownLatch(1);
        responses.put("/matches/results/player/7?page=2", () -> awaitPage(laterPage, new MockResponse().setResponseCode(503)));
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
            assertThat(visibleText()).contains("Axtarış davam edir").doesNotContain(DOUBLES_NEWER, "Qələbə sayı", "Matç tapılmadı");
        } finally {
            laterPage.countDown();
        }

        // assert
        awaitText("Axtarış tamamlanmadı");
        assertThat(matchCards()).containsExactly(DOUBLES);

        // arrange
        CountDownLatch retryPage = new CountDownLatch(1);
        CountDownLatch tournamentArchive = new CountDownLatch(1);
        responses.put("/matches/results/player/7?page=2", () -> awaitPage(retryPage, new MockResponse().setBody(laterDoublesPage())));
        String teamPage = "/matches/results/event/912?page=2";
        responses.put(teamPage, () -> awaitPage(tournamentArchive, new MockResponse().setBody(website.get(teamPage))));

        // act & assert
        try {
            clickText("Yenidən cəhd et");
            assertThat(visibleText()).contains("Axtarış davam edir");
            assertThat(matchCards()).containsExactly(DOUBLES);
            retryPage.countDown();
            awaitText(DOUBLES_NEWER);
            assertThat(visibleText()).contains("Axtarış davam edir").doesNotContain("Qələbə sayı");
            assertThat(matchCards()).containsExactly(DOUBLES_NEWER, DOUBLES);
        } finally {
            retryPage.countDown();
            tournamentArchive.countDown();
        }

        // assert
        awaitText("Axtarış tamamlandı");
        assertThat(visibleText()).containsSequence("Qələbə sayı", "2–1", ERHAN + "\n" + ELCHIN, ALIKHAN + "\n" + KHAYAL);
        assertThat(matchCards()).containsExactly(DOUBLES_NEWER, DOUBLES_TEAM_GAME, DOUBLES);

        // act
        pickPlayer(R.id.head_to_head_left_first, "Elç", ELCHIN);
        pickPlayer(R.id.head_to_head_left_second, "Erh", ERHAN);
        clickText("Axtar");

        // assert
        awaitText("Axtarış tamamlandı");
        assertThat(visibleText()).containsSequence("Qələbə sayı", "2–1", ELCHIN + "\n" + ERHAN, ALIKHAN + "\n" + KHAYAL);
        assertThat(matchCards()).containsExactly(DOUBLES_NEWER, DOUBLES_TEAM_GAME, DOUBLES);

        // act
        pickPlayer(R.id.head_to_head_left_first, "Əli", ALIKHAN);
        pickPlayer(R.id.head_to_head_left_second, "Xəy", KHAYAL);
        pickPlayer(R.id.head_to_head_right_first, "Erh", ERHAN);
        pickPlayer(R.id.head_to_head_right_second, "Elç", ELCHIN);
        clickText("Axtar");

        // assert
        awaitText("Axtarış tamamlandı");
        assertThat(visibleText()).containsSequence("Qələbə sayı", "1–2", ALIKHAN + "\n" + KHAYAL, ERHAN + "\n" + ELCHIN);
        assertThat(matchCards()).containsExactly(DOUBLES_NEWER, DOUBLES_TEAM_GAME, DOUBLES);
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
        assertThat(matchCards()).containsExactly(HISTORICAL);

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
        assertThat(visibleText()).containsSequence("Qələbə sayı", "1–0", KHAYAL + "\n" + FIRDOVSI, VAQIF + "\n" + SHAKIR);
        assertThat(matchCards()).containsExactly(HISTORICAL);
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

    private ImageView suggestionPortrait(String label) {
        View suggestion = findText(activity().getWindow().getDecorView(), label);
        return ((View) suggestion.getParent()).findViewById(android.R.id.icon);
    }

    private void awaitPortrait(String label) throws InterruptedException {
        for (int attempt = 0; attempt < 250; attempt++) {
            shadowOf(Looper.getMainLooper()).idle();
            if (suggestionPortrait(label).getDrawable() instanceof BitmapDrawable) break;
            Thread.sleep(20);
        }
        assertThat(suggestionPortrait(label).getDrawable()).isInstanceOfSatisfying(BitmapDrawable.class,
                image -> assertThat(image.getBitmap().getPixel(12, 12)).isEqualTo(Color.BLUE));
    }

    private MockResponse awaitPage(CountDownLatch release, MockResponse response) {
        try {
            if (release.await(15, TimeUnit.SECONDS)) return response;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
        return new MockResponse().setResponseCode(503);
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

    private List<String> matchCards() {
        return visibleText().stream()
                .filter(text -> text.contains(" — ") && (text.endsWith(" · Duel") || text.endsWith(" · Turnir")))
                .toList();
    }

}
