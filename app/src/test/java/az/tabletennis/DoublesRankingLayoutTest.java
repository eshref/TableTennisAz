package az.tabletennis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.robolectric.Shadows.shadowOf;

import android.os.Looper;
import android.view.View;
import android.widget.ListView;
import android.widget.TextView;
import java.util.Arrays;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.ParameterizedRobolectricTestRunner;
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(ParameterizedRobolectricTestRunner.class)
@Config(sdk = 26, application = RankingsUiScenario.FixtureApplication.class)
public class DoublesRankingLayoutTest extends DoublesPhotoScenario {
    private static final String FIRST = "Əbdürrəhman Məmmədəmin Azərbaycanlı Həsənov";
    private static final String SECOND = "Məryəm Əbdülrəhim Azərbaycanlı İmanova";
    private final String qualifiers;
    private final int minimumLines;

    public DoublesRankingLayoutTest(String qualifiers, int minimumLines) {
        this.qualifiers = qualifiers;
        this.minimumLines = minimumLines;
    }

    @Parameters(name = "{0}") public static Collection<Object[]> layouts() {
        return Arrays.asList(new Object[][] {
                {"w320dp-h568dp-notnight", 5}, {"w320dp-h568dp-night", 5},
                {"w891dp-h320dp-land-notnight", 2}, {"w891dp-h320dp-land-night", 2}
        });
    }

    @Test public void largeTextKeepsBothFullNamesPointsPublishedPositionAndMovementReadable() throws Exception {
        // arrange
        RuntimeEnvironment.setQualifiers(qualifiers);
        RuntimeEnvironment.setFontScale(1.5f);
        prepareDoubles();
        website.put("/api/getDuoInfo?id=110", pairInfo(110, 12, FIRST, 13, SECOND));

        // act
        openScreen();
        activity().findViewById(R.id.doubles_mode).performClick();

        // assert
        awaitText("1. " + FIRST + " / " + SECOND + " — 800 xal");

        // arrange
        website.put("/players/duos", "<div id='center'><div class='ranking-page-list'>"
                + pairRow(110, 2, 800) + pairRow(220, 4, 800) + pairRow(330, 5, 600) + "</div></div>");

        // act
        activity().findViewById(R.id.refresh).performClick();

        // assert
        awaitText("2. " + FIRST + " / " + SECOND + " — 800 xal, 1 pillə geriləyib");

        // act
        ListView list = activity().findViewById(android.R.id.list);
        list.setSelection(1);
        shadowOf(Looper.getMainLooper()).idle();

        // assert
        TextView names = (TextView) findText(list, FIRST + "\n" + SECOND);
        TextView points = (TextView) findText(list, "800 xal");
        TextView movement = (TextView) findText(list, "↓ 1");
        TextView position = (TextView) findText(list, "#2");
        View row = (View) names.getParent().getParent();
        assertThat(names.getLineCount()).isGreaterThanOrEqualTo(minimumLines);
        assertThat(names.getLayout().getEllipsisCount(names.getLineCount() - 1)).isZero();
        assertThat(points.getTop()).isGreaterThanOrEqualTo(names.getBottom());
        assertThat(movement.getTop()).isGreaterThanOrEqualTo(points.getBottom());
        assertThat(names.getRight()).isLessThanOrEqualTo(((View) position.getParent()).getLeft());
        assertThat(row.getHeight()).isGreaterThanOrEqualTo(Presentation.dp(activity(), 48));
        assertThat(row.getHeight()).isLessThanOrEqualTo(list.getHeight());
        assertThat(row.getContentDescription().toString()).contains(FIRST, SECOND, "800 xal", "1 pillə geriləyib");
    }
}
