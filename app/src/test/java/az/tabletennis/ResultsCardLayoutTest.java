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
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

@RunWith(ParameterizedRobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = {26, 36}, application = RankingsUiScenario.FixtureApplication.class)
public class ResultsCardLayoutTest extends ProductUiScenario {
    private static final String FIRST = "Əbdürrəhman Məmmədəmin Azərbaycanlı Həsənov";
    private static final String SECOND = "Məryəm Əbdülrəhim Azərbaycanlı İmanova";
    private final String qualifiers;

    public ResultsCardLayoutTest(String qualifiers) { this.qualifiers = qualifiers; }

    @Parameters(name = "{0}") public static Collection<Object[]> layouts() {
        return Arrays.asList(new Object[][] {
                {"w320dp-h568dp-notnight"}, {"w320dp-h568dp-night"},
                {"w891dp-h320dp-land-notnight"}, {"w891dp-h320dp-land-night"}
        });
    }

    @Test public void largeTextKeepsFullDoublesNamesSeparateFromScoresAndFiltersReachable() throws Exception {
        // arrange
        RuntimeEnvironment.setQualifiers(qualifiers);
        RuntimeEnvironment.setFontScale(1.5f);
        website.put("/matches/results", "<div id='center'><div class='tournament-list'></div></div>");
        website.put("/matches/duellos", html("duels.html").getBody().readUtf8()
                .replace("Əli Məmmədov", FIRST).replace("Işıl Əliyeva", SECOND).replace("3-2", "1-3"));

        // act
        openDirect(ResultsActivity.class, "", 0);

        // assert
        awaitText("İlqar Həsənov\n" + FIRST);
        ViewGroup rows = activity().findViewById(R.id.result_rows);
        ViewGroup card = (ViewGroup) rows.getChildAt(1);
        TextView left = (TextView) findText(card, "İlqar Həsənov\n" + FIRST);
        TextView right = (TextView) findText(card, "Şəms Quliyeva\n" + SECOND);
        TextView leftScore = (TextView) findText(card, "1");
        TextView rightScore = (TextView) findText(card, "3");
        assertThat(left.getLayout().getEllipsisCount(left.getLineCount() - 1)).isZero();
        assertThat(right.getLayout().getEllipsisCount(right.getLineCount() - 1)).isZero();
        assertThat(left.getLineCount()).isGreaterThanOrEqualTo(2);
        assertThat(left.getRight()).isLessThanOrEqualTo(leftScore.getLeft());
        assertThat(right.getRight()).isLessThanOrEqualTo(rightScore.getLeft());
        assertThat(left.getTypeface().isBold()).isFalse();
        assertThat(right.getTypeface().isBold() || right.getPaint().isFakeBoldText()).isTrue();
        assertThat(card.getContentDescription().toString()).contains(FIRST, SECOND, "1-3", "Qoşalar", "Duel", "15 Nov. 2026");
        assertThat(findText(card, "Qoşalar · Duel")).isNotNull();
        assertThat(findText(card, "15 Nov. 2026")).isNotNull();
        assertThat(findText(card, "Duello")).isNotNull();
        for (String label : new String[] {"Hamısı", "Turnirlər", "Duellər"}) {
            View filter = findText(activity().getWindow().getDecorView(), label);
            assertThat(filter).isNotNull();
            assertThat(filter.getHeight()).isGreaterThanOrEqualTo(Presentation.dp(activity(), 48));
            assertThat(filter.getWidth()).isLessThanOrEqualTo(activity().findViewById(R.id.main_scroll).getWidth());
            assertThat(((TextView) filter).getLineCount()).isEqualTo(1);
            assertThat(filter.getRight()).isLessThanOrEqualTo(((View) filter.getParent()).getWidth());
        }
    }
}
