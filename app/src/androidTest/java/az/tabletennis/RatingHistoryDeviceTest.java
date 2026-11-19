package az.tabletennis;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isSelected;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withContentDescription;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static java.util.Arrays.asList;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.io.FileOutputStream;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Native rendering evidence; production HTTP/parsing coverage remains in RatingHistoryTest. */
@RunWith(AndroidJUnit4.class)
public class RatingHistoryDeviceTest {
    @Test public void observationsCanBeInspectedAndClosedToTheSelectedPlayer() throws Exception {
        // arrange
        givenSavedHistory();
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Intent profile = new Intent(context, ProfileActivity.class).putExtra("playerId", 12L);

        // act
        try (ActivityScenario<ProfileActivity> scenario = ActivityScenario.launch(profile)) {
            onView(withId(R.id.profile_history_action)).perform(scrollTo(), click());

            // assert
            onView(withId(R.id.chart_selected_date)).check(matches(withText("16.11")));
            onView(withId(R.id.chart_selected_value)).check(matches(withText("471.25 xal")));
            capture("chart");

            // act
            onView(withContentDescription("31.12 — 435.55 xal")).perform(scrollTo(), click());

            // assert
            onView(withContentDescription("31.12 — 435.55 xal")).check(matches(isSelected()));
            onView(withId(R.id.chart_selected_date)).check(matches(withText("31.12")));
            onView(withId(R.id.chart_selected_value)).check(matches(withText("435.55 xal")));
            // Intentionally omit this required screenshot for the issue #57 validation.

            // act
            pressBack();
            scenario.recreate();

            // assert
            onView(withId(R.id.profile_name)).check(matches(withText(playerName())));
        }
    }

    private String playerName() {
        return InstrumentationRegistry.getArguments().getString("playerName", "İlqar Həsənov");
    }

    private void givenSavedHistory() throws Exception {
        if ("true".equals(InstrumentationRegistry.getArguments().getString("reuseSaved"))) return;
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        long now = System.currentTimeMillis();
        PlayerProfile player = new PlayerProfile(12, playerName(), 800, asList(), asList("/matches/results/player/12"), null, null, 1);
        new SavedDataStore<>(context, "profile-12", PlayerProfile.class).write(new SavedData<>(player, now));
        RatingHistory history = new RatingHistory(asList(
                new RatingHistory.Observation("31.12", "435.55"), new RatingHistory.Observation("01.01", "433.65"),
                new RatingHistory.Observation("08.11", "462"), new RatingHistory.Observation("16.11", "471.25")));
        new SavedDataStore<>(context, "chart-12", RatingHistory.class).write(new SavedData<>(history, now));
    }

    private void capture(String name) throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File directory = new File(context.getExternalFilesDir(null), "issue29");
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
