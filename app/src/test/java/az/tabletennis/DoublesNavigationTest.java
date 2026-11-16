package az.tabletennis;

import static org.assertj.core.api.Assertions.assertThat;

import android.widget.EditText;
import android.widget.ListView;
import java.time.Duration;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 26, qualifiers = "w411dp-h891dp", application = RankingsUiScenario.FixtureApplication.class)
public class DoublesNavigationTest extends DoublesUiScenario {
    @Test public void returningFromHistoryAndRecreatingRankingsPreservesTheScrolledSearchAndBothRankingSelections() throws Exception {
        // arrange
        StringBuilder rows = new StringBuilder();
        for (int i = 1; i <= 25; i++) {
            rows.append(pairRow(i, i, 900 - i));
            website.put("/api/getDuoInfo?id=" + i, pairInfo(i, 12, "İlqar Həsənov", 100 + i, "Tərəfdaş " + i));
        }
        website.put("/players/duos", "<div id='center'><div class='ranking-page-list'>" + rows + "</div></div>");
        website.put("/matches/results/duo/14", pairHistory("<div class='no-info'>Matç yoxdur</div>"));
        openScreen();

        // assert
        awaitText("1. İlqar Həsənov — 800 xal");

        // act
        ((EditText) activity().findViewById(R.id.search)).setText("ışıl");
        clickText("Qoşalar");

        // assert
        awaitText("1. İlqar Həsənov / Tərəfdaş 1 — 899 xal");

        // act
        ((EditText) activity().findViewById(R.id.search)).setText("ilqar");
        ListView list = activity().findViewById(android.R.id.list);
        list.setSelectionFromTop(12, -11);
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(100));

        // assert
        assertThat(list.getFirstVisiblePosition()).isEqualTo(12);
        int top = list.getChildAt(0).getTop();

        // act
        clickText("14. İlqar Həsənov / Tərəfdaş 14 — 886 xal");
        screen.pause().stop();
        followNavigation();

        // assert
        awaitText("Matç tarixçəsi yoxdur.");

        // act
        back();
        screen.restart().start().resume().visible();
        screen.recreate();
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(100));

        // assert
        ListView restored = activity().findViewById(android.R.id.list);
        assertThat(restored.getFirstVisiblePosition()).isEqualTo(12);
        assertThat(restored.getChildAt(0).getTop()).isEqualTo(top);
        assertThat(((EditText) activity().findViewById(R.id.search)).getText().toString()).isEqualTo("ilqar");

        // act
        clickText("Oyunçular");
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(100));

        // assert
        assertThat(((EditText) activity().findViewById(R.id.search)).getText().toString()).isEqualTo("ışıl");
        assertThat(visibleText()).contains("3. Işıl Əliyeva — 600 xal").doesNotContain("1. İlqar Həsənov — 800 xal");

        // act
        clickText("Qoşalar");
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(100));

        // assert
        assertThat(((EditText) activity().findViewById(R.id.search)).getText().toString()).isEqualTo("ilqar");
        assertThat(restored.getFirstVisiblePosition()).isEqualTo(12);
    }
}
