package az.tabletennis;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.hasMinimumChildCount;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.startsWith;

import android.content.Context;
import android.os.SystemClock;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Opt-in: actual Android TLS/HTTP stack against both live website categories. */
@RunWith(AndroidJUnit4.class)
public class LiveRankingsTest {
    @Test public void bothLiveCategoriesProduceReadableRankings() {
        // arrange
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.deleteFile("rankings.json");
        context.deleteFile("rankings.json.bak");

        // act
        try (ActivityScenario<RankingsActivity> scenario = ActivityScenario.launch(RankingsActivity.class)) {
            long deadline = SystemClock.elapsedRealtime() + 90_000;
            while (true) {
                try {
                    onView(withId(R.id.updated)).check(matches(withText(startsWith("Son uğurlu yenilənmə: "))));
                    break;
                } catch (AssertionError pending) {
                    if (SystemClock.elapsedRealtime() >= deadline) throw pending;
                    SystemClock.sleep(200);
                }
            }

            // assert
            onView(withText(R.string.rankings_title)).check(matches(isDisplayed()));
            onView(withId(android.R.id.list)).check(matches(hasMinimumChildCount(1)));
        }
    }
}
