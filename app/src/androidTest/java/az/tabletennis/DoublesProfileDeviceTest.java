package az.tabletennis;

import static androidx.test.espresso.Espresso.onData;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isChecked;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withContentDescription;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static java.util.Arrays.asList;
import static org.hamcrest.Matchers.anything;
import static org.hamcrest.Matchers.endsWith;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.VectorDrawable;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;
import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Installed saved-data rendering and Android navigation; real HTTP acquisition is covered by host workflows. */
@RunWith(AndroidJUnit4.class)
public class DoublesProfileDeviceTest {
    private static final String FIRST = "Əlixan Əlizadə";
    private static final String SECOND = "Xəyal Həşimli";
    private static final String LONG_FIRST = "Əbdürrəhman Məmmədəmin Azərbaycanlı Həsənov";
    private static final String LONG_SECOND = "Məryəm Əbdülrəhim Azərbaycanlı İmanova";

    @Test public void partnershipPhotosInlineModesPartnersAndMatchReturnToTheSearchedDoublesContext() throws Exception {
        // arrange
        givenSavedJourney();

        // act
        try (ActivityScenario<RankingsActivity> scenario = ActivityScenario.launch(RankingsActivity.class)) {
            onView(withId(R.id.doubles_mode)).perform(click());
            search(scenario, "xəyal");
            onView(withId(R.id.open_menu)).perform(click());

            // assert
            onView(withId(R.id.destination_rankings)).check(matches(isDisplayed()));
            capture("drawer");

            // act
            pressBack();
            onData(anything()).inAdapterView(withId(android.R.id.list)).atPosition(1).perform(click());

            // assert
            onView(withText(FIRST)).check(matches(withText(FIRST)));
            onView(withText(SECOND)).check(matches(withText(SECOND)));
            onView(withId(R.id.profile_points)).check(matches(withText("858")));
            onView(withId(R.id.pair_position)).check(matches(withText("#2")));
            onView(withId(R.id.pair_gold)).check(matches(withContentDescription("Qızıl medal: 41")));
            onView(withId(R.id.pair_silver)).check(matches(withContentDescription("Gümüş medal: 10")));
            onView(withId(R.id.pair_bronze)).check(matches(withContentDescription("Bürünc medal: 12")));
            awaitPortraits();
            capture("profile");

            // act
            onView(withId(R.id.chart_selected_value)).perform(scrollTo());
            capture("points");
            onView(withId(R.id.pair_chart_rank)).perform(scrollTo(), click());

            // assert
            onView(withId(R.id.chart_selected_value)).check(matches(withText("#2")));

            // act
            onView(withId(R.id.chart_selected_value)).perform(scrollTo());
            capture("rank");
            onView(withText(endsWith("— #9"))).perform(scrollTo(), click());

            // assert
            onView(withId(R.id.chart_selected_value)).check(matches(withText("#9")));

            // act
            onView(withId(R.id.pair_chart_points)).perform(scrollTo(), click());

            // assert
            onView(withId(R.id.chart_selected_value)).check(matches(withText("800 xal")));

            // act
            onView(withId(R.id.pair_chart_rank)).perform(click());
            recreateProfile();

            // assert
            onView(withId(R.id.pair_chart_rank)).check(matches(isChecked()));

            // act
            onView(withText(FIRST)).perform(scrollTo(), click());

            // assert
            onView(withId(R.id.profile_name)).check(matches(withText(FIRST)));

            // act
            pressBack();
            onView(withText(SECOND)).perform(scrollTo(), click());

            // assert
            onView(withId(R.id.profile_name)).check(matches(withText(SECOND)));

            // act
            pressBack();
            onView(withText(R.string.match_history)).perform(scrollTo());
            onView(withText("Bakı həvəskarlar kuboku")).perform(scrollTo());
            capture("matches");
            onView(withText("Bakı həvəskarlar kuboku")).perform(scrollTo(), click());

            // assert
            onView(withId(R.id.match_score_left)).check(matches(withText("3")));
            onView(withId(R.id.match_score_right)).check(matches(withText("2")));

            // act
            pressBack();

            // assert
            onView(withId(R.id.pair_chart_rank)).check(matches(isChecked()));
            onView(withId(R.id.pair_position)).check(matches(withText("#2")));

            // act
            pressBack();
            scenario.recreate();

            // assert
            onView(withId(R.id.doubles_mode)).check(matches(isChecked()));
            onView(withId(R.id.search)).check(matches(withText("xəyal")));

            // act
            search(scenario, "məmmədəmin");
            onData(anything()).inAdapterView(withId(android.R.id.list)).atPosition(1).perform(click());

            // assert
            onView(withText(LONG_FIRST)).check(matches(withText(LONG_FIRST)));
            onView(withText(LONG_SECOND)).check(matches(withText(LONG_SECOND)));
            onView(withId(R.id.pair_gold)).check(matches(withText("—")));
            onView(withId(R.id.pair_silver)).check(matches(withText("0")));
            onView(withId(android.R.id.icon)).check((view, missing) -> {
                if (missing != null) throw missing;
                ViewGroup avatar = (ViewGroup) view;
                for (int i = 0; i < 2; i++) {
                    if (!(((ImageView) avatar.getChildAt(i)).getDrawable() instanceof VectorDrawable))
                        throw new AssertionError("Missing independent partner silhouette");
                }
            });

            // act
            onView(withText(LONG_SECOND)).perform(scrollTo());
            capture("long-names");
            pressBack();
        }
    }

    private void search(ActivityScenario<RankingsActivity> scenario, String query) {
        scenario.onActivity(activity -> {
            android.widget.ListView list = activity.findViewById(android.R.id.list);
            list.setSelection(0);
            View search = activity.findViewById(R.id.search);
            search.requestRectangleOnScreen(new Rect(0, 0, search.getWidth(), search.getHeight()), true);
        });
        onView(withId(R.id.search)).perform(replaceText(query), closeSoftKeyboard());
    }

    private void recreateProfile() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            for (Activity activity : ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)) {
                if (activity instanceof PairResultsActivity) { activity.recreate(); return; }
            }
            throw new IllegalStateException("No active partnership profile to recreate");
        });
    }

    private void givenSavedJourney() throws Exception {
        if (Boolean.parseBoolean(InstrumentationRegistry.getArguments().getString("reuseSaved", "false"))) return;
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        long now = System.currentTimeMillis();
        new RankingsStore(context).write(new RankingSnapshot(asList(new RankingSnapshot.Player(7, FIRST, 600)), now));
        DoublesPair pair = pair(151, 7, FIRST, 70, SECOND);
        DoublesPair shared = pair(152, 7, FIRST, 13, "İlqar Həsənov");
        DoublesPair longNames = pair(440, 90003, LONG_FIRST, 90004, LONG_SECOND);
        DoublesRanking first = new DoublesRanking(asList(new DoublesRanking.Row(pair, 9, 800))).observedAfter(null, now - 172_800_000);
        DoublesRanking middle = new DoublesRanking(asList(new DoublesRanking.Row(pair, 3, 850))).observedAfter(first, now - 120_000);
        DoublesRanking current = new DoublesRanking(asList(new DoublesRanking.Row(shared, 1, 900),
                new DoublesRanking.Row(pair, 2, 858), new DoublesRanking.Row(longNames, 4, 600))).observedAfter(middle, now);
        new SavedDataStore<>(context, "doubles-ranking", DoublesRanking.class).write(new SavedData<>(current, now));
        MatchSummary tournament = new MatchSummary(501, asList(FIRST, SECOND), asList("İlqar Həsənov", "Məryəm İmanova"),
                "16 Nov. 2026", "3-2", 901, "Bakı həvəskarlar kuboku", false);
        MatchSummary duel = new MatchSummary(502, asList("İlqar Həsənov", "Məryəm İmanova"), asList(SECOND, FIRST),
                "15 Nov. 2026", "2-3", 0, "Duello", true);
        new SavedDataStore<>(context, "duo-results-151", PairHistory.class).write(new SavedData<>(
                new PairHistory(pair, asList(tournament, duel), asList("/matches/results/duo/151"),
                        "/matches/results/duo/151?page=2", new PairMedals(41, 10, 12)), now));
        new SavedDataStore<>(context, "duo-results-440", PairHistory.class).write(new SavedData<>(
                new PairHistory(longNames, asList(), asList("/matches/results/duo/440"), null, new PairMedals(null, 0, 1)), now));
        new SavedDataStore<>(context, "profile-7", PlayerProfile.class).write(new SavedData<>(
                new PlayerProfile(7, FIRST, 600, asList(), asList(), null), now));
        new SavedDataStore<>(context, "profile-70", PlayerProfile.class).write(new SavedData<>(
                new PlayerProfile(70, SECOND, 700, asList(), asList(), null), now));
        new SavedDataStore<>(context, "match-501", MatchDetails.class).write(new SavedData<>(new MatchDetails(501,
                asList(pair.first, pair.second), asList(new MatchDetails.PlayerLink(13, "İlqar Həsənov"),
                new MatchDetails.PlayerLink(74, "Məryəm İmanova")), tournament.date, "18:00", tournament.score, tournament.eventName), now));
        File portraits = new File(context.getFilesDir(), "player-portraits");
        if (!portraits.isDirectory() && !portraits.mkdirs()) throw new IllegalStateException("Cannot prepare portraits");
        givenPortrait(new File(portraits, "7.png"), "elixan.png");
        givenPortrait(new File(portraits, "70.png"), "xeyal.png");
        for (long id : new long[] {90003, 90004}) {
            File absent = new File(portraits, id + ".png");
            if (absent.exists() && !absent.delete()) throw new IllegalStateException("Cannot prepare missing portrait");
        }
    }

    private DoublesPair pair(long id, long firstId, String first, long secondId, String second) {
        return new DoublesPair(id, new MatchDetails.PlayerLink(firstId, first), new MatchDetails.PlayerLink(secondId, second), null, null, true);
    }

    private Bitmap fixture(String asset) throws Exception {
        try (var input = InstrumentationRegistry.getInstrumentation().getContext().getAssets().open("portraits/" + asset)) {
            Bitmap source = BitmapFactory.decodeStream(input);
            Bitmap thumbnail = Bitmap.createScaledBitmap(source, 70, 105, true);
            if (source != thumbnail) source.recycle();
            return thumbnail;
        }
    }

    private void givenPortrait(File destination, String asset) throws Exception {
        Bitmap portrait = fixture(asset);
        try (var output = new FileOutputStream(destination)) {
            if (!portrait.compress(Bitmap.CompressFormat.PNG, 100, output)) throw new IllegalStateException("Cannot prepare portrait");
        } finally { portrait.recycle(); }
    }

    private void awaitPortraits() throws Exception {
        Bitmap first = fixture("elixan.png");
        Bitmap second = fixture("xeyal.png");
        AtomicBoolean ready = new AtomicBoolean();
        long deadline = SystemClock.elapsedRealtime() + 15_000;
        try {
            do {
                onView(withId(android.R.id.icon)).check((view, missing) -> {
                    if (missing != null) throw missing;
                    ViewGroup avatar = (ViewGroup) view;
                    ImageView left = (ImageView) avatar.getChildAt(0);
                    ImageView right = (ImageView) avatar.getChildAt(1);
                    ready.set(left.getDrawable() instanceof BitmapDrawable a && right.getDrawable() instanceof BitmapDrawable b
                            && a.getBitmap().sameAs(first) && b.getBitmap().sameAs(second));
                });
                if (ready.get()) return;
                SystemClock.sleep(100);
            } while (SystemClock.elapsedRealtime() < deadline);
            throw new AssertionError("Incorrect partnership portraits");
        } finally { first.recycle(); second.recycle(); }
    }

    private void capture(String name) throws Exception {
        CountDownLatch committed = new CountDownLatch(1);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            for (Activity activity : ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)) {
                View root = activity.getWindow().getDecorView();
                if (android.os.Build.VERSION.SDK_INT >= 29 && root.isHardwareAccelerated())
                    root.getViewTreeObserver().registerFrameCommitCallback(committed::countDown);
                else root.postOnAnimation(committed::countDown);
                root.invalidate();
                return;
            }
            throw new IllegalStateException("No resumed activity to capture");
        });
        if (!committed.await(30, TimeUnit.SECONDS)) throw new IllegalStateException("Android did not commit the screenshot frame");
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File directory = new File(context.getExternalFilesDir(null), "issue31");
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
        try (var output = new FileOutputStream(new File(directory, prefix + "-" + name + ".png"))) {
            if (!screenshot.compress(Bitmap.CompressFormat.PNG, 100, output)) throw new IllegalStateException("Cannot save screenshot");
        } finally { screenshot.recycle(); }
    }
}
