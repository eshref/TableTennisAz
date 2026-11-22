package az.tabletennis;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom;
import static androidx.test.espresso.matcher.ViewMatchers.withContentDescription;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static az.tabletennis.HeadToHeadFixtures.ASHRAF;
import static az.tabletennis.HeadToHeadFixtures.ILQAR;
import static az.tabletennis.HeadToHeadFixtures.NEWER;
import static az.tabletennis.HeadToHeadFixtures.OLDER;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import android.os.SystemClock;
import android.view.View;
import android.widget.Button;
import android.widget.ScrollView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.espresso.NoMatchingViewException;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.net.InetAddress;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.tls.HandshakeCertificates;
import okhttp3.tls.HeldCertificate;
import org.hamcrest.Matcher;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class HeadToHeadDeviceTest {
    @Test public void menuSearchAndDetailsReturnWorkWithTheInstalledApp() throws Exception {
        // arrange
        var application = ApplicationProvider.<FixtureTestRunner.FixtureApplication>getApplicationContext();
        try (MockWebServer server = new MockWebServer()) {
            givenPublishedPlayerHistories(server, application);

            // act
            try (ActivityScenario<RankingsActivity> rankings = ActivityScenario.launch(RankingsActivity.class)) {
                onView(withId(R.id.open_menu)).perform(click());
                onView(withId(R.id.destination_head_to_head)).perform(scrollTo());

                // assert
                onView(withId(R.id.destination_head_to_head)).check(matches(withText("Üz-üzə")));
                capture("head-to-head-menu");

                // act
                onView(withId(R.id.destination_head_to_head)).perform(click());
                selectPlayer(R.id.head_to_head_first, "Səf", ASHRAF);
                selectPlayer(R.id.head_to_head_second, "İlq", ILQAR);
                onView(withText("Axtar")).perform(scrollTo(), click());

                // assert
                awaitView(withText("Axtarış tamamlandı"));
                onView(withContentDescription(NEWER)).perform(scrollTo());
                capture("head-to-head-results");

                // act
                onView(withContentDescription(OLDER)).perform(scrollTo());
                int position = scrollPosition();
                onView(withContentDescription(OLDER)).perform(click());

                // assert
                awaitView(withText("10 Aug. 2021"));
                onView(withId(R.id.match_score_left)).check(matches(withText("3")));
                onView(withId(R.id.match_score_right)).check(matches(withText("2")));

                // act
                pressBack();

                // assert
                assertThat(scrollPosition(), is(position));
                onView(withId(R.id.head_to_head_first)).check(matches(withText(ASHRAF)));
                onView(withId(R.id.head_to_head_second)).check(matches(withText(ILQAR)));
                onView(withContentDescription(OLDER)).check(matches(withId(R.id.match_card)));
                capture("head-to-head-return");
            } finally {
                application.fixtureClient = null;
            }
        }
    }

    private void givenPublishedPlayerHistories(MockWebServer server, FixtureTestRunner.FixtureApplication application)
            throws Exception {
        Map<String, String> pages = new HashMap<>();
        HeadToHeadFixtures.publish(pages);
        new RankingsStore(application).write(new RankingSnapshot(
                Collections.singletonList(new RankingSnapshot.Player(13, ILQAR, 894)), application.clock().millis()));
        server.setDispatcher(new Dispatcher() {
            @Override public MockResponse dispatch(RecordedRequest request) {
                String page = pages.get(request.getPath());
                if (page == null) return new MockResponse().setResponseCode(404);
                return new MockResponse().setHeader("Content-Type", "text/html; charset=utf-8").setBody(page);
            }
        });
        HeldCertificate certificate = new HeldCertificate.Builder().addSubjectAlternativeName("127.0.0.1").build();
        HandshakeCertificates serverTls = new HandshakeCertificates.Builder().heldCertificate(certificate).build();
        HandshakeCertificates clientTls = new HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate()).build();
        server.useHttps(serverTls.sslSocketFactory(), false);
        server.start(InetAddress.getByName("127.0.0.1"), 0);
        application.fixtureClient = new WebsiteClient(server.url("/"), new OkHttpClient.Builder()
                .sslSocketFactory(clientTls.sslSocketFactory(), clientTls.trustManager()));
    }

    private void selectPlayer(int id, String query, String name) {
        onView(withId(id)).perform(scrollTo(), click(), replaceText(query), closeSoftKeyboard());
        Matcher<View> suggestion = allOf(isAssignableFrom(Button.class), withText(name));
        awaitView(suggestion);
        onView(suggestion).perform(scrollTo(), click());
    }

    private void awaitView(Matcher<View> matcher) {
        long deadline = SystemClock.elapsedRealtime() + 15_000;
        while (true) {
            try {
                onView(matcher).check((view, missing) -> { if (missing != null) throw missing; });
                return;
            } catch (NoMatchingViewException missing) {
                if (SystemClock.elapsedRealtime() >= deadline) throw missing;
                SystemClock.sleep(100);
            }
        }
    }

    private int scrollPosition() {
        int[] position = new int[1];
        onView(withId(R.id.main_scroll)).check((view, missing) -> {
            if (missing != null) throw missing;
            position[0] = ((ScrollView) view).getScrollY();
        });
        return position[0];
    }

    private void capture(String id) throws Exception {
        var instrumentation = InstrumentationRegistry.getInstrumentation();
        String prefix = InstrumentationRegistry.getArguments().getString("capturePrefix", "local");
        ScreenshotCheckpoint.captureWindow(new File(instrumentation.getTargetContext().getFilesDir(), prefix + "-" + id + ".png"));
    }
}
