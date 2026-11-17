package az.tabletennis;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 26, qualifiers = "w411dp-h891dp", application = RankingsUiScenario.FixtureApplication.class)
public class PlayerHistoryPagingTest extends ProductUiScenario {
    @Test public void historyReadsAllContainersVerifiesSameNamedTeamParticipantsAndKeepsPagingAndDetailsOffline() throws Exception {
        // arrange
        prepareProfile(12, "İlqar Həsənov");
        website.put("/matches/results/player/12", html("team-history.html").getBody().readUtf8());
        website.put("/matches/results/player/12?page=2", html("history.html").getBody().readUtf8()
                .replace("15 Nov. 2026", "13 Nov. 2026").replace("14 Nov. 2026", "12 Nov. 2026").replace("/matches/502", "/matches/505"));
        String detail = html("singles-detail.html").getBody().readUtf8().replace("16 Nov. 2026", "15 Nov. 2026").replace("Duello", "Komanda Liqası");
        website.put("/matches/601", detail);
        website.put("/matches/603", detail.replace("/players/12", "/players/99"));

        // act
        openDirect(ProfileActivity.class, "playerId", 12);

        // assert
        awaitText("15 Nov. 2026 · İlqar Həsənov — Şəms Quliyeva · 3-1 · Komanda Liqası · Tək · Turnir");
        assertThat(visibleText().stream().filter(text -> text.endsWith("· Komanda Liqası · Tək · Turnir"))).hasSize(1);
        assertThat(visibleText()).contains("14 Nov. 2026 · İlqar Həsənov — Şəms Quliyeva · 3-1 · Liqa · Tək · Turnir");
        assertThat(requestedPaths()).contains("/matches/601", "/matches/603").doesNotContain("/matches/604");
        String updated = ((android.widget.TextView) activity().findViewById(R.id.updated)).getText().toString();

        // arrange
        FixtureApplication.time = java.time.Clock.offset(FixtureApplication.time, java.time.Duration.ofMinutes(2));

        // act
        clickText("Daha çox yüklə");

        // assert
        awaitText("13 Nov. 2026 · İlqar Həsənov / Əli Məmmədov — Şəms Quliyeva / Işıl Əliyeva · 3-2 · Kupa · Qoşalar · Turnir");
        assertThat(visibleText()).contains(updated, "15 Nov. 2026 · İlqar Həsənov — Şəms Quliyeva · 3-1 · Komanda Liqası · Tək · Turnir");
        assertThat(requestedPaths()).containsExactly("/matches/results/player/12?page=2");

        // act
        recreateProductScreen();
        activity().findViewById(R.id.refresh).performClick();

        // assert
        awaitText("Məlumat yeniləndi.");
        assertThat(visibleText()).contains("13 Nov. 2026 · İlqar Həsənov / Əli Məmmədov — Şəms Quliyeva / Işıl Əliyeva · 3-2 · Kupa · Qoşalar · Turnir");
        assertThat(requestedPaths()).contains("/matches/results/player/12", "/matches/results/player/12?page=2");
        updated = ((android.widget.TextView) activity().findViewById(R.id.updated)).getText().toString();

        // arrange
        closeProductScreens();
        FixtureApplication.online = false;
        server.shutdown();

        // act
        openDirect(ProfileActivity.class, "playerId", 12);

        // assert
        assertThat(visibleText()).contains(updated, "13 Nov. 2026 · İlqar Həsənov / Əli Məmmədov — Şəms Quliyeva / Işıl Əliyeva · 3-2 · Kupa · Qoşalar · Turnir");

        // act
        clickText("15 Nov. 2026 · İlqar Həsənov — Şəms Quliyeva · 3-1 · Komanda Liqası · Tək · Turnir");
        followNavigation();

        // assert
        assertThat(visibleText()).contains("Matç haqqında", "3", "1", "15 Nov. 2026");
    }
}
