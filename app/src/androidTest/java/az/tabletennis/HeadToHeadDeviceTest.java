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
import static az.tabletennis.HeadToHeadFixtures.ALIKHAN;
import static az.tabletennis.HeadToHeadFixtures.ARCHIVE_FINAL_DUEL;
import static az.tabletennis.HeadToHeadFixtures.ARCHIVE_TEAM_GAME;
import static az.tabletennis.HeadToHeadFixtures.ARCHIVE_TOURNAMENT;
import static az.tabletennis.HeadToHeadFixtures.ASHRAF;
import static az.tabletennis.HeadToHeadFixtures.DOUBLES;
import static az.tabletennis.HeadToHeadFixtures.DOUBLES_TEAM_GAME;
import static az.tabletennis.HeadToHeadFixtures.ELCHIN;
import static az.tabletennis.HeadToHeadFixtures.ERHAN;
import static az.tabletennis.HeadToHeadFixtures.ILQAR;
import static az.tabletennis.HeadToHeadFixtures.KHAYAL;
import static az.tabletennis.HeadToHeadFixtures.NEWER;
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
            givenPublishedResults(server, application);

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
                onView(withContentDescription(ARCHIVE_TOURNAMENT)).check(matches(withId(R.id.match_card)));
                onView(withContentDescription(ARCHIVE_TEAM_GAME)).check(matches(withId(R.id.match_card)));
                onView(withContentDescription(NEWER)).check(matches(withId(R.id.match_card)));
                onView(withContentDescription(ARCHIVE_FINAL_DUEL)).check(matches(withId(R.id.match_card)));
                onView(withContentDescription(ARCHIVE_FINAL_DUEL)).perform(scrollTo());
                capture("head-to-head-singles-duel");

                // act
                onView(withContentDescription(ARCHIVE_TEAM_GAME)).perform(scrollTo());
                capture("head-to-head-team-game");
                int position = scrollPosition();
                onView(withContentDescription(ARCHIVE_TEAM_GAME)).perform(click());

                // assert
                awaitView(withText("12 Nov. 2026"));
                awaitView(withText("Komanda Liqası"));
                onView(withId(R.id.match_score_left)).check(matches(withText("2")));
                onView(withId(R.id.match_score_right)).check(matches(withText("3")));
                capture("head-to-head-team-game-details");

                // act
                pressBack();

                // assert
                assertThat(scrollPosition(), is(position));
                onView(withId(R.id.head_to_head_first)).check(matches(withText(ASHRAF)));
                onView(withId(R.id.head_to_head_second)).check(matches(withText(ILQAR)));
                onView(withContentDescription(ARCHIVE_TEAM_GAME)).check(matches(withId(R.id.match_card)));

                // act
                onView(withId(R.id.head_to_head_doubles)).perform(scrollTo(), click());
                selectPlayer(R.id.head_to_head_left_first, "Erh", ERHAN);
                selectPlayer(R.id.head_to_head_left_second, "Elç", ELCHIN);
                selectPlayer(R.id.head_to_head_right_first, "Əli", ALIKHAN);
                selectPlayer(R.id.head_to_head_right_second, "Xəy", KHAYAL);
                onView(withId(R.id.head_to_head_doubles)).perform(scrollTo());
                capture("head-to-head-doubles-form");
                onView(withText("Axtar")).perform(scrollTo(), click());

                // assert
                awaitView(withText("Axtarış tamamlandı"));
                onView(withContentDescription(DOUBLES_TEAM_GAME)).check(matches(withId(R.id.match_card)));
                onView(withContentDescription(DOUBLES)).perform(scrollTo());
                capture("head-to-head-results");

                // act
                int doublesPosition = scrollPosition();
                onView(withContentDescription(DOUBLES)).perform(click());

                // assert
                awaitView(withText("09 Nov. 2026"));
                onView(withId(R.id.match_score_left)).check(matches(withText("3")));
                onView(withId(R.id.match_score_right)).check(matches(withText("2")));

                // act
                pressBack();

                // assert
                assertThat(scrollPosition(), is(doublesPosition));
                onView(withId(R.id.head_to_head_left_first)).check(matches(withText(ERHAN)));
                onView(withId(R.id.head_to_head_left_second)).check(matches(withText(ELCHIN)));
                onView(withId(R.id.head_to_head_right_first)).check(matches(withText(ALIKHAN)));
                onView(withId(R.id.head_to_head_right_second)).check(matches(withText(KHAYAL)));
                onView(withContentDescription(DOUBLES)).check(matches(withId(R.id.match_card)));
                capture("head-to-head-return");
            } finally {
                application.fixtureClient = null;
            }
        }
    }

    private void givenPublishedResults(MockWebServer server, FixtureTestRunner.FixtureApplication application)
            throws Exception {
        Map<String, String> pages = new HashMap<>();
        HeadToHeadFixtures.publish(pages);
        HeadToHeadFixtures.publishDoubles(pages);
        HeadToHeadFixtures.publishTournamentArchive(pages);
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
        // MockWebServer may reverse-resolve its bind address to localhost. Use the certificate's IP identity.
        var fixtureUrl = server.url("/").newBuilder().host("127.0.0.1").build();
        application.fixtureClient = new WebsiteClient(fixtureUrl, new OkHttpClient.Builder()
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
        File directory = new File(instrumentation.getTargetContext().getExternalFilesDir(null), "head-to-head");
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IllegalStateException("Cannot save screenshots");
        ScreenshotCheckpoint.captureWindow(new File(directory, prefix + "-" + id + ".png"));
    }
}
