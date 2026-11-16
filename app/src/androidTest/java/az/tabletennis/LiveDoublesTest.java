package az.tabletennis;

import static androidx.test.espresso.Espresso.onData;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.hasDescendant;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.anything;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;

import android.content.Context;
import android.os.SystemClock;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Opt-in live compatibility check for doubles HTML, pair JSON, and pair history. */
@RunWith(AndroidJUnit4.class)
public class LiveDoublesTest {
    @Test public void liveDoublesShowFullNamesAndOpenTheirPublishedPairHistory() {
        // arrange
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        for (java.io.File file : context.getFilesDir().listFiles()) {
            if ((file.getName().startsWith("saved-") || file.getName().startsWith("rankings.json")) && !file.delete()) {
                throw new IllegalStateException("Could not prepare a fresh doubles visit");
            }
        }

        // act
        try (ActivityScenario<RankingsActivity> scenario = ActivityScenario.launch(RankingsActivity.class)) {
            // assert
            awaitSuccessfulData(150_000);

            // act
            onView(withId(R.id.doubles_mode)).perform(click());

            // assert
            awaitSuccessfulData(900_000);
            onView(withId(android.R.id.list)).check(matches(hasDescendant(withText(containsString(" / ")))));

            // act
            onData(anything()).inAdapterView(withId(android.R.id.list)).atPosition(0).perform(click());

            // assert
            awaitSuccessfulData(150_000);
            onView(withText(R.string.pair_results)).check(matches(isDisplayed()));
            onView(withId(android.R.id.content)).check(matches(hasDescendant(anyOf(
                    withText(containsString(" · ")), withText(R.string.no_history)))));
        }
    }

    private void awaitSuccessfulData(long timeout) {
        long deadline = SystemClock.elapsedRealtime() + timeout;
        java.util.concurrent.atomic.AtomicBoolean readable = new java.util.concurrent.atomic.AtomicBoolean();
        do {
            onView(withId(R.id.updated)).check((view, missing) -> {
                if (missing != null) throw missing;
                readable.set(((android.widget.TextView) view).getText().toString().startsWith("Son uğurlu yenilənmə: "));
            });
            if (readable.get()) break;
            SystemClock.sleep(500);
        } while (SystemClock.elapsedRealtime() < deadline);
        onView(withId(R.id.updated)).check(matches(withText(startsWith("Son uğurlu yenilənmə: "))));
    }
}
