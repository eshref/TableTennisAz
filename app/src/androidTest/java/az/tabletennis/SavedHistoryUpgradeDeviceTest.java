package az.tabletennis;

import static androidx.test.espresso.Espresso.onData;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withContentDescription;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withResourceName;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.anything;
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Runs against both installed APKs, whose Android resource IDs can differ. */
@RunWith(AndroidJUnit4.class)
public class SavedHistoryUpgradeDeviceTest {
    private static final String FIRST = "Əlixan Əlizadə";
    private static final String SECOND = "Xəyal Həşimli";
    private static final String EARLIER = "11.01.2025 12:00:00.000";
    private static final String LATER = "12.01.2025 12:00:00.000";

    @Test public void savedDoublesHistoryIsVisible() throws Exception {
        // arrange
        var instrumentation = InstrumentationRegistry.getInstrumentation();
        String phase = InstrumentationRegistry.getArguments().getString("capturePrefix");
        assumeTrue("Run through the signed upgrade verifier", "upgrade-before".equals(phase) || "upgrade-after".equals(phase));
        Context app = instrumentation.getTargetContext();
        if (phase.equals("upgrade-before")) givenSavedHistory(app);
        File captures = new File(app.getExternalFilesDir(null), "upgrade");
        if (!captures.isDirectory() && !captures.mkdirs()) throw new IOException("Cannot create upgrade evidence directory");

        // act
        try (ActivityScenario<RankingsActivity> scenario = ActivityScenario.launch(RankingsActivity.class)) {
            onView(withResourceName("doubles_mode")).perform(click());
            onData(anything()).inAdapterView(withId(android.R.id.list)).atPosition(1).perform(click());

            // act & assert
            onView(withText(FIRST)).perform(scrollTo()).check(matches(isDisplayed()));
            onView(withText(SECOND)).perform(scrollTo()).check(matches(isDisplayed()));
            thenSelectedObservation(LATER, "858 xal");

            // act
            onView(withContentDescription(EARLIER + " — 800 xal")).perform(scrollTo(), click());

            // act & assert
            thenSelectedObservation(EARLIER, "800 xal");
            ScreenshotCheckpoint.captureWindow(new File(captures, phase + "-rating.png"));

            // act
            onView(withResourceName("pair_chart_rank")).perform(scrollTo(), click());

            // act & assert
            thenSelectedObservation(EARLIER, "#9");
            ScreenshotCheckpoint.captureWindow(new File(captures, phase + "-ranking.png"));

            // act
            onView(withContentDescription(LATER + " — #2")).perform(scrollTo(), click());

            // act & assert
            thenSelectedObservation(LATER, "#2");
        }
    }

    private void thenSelectedObservation(String date, String value) {
        onView(withResourceName("chart_selected_value")).perform(scrollTo()).check(matches(allOf(isDisplayed(), withText(value))));
        onView(withResourceName("chart_selected_date")).check(matches(allOf(isDisplayed(), withText(date))));
    }

    private void givenSavedHistory(Context app) throws IOException {
        long now = System.currentTimeMillis();
        new RankingsStore(app).write(new RankingSnapshot(List.of(new RankingSnapshot.Player(7, FIRST, 600)), now));
        DoublesPair pair = new DoublesPair(151, new MatchDetails.PlayerLink(7, FIRST),
                new MatchDetails.PlayerLink(70, SECOND), null, null, true);
        DoublesRanking earlier = new DoublesRanking(List.of(new DoublesRanking.Row(pair, 9, 800)))
                .observedAfter(null, fixtureTime(11));
        DoublesRanking later = new DoublesRanking(List.of(new DoublesRanking.Row(pair, 2, 858)))
                .observedAfter(earlier, fixtureTime(12));
        new SavedDataStore<>(app, "doubles-ranking", DoublesRanking.class).write(new SavedData<>(later, now));
        new SavedDataStore<>(app, "duo-results-151", PairHistory.class).write(new SavedData<>(
                new PairHistory(pair, List.of(), List.of(), null, new PairMedals(0, 0, 0)), now));
    }

    private long fixtureTime(int day) {
        return LocalDateTime.of(2025, 1, day, 12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}
