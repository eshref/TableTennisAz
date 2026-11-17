package az.tabletennis;

import static org.assertj.core.api.Assertions.assertThat;

import android.view.View;
import android.widget.TextView;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 26, qualifiers = "w411dp-h891dp", application = RankingsUiScenario.FixtureApplication.class)
public class RatingHistoryTest extends ProductUiScenario {
    @Test public void absentSourceObservationsAreSavedAsNoHistoryInsteadOfFabricatedPoints() throws Exception {
        // arrange
        website.put("/api/getPlayerPoints?id=12", "[]");

        // act
        openDirect(ChartActivity.class, "playerId", 12);

        // assert
        awaitText("Xal tarixçəsi mövcud deyil.");
        assertThat(activity().<android.view.View>findViewById(R.id.chart)).isNull();
        assertThat(((android.widget.TextView) activity().findViewById(R.id.updated)).getText().toString()).startsWith("Son uğurlu yenilənmə: ");
    }

    @Test public void oneSourceObservationShowsItsValueWithoutClaimingAChange() throws Exception {
        // arrange
        website.put("/api/getPlayerPoints?id=12", "[{\"day\":\"31.12\",\"point\":435.55002}]");

        // act
        openDirect(ChartActivity.class, "playerId", 12);

        // assert
        awaitText("31.12 — 435.55 xal");
        assertThat(visibleText()).contains("Mənbədə yalnız bir xal müşahidəsi var. Dəyişmə göstərilmir.");
        assertThat(activity().findViewById(R.id.chart).getContentDescription().toString()).isEqualTo("31.12 — 435.55 xal");
        assertThat(findText(activity().findViewById(R.id.chart_observations), "31.12 — 435.55 xal").isSelected()).isTrue();
    }

    @Test public void inspectingObservationsRetainsSelectionAcrossRecreationWithoutDownloadingOrChangingUpdateTime() throws Exception {
        // arrange
        website.put("/api/getPlayerPoints?id=12", "[{\"day\":\"31.12\",\"point\":435.55002},{\"day\":\"01.01\",\"point\":433.65002}]");

        // act
        openDirect(ChartActivity.class, "playerId", 12);

        // assert
        awaitText("31.12 — 435.55 xal");
        String updated = ((TextView) activity().findViewById(R.id.updated)).getText().toString();
        requestedPaths();

        // act
        clickText("31.12 — 435.55 xal");
        recreateProductScreen();

        // assert
        View selected = findText(activity().getWindow().getDecorView(), "31.12 — 435.55 xal");
        assertThat(selected.isSelected()).isTrue();
        assertThat(((TextView) activity().findViewById(R.id.chart_selected_date)).getText().toString()).isEqualTo("31.12");
        assertThat(((TextView) activity().findViewById(R.id.chart_selected_value)).getText().toString()).isEqualTo("435.55 xal");
        assertThat(((TextView) activity().findViewById(R.id.updated)).getText().toString()).isEqualTo(updated);
        assertThat(requestedPaths()).isEmpty();
    }

    @Test public void selectedPlayersPointsKeepSourceLabelsAndOrderWithoutInventedYearsAndRemainOffline() throws Exception {
        // arrange
        prepareProfile(12, "İlqar Həsənov");
        website.put("/api/getPlayerPoints?id=12", "[{\"day\":\"31.12\",\"point\":435.55002},{\"day\":\"01.01\",\"point\":433.65002}]");
        openScreen();

        // assert
        awaitText("1. İlqar Həsənov — 800 xal");

        // act
        clickText("1. İlqar Həsənov — 800 xal");
        followNavigation();

        // assert
        awaitText("800 xal");

        // act
        clickText("Xal tarixçəsi");
        followNavigation();

        // assert
        awaitText("31.12 — 435.55 xal");
        assertThat(visibleText()).containsSubsequence("31.12 — 435.55 xal", "01.01 — 433.65 xal")
                .noneMatch(text -> text.contains("435.55002") || text.contains("800 xal"));
        assertThat(activity().findViewById(R.id.chart).getContentDescription().toString())
                .contains("31.12 — 435.55 xal", "01.01 — 433.65 xal");
        String updated = ((android.widget.TextView) activity().findViewById(R.id.updated)).getText().toString();

        // arrange
        server.shutdown();

        // act
        restart();

        // assert
        awaitText("1. İlqar Həsənov — 800 xal");

        // act
        clickText("1. İlqar Həsənov — 800 xal");
        followNavigation();
        clickText("Xal tarixçəsi");
        followNavigation();

        // assert
        assertThat(visibleText()).contains("31.12 — 435.55 xal", "01.01 — 433.65 xal", updated);
    }
}
