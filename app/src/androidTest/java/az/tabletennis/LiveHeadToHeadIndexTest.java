package az.tabletennis;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.hasSibling;
import static androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.espresso.NoMatchingViewException;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import org.hamcrest.Matcher;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class LiveHeadToHeadIndexTest {
    @Test public void freshInstallShowsThePublishedSinglesScoreWithinFiveSeconds() throws Exception {
        try (ActivityScenario<HeadToHeadActivity> scenario = ActivityScenario.launch(HeadToHeadActivity.class)) {
            selectPlayer(R.id.head_to_head_first, "Səfərov", "Əşrəf Səfərov", 15);
            selectPlayer(R.id.head_to_head_second, "Etibar", "Etibar Yunusov", 20);
            verifyPublishedScore("singles");
        }
    }

    @Test public void freshInstallShowsThePublishedDoublesScoreWithinFiveSeconds() throws Exception {
        try (ActivityScenario<HeadToHeadActivity> scenario = ActivityScenario.launch(HeadToHeadActivity.class)) {
            onView(withId(R.id.head_to_head_doubles)).perform(click());
            selectPlayer(R.id.head_to_head_left_first, "Mustafayev", "Elçin Mustafayev", 22);
            selectPlayer(R.id.head_to_head_left_second, "Erhan", "Erhan Əliyev", 14);
            selectPlayer(R.id.head_to_head_right_first, "Həşimli", "Xəyal Həşimli", 70);
            selectPlayer(R.id.head_to_head_right_second, "Əlixan", "Əlixan Əlizadə", 7);
            verifyPublishedScore("doubles");
        }
    }

    private void verifyPublishedScore(String format) throws Exception {
        // arrange
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        ConnectivityManager connectivity = context.getSystemService(ConnectivityManager.class);
        NetworkCapabilities network = connectivity.getNetworkCapabilities(connectivity.getActiveNetwork());
        if (network == null || !network.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))
            throw new IllegalStateException("The live index check requires validated internet access");

        var search = onView(withText("Axtar"));
        search.perform(scrollTo());

        // act
        long start = SystemClock.elapsedRealtime();
        search.perform(click());

        // assert
        Matcher<View> scoreView = allOf(isAssignableFrom(TextView.class),
                hasSibling(withText("Qələbə sayı")), isDisplayed());
        await(scoreView, start + 5_000);
        long elapsed = SystemClock.elapsedRealtime() - start;
        String[] score = new String[1];
        onView(scoreView).check((view, missing) -> {
            if (missing != null) throw missing;
            score[0] = ((TextView) view).getText().toString();
        });
        String[] refresh = new String[1];
        onView(withText(startsWith("Mənbənin yenilənmə tarixi"))).perform(scrollTo())
                .check((view, missing) -> {
                    if (missing != null) throw missing;
                    refresh[0] = ((TextView) view).getText().toString();
                });
        Log.i("HeadToHeadIndexLive", format + " on " + Build.MODEL + " (Android API " + Build.VERSION.SDK_INT + "), "
                + network + ", " + refresh[0] + ", Qələbə sayı " + score[0] + ", " + elapsed + " ms");
        ScreenshotCheckpoint.captureWindow(new File(context.getFilesDir(), "live-" + format + "-index.png"));
        assertTrue("Published win score: " + score[0], score[0].matches("[0-9]+–[0-9]+") && !"0–0".equals(score[0]));
        assertTrue("Source refresh date: " + refresh[0], refresh[0].matches(".*[0-9]{2}\\.[0-9]{2}\\.[0-9]{4}"));
        assertTrue("Elapsed from Axtar to the final score: " + elapsed + " ms", elapsed <= 5_000);
        onView(withText("Qələbə sayı")).check(matches(isDisplayed()));
    }

    private void selectPlayer(int field, String query, String name, long id) {
        onView(withId(field)).perform(scrollTo(), click(), replaceText(query), closeSoftKeyboard());
        Matcher<View> suggestion = allOf(isAssignableFrom(Button.class),
                anyOf(withText(name), withText(name + " · Profil " + id)));
        await(suggestion, SystemClock.elapsedRealtime() + 30_000);
        onView(suggestion).perform(scrollTo(), click());
    }

    private void await(Matcher<View> matcher, long deadline) {
        while (true) {
            try {
                onView(matcher).check((view, missing) -> { if (missing != null) throw missing; });
                return;
            } catch (NoMatchingViewException missing) {
                if (SystemClock.elapsedRealtime() >= deadline) throw missing;
                SystemClock.sleep(25);
            }
        }
    }
}
