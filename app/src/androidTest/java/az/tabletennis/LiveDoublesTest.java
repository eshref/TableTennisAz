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
    @Test public void liveDoublesOpenTheirProfileWithMatchingStandingMedalsAndResults() {
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

            java.util.concurrent.atomic.AtomicReference<String> selected = new java.util.concurrent.atomic.AtomicReference<>();
            scenario.onActivity(activity -> selected.set(((android.widget.ListView) activity.findViewById(android.R.id.list))
                    .getAdapter().getItem(1).toString()));
            java.util.regex.Matcher row = java.util.regex.Pattern.compile("^(\\d+)\\. (.+) / (.+) — (\\d+) xal$")
                    .matcher(selected.get());
            if (!row.matches()) throw new AssertionError("Unreadable published doubles row: " + selected.get());

            // act
            onData(anything()).inAdapterView(withId(android.R.id.list)).atPosition(1).perform(click());

            // assert
            awaitSuccessfulData(150_000);
            onView(withText(R.string.pair_profile)).check(matches(isDisplayed()));
            onView(withText(row.group(2))).check(matches(isDisplayed()));
            onView(withText(row.group(3))).check(matches(isDisplayed()));
            onView(withId(R.id.profile_points)).check(matches(withText(row.group(4))));
            onView(withId(R.id.pair_position)).check(matches(withText("#" + row.group(1))));
            assertMedal(R.id.pair_gold, "Qızıl medal");
            assertMedal(R.id.pair_silver, "Gümüş medal");
            assertMedal(R.id.pair_bronze, "Bürünc medal");
            onView(withId(android.R.id.content)).check(matches(hasDescendant(anyOf(
                    withText(containsString(" · ")), withText(R.string.no_history)))));
        }
    }

    private void assertMedal(int id, String description) {
        onView(withId(id)).check((view, missing) -> {
            if (missing != null) throw missing;
            String text = view.getContentDescription().toString();
            if (!text.matches(description + ": [0-9]+")) throw new AssertionError("Missing published medal count: " + text);
        });
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
