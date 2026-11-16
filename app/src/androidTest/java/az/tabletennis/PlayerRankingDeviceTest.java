package az.tabletennis;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.hasDescendant;
import static androidx.test.espresso.matcher.ViewMatchers.withContentDescription;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.allOf;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.os.SystemClock;
import android.view.View;
import android.widget.ImageView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.espresso.matcher.BoundedMatcher;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.io.FileOutputStream;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Opt-in: installed native layout and cached portrait identity, including a separate process relaunch. */
@RunWith(AndroidJUnit4.class)
public class PlayerRankingDeviceTest {
    @Test public void comfortableRowsRenderFullValuesAndTheCorrectOfflinePortraits() throws Exception {
        // arrange
        if (!Boolean.parseBoolean(InstrumentationRegistry.getArguments().getString("reuseSaved", "false"))) givenSavedRankingAndPortraits();
        Bitmap ilqar = fixture("ilqar.png");
        Bitmap maryam = fixture("maryam.png");

        // act
        try (ActivityScenario<RankingsActivity> scenario = ActivityScenario.launch(RankingsActivity.class)) {
            // assert
            awaitPortrait("1. İlqar Həsənov — 891 xal", ilqar);
            awaitPortrait("2. Məryəm İmanova — 800 xal", maryam);
            onView(withText("891 xal")).check(matches(withText("891 xal")));
            onView(withText("#1")).check(matches(withText("#1")));
            capture("players");

            // act
            onView(withId(R.id.search)).perform(replaceText("məmmədəmin"), closeSoftKeyboard());

            // assert
            onView(withContentDescription("3. Əbdürrəhman Məmmədəmin Azərbaycanlı Həsənov — 600 xal"))
                    .check(matches(hasDescendant(withText("Əbdürrəhman Məmmədəmin Azərbaycanlı Həsənov"))));
            capture("long-name");

            // act
            scenario.recreate();

            // assert
            onView(withId(R.id.search)).check(matches(withText("məmmədəmin")));
        }
    }

    private void givenSavedRankingAndPortraits() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        new RankingsStore(context).write(new RankingSnapshot(java.util.Arrays.asList(
                new RankingSnapshot.Player(13, "İlqar Həsənov", 891),
                new RankingSnapshot.Player(74, "Məryəm İmanova", 800),
                new RankingSnapshot.Player(90001, "Əbdürrəhman Məmmədəmin Azərbaycanlı Həsənov", 600)), System.currentTimeMillis()));
        File directory = new File(context.getFilesDir(), "player-portraits");
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IllegalStateException("Cannot prepare cached portraits");
        givenPortrait(new File(directory, "13.png"), "ilqar.png");
        givenPortrait(new File(directory, "74.png"), "maryam.png");
    }

    private void givenPortrait(File destination, String asset) throws Exception {
        try (var input = InstrumentationRegistry.getInstrumentation().getContext().getAssets().open("portraits/" + asset);
             var output = new FileOutputStream(destination)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
        }
    }

    private Bitmap fixture(String asset) throws Exception {
        try (var input = InstrumentationRegistry.getInstrumentation().getContext().getAssets().open("portraits/" + asset)) {
            return BitmapFactory.decodeStream(input);
        }
    }

    private void awaitPortrait(String row, Bitmap expected) {
        long deadline = SystemClock.elapsedRealtime() + 15_000;
        java.util.concurrent.atomic.AtomicBoolean ready = new java.util.concurrent.atomic.AtomicBoolean();
        do {
            onView(withContentDescription(row)).check((view, missing) -> {
                if (missing != null) throw missing;
                ImageView photo = view.findViewById(android.R.id.icon);
                ready.set(photo.getDrawable() instanceof BitmapDrawable bitmap && bitmap.getBitmap().sameAs(expected));
            });
            if (ready.get()) break;
            SystemClock.sleep(100);
        } while (SystemClock.elapsedRealtime() < deadline);
        onView(withContentDescription(row)).check(matches(hasDescendant(allOf(withId(android.R.id.icon), portrait(expected)))));
    }

    private Matcher<View> portrait(Bitmap expected) {
        return new BoundedMatcher<View, ImageView>(ImageView.class) {
            @Override public void describeTo(Description description) { description.appendText("the intended player's cached portrait"); }
            @Override protected boolean matchesSafely(ImageView view) {
                return view.getDrawable() instanceof BitmapDrawable bitmap && bitmap.getBitmap().sameAs(expected);
            }
        };
    }

    private void capture(String name) throws Exception {
        String prefix = InstrumentationRegistry.getArguments().getString("capturePrefix", "light");
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File directory = new File(context.getExternalFilesDir(null), "issue25");
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
