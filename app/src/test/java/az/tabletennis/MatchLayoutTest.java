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
public class MatchLayoutTest extends ProductUiScenario {
    private static final String NAME = "Əbdürrəhman Məmmədəmin Azərbaycanlı Həsənov";
    private final String qualifiers;

    public MatchLayoutTest(String qualifiers) { this.qualifiers = qualifiers; }

    @Parameters(name = "{0}") public static Collection<Object[]> layouts() {
        return Arrays.asList(new Object[][] {
                {"w320dp-h568dp-notnight"}, {"w320dp-h568dp-night"},
                {"w891dp-h320dp-land-notnight"}, {"w891dp-h320dp-land-night"}
        });
    }

    @Test public void largeTextScoreboardKeepsAllSameNamedParticipantsReachableAndRestoresMatchAfterEachProfile() throws Exception {
        // arrange
        RuntimeEnvironment.setQualifiers(qualifiers);
        RuntimeEnvironment.setFontScale(1.5f);
        org.jsoup.nodes.Document source = org.jsoup.Jsoup.parse(html("doubles-detail.html").getBody().readUtf8());
        source.select(".match-player-name").forEach(name -> name.text(NAME));
        website.put("/matches/501", source.outerHtml());
        for (long id : new long[] {12, 13, 11, 14}) {
            prepareProfile(id, NAME);
            website.compute("/matches/results/player/" + id, (path, body) -> body.replace("Kupa", "Oyunçu " + id));
        }

        // act
        openDirect(MatchActivity.class, "matchId", 501);

        // assert
        awaitText("Məlumat yeniləndi.");
        assertThat(((TextView) activity().findViewById(R.id.match_score_left)).getText().toString()).isEqualTo("3");
        assertThat(((TextView) activity().findViewById(R.id.match_score_right)).getText().toString()).isEqualTo("2");
        assertThat(activity().findViewById(R.id.match_score_left).getContentDescription().toString()).isEqualTo(NAME + " / " + NAME + " · Hesab: 3");
        assertThat(activity().findViewById(R.id.match_score_right).getContentDescription().toString()).isEqualTo(NAME + " / " + NAME + " · Hesab: 2");
        String updated = ((TextView) activity().findViewById(R.id.updated)).getText().toString();

        for (long[] participant : new long[][] {
                {R.id.participants_left, 0, 12}, {R.id.participants_left, 1, 13},
                {R.id.participants_right, 0, 11}, {R.id.participants_right, 1, 14}}) {
            // act
            ViewGroup people = activity().findViewById((int) participant[0]);
            ViewGroup row = (ViewGroup) people.getChildAt((int) participant[1]);
            TextView name = (TextView) row.getChildAt(1);
            name.requestRectangleOnScreen(new Rect(0, 0, name.getWidth(), name.getHeight()), true);
            shadowOf(Looper.getMainLooper()).idle();

            // assert
            assertThat(name.getText().toString()).isEqualTo(NAME);
            assertThat(name.getLayout().getEllipsisCount(name.getLineCount() - 1)).isZero();
            assertThat(name.getLayout().getLineEnd(name.getLineCount() - 1)).isEqualTo(NAME.length());
            assertThat(name.getHeight()).isGreaterThanOrEqualTo(Presentation.dp(activity(), 48));
            Rect bounds = new Rect();
            assertThat(name.getGlobalVisibleRect(bounds)).isTrue();
            assertThat(bounds.height()).isEqualTo(name.getHeight());
            TextView score = activity().findViewById(participant[0] == R.id.participants_left ? R.id.match_score_left : R.id.match_score_right);
            assertThat(((View) people.getParent()).getRight()).isLessThanOrEqualTo(score.getLeft());
            assertThat(score.getLayout().getLineCount()).isEqualTo(1);
            int position = ((ScrollView) activity().findViewById(R.id.main_scroll)).getScrollY();

            // act
            name.performClick();
            followNavigation();

            // assert
            awaitText("15 Nov. 2026 · İlqar Həsənov / Əli Məmmədov — Şəms Quliyeva / Işıl Əliyeva · 3-2 · Oyunçu " + participant[2] + " · Qoşalar · Turnir");
            assertThat(((TextView) activity().findViewById(R.id.profile_name)).getText().toString()).isEqualTo(NAME);

            // act
            back();
            recreateProductScreen();
            shadowOf(Looper.getMainLooper()).idle();

            // assert
            assertThat(((ScrollView) activity().findViewById(R.id.main_scroll)).getScrollY()).isEqualTo(position);
            assertThat(visibleText()).contains(updated, "3", "2");
        }
    }
}
