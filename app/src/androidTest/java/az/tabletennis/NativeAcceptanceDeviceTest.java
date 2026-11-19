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
import static androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA;
import static androidx.test.espresso.matcher.ViewMatchers.isSelected;
import static androidx.test.espresso.matcher.ViewMatchers.withContentDescription;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static java.util.Arrays.asList;
import static org.hamcrest.Matchers.anything;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.hasToString;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.Rect;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

/** One installed journey connects the seven approved views, including drawer/Back context.
 * Run separately from other saved fixtures; reuseSaved repeats it after a process restart. */
@RunWith(AndroidJUnit4.class)
public class NativeAcceptanceDeviceTest {
    private static final String ILQAR = "İlqar Həsənov";
    private static final String ELIXAN = "Əlixan Əlizadə";
    private static final String XEYAL = "Xəyal Həşimli";
    private static final String EVENT = "Bakı həvəskarlar kuboku";
    private static final String LONG_EVENT = "Bakı həvəskarlar kuboku 2026 — final mərhələsi";
    private static final String SINGLE_CARD = "16 Nov. 2026 · İlqar Həsənov — Məryəm İmanova · 3-1 · " + EVENT + " · Tək · Turnir";

    @Test public void sevenViewsKeepTheirIdentitiesAndBackContextAcrossBothMainDestinations() throws Exception {
        // arrange
        givenApprovedReferenceJourney();

        // act
        try (ActivityScenario<RankingsActivity> rankings = ActivityScenario.launch(RankingsActivity.class)) {
            // assert
            onView(withId(R.id.players_mode)).check(matches(isChecked()));
            onData(anything()).inAdapterView(withId(android.R.id.list)).atPosition(1)
                    .check(matches(withContentDescription("1. İlqar Həsənov — 891 xal")));
            awaitPortraits(withContentDescription("1. İlqar Həsənov — 891 xal"), 1);
            capture("players");

            // act
            search(rankings, "ilqar");
            onData(anything()).inAdapterView(withId(android.R.id.list)).atPosition(1).perform(click());

            // assert
            onView(withId(R.id.profile_name)).check(matches(withText(ILQAR)));
            onView(withId(R.id.profile_points)).check(matches(withText("891 xal")));
            awaitPortraits(withId(android.R.id.icon), 1);
            capture("player-profile");

            // act
            onView(withId(R.id.profile_history_action)).perform(scrollTo(), click());
            onView(withId(R.id.chart_selected_value)).perform(scrollTo());

            // assert
            onView(withId(R.id.chart_selected_value)).check(matches(withText("891 xal")));
            capture("singles-history");

            // act
            onView(withContentDescription("01 noyabr — 839 xal")).perform(scrollTo(), click());

            // assert
            onView(withContentDescription("01 noyabr — 839 xal")).check(matches(isSelected()));
            onView(withId(R.id.chart_selected_value)).check(matches(withText("839 xal")));

            // act
            pressBack();
            pressBack();

            // assert
            onView(withId(R.id.search)).check(matches(withText("ilqar")));

            // act
            showRankingControls(rankings);
            onView(withId(R.id.doubles_mode)).perform(click());

            // assert
            onData(anything()).inAdapterView(withId(android.R.id.list)).atPosition(1)
                    .check(matches(withContentDescription("1. Erhan Əliyev / Elçin Mustafayev — 864 xal")));
            awaitPortraits(withContentDescription("1. Erhan Əliyev / Elçin Mustafayev — 864 xal"), 1);
            capture("doubles");

            // act
            search(rankings, "xəyal");
            onData(anything()).inAdapterView(withId(android.R.id.list)).atPosition(1).perform(click());

            // assert
            onView(withText(ELIXAN)).check(matches(withText(ELIXAN)));
            onView(withText(XEYAL)).check(matches(withText(XEYAL)));
            onView(withId(R.id.profile_points)).check(matches(withText("858")));
            onView(withId(R.id.pair_position)).check(matches(withText("#2")));
            awaitPortraits(withId(android.R.id.icon), 2);
            capture("doubles-profile");

            // act
            onView(withId(R.id.pair_chart_rank)).perform(scrollTo(), click());
            onView(withId(R.id.chart_selected_value)).perform(scrollTo());

            // assert
            onView(withId(R.id.chart_selected_value)).check(matches(withText("#2")));
            capture("doubles-rank-history");

            // act
            onView(withText(XEYAL)).perform(scrollTo(), click());

            // assert
            onView(withId(R.id.profile_name)).check(matches(withText(XEYAL)));

            // act
            pressBack();

            // assert
            onView(withId(R.id.pair_chart_rank)).check(matches(isChecked()));

            // act
            pressBack();

            // assert
            onView(withId(R.id.search)).check(matches(withText("xəyal")));

            // act
            onView(withId(R.id.open_menu)).perform(click());

            // assert
            onView(withId(R.id.destination_rankings)).check(matches(isDisplayed()));
            onView(withId(R.id.privacy_policy)).check(matches(allOf(isDisplayed(), withText(R.string.privacy_policy))));
            capture("drawer");

            // act
            onView(withId(R.id.destination_results)).perform(click());
            onView(withText("Hamısı")).perform(scrollTo(), click());

            // assert
            onView(withText("Hamısı")).check(matches(isChecked()));
            capture("results");

            // act
            onView(allOf(withText(ILQAR), isDescendantOfA(withContentDescription(SINGLE_CARD))))
                    .perform(scrollTo(), click());

            // assert
            onView(withId(R.id.match_score_left)).check(matches(withText("3")));
            onView(withId(R.id.match_score_right)).check(matches(withText("1")));
            awaitPortraits(withId(R.id.participants_left), 1);
            awaitPortraits(withId(R.id.participants_right), 1);
            capture("match");

            // act
            onView(withText("Məryəm İmanova")).perform(scrollTo(), click());

            // assert
            onView(withId(R.id.profile_name)).check(matches(withText("Məryəm İmanova")));

            // act
            pressBack();

            // assert
            onView(withId(R.id.match_score_left)).check(matches(withText("3")));
            onView(withId(R.id.match_score_right)).check(matches(withText("1")));

            // act
            pressBack();

            // assert
            onView(withContentDescription(SINGLE_CARD)).check(matches(withContentDescription(SINGLE_CARD)));

            // act
            onView(withId(R.id.event_filter)).perform(scrollTo(), click());
            onData(hasToString(LONG_EVENT)).perform(click());
            onView(withText(R.string.no_results)).perform(scrollTo());

            // assert
            onView(allOf(withText(LONG_EVENT), isDescendantOfA(withId(R.id.event_filter))))
                    .check(matches(isDisplayed()));
            onView(withText(R.string.no_results)).check(matches(isDisplayed()));
            capture("empty-results");

            // act
            onView(withId(R.id.clear_results_filters)).perform(scrollTo(), click());
            onView(withId(R.id.open_menu)).perform(click());
            onView(withId(R.id.destination_rankings)).perform(click());
            rankings.recreate();

            // assert
            onView(withId(R.id.doubles_mode)).check(matches(isChecked()));
            onView(withId(R.id.search)).check(matches(withText("xəyal")));

            // act
            showRankingControls(rankings);
            onView(withId(R.id.players_mode)).perform(click());

            // assert
            onView(withId(R.id.search)).check(matches(withText("ilqar")));
        }
    }

    private void search(ActivityScenario<RankingsActivity> scenario, String query) {
        showRankingControls(scenario);
        onView(withId(R.id.search)).perform(replaceText(query), closeSoftKeyboard());
    }

    private void showRankingControls(ActivityScenario<RankingsActivity> scenario) {
        scenario.onActivity(activity -> {
            ((android.widget.ListView) activity.findViewById(android.R.id.list)).setSelection(0);
            View input = activity.findViewById(R.id.search);
            input.requestRectangleOnScreen(new Rect(0, 0, input.getWidth(), input.getHeight()), true);
        });
    }

    private Activity currentActivity() {
        return ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).stream()
                .findFirst().orElseThrow(() -> new IllegalStateException("No resumed activity"));
    }

    private void givenApprovedReferenceJourney() throws Exception {
        if (Boolean.parseBoolean(InstrumentationRegistry.getArguments().getString("reuseSaved", "false"))) return;
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Context tests = InstrumentationRegistry.getInstrumentation().getContext();
        JSONObject reference;
        try (var input = tests.getAssets().open("acceptance/reference.json");
             var output = new ByteArrayOutputStream()) {
            copy(input, output);
            reference = new JSONObject(new String(output.toByteArray(), StandardCharsets.UTF_8));
        }
        long now = System.currentTimeMillis();
        List<RankingSnapshot.Player> players = new ArrayList<>();
        JSONObject people = new JSONObject();
        JSONArray identities = reference.getJSONArray("people");
        File portraits = new File(context.getFilesDir(), "player-portraits");
        if (!portraits.isDirectory() && !portraits.mkdirs()) throw new IllegalStateException("Cannot prepare portraits");
        MatchSummary singles = new MatchSummary(3201, asList(ILQAR), asList("Məryəm İmanova"),
                "16 Nov. 2026", "3-1", 901, EVENT, false);
        MatchSummary duel = new MatchSummary(3202, asList("Xaliq Becanov"), asList("Elşad Vahidov"),
                "16 Nov. 2026", "2-3", 0, "Duel", true);
        MatchSummary doubles = new MatchSummary(3203, asList("Erhan Əliyev", "Elçin Mustafayev"), asList(ELIXAN, XEYAL),
                "15 Nov. 2026", "3-2", 901, EVENT, false);
        MatchSummary autumn = new MatchSummary(3204, asList("Sergey Vedmid"), asList("Erhan Əliyev"),
                "15 Nov. 2026", "3-0", 903, "Payız liqası", false);
        for (int i = 0; i < identities.length(); i++) {
            JSONObject person = identities.getJSONObject(i);
            long id = person.getLong("id");
            people.put(Long.toString(id), person);
            Integer points = person.isNull("points") ? null : person.getInt("points");
            Integer rank = person.isNull("rank") ? null : person.getInt("rank");
            if (rank != null) players.add(new RankingSnapshot.Player(id, person.getString("name"), points));
            List<MatchSummary> history = id == 13 || id == 74 ? asList(singles) : asList();
            PlayerProfile profile = new PlayerProfile(id, person.getString("name"), points, history, asList(), null, null, rank);
            new SavedDataStore<>(context, "profile-" + id, PlayerProfile.class).write(new SavedData<>(profile, now));
            File portrait = new File(portraits, id + ".png");
            if (!person.isNull("portraitAsset")) {
                try (var input = tests.getAssets().open(person.getString("portraitAsset"));
                     var output = new FileOutputStream(portrait)) { copy(input, output); }
            } else if (portrait.exists() && !portrait.delete()) throw new IllegalStateException("Cannot prepare silhouette");
        }
        new RankingsStore(context).write(new RankingSnapshot(players, now));
        List<DoublesRanking.Row> pairRows = new ArrayList<>();
        JSONArray pairs = reference.getJSONArray("pairs");
        for (int i = 0; i < pairs.length(); i++) {
            JSONObject row = pairs.getJSONObject(i);
            JSONArray members = row.getJSONArray("members");
            DoublesPair pair = new DoublesPair(row.getLong("id"), player(people, members.getString(0)), player(people, members.getString(1)), null, null, true);
            pairRows.add(new DoublesRanking.Row(pair, row.getInt("rank"), row.getInt("points")));
            JSONArray medals = row.getJSONArray("medals");
            new SavedDataStore<>(context, "duo-results-" + pair.id, PairHistory.class).write(new SavedData<>(
                    new PairHistory(pair, pair.id == 151 || pair.id == 584 ? asList(doubles) : asList(), asList(), null,
                            new PairMedals(medals.getInt(0), medals.getInt(1), medals.getInt(2))), now));
        }
        DoublesPair inspectedPair = pairRows.get(1).pair;
        DoublesRanking prior = new DoublesRanking(asList(new DoublesRanking.Row(inspectedPair, 4, 806)))
                .observedAfter(null, java.time.Instant.parse("2026-11-14T12:00:00Z").toEpochMilli());
        prior = new DoublesRanking(asList(new DoublesRanking.Row(inspectedPair, 3, 820)))
                .observedAfter(prior, java.time.Instant.parse("2026-11-15T08:00:00Z").toEpochMilli());
        prior = new DoublesRanking(asList(
                new DoublesRanking.Row(pairRows.get(0).pair, 1, pairRows.get(0).points),
                new DoublesRanking.Row(inspectedPair, 4, 832),
                new DoublesRanking.Row(pairRows.get(2).pair, 2, pairRows.get(2).points)))
                .observedAfter(prior, java.time.Instant.parse("2026-11-16T09:00:00Z").toEpochMilli());
        new SavedDataStore<>(context, "doubles-ranking", DoublesRanking.class).write(new SavedData<>(
                new DoublesRanking(pairRows).observedAfter(prior,
                        java.time.Instant.parse("2026-11-16T14:30:00Z").toEpochMilli()), now));
        RatingHistory history = new RatingHistory(asList(new RatingHistory.Observation("01 noyabr", "839"),
                new RatingHistory.Observation("06 noyabr", "853"), new RatingHistory.Observation("11 noyabr", "865"),
                new RatingHistory.Observation("16 noyabr", "891")));
        new SavedDataStore<>(context, "chart-13", RatingHistory.class).write(new SavedData<>(history, now));
        new SavedDataStore<>(context, "match-3201", MatchDetails.class).write(new SavedData<>(new MatchDetails(3201,
                asList(player(people, "13")), asList(player(people, "74")), singles.date, "18:00", singles.score, EVENT), now));
        new SavedDataStore<>(context, "match-3203", MatchDetails.class).write(new SavedData<>(new MatchDetails(3203,
                asList(player(people, "14"), player(people, "22")), asList(player(people, "7"), player(people, "70")),
                doubles.date, "", doubles.score, EVENT), now));
        EventCatalog catalog = new EventCatalog(asList(new EventCatalog.Event(901, EVENT),
                new EventCatalog.Event(902, LONG_EVENT), new EventCatalog.Event(903, "Payız liqası")));
        new SavedDataStore<>(context, "events", EventCatalog.class).write(new SavedData<>(catalog, now));
        new SavedDataStore<>(context, "results-all-0", ResultsFeed.class).write(new SavedData<>(
                new ResultsFeed(asList(singles, duel, doubles, autumn), catalog, asList(), asList(), null), now));
        new SavedDataStore<>(context, "results-events-902", ResultsFeed.class).write(new SavedData<>(
                new ResultsFeed(asList(), catalog, asList(), asList(), null), now));
        context.getSharedPreferences("results-selection", Context.MODE_PRIVATE).edit().clear().commit();
    }

    private MatchDetails.PlayerLink player(JSONObject people, String id) throws Exception {
        return new MatchDetails.PlayerLink(Long.parseLong(id), people.getJSONObject(id).getString("name"));
    }

    private void copy(InputStream input, OutputStream output) throws Exception {
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
    }

    private void awaitPortraits(org.hamcrest.Matcher<View> target, int expected) {
        java.util.concurrent.atomic.AtomicBoolean ready = new java.util.concurrent.atomic.AtomicBoolean();
        long deadline = android.os.SystemClock.elapsedRealtime() + 15_000;
        do {
            onView(target).check((view, missing) -> {
                if (missing != null) throw missing;
                ready.set(renderedPortraits(view) == expected);
            });
            if (ready.get()) return;
            android.os.SystemClock.sleep(100);
        } while (android.os.SystemClock.elapsedRealtime() < deadline);
        throw new AssertionError("Expected " + expected + " cached portraits in " + target);
    }

    private int renderedPortraits(View view) {
        if (view instanceof ImageView image && image.getDrawable() instanceof BitmapDrawable) return 1;
        int count = 0;
        if (view instanceof ViewGroup group)
            for (int i = 0; i < group.getChildCount(); i++) count += renderedPortraits(group.getChildAt(i));
        return count;
    }

    private void capture(String name) throws Exception {
        CountDownLatch frame = new CountDownLatch(1);
        Properties visible = new Properties();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            Activity activity = currentActivity();
            View decor = activity.getWindow().getDecorView();
            visible.setProperty("activity", activity.getClass().getSimpleName());
            visible.setProperty("windowFocused", Boolean.toString(decor.hasWindowFocus()));
            visible.setProperty("updated", ((TextView) activity.findViewById(R.id.updated)).getText().toString());
            visible.setProperty("status", ((TextView) activity.findViewById(R.id.status)).getText().toString());
            visible.setProperty("refreshEnabled", Boolean.toString(activity.findViewById(R.id.refresh).isEnabled()));
            visible.setProperty("fontScale", Float.toString(activity.getResources().getConfiguration().fontScale));
            if (Build.VERSION.SDK_INT >= 29 && decor.isHardwareAccelerated())
                decor.getViewTreeObserver().registerFrameCommitCallback(frame::countDown);
            else decor.postOnAnimation(frame::countDown);
            decor.invalidate();
        });
        if (!frame.await(30, TimeUnit.SECONDS)) throw new IllegalStateException("Android did not commit screenshot frame");
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File directory = new File(context.getExternalFilesDir(null), "issue32");
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
        try (var output = new OutputStreamWriter(new FileOutputStream(new File(directory, prefix + "-" + name + ".properties")), StandardCharsets.UTF_8)) {
            visible.store(output, "Installed public view outputs at capture");
        }
    }
}
