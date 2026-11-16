package az.tabletennis;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isChecked;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import android.content.Context;
import android.graphics.Bitmap;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.io.FileOutputStream;
import static java.util.Arrays.asList;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Opt-in installed-app check of Android's actual destination stack, with saved fixtures. */
@RunWith(AndroidJUnit4.class)
public class MainNavigationDeviceTest {
    @Test public void drawerSwitchesReuseRankingsAndBackClosesTheDrawerBeforeReturningFromResults() throws Exception {
        // arrange
        givenSavedMainScreens();

        // act
        try (ActivityScenario<RankingsActivity> scenario = ActivityScenario.launch(RankingsActivity.class)) {
            // assert
            onView(withId(R.id.players_mode)).check(matches(isChecked()));
            capture("players");

            // act
            onView(withId(R.id.doubles_mode)).perform(click());
            onView(withId(R.id.search)).perform(replaceText("ilqar"), closeSoftKeyboard());

            // assert
            onView(withText("1. İlqar Həsənov / Əli Məmmədov — 800 xal")).check(matches(isDisplayed()));
            capture("doubles");

            // act
            onView(withText("1. İlqar Həsənov / Əli Məmmədov — 800 xal")).perform(click());
            onView(withId(R.id.pair_chart_points)).perform(scrollTo());

            // assert
            onView(withId(R.id.pair_chart_points)).check(matches(isDisplayed()));

            // act
            onView(withText(R.string.pair_chart_first)).perform(scrollTo());

            // assert
            onView(withText(R.string.pair_chart_first)).check(matches(isDisplayed()));
            capture("existing-pair-history");

            // act
            pressBack();

            // assert
            onView(withId(R.id.search)).check(matches(withText("ilqar")));

            // act
            onView(withId(R.id.open_menu)).perform(click());

            // assert
            onView(withId(R.id.destination_rankings)).check(matches(isChecked()));
            capture("drawer");

            // act
            onView(withId(R.id.destination_results)).perform(click());

            // assert
            onView(withId(R.id.result_rows)).check(matches(isDisplayed()));
            capture("results");

            // act
            onView(withText(R.string.duel_results)).perform(click());
            onView(withId(R.id.open_menu)).perform(click());
            pressBack();

            // assert
            onView(withText(R.string.duel_results)).check(matches(isChecked()));

            // act
            onView(withId(R.id.open_menu)).perform(click());
            onView(withId(R.id.destination_rankings)).perform(click());

            // assert
            onView(withId(R.id.doubles_mode)).check(matches(isChecked()));
            onView(withId(R.id.search)).check(matches(withText("ilqar")));

            // act
            onView(withId(R.id.open_menu)).perform(click());
            onView(withId(R.id.destination_results)).perform(click());

            // assert
            onView(withText(R.string.duel_results)).check(matches(isChecked()));

            // act
            pressBack();

            // assert
            onView(withId(R.id.doubles_mode)).check(matches(isChecked()));
            onView(withId(R.id.search)).check(matches(withText("ilqar")));

            // arrange
            givenRankingWithoutObservations();

            // act
            onView(withText("1. İlqar Həsənov / Əli Məmmədov — 800 xal")).perform(click());
            onView(withText(R.string.pair_chart_empty)).perform(scrollTo());

            // assert
            onView(withText(R.string.pair_chart_empty)).check(matches(isDisplayed()));
            capture("existing-pair-no-history");
        }
    }

    private void givenSavedMainScreens() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        long now = System.currentTimeMillis();
        new RankingsStore(context).write(new RankingSnapshot(asList(
                new RankingSnapshot.Player(12, "İlqar Həsənov", 800),
                new RankingSnapshot.Player(13, "Əli Məmmədov", 750),
                new RankingSnapshot.Player(14, "Işıl Əliyeva", 600)), now));
        DoublesPair pair = new DoublesPair(110, new MatchDetails.PlayerLink(12, "İlqar Həsənov"),
                new MatchDetails.PlayerLink(13, "Əli Məmmədov"));
        new SavedDataStore<>(context, "doubles-ranking", DoublesRanking.class).write(new SavedData<>(
                new DoublesRanking(asList(new DoublesRanking.Row(pair, 1, 800))).observedAfter(null, now), now));
        new SavedDataStore<>(context, "duo-results-110", PairHistory.class).write(new SavedData<>(
                new PairHistory(pair, asList(), asList(), null, new PairMedals(1, 0, null)), now));
        EventCatalog catalog = new EventCatalog(asList());
        ResultsFeed feed = new ResultsFeed(asList(new MatchSummary(501, asList("İlqar Həsənov"),
                asList("Əli Məmmədov"), "16 Nov. 2026", "3-1", 0, "Duello", true)), catalog, asList(), asList(), null);
        new SavedDataStore<>(context, "events", EventCatalog.class).write(new SavedData<>(catalog, now));
        new SavedDataStore<>(context, "results-all-0", ResultsFeed.class).write(new SavedData<>(feed, now));
        new SavedDataStore<>(context, "results-duels-0", ResultsFeed.class).write(new SavedData<>(feed, now));
        context.getSharedPreferences("results-selection", Context.MODE_PRIVATE).edit().clear().commit();
    }

    private void givenRankingWithoutObservations() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        SavedDataStore<DoublesRanking> store = new SavedDataStore<>(context, "doubles-ranking", DoublesRanking.class);
        SavedData<DoublesRanking> ranking = store.read();
        if (ranking == null) throw new IllegalStateException("Saved ranking is required for the legacy-history scenario");
        store.write(new SavedData<>(new DoublesRanking(ranking.value.rows), System.currentTimeMillis()));
    }

    private void capture(String name) throws Exception {
        String prefix = InstrumentationRegistry.getArguments().getString("capturePrefix", "main");
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File directory = new File(context.getExternalFilesDir(null), "issue24");
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IllegalStateException("Cannot create screenshot directory");
        Bitmap screenshot = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        if (screenshot == null) throw new IllegalStateException("Android did not provide a screenshot");
        try (FileOutputStream output = new FileOutputStream(new File(directory, prefix + "-" + name + ".png"))) {
            if (!screenshot.compress(Bitmap.CompressFormat.PNG, 100, output)) throw new IllegalStateException("Cannot save screenshot");
        } finally {
            screenshot.recycle();
        }
    }
}
