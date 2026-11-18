package az.tabletennis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.robolectric.Shadows.shadowOf;

import android.graphics.Rect;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
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
public class RatingHistoryLayoutTest extends ProductUiScenario {
    private final String qualifiers;

    public RatingHistoryLayoutTest(String qualifiers) { this.qualifiers = qualifiers; }

    @Parameters(name = "{0}") public static Collection<Object[]> layouts() {
        return Arrays.asList(new Object[][] {
                {"w320dp-h568dp-notnight"}, {"w320dp-h568dp-night"},
                {"w891dp-h320dp-land-notnight"}, {"w891dp-h320dp-land-night"}
        });
    }

    @Test public void sourceLabelsAndObservationActionsRemainReadableAndReachableWithLargeText() throws Exception {
        // arrange
        RuntimeEnvironment.setQualifiers(qualifiers);
        RuntimeEnvironment.setFontScale(1.5f);
        prepareProfile(12, "Əbdürrəhman Məmmədəmin Azərbaycanlı Həsənov");
        website.put("/api/getPlayerPoints?id=12", "[{\"day\":\"31.12\",\"point\":435.55002},{\"day\":\"01.01\",\"point\":433.65002}]");
        openDirect(ProfileActivity.class, "playerId", 12);
        awaitText("Əbdürrəhman Məmmədəmin Azərbaycanlı Həsənov");

        // act
        clickText("Xal tarixçəsi");
        followNavigation();

        // assert
        awaitText("31.12 — 435.55 xal");
        assertThat(visibleText()).contains("Əbdürrəhman Məmmədəmin Azərbaycanlı Həsənov", "435.55", "433.65", "31.12", "01.01", "Müşahidələr");
        assertThat(activity().findViewById(R.id.chart).getContentDescription().toString())
                .isEqualTo("31.12 — 435.55 xal; 01.01 — 433.65 xal");
        TextView selectedDate = activity().findViewById(R.id.chart_selected_date);
        TextView selectedValue = activity().findViewById(R.id.chart_selected_value);
        assertThat(selectedDate.getRight()).isLessThanOrEqualTo(selectedValue.getLeft());
        assertReadable((ViewGroup) activity().getWindow().getDecorView());
        View refresh = activity().findViewById(R.id.refresh);
        assertThat(refresh.getContentDescription().toString()).isEqualTo("Yenilə");
        assertThat(refresh.getHeight()).isGreaterThanOrEqualTo(Presentation.dp(activity(), 48));
        assertThat(refresh.getGlobalVisibleRect(new Rect())).isTrue();

        // act
        View observation = findText(activity().getWindow().getDecorView(), "31.12 — 435.55 xal");
        observation.requestRectangleOnScreen(new Rect(0, 0, observation.getWidth(), observation.getHeight()), true);
        shadowOf(Looper.getMainLooper()).idle();
        observation.performClick();

        // assert
        Rect visible = new Rect();
        assertThat(observation.getGlobalVisibleRect(visible)).isTrue();
        assertThat(visible.height()).isEqualTo(observation.getHeight());
        assertThat(observation.getHeight()).isGreaterThanOrEqualTo(Presentation.dp(activity(), 48));
        assertThat(observation.isSelected()).isTrue();
        TextView date = (TextView) findText(observation, "31.12");
        TextView value = (TextView) findText(observation, "435.55 xal");
        assertThat(date).as("observation date column").isNotNull();
        assertThat(value).as("observation points column").isNotNull();
        assertThat(date.getRight()).isLessThanOrEqualTo(value.getLeft());
        int scroll = ((ScrollView) activity().findViewById(R.id.main_scroll)).getScrollY();

        // act
        recreateProductScreen();
        shadowOf(Looper.getMainLooper()).idle();

        // assert
        assertThat(((ScrollView) activity().findViewById(R.id.main_scroll)).getScrollY()).isEqualTo(scroll);
        assertThat(findText(activity().getWindow().getDecorView(), "31.12 — 435.55 xal").isSelected()).isTrue();

        // act
        back();

        // assert
        assertThat(activity()).isInstanceOf(ProfileActivity.class);
        assertThat(visibleText()).contains("Əbdürrəhman Məmmədəmin Azərbaycanlı Həsənov", "800 xal");
    }

    private void assertReadable(ViewGroup group) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child.getVisibility() != View.VISIBLE) continue;
            if (child instanceof TextView label && label.length() > 0) {
                assertThat(label.getLayout()).as(label.getText().toString()).isNotNull();
                assertThat(label.getLayout().getEllipsisCount(label.getLineCount() - 1)).as(label.getText().toString()).isZero();
                assertThat(label.getLayout().getHeight()).as(label.getText().toString())
                        .isLessThanOrEqualTo(label.getHeight() - label.getCompoundPaddingTop() - label.getCompoundPaddingBottom());
                assertThat(label.getRight()).as(label.getText().toString()).isLessThanOrEqualTo(group.getWidth());
            }
            if (child instanceof ViewGroup nested) assertReadable(nested);
        }
    }
}
