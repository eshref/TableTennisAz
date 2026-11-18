package az.tabletennis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.robolectric.Shadows.shadowOf;

import android.graphics.Rect;
import android.os.Looper;
import android.view.View;
import android.widget.ScrollView;
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
public class ProfileLayoutTest extends ProductUiScenario {
    private static final String NAME = "Əbdürrəhman Məmmədəmin Azərbaycanlı Həsənov";
    private final String qualifiers;

    public ProfileLayoutTest(String qualifiers) { this.qualifiers = qualifiers; }

    @Parameters(name = "{0}") public static Collection<Object[]> layouts() {
        return Arrays.asList(new Object[][] {
                {"w320dp-h568dp-notnight"}, {"w320dp-h568dp-night"},
                {"w891dp-h320dp-land-notnight"}, {"w891dp-h320dp-land-night"}
        });
    }

    @Test public void profileKeepsFullIdentitySeparateStatsAndChartReachableAndRestoresScrollAfterRecreation() throws Exception {
        // arrange
        RuntimeEnvironment.setQualifiers(qualifiers);
        RuntimeEnvironment.setFontScale(1.5f);
        prepareProfile(12, NAME);
        website.compute("/matches/results/player/12", (path, body) -> body.replace("İlqar Həsənov", NAME));
        website.put("/api/getPlayerPoints?id=12", "[{\"day\":\"31.12\",\"point\":435.55}]");

        // act
        openDirect(ProfileActivity.class, "playerId", 12);

        // assert
        awaitText(NAME);
        TextView name = activity().findViewById(R.id.profile_name);
        TextView points = activity().findViewById(R.id.profile_points);
        TextView position = activity().findViewById(R.id.profile_position);
        View photo = activity().findViewById(android.R.id.icon);
        assertThat(name.getLayout().getEllipsisCount(name.getLineCount() - 1)).isZero();
        assertThat(name.getLayout().getLineEnd(0)).isGreaterThanOrEqualTo("Əbdürrəhman".length());
        assertThat(name.getRight()).isLessThanOrEqualTo(((View) name.getParent()).getWidth());
        assertThat(points.getText().toString()).isEqualTo("800 xal");
        assertThat(position.getText().toString()).isEqualTo("#1");
        assertThat(((View) points.getParent()).getRight()).isLessThanOrEqualTo(((View) position.getParent()).getLeft());
        assertThat(((android.widget.ImageView) photo).getDrawable()).isInstanceOf(android.graphics.drawable.VectorDrawable.class);
        assertThat(photo.getImportantForAccessibility()).isEqualTo(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        assertThat(visibleText()).contains("15 Nov. 2026 · " + NAME + " / Əli Məmmədov — Şəms Quliyeva / Işıl Əliyeva · 3-2 · Kupa · Qoşalar · Turnir");
        if (android.os.Build.VERSION.SDK_INT >= 28) assertThat(name.isAccessibilityHeading()).isTrue();

        // act
        View chart = activity().findViewById(R.id.profile_history_action);
        chart.requestRectangleOnScreen(new Rect(0, 0, chart.getWidth(), chart.getHeight()), true);
        shadowOf(Looper.getMainLooper()).idle();

        // assert
        Rect visible = new Rect();
        assertThat(chart.getGlobalVisibleRect(visible)).isTrue();
        assertThat(visible.height()).isEqualTo(chart.getHeight());
        assertThat(chart.getHeight()).isGreaterThanOrEqualTo(Presentation.dp(activity(), 48));
        int scroll = ((ScrollView) activity().findViewById(R.id.main_scroll)).getScrollY();

        // act
        clickText("Xal tarixçəsi");
        followNavigation();

        // assert
        awaitText("31.12 — 435.55 xal");

        // act
        back();

        // arrange
        FixtureApplication.time = java.time.Clock.offset(FixtureApplication.time, java.time.Duration.ofHours(49));

        // act
        recreateProductScreen();
        shadowOf(Looper.getMainLooper()).idle();

        // assert
        awaitText("Məlumat yeniləndi.");
        assertThat(((ScrollView) activity().findViewById(R.id.main_scroll)).getScrollY()).isEqualTo(scroll);
        assertThat(visibleText()).contains(NAME, "800 xal", "#1");
    }
}
