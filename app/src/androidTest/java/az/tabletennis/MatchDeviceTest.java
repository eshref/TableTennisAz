package az.tabletennis;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static java.util.Arrays.asList;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/** Installed rendering and real Android Back; production HTTP coverage remains in the host journeys. */
@RunWith(Parameterized.class)
public class MatchDeviceTest {
    private final MatchDetails match;
    private final String leftScore;
    private final String rightScore;

    public MatchDeviceTest(String kind, MatchDetails match, String leftScore, String rightScore) {
        this.match = match;
        this.leftScore = leftScore;
        this.rightScore = rightScore;
    }

    @Parameterized.Parameters(name = "{0}") public static Collection<Object[]> scenarios() {
        return asList(new Object[][] {
                {"singles", new MatchDetails(502,
                        asList(new MatchDetails.PlayerLink(12, "İlqar Həsənov")),
                        asList(new MatchDetails.PlayerLink(11, "Şəms Quliyeva")),
                        "16 Nov. 2026", "", "1-3", "Bakı həvəskarlar kuboku"), "1", "3"},
                {"doubles", new MatchDetails(501,
                        asList(new MatchDetails.PlayerLink(12, "İlqar Həsənov"), new MatchDetails.PlayerLink(13, "Əli Məmmədov")),
                        asList(new MatchDetails.PlayerLink(11, "Şəms Quliyeva"), new MatchDetails.PlayerLink(14, "Işıl Əliyeva")),
                        "15 Nov. 2026", "18:00", "3-2", "Bakı həvəskarlar kuboku"), "3", "2"}
        });
    }

    @Test public void scoreboardLinksEveryParticipantAndReturnsToTheSavedMatchAfterRecreation() throws Exception {
        // arrange
        givenSavedMatch();
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Intent intent = new Intent(context, MatchActivity.class).putExtra("matchId", match.id);
        java.util.List<MatchDetails.PlayerLink> players = new java.util.ArrayList<>(match.left);
        players.addAll(match.right);

        // act
        try (ActivityScenario<MatchActivity> scenario = ActivityScenario.launch(intent)) {
            // assert
            onView(withId(R.id.match_score_left)).check(matches(withText(leftScore)));
            onView(withId(R.id.match_score_right)).check(matches(withText(rightScore)));
            capture();

            for (MatchDetails.PlayerLink player : players) {
                // act
                onView(withText(player.name)).perform(scrollTo(), click());

                // assert
                onView(withId(R.id.profile_name)).check(matches(withText(player.name)));

                // act
                pressBack();

                // assert
                onView(withId(R.id.match_score_left)).check(matches(withText(leftScore)));
                onView(withId(R.id.match_score_right)).check(matches(withText(rightScore)));
            }

            // act
            scenario.recreate();

            // assert
            onView(withId(R.id.match_score_left)).check(matches(withText(leftScore)));
            onView(withId(R.id.match_score_right)).check(matches(withText(rightScore)));
        }
    }

    private void givenSavedMatch() throws Exception {
        if ("true".equals(InstrumentationRegistry.getArguments().getString("reuseSaved"))) return;
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        long now = System.currentTimeMillis();
        new SavedDataStore<>(context, "match-" + match.id, MatchDetails.class).write(new SavedData<>(match, now));
        java.util.List<MatchDetails.PlayerLink> players = new java.util.ArrayList<>(match.left);
        players.addAll(match.right);
        for (MatchDetails.PlayerLink player : players) {
            PlayerProfile profile = new PlayerProfile(player.id, player.name, null, asList(), asList(), null);
            new SavedDataStore<>(context, "profile-" + player.id, PlayerProfile.class).write(new SavedData<>(profile, now));
        }
        File portraits = new File(context.getFilesDir(), "player-portraits");
        if (!portraits.isDirectory() && !portraits.mkdirs()) throw new IllegalStateException("Cannot prepare portraits");
        try (var input = InstrumentationRegistry.getInstrumentation().getContext().getAssets().open("portraits/ilqar.png");
             var output = new FileOutputStream(new File(portraits, "12.png"))) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
        }
    }

    private void capture() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File directory = new File(context.getExternalFilesDir(null), "issue30");
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
        try (var output = new FileOutputStream(new File(directory, prefix + "-" + match.id + ".png"))) {
            if (!screenshot.compress(Bitmap.CompressFormat.PNG, 100, output)) throw new IllegalStateException("Cannot save screenshot");
        } finally { screenshot.recycle(); }
    }
}
