package az.tabletennis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.robolectric.Shadows.shadowOf;

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

@RunWith(ParameterizedRobolectricTestRunner.class)
@Config(sdk = 26, application = RankingsUiScenario.FixtureApplication.class)
public class PlayerRankingLayoutTest extends PlayerPhotoScenario {
    private static final String LONG_NAME = "Əbdürrəhman Məmmədəmin Azərbaycanlı Həsənov";
    private final String qualifiers;
    private final int minimumLines;

    public PlayerRankingLayoutTest(String qualifiers, int minimumLines) {
        this.qualifiers = qualifiers;
        this.minimumLines = minimumLines;
    }

    @Parameters(name = "{0}") public static Collection<Object[]> layouts() {
        return Arrays.asList(new Object[][] {
                {"w320dp-h568dp-notnight", 2}, {"w320dp-h568dp-night", 2},
                {"w891dp-h320dp-land-notnight", 1}, {"w891dp-h320dp-land-night", 1}
        });
    }

    @Test public void largeTextKeepsFullNamePointsRankAndRowTouchTargetReadable() throws Exception {
        // arrange
        RuntimeEnvironment.setQualifiers(qualifiers);
        RuntimeEnvironment.setFontScale(1.5f);
        website.put("/players?level=amateur", ranking("/assets/img/person.png", "/assets/img/person.png").replace("İlqar Həsənov", LONG_NAME));

        // act
        openScreen();

        // assert
        awaitText("Məlumat yeniləndi.");
        shadowOf(Looper.getMainLooper()).idle();
        TextView name = (TextView) findText(activity().getWindow().getDecorView(), LONG_NAME);
        TextView points = (TextView) findText(activity().getWindow().getDecorView(), "800 xal");
        TextView rank = (TextView) findText(activity().getWindow().getDecorView(), "#1");
        View row = (View) name.getParent().getParent();
        assertThat(name.getLineCount()).isGreaterThanOrEqualTo(minimumLines);
        assertThat(name.getLayout().getEllipsisCount(name.getLineCount() - 1)).isZero();
        assertThat(points.getTop()).isGreaterThanOrEqualTo(name.getBottom());
        assertThat(row.getHeight()).isGreaterThanOrEqualTo(Presentation.dp(activity(), 48));
        assertThat(row.getContentDescription().toString()).isEqualTo("1. " + LONG_NAME + " — 800 xal");
        assertThat(rank.getText().toString()).isEqualTo("#1");
        assertThat(photo(LONG_NAME).getClipToOutline()).isTrue();
        assertThat(requestedPaths()).containsExactly("/players?level=amateur");
    }
}
