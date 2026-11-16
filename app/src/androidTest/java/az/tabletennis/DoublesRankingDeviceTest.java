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

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.os.SystemClock;
import android.view.ViewGroup;
import android.widget.ImageView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Opt-in installed-app comparison with the approved Comfortable doubles reference. */
@RunWith(AndroidJUnit4.class)
public class DoublesRankingDeviceTest {
    @Test public void splitPortraitsPublishedPositionsAndWrappingPartnerNamesRenderOffline() throws Exception {
        // arrange
        if (!Boolean.parseBoolean(InstrumentationRegistry.getArguments().getString("reuseSaved", "false"))) givenSavedPartnerships();
        Bitmap ilqar = fixture("ilqar.png");
        Bitmap maryam = fixture("maryam.png");

        // act
        try (ActivityScenario<RankingsActivity> scenario = ActivityScenario.launch(RankingsActivity.class)) {
            onView(withId(R.id.doubles_mode)).perform(click());
            scrollControlsIfNeeded(scenario);

            // assert
            awaitPortrait("1. İlqar Həsənov / Məryəm İmanova — 891 xal, 2 pillə yüksəlib", 0, ilqar);
            awaitPortrait("1. İlqar Həsənov / Məryəm İmanova — 891 xal, 2 pillə yüksəlib", 1, maryam);
            onView(withText("#1")).check(matches(withText("#1")));
            onView(withText("↑ 2")).check(matches(withText("↑ 2")));
            capture("doubles");

            // act
            scenario.onActivity(activity -> ((android.widget.ListView) activity.findViewById(android.R.id.list)).setSelection(0));
            onView(withId(R.id.search)).perform(replaceText("məmmədəmin"), closeSoftKeyboard());
            scrollControlsIfNeeded(scenario);

            // assert
            onView(withContentDescription("4. Əbdürrəhman Məmmədəmin Azərbaycanlı Həsənov / Məryəm Əbdülrəhim Azərbaycanlı İmanova — 600 xal, 2 pillə geriləyib"))
                    .check(matches(hasDescendant(withText("Əbdürrəhman Məmmədəmin Azərbaycanlı Həsənov\nMəryəm Əbdülrəhim Azərbaycanlı İmanova"))));
            capture("long-names");

            // act
            scenario.recreate();
            scenario.onActivity(activity -> ((android.widget.ListView) activity.findViewById(android.R.id.list)).setSelection(0));

            // assert
            onView(withId(R.id.search)).check(matches(withText("məmmədəmin")));
        }
    }

    private void scrollControlsIfNeeded(ActivityScenario<RankingsActivity> scenario) {
        scenario.onActivity(activity -> {
            android.content.res.Configuration configuration = activity.getResources().getConfiguration();
            if (configuration.screenHeightDp < 480 || configuration.fontScale >= 1.3f)
                ((android.widget.ListView) activity.findViewById(android.R.id.list)).setSelection(1);
        });
    }

    private void givenSavedPartnerships() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        long now = System.currentTimeMillis();
        new RankingsStore(context).write(new RankingSnapshot(Arrays.asList(new RankingSnapshot.Player(13, "İlqar Həsənov", 891)), now));
        DoublesPair first = pair(110, 13, "İlqar Həsənov", 74, "Məryəm İmanova");
        DoublesPair second = pair(220, 13, "İlqar Həsənov", 90001, "Şəms Quliyeva");
        DoublesPair third = pair(330, 90002, "Işıl Əliyeva", 74, "Məryəm İmanova");
        DoublesPair fourth = pair(440, 90003, "Əbdürrəhman Məmmədəmin Azərbaycanlı Həsənov", 90004, "Məryəm Əbdülrəhim Azərbaycanlı İmanova");
        DoublesRanking previous = new DoublesRanking(Arrays.asList(new DoublesRanking.Row(second, 1, 900),
                new DoublesRanking.Row(fourth, 2, 895), new DoublesRanking.Row(first, 3, 891), new DoublesRanking.Row(third, 4, 600)))
                .observedAfter(null, now - 120_000);
        DoublesRanking current = new DoublesRanking(Arrays.asList(new DoublesRanking.Row(first, 1, 891),
                new DoublesRanking.Row(second, 2, 800), new DoublesRanking.Row(third, 3, 800), new DoublesRanking.Row(fourth, 4, 600)));
        new SavedDataStore<>(context, "doubles-ranking", DoublesRanking.class).write(new SavedData<>(current.observedAfter(previous, now), now));
        File directory = new File(context.getFilesDir(), "player-portraits");
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IllegalStateException("Cannot prepare portraits");
        givenPortrait(new File(directory, "13.png"), "ilqar.png");
        givenPortrait(new File(directory, "74.png"), "maryam.png");
    }

    private DoublesPair pair(long id, long first, String firstName, long second, String secondName) {
        return new DoublesPair(id, new MatchDetails.PlayerLink(first, firstName), new MatchDetails.PlayerLink(second, secondName), null, null, true);
    }

    private void givenPortrait(File destination, String asset) throws Exception {
        try (var input = InstrumentationRegistry.getInstrumentation().getContext().getAssets().open("portraits/" + asset);
             var output = new FileOutputStream(destination)) {
            byte[] bytes = new byte[8192];
            int count;
            while ((count = input.read(bytes)) != -1) output.write(bytes, 0, count);
        }
    }

    private Bitmap fixture(String asset) throws Exception {
        try (var input = InstrumentationRegistry.getInstrumentation().getContext().getAssets().open("portraits/" + asset)) {
            return BitmapFactory.decodeStream(input);
        }
    }

    private void awaitPortrait(String row, int half, Bitmap expected) {
        AtomicBoolean ready = new AtomicBoolean();
        long deadline = SystemClock.elapsedRealtime() + 15_000;
        do {
            onView(withContentDescription(row)).check((view, missing) -> {
                if (missing != null) throw missing;
                ImageView image = (ImageView) ((ViewGroup) view.findViewById(android.R.id.icon)).getChildAt(half);
                ready.set(image.getDrawable() instanceof BitmapDrawable bitmap && bitmap.getBitmap().sameAs(expected));
                if (!ready.get() && SystemClock.elapsedRealtime() >= deadline) throw new AssertionError("Incorrect partner portrait");
            });
            if (ready.get()) return;
            SystemClock.sleep(100);
        } while (true);
    }

    private void capture(String name) throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File directory = new File(context.getExternalFilesDir(null), "issue26");
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IllegalStateException("Cannot save screenshots");
        String prefix = InstrumentationRegistry.getArguments().getString("capturePrefix", "light");
        Bitmap screenshot = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        // API 26 UiAutomation can return null after a display resize; use Android's native capture tool.
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
