package az.tabletennis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.TextView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.FutureTask;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Uses platform views because both installed APKs can have different dependencies and resource IDs. */
@RunWith(AndroidJUnit4.class)
public class SavedHistoryUpgradeDeviceTest {
    private static final String FIRST = "Əlixan Əlizadə";
    private static final String SECOND = "Xəyal Həşimli";
    private static final String EARLIER = "11.01.2025 12:00:00.000";
    private static final String LATER = "12.01.2025 12:00:00.000";

    @Test public void savedDoublesHistoryIsVisible() throws Exception {
        // arrange
        var instrumentation = InstrumentationRegistry.getInstrumentation();
        String phase = InstrumentationRegistry.getArguments().getString("capturePrefix");
        assumeTrue("Run through the signed upgrade verifier", "upgrade-before".equals(phase) || "upgrade-after".equals(phase));
        Context app = instrumentation.getTargetContext();
        if (phase.equals("upgrade-before")) givenSavedHistory(app);
        File captures = new File(app.getExternalFilesDir(null), "upgrade");
        if (!captures.isDirectory() && !captures.mkdirs()) throw new IOException("Cannot create upgrade evidence directory");

        // act
        var rankings = instrumentation.startActivitySync(new Intent(app, RankingsActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        try {
            onScreen(root -> click(resourceView(root, "doubles_mode")));
            var profile = instrumentation.addMonitor(PairResultsActivity.class.getName(), null, false);
            onScreen(root -> {
                ListView list = root.findViewById(android.R.id.list);
                int position = 1;
                View pair = list.getChildAt(position - list.getFirstVisiblePosition());
                if (pair == null || !list.performItemClick(pair, position, list.getItemIdAtPosition(position)))
                    throw new IllegalStateException("Cannot open the first doubles pair");
            });
            if (instrumentation.waitForMonitorWithTimeout(profile, 10_000) == null)
                throw new IllegalStateException("The doubles profile did not open");

            // act & assert
            thenNameVisible(FIRST);
            thenNameVisible(SECOND);
            thenSelectedObservation(LATER, "858 xal");

            // act
            selectObservation(EARLIER, "800 xal");

            // act & assert
            thenSelectedObservation(EARLIER, "800 xal");
            ScreenshotCheckpoint.captureWindow(new File(captures, phase + "-rating.png"));

            // act
            onScreen(root -> click(resourceView(root, "pair_chart_rank")));

            // act & assert
            thenSelectedObservation(EARLIER, "#9");
            ScreenshotCheckpoint.captureWindow(new File(captures, phase + "-ranking.png"));

            // act
            selectObservation(LATER, "#2");

            // act & assert
            thenSelectedObservation(LATER, "#2");
        } finally {
            instrumentation.runOnMainSync(rankings::finish);
        }
    }

    private void selectObservation(String date, String value) throws Exception {
        String label = date + " — " + value;
        onScreen(root -> click(requireView(root, view -> TextUtils.equals(view.getContentDescription(), label), label)));
    }

    private void thenNameVisible(String name) throws Exception {
        onScreen(root -> {
            View label = requireView(root, view -> view instanceof TextView text && TextUtils.equals(text.getText(), name), name);
            reveal(label);
            assertTrue("Pair name should be visible: " + name, visible(label));
        });
    }

    private void thenSelectedObservation(String date, String value) throws Exception {
        onScreen(root -> {
            TextView selectedValue = (TextView) resourceView(root, "chart_selected_value");
            TextView selectedDate = (TextView) resourceView(root, "chart_selected_date");
            reveal(selectedValue);
            assertTrue("The selected date and value should be visible", visible(selectedDate) && visible(selectedValue));
            assertEquals(date, selectedDate.getText().toString());
            assertEquals(value, selectedValue.getText().toString());
        });
    }

    private void onScreen(Consumer<View> action) throws Exception {
        var instrumentation = InstrumentationRegistry.getInstrumentation();
        instrumentation.waitForIdleSync();
        var task = new FutureTask<Void>(() -> {
            var activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                    .stream().findFirst().orElseThrow(() -> new IllegalStateException("No resumed screen"));
            action.accept(activity.getWindow().getDecorView());
            return null;
        });
        instrumentation.runOnMainSync(task);
        task.get();
    }

    private View resourceView(View root, String name) {
        int id = root.getResources().getIdentifier(name, "id", root.getContext().getPackageName());
        View found = root.findViewById(id);
        if (found == null) throw new IllegalStateException("Cannot find screen control " + name);
        return found;
    }

    private View requireView(View root, Predicate<View> matches, String label) {
        View found = findView(root, matches);
        if (found == null) throw new IllegalStateException("Cannot find screen label " + label);
        return found;
    }

    private View findView(View view, Predicate<View> matches) {
        if (matches.test(view)) return view;
        if (view instanceof ViewGroup group) {
            for (int index = 0; index < group.getChildCount(); index++) {
                View found = findView(group.getChildAt(index), matches);
                if (found != null) return found;
            }
        }
        return null;
    }

    private void click(View view) {
        reveal(view);
        if (!visible(view) || !view.isEnabled() || !view.isClickable())
            throw new IllegalStateException("Screen control is unavailable for a click");
        view.performClick();
    }

    private void reveal(View view) {
        view.requestRectangleOnScreen(new Rect(0, 0, view.getWidth(), view.getHeight()), true);
    }

    private boolean visible(View view) {
        return view.isShown() && view.getGlobalVisibleRect(new Rect());
    }

    private void givenSavedHistory(Context app) throws IOException {
        long now = System.currentTimeMillis();
        new RankingsStore(app).write(new RankingSnapshot(List.of(new RankingSnapshot.Player(7, FIRST, 600)), now));
        DoublesPair pair = new DoublesPair(151, new MatchDetails.PlayerLink(7, FIRST),
                new MatchDetails.PlayerLink(70, SECOND), null, null, true);
        DoublesRanking earlier = new DoublesRanking(List.of(new DoublesRanking.Row(pair, 9, 800)))
                .observedAfter(null, fixtureTime(11));
        DoublesRanking later = new DoublesRanking(List.of(new DoublesRanking.Row(pair, 2, 858)))
                .observedAfter(earlier, fixtureTime(12));
        new SavedDataStore<>(app, "doubles-ranking", DoublesRanking.class).write(new SavedData<>(later, now));
        new SavedDataStore<>(app, "duo-results-151", PairHistory.class).write(new SavedData<>(
                new PairHistory(pair, List.of(), List.of(), null, new PairMedals(0, 0, 0)), now));
    }

    private long fixtureTime(int day) {
        return LocalDateTime.of(2025, 1, day, 12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}
