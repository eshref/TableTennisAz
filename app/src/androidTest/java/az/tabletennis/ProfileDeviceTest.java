package az.tabletennis;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.onData;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayingAtLeast;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static androidx.test.espresso.matcher.ViewMatchers.withContentDescription;
import static java.util.Arrays.asList;
import static org.hamcrest.Matchers.allOf;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.io.FileOutputStream;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Installed profile rendering, separate chart and real Android Back; HTTP coverage lives in host integration tests. */
@RunWith(AndroidJUnit4.class)
public class ProfileDeviceTest {
    @Test public void selectedPlayerChartMatchAndParticipantReturnThroughTheSavedJourney() throws Exception {
        // arrange
        String name = InstrumentationRegistry.getArguments().getString("playerName", "İlqar Həsənov");
        givenSavedJourney(name);

        // act
        try (ActivityScenario<RankingsActivity> scenario = ActivityScenario.launch(RankingsActivity.class)) {
            onView(withId(R.id.search)).perform(replaceText(name), closeSoftKeyboard());
            onData(org.hamcrest.Matchers.equalTo("1. " + name + " — 800 xal"))
                    .inAdapterView(withId(android.R.id.list)).perform(click());

            // assert
            onView(withId(R.id.profile_name)).check(matches(withText(name)));
            onView(withId(R.id.profile_points)).check(matches(withText("800 xal")));
            onView(withId(R.id.profile_position)).check(matches(withText("#1")));
            awaitPortrait();
            capture("profile");

            // act
            onView(withId(R.id.profile_history_action)).perform(scrollTo(), click());

            // assert
            onView(withId(R.id.chart_observation)).check(matches(withContentDescription("31.12 — 435.55 xal")));

            // act
            pressBack();
            onView(withId(R.id.match_stage_badge)).perform(scrollTo());

            // assert
            onView(withId(R.id.profile_name)).check(matches(withText(name)));
            onView(withId(R.id.match_stage_badge)).check(matches(allOf(withText(R.string.stage_group), isDisplayingAtLeast(100))));
            onView(withContentDescription("16 Nov. 2026 · " + name
                    + " — Şəms Quliyeva · 3-1 · Bakı həvəskarlar kuboku · Tək · Turnir · Qrup oyunu"))
                    .check(matches(withId(R.id.match_card)));
            capture("history");

            // act
            onView(withText("Şəms Quliyeva")).perform(scrollTo(), click());

            // assert
            onView(withId(R.id.match_score_left)).check(matches(withText("3")));
            onView(withId(R.id.match_score_right)).check(matches(withText("1")));

            // act
            onView(withText("Şəms Quliyeva")).perform(scrollTo(), click());

            // assert
            onView(withId(R.id.profile_name)).check(matches(withText("Şəms Quliyeva")));
            onView(withText(R.string.position_unavailable)).check(matches(withText(R.string.position_unavailable)));

            // act
            pressBack();
            pressBack();

            // assert
            onView(withId(R.id.profile_name)).check(matches(withText(name)));

            // act
            pressBack();
            scenario.recreate();

            // assert
            onView(withId(R.id.search)).check(matches(withText(name)));
            onData(org.hamcrest.Matchers.equalTo("1. " + name + " — 800 xal"))
                    .inAdapterView(withId(android.R.id.list))
                    .check(matches(withContentDescription("1. " + name + " — 800 xal")));
        }
    }

    private void awaitPortrait() throws Exception {
        boolean fallback = "true".equals(InstrumentationRegistry.getArguments().getString("fallback"));
        Bitmap expected;
        try (var input = InstrumentationRegistry.getInstrumentation().getContext().getAssets().open("portraits/ilqar.png")) {
            expected = BitmapFactory.decodeStream(input);
        }
        org.hamcrest.Matcher<android.view.View> portrait = new org.hamcrest.TypeSafeMatcher<>() {
            @Override public void describeTo(org.hamcrest.Description description) {
                description.appendText("the selected player's portrait or requested local silhouette");
            }
            @Override protected boolean matchesSafely(android.view.View view) {
                if (!(view instanceof android.widget.ImageView photo)) return false;
                return fallback ? photo.getDrawable() instanceof android.graphics.drawable.VectorDrawable
                        : photo.getDrawable() instanceof android.graphics.drawable.BitmapDrawable image && image.getBitmap().sameAs(expected);
            }
        };
        java.util.concurrent.atomic.AtomicBoolean ready = new java.util.concurrent.atomic.AtomicBoolean();
        long deadline = android.os.SystemClock.uptimeMillis() + 10000;
        while (!ready.get() && android.os.SystemClock.uptimeMillis() < deadline) {
            onView(withId(android.R.id.icon)).check((view, failure) -> ready.set(portrait.matches(view)));
            if (!ready.get()) android.os.SystemClock.sleep(100);
        }
        onView(withId(android.R.id.icon)).check(matches(portrait));
        expected.recycle();
    }

    private void givenSavedJourney(String name) throws Exception {
        if ("true".equals(InstrumentationRegistry.getArguments().getString("reuseSaved"))) return;
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        long now = System.currentTimeMillis();
        new RankingsStore(context).write(new RankingSnapshot(asList(new RankingSnapshot.Player(12, name, 800)), now));
        MatchSummary single = new MatchSummary(502, asList(name), asList("Şəms Quliyeva"),
                "16 Nov. 2026", "3-1", 901, "Bakı həvəskarlar kuboku", false, false, TournamentStage.GROUP);
        MatchSummary doubles = new MatchSummary(501, asList(name, "Əli Məmmədov"), asList("Məryəm İmanova", "Işıl Əliyeva"),
                "15 Nov. 2026", "2-3", 0, "Duello", true);
        PlayerProfile profile = new PlayerProfile(12, name, 800, asList(single, doubles),
                asList("/matches/results/player/12"), null, null, 1);
        PlayerProfile participant = new PlayerProfile(11, "Şəms Quliyeva", 950, asList(), asList("/matches/results/player/11"), null);
        new SavedDataStore<>(context, "profile-12", PlayerProfile.class).write(new SavedData<>(profile, now));
        new SavedDataStore<>(context, "profile-11", PlayerProfile.class).write(new SavedData<>(participant, now));
        RatingHistory chart = new RatingHistory(asList(new RatingHistory.Observation("31.12", "435.55")));
        new SavedDataStore<>(context, "chart-12", RatingHistory.class).write(new SavedData<>(chart, now));
        MatchDetails details = new MatchDetails(502, asList(new MatchDetails.PlayerLink(12, name)),
                asList(new MatchDetails.PlayerLink(11, "Şəms Quliyeva")), "16 Nov. 2026", "", "3-1", "Bakı həvəskarlar kuboku");
        new SavedDataStore<>(context, "match-502", MatchDetails.class).write(new SavedData<>(details, now));
        File portraits = new File(context.getFilesDir(), "player-portraits");
        if (!portraits.isDirectory() && !portraits.mkdirs()) throw new IllegalStateException("Cannot prepare portraits");
        File photo = new File(portraits, "12.png");
        if ("true".equals(InstrumentationRegistry.getArguments().getString("fallback"))) {
            if (photo.exists() && !photo.delete()) throw new IllegalStateException("Cannot prepare fallback");
        } else {
            try (var input = InstrumentationRegistry.getInstrumentation().getContext().getAssets().open("portraits/ilqar.png");
                 var output = new FileOutputStream(photo)) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            }
        }
    }

    private void capture(String name) throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File directory = new File(context.getExternalFilesDir(null), "issue28");
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IllegalStateException("Cannot save screenshots");
        String prefix = InstrumentationRegistry.getArguments().getString("capturePrefix", "light");
        ScreenshotCheckpoint.captureWindow(new File(directory, prefix + "-" + name + ".png"));
    }
}
