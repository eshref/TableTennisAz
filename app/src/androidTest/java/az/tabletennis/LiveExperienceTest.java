package az.tabletennis;

import static androidx.test.espresso.Espresso.onData;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.hasDescendant;
import static androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withContentDescription;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.anything;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.isEmptyOrNullString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;

import android.content.Context;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import androidx.test.core.app.ActivityScenario;
import androidx.test.espresso.UiController;
import androidx.test.espresso.ViewAction;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.hamcrest.Matcher;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Opt-in, read-only journeys against the live website using the installed APK. */
@RunWith(AndroidJUnit4.class)
public class LiveExperienceTest {
    @Test public void rankingsProfileChartAndDuelMatchParticipantUseTheLiveAndroidClient() {
        // arrange
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        for (java.io.File file : context.getFilesDir().listFiles()) {
            if ((file.getName().startsWith("saved-") || file.getName().startsWith("rankings.json")) && !file.delete()) {
                throw new IllegalStateException("Could not prepare a fresh live visit");
            }
        }

        // act
        try (ActivityScenario<RankingsActivity> scenario = ActivityScenario.launch(RankingsActivity.class)) {
            // assert
            awaitSuccessfulData();

            // act
            onData(anything()).inAdapterView(withId(android.R.id.list)).atPosition(1).perform(click());

            // assert
            awaitSuccessfulData();
            onView(withId(R.id.profile_name)).check(matches(withText(not(isEmptyOrNullString()))));
            onView(withId(R.id.profile_points)).check(matches(withText(endsWith(" xal"))));

            // act
            onView(withText(R.string.rating_history)).perform(scrollTo(), click());

            // assert
            awaitSuccessfulData();
            onView(withText(R.string.rating_history)).check(matches(isDisplayed()));
            onView(withId(android.R.id.content)).check(matches(anyOf(
                    hasDescendant(allOf(withId(R.id.chart), withContentDescription(not(isEmptyOrNullString())))),
                    hasDescendant(withText(R.string.no_rating_history)))));

            // act
            pressBack();
            pressBack();
            onView(withId(R.id.open_menu)).perform(click());
            onView(withId(R.id.destination_duels)).perform(scrollTo(), click());

            // assert
            awaitSuccessfulData();

            // act
            onView(withId(R.id.result_rows)).perform(clickFirstAction());

            // assert
            awaitSuccessfulData();
            onView(withId(R.id.match_score_left)).check(matches(withText(not(isEmptyOrNullString()))));
            onView(withId(R.id.match_score_right)).check(matches(withText(not(isEmptyOrNullString()))));

            // act
            onView(withId(R.id.participants_left)).perform(clickFirstAction());

            // assert
            awaitSuccessfulData();
            onView(withId(R.id.profile_name)).check(matches(withText(not(isEmptyOrNullString()))));
        }
    }

    private void awaitSuccessfulData() {
        long deadline = SystemClock.elapsedRealtime() + 150_000;
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

    private ViewAction clickFirstAction() {
        return new ViewAction() {
            @Override public Matcher<View> getConstraints() { return isAssignableFrom(ViewGroup.class); }
            @Override public String getDescription() { return "Open the first displayed result or participant"; }
            @Override public void perform(UiController controller, View view) {
                ViewGroup group = (ViewGroup) view;
                for (int i = 0; i < group.getChildCount(); i++) {
                    View action = group.getChildAt(i);
                    if (action.isClickable()) {
                        action.performClick();
                        controller.loopMainThreadUntilIdle();
                        return;
                    }
                }
                throw new IllegalStateException("No result or participant to open");
            }
        };
    }
}
