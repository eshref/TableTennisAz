package az.tabletennis;

import static org.assertj.core.api.Assertions.assertThat;

import android.graphics.Color;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import java.time.Clock;
import java.time.Duration;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 26, qualifiers = "w411dp-h891dp", application = RankingsUiScenario.FixtureApplication.class)
public class DoublesMovementTest extends DoublesUiScenario {
    @Test public void legacyRankingRemainsReadableAndComparisonStartsWithActualNewObservations() throws Exception {
        // arrange
        prepareDoubles();
        try (var input = getClass().getResourceAsStream("/fixtures/legacy-doubles-ranking");
             var output = org.robolectric.RuntimeEnvironment.getApplication()
                     .openFileOutput("saved-doubles-ranking", android.content.Context.MODE_PRIVATE)) {
            input.transferTo(output);
        }
        FixtureApplication.online = false;

        // act
        openScreen();
        clickText("Qoşalar");

        // assert
        awaitText("2. İlqar Həsənov / Əli Məmmədov — 800 xal");
        assertThat(visibleText()).noneMatch(text -> text.contains("pillə yüksəlib") || text.contains("pillə geriləyib"));

        // arrange
        FixtureApplication.online = true;
        ranking(pairRow(110, 9, 800));

        // act
        screen.pause().resume().visible();
        activity().findViewById(R.id.refresh).performClick();

        // assert
        awaitText("9. İlqar Həsənov / Əli Məmmədov — 800 xal");
        assertThat(visibleText()).noneMatch(text -> text.contains("pillə yüksəlib") || text.contains("pillə geriləyib"));

        // arrange
        ranking(pairRow(110, 11, 800));

        // act
        activity().findViewById(R.id.refresh).performClick();

        // assert
        awaitText("11. İlqar Həsənov / Əli Məmmədov — 800 xal, 2 pillə geriləyib");
    }

    @Test public void failedPersistenceKeepsTheSavedComparisonAndTheNextRefreshUsesItsSuccessfulBaseline() throws Exception {
        // arrange
        prepareDoubles();
        ranking(pairRow(110, 12, 800));

        // act
        openScreen();
        clickText("Qoşalar");

        // assert
        awaitText("12. İlqar Həsənov / Əli Məmmədov — 800 xal");

        // arrange
        ranking(pairRow(110, 9, 800));

        // act
        activity().findViewById(R.id.refresh).performClick();

        // assert
        awaitText("9. İlqar Həsənov / Əli Məmmədov — 800 xal, 3 pillə yüksəlib");
        String updated = ((TextView) activity().findViewById(R.id.updated)).getText().toString();

        // arrange
        ranking(pairRow(110, 5, 900));
        FixtureApplication.time = Clock.offset(FixtureApplication.time, Duration.ofMinutes(2));
        java.io.File directory = org.robolectric.RuntimeEnvironment.getApplication().getFilesDir();
        java.io.File saved = new java.io.File(directory, "saved-doubles-ranking");
        if (!saved.setWritable(false, false) || !directory.setWritable(false, false)) {
            throw new IllegalStateException("Could not prepare read-only saved storage");
        }
        try {
            // act
            activity().findViewById(R.id.refresh).performClick();

            // assert
            awaitText("Yeniləmək mümkün olmadı. Saxlanmış məlumat göstərilir.");
            assertThat(visibleText()).contains(updated, "9. İlqar Həsənov / Əli Məmmədov — 800 xal, 3 pillə yüksəlib");
        } finally {
            directory.setWritable(true, true);
            saved.setWritable(true, true);
        }

        // arrange
        FixtureApplication.online = false;

        // act
        restart();
        clickText("Qoşalar");

        // assert
        awaitText("9. İlqar Həsənov / Əli Məmmədov — 800 xal, 3 pillə yüksəlib");
        assertThat(visibleText()).contains(updated);

        // arrange
        FixtureApplication.online = true;
        ranking(pairRow(110, 11, 800));

        // act
        screen.pause().resume().visible();
        activity().findViewById(R.id.refresh).performClick();

        // assert
        awaitText("11. İlqar Həsənov / Əli Məmmədov — 800 xal, 2 pillə geriləyib");
    }

    @Test public void successfulRefreshesReplaceMovementAndMissingPreviousPairsNeverUseOlderPositions() throws Exception {
        // arrange
        prepareDoubles();
        ranking(pairRow(220, 9, 800) + pairRow(110, 12, 800) + pairRow(330, 15, 600));

        // act
        openScreen();
        clickText("Qoşalar");

        // assert
        awaitText("12. İlqar Həsənov / Əli Məmmədov — 800 xal");
        assertThat(visibleText()).noneMatch(text -> text.contains("pillə yüksəlib") || text.contains("pillə geriləyib"));

        // arrange
        ranking(pairRow(110, 9, 800) + pairRow(220, 11, 800) + pairRow(330, 15, 600));
        FixtureApplication.time = Clock.offset(FixtureApplication.time, Duration.ofMinutes(2));

        // act
        activity().findViewById(R.id.refresh).performClick();

        // assert
        awaitText("9. İlqar Həsənov / Əli Məmmədov — 800 xal, 3 pillə yüksəlib");
        assertThat(visibleText()).contains("11. İlqar Həsənov / Şəms Quliyeva — 800 xal, 2 pillə geriləyib",
                "15. Işıl Əliyeva / Əli Məmmədov — 600 xal");
        ListView list = activity().findViewById(android.R.id.list);
        android.view.View up = findText(list, "9. İlqar Həsənov / Əli Məmmədov — 800 xal, 3 pillə yüksəlib");
        android.view.View down = findText(list, "11. İlqar Həsənov / Şəms Quliyeva — 800 xal, 2 pillə geriləyib");
        assertThat(up.getContentDescription().toString()).contains("3 pillə yüksəlib");
        assertThat(down.getContentDescription().toString()).contains("2 pillə geriləyib");
        TextView upValue = (TextView) findText(up, "↑ 3");
        TextView downValue = (TextView) findText(down, "↓ 2");
        assertThat(upValue.getCurrentTextColor()).isEqualTo(Color.rgb(24, 115, 58));
        assertThat(downValue.getCurrentTextColor()).isEqualTo(Color.rgb(179, 38, 30));
        assertThat(upValue.getTextSize()).isLessThan(((TextView) up.findViewById(android.R.id.text2)).getTextSize());
        String updated = ((TextView) activity().findViewById(R.id.updated)).getText().toString();

        // act
        ((EditText) activity().findViewById(R.id.search)).setText("məmmədov");
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(100));

        // assert
        awaitText("9. İlqar Həsənov / Əli Məmmədov — 800 xal, 3 pillə yüksəlib");
        assertThat(visibleText()).doesNotContain("11. İlqar Həsənov / Şəms Quliyeva — 800 xal, 2 pillə geriləyib");

        // act
        ((EditText) activity().findViewById(R.id.search)).setText("ilqar");
        screen.recreate();

        // assert
        awaitText("9. İlqar Həsənov / Əli Məmmədov — 800 xal, 3 pillə yüksəlib");
        assertThat(visibleText()).contains(updated, "11. İlqar Həsənov / Şəms Quliyeva — 800 xal, 2 pillə geriləyib");

        // arrange
        FixtureApplication.online = false;
        FixtureApplication.time = Clock.offset(FixtureApplication.time, Duration.ofDays(365));

        // act
        restart();
        clickText("Qoşalar");

        // assert
        awaitText("9. İlqar Həsənov / Əli Məmmədov — 800 xal, 3 pillə yüksəlib");
        assertThat(visibleText()).contains(updated, "11. İlqar Həsənov / Şəms Quliyeva — 800 xal, 2 pillə geriləyib");
        assertThat(activity().findViewById(R.id.refresh).isEnabled()).isFalse();

        // arrange
        FixtureApplication.online = true;
        ranking(pairRow(220, 11, 800) + pairRow(330, 15, 600));

        // act
        screen.pause().resume().visible();

        // assert
        awaitText("11. İlqar Həsənov / Şəms Quliyeva — 800 xal");
        assertThat(visibleText()).noneMatch(text -> text.contains("pillə yüksəlib") || text.contains("pillə geriləyib"));
        assertThat(visibleText()).doesNotContain("9. İlqar Həsənov / Əli Məmmədov — 800 xal, 3 pillə yüksəlib");

        // arrange
        ranking(pairRow(110, 4, 800) + pairRow(220, 11, 800) + pairRow(330, 15, 600));

        // act
        activity().findViewById(R.id.refresh).performClick();

        // assert
        awaitText("4. İlqar Həsənov / Əli Məmmədov — 800 xal");
        assertThat(visibleText()).noneMatch(text -> text.contains("pillə yüksəlib") || text.contains("pillə geriləyib"));

        // arrange
        ranking(pairRow(110, 3, 800) + pairRow(220, 11, 800) + pairRow(330, 15, 600));

        // act
        activity().findViewById(R.id.refresh).performClick();

        // assert
        awaitText("3. İlqar Həsənov / Əli Məmmədov — 800 xal, 1 pillə yüksəlib");

        // act
        activity().findViewById(R.id.refresh).performClick();

        // assert
        awaitText("3. İlqar Həsənov / Əli Məmmədov — 800 xal");
        assertThat(visibleText()).noneMatch(text -> text.contains("pillə yüksəlib") || text.contains("pillə geriləyib"));
    }

    private void ranking(String rows) {
        website.put("/players/duos", "<div id='center'><div class='ranking-page-list'>" + rows + "</div></div>");
    }
}
