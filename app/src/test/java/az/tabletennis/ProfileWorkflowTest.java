package az.tabletennis;

import static org.assertj.core.api.Assertions.assertThat;

import android.widget.EditText;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 26, qualifiers = "w411dp-h891dp", application = RankingsUiScenario.FixtureApplication.class)
public class ProfileWorkflowTest extends ProductUiScenario {
    @Test public void searchOpensTheSelectedIdentityWithCurrentPointsAndBothHistorySides() throws Exception {
        // arrange
        website.put("/players/12", "<div id='center'><div class='player-block'><div class='name-div'>İlqar Həsənov</div></div>"
                + "<div id='chart-user-id'>12</div></div>");
        website.put("/matches/results/player/12", html("history.html").getBody().readUtf8());
        openScreen();

        // assert
        awaitText("2. İlqar Həsənov — 800 xal");

        // act
        ((EditText) activity().findViewById(R.id.search)).setText("ilqar");
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        clickText("2. İlqar Həsənov — 800 xal");
        followNavigation();

        // assert
        awaitText("İlqar Həsənov");
        awaitText("800 xal");
        awaitText("15 Nov. 2026 · İlqar Həsənov / Əli Məmmədov — Şəms Quliyeva / Işıl Əliyeva · 3-2 · Kupa");
        assertThat(visibleText()).contains("14 Nov. 2026 · İlqar Həsənov — Şəms Quliyeva · 3-1 · Liqa");

        // act
        back();

        // assert
        assertThat(((EditText) activity().findViewById(R.id.search)).getText().toString()).isEqualTo("ilqar");
    }

    @Test public void sameNamedPlayersKeepTheirOwnPointsAndHistoryAcrossAnOfflineRestart() throws Exception {
        // arrange
        website.replaceAll((path, body) -> body.replace("Şəms Quliyeva", "Eyni Ad").replace("İlqar Həsənov", "Eyni Ad"));
        prepareProfile(11, "Eyni Ad");
        prepareProfile(12, "Eyni Ad");
        website.put("/matches/results/player/11", html("history.html").getBody().readUtf8().replace("Kupa", "Oyunçu 11"));
        website.put("/matches/results/player/12", html("history.html").getBody().readUtf8().replace("Kupa", "Oyunçu 12"));
        openScreen();

        // assert
        awaitText("1. Eyni Ad — 950 xal");

        // act
        clickText("1. Eyni Ad — 950 xal");
        followNavigation();

        // assert
        awaitText("950 xal");
        assertThat(visibleText()).anyMatch(text -> text.endsWith("· Oyunçu 11"));

        // act
        back();
        clickText("2. Eyni Ad — 800 xal");
        followNavigation();

        // assert
        awaitText("800 xal");
        assertThat(visibleText()).anyMatch(text -> text.endsWith("· Oyunçu 12"));
        String updated = ((android.widget.TextView) activity().findViewById(R.id.updated)).getText().toString();

        // arrange
        server.shutdown();

        // act
        restart();

        // assert
        awaitText("2. Eyni Ad — 800 xal");

        // act
        clickText("2. Eyni Ad — 800 xal");
        followNavigation();

        // assert
        assertThat(visibleText()).contains("Eyni Ad", "800 xal", updated)
                .anyMatch(text -> text.endsWith("· Oyunçu 12")).noneMatch(text -> text.endsWith("· Oyunçu 11"));

        // act
        back();
        clickText("1. Eyni Ad — 950 xal");
        followNavigation();

        // assert
        assertThat(visibleText()).contains("Eyni Ad", "950 xal").anyMatch(text -> text.endsWith("· Oyunçu 11"));
    }
}
