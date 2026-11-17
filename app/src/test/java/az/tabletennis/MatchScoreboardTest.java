package az.tabletennis;

import static org.assertj.core.api.Assertions.assertThat;

import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.util.Arrays;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.ParameterizedRobolectricTestRunner;
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters;
import org.robolectric.annotation.Config;

@RunWith(ParameterizedRobolectricTestRunner.class)
@Config(sdk = 26, qualifiers = "w411dp-h891dp", application = RankingsUiScenario.FixtureApplication.class)
public class MatchScoreboardTest extends ProductUiScenario {
    private final String fixture;
    private final String leftScore;
    private final String rightScore;
    private final int winner;
    private final String kind;
    private final String date;

    public MatchScoreboardTest(String fixture, String leftScore, String rightScore, int winner, String kind, String date) {
        this.fixture = fixture;
        this.leftScore = leftScore;
        this.rightScore = rightScore;
        this.winner = winner;
        this.kind = kind;
        this.date = date;
    }

    @Parameters(name = "{0}: {1}-{2}") public static Collection<Object[]> outcomes() {
        return Arrays.asList(new Object[][] {
                {"singles-detail.html", "1", "3", R.id.participants_right, "Tək", "16 Nov. 2026"},
                {"doubles-detail.html", "3", "2", R.id.participants_left, "Qoşalar", "15 Nov. 2026 · 18:00"},
                {"singles-detail.html", "2", "2", 0, "Tək", "16 Nov. 2026"}
        });
    }

    @Test public void scoreboardAttributesEachScoreAndOnlyLabelsTheRecordedWinner() throws Exception {
        // arrange
        org.jsoup.nodes.Document source = org.jsoup.Jsoup.parse(html(fixture).getBody().readUtf8());
        source.select(".match-player-score").get(0).text(leftScore);
        source.select(".match-player-score").get(1).text(rightScore);
        website.put("/matches/501", source.outerHtml());

        // act
        openDirect(MatchActivity.class, "matchId", 501);

        // assert
        awaitText("İlqar Həsənov");
        ViewGroup left = activity().findViewById(R.id.participants_left);
        ViewGroup right = activity().findViewById(R.id.participants_right);
        assertThat(((TextView) activity().findViewById(R.id.match_score_left)).getText().toString()).isEqualTo(leftScore);
        assertThat(((TextView) activity().findViewById(R.id.match_score_right)).getText().toString()).isEqualTo(rightScore);
        assertThat(findText(left, "İlqar Həsənov")).isNotNull();
        assertThat(findText(right, "Şəms Quliyeva")).isNotNull();
        assertThat(findText((View) left.getParent(), "Qalib") != null).isEqualTo(winner == R.id.participants_left);
        assertThat(findText((View) right.getParent(), "Qalib") != null).isEqualTo(winner == R.id.participants_right);
        assertThat(visibleText()).contains(kind, date);
        assertThat(activity().findViewById(R.id.refresh).getContentDescription().toString()).isEqualTo("Yenilə");
    }
}
