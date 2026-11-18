package az.tabletennis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.robolectric.Shadows.shadowOf;

import android.graphics.Rect;
import android.os.Looper;
import android.view.View;
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
public class DoublesProfileLayoutTest extends DoublesUiScenario {
    private static final String FIRST = "Əbdürrəhman Məmmədəmin Azərbaycanlı Həsənov";
    private static final String SECOND = "Məryəm Əbdülrəhim Azərbaycanlı İmanova";
    private final String qualifiers;

    public DoublesProfileLayoutTest(String qualifiers) { this.qualifiers = qualifiers; }

    @Parameters(name = "{0}") public static Collection<Object[]> layouts() {
        return Arrays.asList(new Object[][] {
                {"w320dp-h568dp-notnight"}, {"w320dp-h568dp-night"},
                {"w891dp-h320dp-land-notnight"}, {"w891dp-h320dp-land-night"}
        });
    }

    @Test public void largeTextKeepsBothLinkedNamesStandingMedalsAndInlineObservationsReadable() throws Exception {
        // arrange
        RuntimeEnvironment.setQualifiers(qualifiers);
        RuntimeEnvironment.setFontScale(1.5f);
        prepareDoubles();
        website.put("/api/getDuoInfo?id=110", pairInfo(110, 12, FIRST, 13, SECOND)
                .replace("}", ",\"firstPlaceCount\":null,\"secondPlaceCount\":0,\"thirdPlaceCount\":1}"));
        website.put("/matches/results/duo/110", pairHistory("<div class='no-info'>Matç yoxdur</div>"));

        // act
        openScreen();
        activity().findViewById(R.id.doubles_mode).performClick();

        // assert
        awaitText("Məlumat yeniləndi.");

        // act
        ((android.widget.ListView) activity().findViewById(android.R.id.list)).setSelection(1);
        shadowOf(Looper.getMainLooper()).idle();

        // assert
        awaitText("1. " + FIRST + " / " + SECOND + " — 800 xal");

        // act
        clickText("1. " + FIRST + " / " + SECOND + " — 800 xal");
        followNavigation();

        // assert
        awaitText("Matç tarixçəsi yoxdur.");
        TextView first = (TextView) findText(activity().getWindow().getDecorView(), FIRST);
        TextView second = (TextView) findText(activity().getWindow().getDecorView(), SECOND);
        assertThat(first.isClickable()).isTrue();
        assertThat(second.isClickable()).isTrue();
        TextView points = activity().findViewById(R.id.profile_points);
        TextView position = activity().findViewById(R.id.pair_position);
        assertThat(points.getText().toString()).isEqualTo("800");
        assertThat(points.getContentDescription().toString()).isEqualTo("800 xal");
        assertThat(position.getText().toString()).isEqualTo("#1");
        assertThat(position.getContentDescription().toString()).isEqualTo("Reytinqdə yer: #1");
        assertThat(((View) points.getParent()).getRight()).isLessThanOrEqualTo(((View) position.getParent()).getLeft());
        TextView gold = activity().findViewById(R.id.pair_gold);
        TextView silver = activity().findViewById(R.id.pair_silver);
        TextView bronze = activity().findViewById(R.id.pair_bronze);
        assertThat(gold.getText().toString()).isEqualTo("—");
        assertThat(gold.getContentDescription().toString()).isEqualTo("Qızıl medal: məlumat mövcud deyil");
        assertThat(silver.getText().toString()).isEqualTo("0");
        assertThat(silver.getContentDescription().toString()).isEqualTo("Gümüş medal: 0");
        assertThat(bronze.getText().toString()).isEqualTo("1");
        for (TextView label : Arrays.asList(first, second, points, position, gold, silver, bronze)) assertReadable(label);

        // act
        bringIntoView(first);

        // assert
        Rect visible = new Rect();
        assertThat(first.getGlobalVisibleRect(visible)).isTrue();
        assertThat(visible.height()).isEqualTo(first.getHeight());
        assertThat(first.getHeight()).isGreaterThanOrEqualTo(Presentation.dp(activity(), 48));

        // act
        clickText("Mövqe");
        TextView date = activity().findViewById(R.id.chart_selected_date);
        bringIntoView(date);

        // assert
        assertThat(date.getText().toString()).isEqualTo("15.11.2026 12:00:00.000");
        assertReadable(date);
        assertThat(activity().findViewById(R.id.chart).getContentDescription().toString())
                .isEqualTo("15.11.2026 12:00:00.000 — #1");
        assertThat(activity().<TextView>findViewById(R.id.chart_selected_value).getText().toString()).isEqualTo("#1");
        android.view.ViewGroup observation = (android.view.ViewGroup) ((android.view.ViewGroup) activity().findViewById(R.id.chart_observations)).getChildAt(0);
        TextView observationDate = observation.findViewById(android.R.id.text1);
        TextView observationValue = observation.findViewById(android.R.id.text2);
        assertReadable(observationDate);
        assertReadable(observationValue);
        assertThat(observation.getContentDescription().toString()).isEqualTo("15.11.2026 12:00:00.000 — #1");
        assertThat(observationDate.getRight()).isLessThanOrEqualTo(observationValue.getLeft());
        assertThat(date.getRight()).isLessThanOrEqualTo(activity().findViewById(R.id.chart_selected_value).getLeft());
        assertThat(observation.getHeight()).isGreaterThanOrEqualTo(Presentation.dp(activity(), 48));
    }

    private void assertReadable(TextView label) {
        assertThat(label.getLayout()).isNotNull();
        int last = label.getLineCount() - 1;
        assertThat(label.getLayout().getEllipsisCount(last)).isZero();
        assertThat(label.getLayout().getLineEnd(last)).isEqualTo(label.getText().length());
        assertThat(label.getLeft()).isGreaterThanOrEqualTo(0);
        assertThat(label.getRight()).isLessThanOrEqualTo(((View) label.getParent()).getWidth());
    }

    private void bringIntoView(View view) {
        view.requestRectangleOnScreen(new Rect(0, 0, view.getWidth(), view.getHeight()), true);
        shadowOf(Looper.getMainLooper()).idle();
    }
}
