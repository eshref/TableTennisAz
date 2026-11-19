package az.tabletennis;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.hasDescendant;
import static androidx.test.espresso.matcher.ViewMatchers.isChecked;
import static androidx.test.espresso.matcher.ViewMatchers.withContentDescription;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static androidx.test.espresso.matcher.ViewMatchers.withSpinnerText;
import static java.util.Arrays.asList;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.io.FileOutputStream;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Installed native rendering and Back journey, complementary to the HTTP-fixture tests. */
@RunWith(AndroidJUnit4.class)
public class ResultsDeviceTest {
    private static final String FIRST = "Əbdürrəhman Məmmədəmin Azərbaycanlı Həsənov";
    private static final String SECOND = "Məryəm Əbdülrəhim Azərbaycanlı İmanova";
    private static final String EVENT = "Bakı həvəskarlar kuboku 2026";
    private static final String STAGE = "3-cü yer oyunu";
    private static final String CARD = "16 Nov. 2026 · İlqar Həsənov — Şəms Quliyeva · 3-1 · "
            + EVENT + " · Tək · Turnir · " + STAGE;

    @Test public void resultsCardsFiltersAndBackRetainTheSelectedFeed() throws Exception {
        // arrange
        givenSavedResults();

        // act
        try (ActivityScenario<ResultsActivity> scenario = ActivityScenario.launch(ResultsActivity.class)) {
            onView(withId(R.id.match_stage_badge)).perform(scrollTo());

            // assert
            onView(withContentDescription(CARD)).check(matches(hasDescendant(withText("3"))));
            onView(withContentDescription(CARD)).check(matches(hasDescendant(withText("İlqar Həsənov"))));
            onView(withContentDescription(CARD)).check(matches(hasDescendant(withText("Tək · Turnir"))));
            onView(withId(R.id.match_stage_badge)).check(matches(withText(STAGE)));
            onView(withContentDescription(CARD)).check((view, missing) -> {
                if (missing != null) throw missing;
                ViewGroup card = (ViewGroup) view;
                TextView event = card.findViewById(R.id.match_event_name);
                TextView stage = card.findViewById(R.id.match_stage_badge);
                Rect eventBounds = new Rect();
                Rect stageBounds = new Rect();
                event.getDrawingRect(eventBounds);
                stage.getDrawingRect(stageBounds);
                card.offsetDescendantRectToMyCoords(event, eventBounds);
                card.offsetDescendantRectToMyCoords(stage, stageBounds);
                boolean narrow = "narrow-large".equals(InstrumentationRegistry.getArguments().getString("capturePrefix"));
                if (narrow && stageBounds.top < eventBounds.bottom) {
                    throw new AssertionError("The stage should wrap below the tournament at 320×568 with 150% text");
                }
                if (!narrow && (stageBounds.left < eventBounds.right || stageBounds.top >= eventBounds.bottom)) {
                    throw new AssertionError("The stage should sit beside the tournament when it fits");
                }
            });
            capture("results");

            // act
            onView(withText("Turnirlər")).perform(scrollTo(), click());
            scenario.onActivity(activity -> ((android.widget.Spinner) activity.findViewById(R.id.event_filter)).setSelection(1));
            onView(withContentDescription(CARD)).perform(scrollTo(), click());

            // assert
            onView(withId(R.id.match_score_left)).check(matches(withText("3")));
            onView(withId(R.id.match_score_right)).check(matches(withText("1")));

            // act
            pressBack();

            // assert
            onView(withText("Turnirlər")).check(matches(isChecked()));
            onView(withId(R.id.event_filter)).check(matches(withSpinnerText(EVENT)));
            onView(withContentDescription(CARD)).check(matches(hasDescendant(withText(EVENT))));

            // act
            onView(withText("Hamısı")).perform(scrollTo(), click());
            onView(withText("İlqar Həsənov\n" + FIRST)).perform(scrollTo());

            // assert
            onView(withText("İlqar Həsənov\n" + FIRST)).check(matches(withText("İlqar Həsənov\n" + FIRST)));
            capture("long-names");

            // act
            onView(withText("Şəms Quliyeva\n" + SECOND)).perform(scrollTo());

            // assert
            onView(withText("Şəms Quliyeva\n" + SECOND)).check(matches(withText("Şəms Quliyeva\n" + SECOND)));
            capture("long-names-second-side");

            // act
            onView(withText("Duellər")).perform(scrollTo(), click());

            // assert
            onView(withText(R.string.no_results)).check(matches(withText(R.string.no_results)));
            // act
            onView(withText(R.string.no_results)).perform(scrollTo());

            // assert
            capture("empty");

            // act
            onView(withId(R.id.clear_results_filters)).perform(scrollTo(), click());
            scenario.recreate();

            // assert
            onView(withText("Hamısı")).check(matches(isChecked()));
            onView(withContentDescription(CARD)).check(matches(hasDescendant(withText("1"))));
        }
    }

    private void givenSavedResults() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        long now = System.currentTimeMillis();
        EventCatalog catalog = new EventCatalog(asList(new EventCatalog.Event(901, EVENT)));
        MatchSummary singles = new MatchSummary(502, asList("İlqar Həsənov"), asList("Şəms Quliyeva"),
                "16 Nov. 2026", "3-1", 901, EVENT, false, false, TournamentStage.THIRD_PLACE);
        MatchSummary doubles = new MatchSummary(501, asList("İlqar Həsənov", FIRST), asList("Şəms Quliyeva", SECOND),
                "15 Nov. 2026", "1-3", 0, "Duello", true);
        ResultsFeed all = new ResultsFeed(asList(singles, doubles), catalog, asList(), asList(), null);
        ResultsFeed events = new ResultsFeed(asList(singles), catalog, asList(), asList(), null);
        ResultsFeed empty = new ResultsFeed(asList(), catalog, asList(), asList(), null);
        new SavedDataStore<>(context, "events", EventCatalog.class).write(new SavedData<>(catalog, now));
        new SavedDataStore<>(context, "results-all-0", ResultsFeed.class).write(new SavedData<>(all, now));
        new SavedDataStore<>(context, "results-events-0", ResultsFeed.class).write(new SavedData<>(events, now));
        new SavedDataStore<>(context, "results-events-901", ResultsFeed.class).write(new SavedData<>(events, now));
        new SavedDataStore<>(context, "results-duels-0", ResultsFeed.class).write(new SavedData<>(empty, now));
        MatchDetails details = new MatchDetails(502, asList(new MatchDetails.PlayerLink(12, "İlqar Həsənov")),
                asList(new MatchDetails.PlayerLink(11, "Şəms Quliyeva")), "16 Nov. 2026", "", "3-1", EVENT);
        new SavedDataStore<>(context, "match-502", MatchDetails.class).write(new SavedData<>(details, now));
        context.getSharedPreferences("results-selection", Context.MODE_PRIVATE).edit().clear().commit();
    }

    private void capture(String name) throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File directory = new File(context.getExternalFilesDir(null), "issue27");
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IllegalStateException("Cannot save screenshots");
        String prefix = InstrumentationRegistry.getArguments().getString("capturePrefix", "light");
        Bitmap screenshot = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        if (screenshot == null) {
            try (var input = new android.os.ParcelFileDescriptor.AutoCloseInputStream(
                    InstrumentationRegistry.getInstrumentation().getUiAutomation().executeShellCommand("screencap -p"))) {
                screenshot = BitmapFactory.decodeStream(input);
            }
        }
        if (screenshot == null) throw new IllegalStateException("Android did not provide a screenshot");
        File png = new File(directory, prefix + "-" + name + ".png");
        try (var output = new FileOutputStream(png)) {
            if (!screenshot.compress(Bitmap.CompressFormat.PNG, 100, output)) throw new IllegalStateException("Cannot save screenshot");
        } finally { screenshot.recycle(); }
        ScreenshotCheckpoint.exportIfRequired(png);
    }
}
