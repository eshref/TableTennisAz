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

    @Test public void eventNameWrapsInTheFilterAndItsDropdown() throws Exception {
        // arrange
        RuntimeEnvironment.setQualifiers(qualifiers);
        RuntimeEnvironment.setFontScale(1.5f);
        android.content.Context context = RuntimeEnvironment.getApplication();
        String name = "Bakı həvəskarlar kuboku 2026 — final mərhələsi";
        EventCatalog catalog = new EventCatalog(Arrays.asList(new EventCatalog.Event(901, name)));
        ResultsFeed feed = new ResultsFeed(Arrays.asList(), catalog, Arrays.asList(), Arrays.asList(), null);
        long now = FixtureApplication.time.millis();
        new SavedDataStore<>(context, "events", EventCatalog.class).write(new SavedData<>(catalog, now));
        new SavedDataStore<>(context, "results-all-0", ResultsFeed.class).write(new SavedData<>(feed, now));
        new SavedDataStore<>(context, "results-events-901", ResultsFeed.class).write(new SavedData<>(feed, now));
        context.getSharedPreferences("results-selection", android.content.Context.MODE_PRIVATE).edit().clear().commit();

        // act
        openDirect(ResultsActivity.class, "", 0);
        android.widget.Spinner filter = activity().findViewById(R.id.event_filter);
        android.widget.ListView popup = new android.widget.ListView(activity());
        TextView option = (TextView) filter.getAdapter().getDropDownView(1, null, popup);
        int optionWidth = filter.getWidth() - filter.getPaddingLeft() - filter.getPaddingRight();
        option.measure(View.MeasureSpec.makeMeasureSpec(optionWidth, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED);
        option.layout(0, 0, option.getMeasuredWidth(), option.getMeasuredHeight());
        filter.setSelection(1);
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(100));

        // assert
        for (TextView label : Arrays.asList(option, (TextView) filter.getSelectedView())) {
            assertThat(label.getText().toString()).isEqualTo(name);
            assertThat(label.getLayout()).isNotNull();
            for (int line = 0; line < label.getLineCount(); line++) {
                assertThat(label.getLayout().getEllipsisCount(line)).isZero();
                assertThat(label.getLayout().getLineMax(line)).isLessThanOrEqualTo((float) (label.getWidth()
                        - label.getCompoundPaddingLeft() - label.getCompoundPaddingRight()));
            }
            assertThat(label.getLayout().getHeight()).isLessThanOrEqualTo(label.getHeight()
                    - label.getCompoundPaddingTop() - label.getCompoundPaddingBottom());
        }
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
        ViewGroup card = (ViewGroup) findText(rows, "15 Nov. 2026 · İlqar Həsənov / " + FIRST
                + " — Şəms Quliyeva / " + SECOND + " · 1-3 · Duello · Qoşalar · Duel");
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
