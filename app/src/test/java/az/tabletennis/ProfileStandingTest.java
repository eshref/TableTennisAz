package az.tabletennis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.ParameterizedRobolectricTestRunner;
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters;
import org.robolectric.annotation.Config;

@RunWith(ParameterizedRobolectricTestRunner.class)
@Config(sdk = 26, qualifiers = "w411dp-h891dp", application = RankingsUiScenario.FixtureApplication.class)
public class ProfileStandingTest extends ProductUiScenario {
    private final long id;
    private final String name;
    private final String points;
    private final String position;
    private final String membership;

    public ProfileStandingTest(long id, String name, String points, String position, String membership) {
        this.id = id;
        this.name = name;
        this.points = points;
        this.position = position;
        this.membership = membership;
    }

    @Parameters(name = "{1}") public static Collection<Object[]> standings() {
        return Arrays.asList(new Object[][] {
                {12L, "İlqar Həsənov", "800 xal", "#1", "Həvəskar oyunçu"},
                {13L, "Əli Məmmədov", "800 xal", "#1", "Həvəskar oyunçu"},
                {14L, "Işıl Əliyeva", "600 xal", "#3", "Həvəskar oyunçu"},
                {11L, "Şəms Quliyeva", "950 xal", "Mövqe mövcud deyil", "Oyunçu"},
                {99L, "Reytinqdən kənar oyunçu", "Cari xal məlumatı mövcud deyil.", "Mövqe mövcud deyil", "Oyunçu"}
        });
    }

    @Test public void profileUsesSourcePointsAndOnlyKnownAmateurCompetitionPositions() throws Exception {
        // arrange
        prepareProfile(id, name);
        website.put("/matches/results/player/" + id,
                "<div id='center'><div class='matches-box'><div class='no-info'>Matç yoxdur</div></div></div>");

        // act
        openDirect(ProfileActivity.class, "playerId", id);

        // assert
        awaitText(name);
        assertThat(visibleText()).contains(points, position, membership, "Xal tarixçəsi", "Matç tarixçəsi yoxdur.");
        assertThat(visibleText()).doesNotContain("#2", "#4");
        assertThat((android.view.View) activity().findViewById(android.R.id.icon)).isInstanceOf(PlayerPortraitView.class);
    }
}
