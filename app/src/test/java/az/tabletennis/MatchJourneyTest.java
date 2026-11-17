package az.tabletennis;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 26, qualifiers = "w411dp-h891dp", application = RankingsUiScenario.FixtureApplication.class)
public class MatchJourneyTest extends ProductUiScenario {
    @Test public void doublesHistoryOpensAllFourIdentitiesAndBackRestoresTheOriginOffline() throws Exception {
        // arrange
        prepareProfile(12, "İlqar Həsənov");
        prepareProfile(13, "Əli Məmmədov");
        prepareProfile(11, "Şəms Quliyeva");
        prepareProfile(14, "Işıl Əliyeva");
        website.put("/matches/501", html("doubles-detail.html").getBody().readUtf8());
        openScreen();

        // assert
        awaitText("1. İlqar Həsənov — 800 xal");

        // act
        clickText("1. İlqar Həsənov — 800 xal");
        followNavigation();

        // assert
        awaitText("800 xal");

        // act
        clickText("15 Nov. 2026 · İlqar Həsənov / Əli Məmmədov — Şəms Quliyeva / Işıl Əliyeva · 3-2 · Kupa · Qoşalar · Turnir");
        followNavigation();

        // assert
        awaitText("Matç haqqında");
        awaitText("3-2");
        assertThat(visibleText()).contains("15 Nov. 2026", "18:00", "İlqar Həsənov", "Əli Məmmədov", "Şəms Quliyeva", "Işıl Əliyeva");
        String updated = ((android.widget.TextView) activity().findViewById(R.id.updated)).getText().toString();

        for (String name : new String[] {"İlqar Həsənov", "Əli Məmmədov", "Şəms Quliyeva", "Işıl Əliyeva"}) {
            // act
            clickText(name);
            followNavigation();

            // assert
            awaitText(name);
            awaitText("Matç tarixçəsi");

            // act
            back();

            // assert
            assertThat(visibleText()).contains("Matç haqqında", "3-2");
        }

        // arrange
        server.shutdown();

        // act
        restart();

        // assert
        awaitText("1. İlqar Həsənov — 800 xal");

        // act
        clickText("1. İlqar Həsənov — 800 xal");
        followNavigation();
        clickText("15 Nov. 2026 · İlqar Həsənov / Əli Məmmədov — Şəms Quliyeva / Işıl Əliyeva · 3-2 · Kupa · Qoşalar · Turnir");
        followNavigation();

        // assert
        assertThat(visibleText()).contains("Matç haqqında", "3-2", updated, "Işıl Əliyeva");

        // act
        back();

        // assert
        assertThat(visibleText()).contains("İlqar Həsənov", "Matç tarixçəsi");
    }
}
