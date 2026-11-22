package az.tabletennis;

import static androidx.test.espresso.Espresso.onData;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.assertion.ViewAssertions.doesNotExist;
import static androidx.test.espresso.matcher.ViewMatchers.hasDescendant;
import static androidx.test.espresso.matcher.ViewMatchers.hasSibling;
import static androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA;
import static androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom;
import static androidx.test.espresso.matcher.ViewMatchers.isChecked;
import static androidx.test.espresso.matcher.ViewMatchers.isCompletelyDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withContentDescription;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static java.util.Arrays.asList;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.anything;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.NotificationManager;
import android.app.UiAutomation;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.ParcelFileDescriptor;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.VectorDrawable;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.TextView;
import androidx.test.espresso.NoMatchingViewException;
import androidx.test.espresso.UiController;
import androidx.test.espresso.ViewAction;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.android.gms.tasks.Tasks;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.hamcrest.Matcher;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Installed native rendering and Back journey, complementary to the HTTP-fixture tests. */
@RunWith(AndroidJUnit4.class)
public class ResultsDeviceTest {
    private static final String FIRST = "Əbdürrəhman Məmmədəmin Azərbaycanlı Həsənov";
    private static final String SECOND = "Məryəm Əbdülrəhim Azərbaycanlı İmanova";
    private static final String EVENT = "Bakı həvəskarlar kuboku 2026";
    private static final String STAGE = "3-cü yer oyunu";
    private static final String CARD = "16 Nov. 2026 · İlqar Həsənov — Şəms Quliyeva · 3-1 · "
            + EVENT + " · Tək · Turnir · " + STAGE;
    private static final String DOUBLES_DUEL = "15 Nov. 2026 · İlqar Həsənov / Əli Məmmədov — "
            + "Şəms Quliyeva / Işıl Əliyeva · 3-2 · Duello · Qoşalar · Duel";
    private static final String DOUBLES_EVENT = "Bakı Qoşa Kuboku 2026";
    private static final String OLDER_DOUBLES_EVENT = "Gəncə Qoşa Kuboku";
    private static final String DOUBLES_CARD = "15 Nov. 2026 · İlqar Həsənov / Əli Məmmədov — "
            + "Şəms Quliyeva / Işıl Əliyeva · 3-2 · " + DOUBLES_EVENT + " · Qoşalar · Turnir · Final";

    @Test public void optInAndTapSinglesAndDoublesAlertsWithoutACatalogue() throws Exception {
        // arrange
        var instrumentation = InstrumentationRegistry.getInstrumentation();
        var application = (FixtureTestRunner.FixtureApplication) instrumentation.getTargetContext().getApplicationContext();
        UiAutomation system = instrumentation.getUiAutomation();
        AccessibilityServiceInfo accessibility = system.getServiceInfo();
        accessibility.flags |= AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
                | AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        system.setServiceInfo(accessibility);
        NotificationManager notifications = application.getSystemService(NotificationManager.class);
        application.fixtureAlertTopic = subscribe -> Tasks.forResult(null);
        if (application.tournamentAlerts().isEnabled()) application.tournamentAlerts().disable();
        boolean prompts = application.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED;
        String singles = "Bakı Şəxsi Sınaq Kuboku";
        String doubles = "Bakı Qoşa Sınaq Kuboku";
        long singlesId = application.clock().millis();
        long doublesId = singlesId + 1;
        String singlesPath = "/matches/results/event/" + singlesId;
        String doublesPath = "/matches/results/event/" + doublesId;
        Map<String, String> pages = Map.of(
                singlesPath, alertResults(1801, "Sınaq A", "Sınaq B", "3-0", singles),
                doublesPath, alertResults(1802, "Sınaq C</div><div>Sınaq D", "Sınaq E</div><div>Sınaq F", "3-2", doubles));
        List<String> resultRequests = new CopyOnWriteArrayList<>();

        try (MockWebServer website = new MockWebServer()) {
            givenAlertResults(website, application, pages, resultRequests);
            try (ActivityScenario<RankingsActivity> scenario = ActivityScenario.launch(RankingsActivity.class)) {
                // act
                onView(withId(R.id.open_menu)).perform(click());
                onView(withId(R.id.tournament_alerts)).perform(scrollTo(), click());

                // assert
                onView(withId(R.id.tournament_alerts_switch)).check(matches(not(isChecked())));

                // act
                onView(withId(R.id.tournament_alerts_switch)).perform(click());
                if (prompts) tapSystemNode(system, "notification permission",
                        root -> root.findAccessibilityNodeInfosByViewId(root.getPackageName() + ":id/permission_allow_button"));

                // assert
                awaitAlertView(allOf(withId(R.id.tournament_alerts_switch), isChecked()));
                capture("tournament-alerts-setting");

                // act
                pressBack();
            }

            // act
            deliverAlert(application, system, singlesId, singles);

            // assert
            awaitAlertView(withContentDescription("16 Nov. 2026 · Sınaq A — Sınaq B · 3-0 · " + singles + " · Tək · Turnir"));
            assertEquals(List.of(singlesPath), resultRequests);
            capture("singles-alert-results");

            // act
            androidx.test.espresso.Espresso.pressBackUnconditionally();
            deliverAlert(application, system, doublesId, doubles);

            // assert
            awaitAlertView(withContentDescription("16 Nov. 2026 · Sınaq C / Sınaq D — Sınaq E / Sınaq F · 3-2 · "
                    + doubles + " · Qoşalar · Turnir"));
            assertEquals(List.of(singlesPath, doublesPath), resultRequests);
            capture("doubles-alert-results");

            // act
            androidx.test.espresso.Espresso.pressBackUnconditionally();
        } finally {
            notifications.cancelAll();
            application.tournamentAlerts().disable();
            application.fixtureAlertTopic = null;
            application.fixtureClient = null;
        }
    }

    private String alertResults(int match, String left, String right, String score, String tournament) {
        return """
                <div id='center'><div class='matches-box'><div><div class='divs-header'>16 Nov. 2026</div>
                <div><a href='/matches/%d'><div class='match-box'>
                <div class='col-3'><div>%s</div></div><div class='col-2'><div class='center'>%s</div></div>
                <div class='col-3'><div>%s</div></div><div class='col-4'>%s</div>
                </div></a></div></div></div></div>
                """.formatted(match, left, score, right, tournament);
    }

    private void givenAlertResults(MockWebServer server, FixtureTestRunner.FixtureApplication application,
                                   Map<String, String> pages, List<String> requests) throws Exception {
        server.setDispatcher(new Dispatcher() {
            @Override public MockResponse dispatch(RecordedRequest request) {
                String page = pages.get(request.getPath());
                if (page == null) return new MockResponse().setResponseCode(404);
                requests.add(request.getPath());
                return new MockResponse().setBody(page);
            }
        });
        application.hostWebsiteFixtures(server);
    }

    private void deliverAlert(FixtureTestRunner.FixtureApplication application, UiAutomation system,
                              long id, String name) throws Exception {
        application.tournamentAlerts().receive(Map.of("eventId", Long.toString(id), "name", name,
                "publishedAt", Long.toString(application.clock().millis())));
        try (var output = new ParcelFileDescriptor.AutoCloseInputStream(
                system.executeShellCommand("cmd statusbar expand-notifications"))) {
            output.readAllBytes();
        }
        tapSystemNode(system, name, root -> root.findAccessibilityNodeInfosByText(name).stream()
                .filter(node -> TextUtils.equals(name, node.getText())).collect(Collectors.toList()));
    }

    private void tapSystemNode(UiAutomation system, String description,
                               Function<AccessibilityNodeInfo, List<AccessibilityNodeInfo>> find) throws Exception {
        long deadline = SystemClock.uptimeMillis() + 15_000;
        List<CharSequence> packages = new ArrayList<>();
        while (SystemClock.uptimeMillis() < deadline) {
            packages.clear();
            for (var window : system.getWindows()) {
                AccessibilityNodeInfo root = window.getRoot();
                if (root == null) continue;
                packages.add(root.getPackageName());
                for (AccessibilityNodeInfo node : find.apply(root)) {
                    if (!node.isVisibleToUser()) continue;
                    Rect bounds = new Rect();
                    node.getBoundsInScreen(bounds);
                    try (var output = new ParcelFileDescriptor.AutoCloseInputStream(
                            system.executeShellCommand("input tap " + bounds.centerX() + " " + bounds.centerY()))) {
                        output.readAllBytes();
                    }
                    return;
                }
            }
            SystemClock.sleep(100);
        }
        throw new AssertionError("Android did not show " + description + "; visible windows: " + packages);
    }

    private void awaitAlertView(Matcher<View> matcher) {
        long deadline = SystemClock.uptimeMillis() + 15_000;
        while (true) {
            try {
                onView(matcher).check(matches(isDisplayed()));
                return;
            } catch (NoMatchingViewException | androidx.test.espresso.NoActivityResumedException missing) {
                if (SystemClock.uptimeMillis() >= deadline) throw missing;
                SystemClock.sleep(100);
            }
        }
    }

    @Test public void menuAndCategoryDotsClearIndependentlyWhenTheirListsOpen() throws Exception {
        // arrange
        givenNewTournaments();
        Matcher<View> singlesDot = allOf(withId(R.id.new_tournaments_dot),
                hasSibling(withId(R.id.destination_singles_tournaments)));
        Matcher<View> doublesDot = allOf(withId(R.id.new_tournaments_dot),
                hasSibling(withId(R.id.destination_doubles_tournaments)));

        // act
        try (ActivityScenario<RankingsActivity> scenario = ActivityScenario.launch(RankingsActivity.class)) {
            // assert
            onView(withId(R.id.menu_new_tournaments_dot)).check(matches(isDisplayed()));
            capture("tournament-menu-dot");

            // act
            onView(withId(R.id.open_menu)).perform(click());

            // assert
            onView(singlesDot).check(matches(isDisplayed()));

            // act
            onView(withId(R.id.destination_doubles_tournaments)).perform(scrollTo());

            // assert
            onView(doublesDot).check(matches(isDisplayed()));
            capture("tournament-category-dots");

            // act
            onView(withId(R.id.destination_singles_tournaments)).perform(scrollTo(), click());

            // assert
            onView(withId(R.id.tournament_cards)).check(matches(isDisplayed()));
            onView(withId(R.id.menu_new_tournaments_dot)).check(matches(isDisplayed()));

            // act
            pressBack();
            scenario.recreate();
            onView(withId(R.id.open_menu)).perform(click());

            // assert
            onView(singlesDot).check(matches(not(isDisplayed())));

            // act
            onView(withId(R.id.destination_doubles_tournaments)).perform(scrollTo());

            // assert
            onView(doublesDot).check(matches(isDisplayed()));
            capture("tournament-doubles-dot");

            // act
            onView(withId(R.id.destination_doubles_tournaments)).perform(scrollTo(), click());

            // assert
            onView(withId(R.id.tournament_cards)).check(matches(isDisplayed()));
            onView(withId(R.id.menu_new_tournaments_dot)).check(matches(not(isDisplayed())));

            // act
            pressBack();
        }
    }

    @Test public void singlesMenuSearchOpensTheTournamentAndItsSavedMatch() throws Exception {
        // arrange
        givenSavedResults();
        givenSavedTournaments();

        try (ActivityScenario<RankingsActivity> scenario = ActivityScenario.launch(RankingsActivity.class)) {
            // act
            onView(withId(R.id.open_menu)).perform(click());
            onView(withId(R.id.destination_singles_tournaments)).perform(click());
            onView(withId(R.id.search)).perform(replaceText("baki"), closeSoftKeyboard());

            // assert
            onData(anything()).inAdapterView(withId(R.id.tournament_cards)).atPosition(0)
                    .check(matches(hasDescendant(withText(EVENT))));
            assertTournamentFacts("14–16 noyabr 2026", "24 oyunçu");

            // act
            framePodium();

            // assert
            assertPodiumLayout();
            onView(withContentDescription(R.string.medal_gold))
                    .check((base, missing) -> assertStep(base, missing, "1", FIRST));
            capture("singles-tournaments");

            // act
            onView(withContentDescription(R.string.medal_gold)).perform(click());
            onView(withId(R.id.match_stage_badge)).perform(scrollTo());

            // assert
            onView(withContentDescription(CARD)).check(matches(hasDescendant(withText(STAGE))));
            capture("tournament-results");

            // act
            onView(allOf(withText("İlqar Həsənov"), isDescendantOfA(withContentDescription(CARD))))
                    .perform(scrollTo(), click());

            // assert
            onView(withId(R.id.match_score_left)).check(matches(withText("3")));
            onView(withId(R.id.match_score_right)).check(matches(withText("1")));

            // act
            pressBack();

            // assert
            onView(withContentDescription(CARD)).check(matches(hasDescendant(withText(STAGE))));

            // act
            pressBack();

            // assert
            onView(withId(R.id.search)).check(matches(withText("baki")));

            // act
            pressBack();

            // assert
            onView(withId(R.id.players_mode)).check(matches(isChecked()));
        }
    }

    @Test public void doublesMenuSearchShowsPairsAndOpensTheSelectedTournament() throws Exception {
        // arrange
        givenSavedTournaments();
        givenSavedDoublesTournamentResults();

        try (ActivityScenario<RankingsActivity> scenario = ActivityScenario.launch(RankingsActivity.class)) {
            // act
            onView(withId(R.id.open_menu)).perform(click());
            onView(withId(R.id.destination_doubles_tournaments)).perform(scrollTo(), click());

            // assert
            onView(withText(R.string.doubles_tournaments)).check(matches(isDisplayed()));
            onData(anything()).inAdapterView(withId(R.id.tournament_cards)).atPosition(0)
                    .check(matches(hasDescendant(withText(DOUBLES_EVENT))));
            onData(anything()).inAdapterView(withId(R.id.tournament_cards)).atPosition(1)
                    .check(matches(allOf(hasDescendant(withText(OLDER_DOUBLES_EVENT)), hasDescendant(withText("— qoşa")))));

            // act
            onView(withId(R.id.search)).perform(replaceText("baki qosa"), closeSoftKeyboard());

            // assert
            onView(withText(EVENT)).check(doesNotExist());
            onView(withText(OLDER_DOUBLES_EVENT)).check(doesNotExist());
            assertTournamentFacts("15–18 noyabr 2026", "8 qoşa");

            // act
            framePodium();

            // assert
            assertPodiumLayout();
            onView(withContentDescription(R.string.medal_gold))
                    .check((base, missing) -> assertStep(base, missing, "1", FIRST, SECOND));
            awaitDoublesPortraits();
            capture("doubles-tournaments");

            // act
            onView(withContentDescription(R.string.medal_gold)).perform(click());
            onView(withId(R.id.match_stage_badge)).perform(scrollTo());

            // assert
            onView(withContentDescription(DOUBLES_CARD)).check(matches(hasDescendant(withText("Final"))));
            capture("doubles-tournament-results");

            // act
            onView(allOf(withText("İlqar Həsənov\nƏli Məmmədov"), isDescendantOfA(withContentDescription(DOUBLES_CARD))))
                    .perform(scrollTo(), click());

            // assert
            onView(withId(R.id.match_score_left)).check(matches(withText("3")));
            onView(withId(R.id.match_score_right)).check(matches(withText("2")));

            // act
            pressBack();
            pressBack();
            onView(withId(R.id.search)).perform(replaceText("gence"), closeSoftKeyboard());

            // assert
            onView(withText("— qoşa")).check(matches(isDisplayed()));
            onView(withContentDescription(R.string.medal_gold)).check(doesNotExist());

            // act
            onView(withText(OLDER_DOUBLES_EVENT)).perform(click());

            // assert
            onView(withContentDescription("12 Nov. 2026 · İlqar Həsənov / Əli Məmmədov — "
                    + "Şəms Quliyeva / Işıl Əliyeva · 3-1 · Gəncə Qoşa Kuboku · Qoşalar · Turnir"))
                    .check(matches(isDisplayed()));

            // act
            pressBack();

            // assert
            onView(withId(R.id.search)).check(matches(withText("gence")));

            // act
            pressBack();

            // assert
            onView(withId(R.id.players_mode)).check(matches(isChecked()));
        }
    }

    @Test public void tournamentResultsAndDuelsLayOutStageAndLongNames() throws Exception {
        // arrange
        givenSavedResults();
        givenSavedTournaments();
        givenSavedDuelFeed(new MatchSummary(501, asList("İlqar Həsənov", FIRST), asList("Şəms Quliyeva", SECOND),
                "15 Nov. 2026", "1-3", 0, "Duello", true));

        try (ActivityScenario<RankingsActivity> scenario = ActivityScenario.launch(RankingsActivity.class)) {
            // act
            onView(withId(R.id.open_menu)).perform(click());
            onView(withId(R.id.destination_singles_tournaments)).perform(scrollTo(), click());
            onView(withText(EVENT)).perform(click());
            onView(withId(R.id.match_stage_badge)).perform(scrollTo());

            // assert
            onView(withContentDescription(CARD)).check(matches(hasDescendant(withText("3"))));
            onView(withContentDescription(CARD)).check(matches(hasDescendant(withText("İlqar Həsənov"))));
            onView(withContentDescription(CARD)).check(matches(hasDescendant(withText("Tək · Turnir"))));
            onView(withId(R.id.match_stage_badge)).check(matches(withText(STAGE)));
            onView(withContentDescription(CARD)).check((view, missing) -> {
                if (missing != null) throw missing;
                ViewGroup card = (ViewGroup) view;
                TextView event = card.findViewById(R.id.match_event_name);
                TextView stage = card.findViewById(R.id.match_stage_badge);
                Rect eventBounds = new Rect();
                Rect stageBounds = new Rect();
                event.getDrawingRect(eventBounds);
                stage.getDrawingRect(stageBounds);
                card.offsetDescendantRectToMyCoords(event, eventBounds);
                card.offsetDescendantRectToMyCoords(stage, stageBounds);
                boolean narrow = "narrow-large".equals(InstrumentationRegistry.getArguments().getString("capturePrefix"));
                if (narrow && stageBounds.top < eventBounds.bottom) {
                    throw new AssertionError("The stage should wrap below the tournament at 320×568 with 150% text");
                }
                if (!narrow && (stageBounds.left < eventBounds.right || stageBounds.top >= eventBounds.bottom)) {
                    throw new AssertionError("The stage should sit beside the tournament when it fits");
                }
            });
            capture("results");

            // act
            pressBack();
            pressBack();
            onView(withId(R.id.open_menu)).perform(click());
            onView(withId(R.id.destination_duels)).perform(scrollTo(), click());
            onView(withText("İlqar Həsənov\n" + FIRST)).perform(scrollTo());

            // assert
            onView(withText("İlqar Həsənov\n" + FIRST)).check(matches(isDisplayed()));
            capture("long-names");

            // act
            onView(withText("Şəms Quliyeva\n" + SECOND)).perform(scrollTo());

            // assert
            onView(withText("Şəms Quliyeva\n" + SECOND)).check(matches(isDisplayed()));
            capture("long-names-second-side");
        }
    }

    @Test public void menuShowsTheEmptyDuelFeed() throws Exception {
        // arrange
        givenSavedDuelFeed();

        try (ActivityScenario<RankingsActivity> scenario = ActivityScenario.launch(RankingsActivity.class)) {
            // act
            onView(withId(R.id.open_menu)).perform(click());
            onView(withId(R.id.destination_duels)).perform(scrollTo(), click());

            // assert
            onView(withText(R.string.no_duels)).check(matches(isDisplayed()));
            capture("empty");
        }
    }

    @Test public void menuOpensDuelsAndBackReturnsThroughItsMatchToRankings() throws Exception {
        // arrange
        givenSavedDuels();

        try (ActivityScenario<RankingsActivity> scenario = ActivityScenario.launch(RankingsActivity.class)) {
            // act
            onView(withId(R.id.open_menu)).perform(click());
            onView(withId(R.id.destination_duels)).perform(scrollTo(), click());

            // assert
            onView(withText(R.string.duel_results)).check(matches(isDisplayed()));
            onView(withText("Tək · Duel")).check(matches(isDisplayed()));
            capture("duels");

            // act
            onView(withId(R.id.open_menu)).perform(click());

            // assert
            onView(withId(R.id.destination_duels)).check(matches(isChecked()));

            // act
            pressBack();
            onView(withText("İlqar Həsənov\nƏli Məmmədov")).perform(scrollTo(), click());
            onView(withText("Işıl Əliyeva")).perform(scrollTo());

            // assert
            onView(withId(R.id.match_score_left)).check(matches(withText("3")));
            onView(withId(R.id.match_score_right)).check(matches(withText("2")));
            onView(withText("Işıl Əliyeva")).check(matches(isDisplayed()));
            capture("duel-match");

            // act
            pressBack();

            // assert
            onView(withContentDescription(DOUBLES_DUEL)).check(matches(hasDescendant(withText("Qoşalar · Duel"))));

            // act
            pressBack();

            // assert
            onView(withId(R.id.open_menu)).check(matches(isDisplayed()));
            onView(withId(R.id.players_mode)).check(matches(isChecked()));
        }
    }

    private void givenSavedDuels() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        long now = System.currentTimeMillis();
        MatchSummary singles = new MatchSummary(502, asList("İlqar Həsənov"), asList("Şəms Quliyeva"),
                "16 Nov. 2026", "3-1", 0, "Duello", true);
        MatchSummary doubles = new MatchSummary(501, asList("İlqar Həsənov", "Əli Məmmədov"), asList("Şəms Quliyeva", "Işıl Əliyeva"),
                "15 Nov. 2026", "3-2", 0, "Duello", true);
        givenSavedDuelFeed(singles, doubles);
        MatchDetails details = new MatchDetails(501,
                asList(new MatchDetails.PlayerLink(12, "İlqar Həsənov"), new MatchDetails.PlayerLink(13, "Əli Məmmədov")),
                asList(new MatchDetails.PlayerLink(11, "Şəms Quliyeva"), new MatchDetails.PlayerLink(14, "Işıl Əliyeva")),
                "15 Nov. 2026", "18:00", "3-2", "Duello");
        new SavedDataStore<>(context, "match-501", MatchDetails.class).write(new SavedData<>(details, now));
    }

    private void givenSavedDuelFeed(MatchSummary... matches) throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        long now = System.currentTimeMillis();
        new RankingsStore(context).write(new RankingSnapshot(asList(new RankingSnapshot.Player(12, "İlqar Həsənov", 800)), now));
        ResultsFeed duels = new ResultsFeed(asList(matches), new EventCatalog(asList()), asList(), asList(), null);
        new SavedDataStore<>(context, "results-duels-0", ResultsFeed.class).write(new SavedData<>(duels, now));
    }

    private void givenSavedTournaments() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        long now = System.currentTimeMillis();
        new RankingsStore(context).write(new RankingSnapshot(
                asList(new RankingSnapshot.Player(12, "İlqar Həsənov", 800)), now));
        TournamentCatalogue catalogue = TournamentCatalogue.parse("""
                {"version":1,"tournaments":[
                  {"id":901,"name":"Bakı həvəskarlar kuboku 2026","format":"singles",
                   "start":"2026-11-14","end":"2026-11-16","players":24,"prize":"0 AZN",
                   "podium":{
                     "gold":[{"id":10001,"name":"%1$s"}],
                     "silver":[{"id":74,"name":"Məryəm İmanova","photo":"/uploads/maryam.png"}],
                     "bronze":[{"id":13,"name":"İlqar Həsənov","photo":"/uploads/players-13.png"}]}},
                  {"id":902,"name":"Gəncə Şəxsi Kuboku","format":"singles","start":"2026-11-12","end":"2026-11-12"},
                  {"id":907,"name":"Gəncə Qoşa Kuboku","format":"doubles","start":"2026-11-12","end":"2026-11-12"},
                  {"id":905,"name":"Bakı Qoşa Kuboku 2026","format":"doubles",
                   "start":"2026-11-15","end":"2026-11-18","pairs":8,"prize":"0 AZN",
                   "podium":{
                     "gold":[{"id":10001,"name":"%1$s"},{"id":10002,"name":"%2$s"}],
                     "silver":[{"id":7,"name":"Əlixan Əlizadə","photo":"/uploads/players-7.png"},
                               {"id":70,"name":"Xəyal Həşimli","photo":"/uploads/players-70.png"}],
                     "bronze":[{"id":74,"name":"Məryəm İmanova","photo":"/uploads/maryam.png"},
                               {"id":13,"name":"İlqar Həsənov","photo":"/uploads/players-13.png"}]}}
                ]}
                """.formatted(FIRST, SECOND));
        new SavedDataStore<>(context, TournamentCatalogue.STORE_KEY, TournamentCatalogue.class)
                .write(new SavedData<>(catalogue, now));
        File portraits = new File(context.getFilesDir(), "player-portraits");
        if (!portraits.isDirectory() && !portraits.mkdirs()) throw new IllegalStateException("Cannot prepare portraits");
        givenPortrait(portraits, 7, "elixan.png");
        givenPortrait(portraits, 70, "xeyal.png");
        givenPortrait(portraits, 74, "maryam.png");
        givenPortrait(portraits, 13, "ilqar.png");
    }

    private void givenNewTournaments() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        long now = System.currentTimeMillis();
        new RankingsStore(context).write(new RankingSnapshot(
                asList(new RankingSnapshot.Player(12, "İlqar Həsənov", 800)), now));
        if (!context.getSharedPreferences("tournament-acknowledgements", Context.MODE_PRIVATE).edit().clear().commit())
            throw new IllegalStateException("Cannot reset tournament acknowledgement");
        TournamentCatalogue baseline = TournamentCatalogue.parse("""
                {"version":1,"firstListed":{"901":100},"tournaments":[]}
                """).withDeviceHistory(null);
        TournamentCatalogue updated = TournamentCatalogue.parse("""
                {"version":1,"firstListed":{"901":100,"902":200,"905":200},"tournaments":[
                  {"id":902,"name":"Gəncə Şəxsi Kuboku","format":"singles","start":"2026-11-17","end":"2026-11-17"},
                  {"id":905,"name":"Bakı Qoşa Kuboku 2026","format":"doubles","start":"2026-11-18","end":"2026-11-18"}
                ]}
                """).withDeviceHistory(baseline);
        new SavedDataStore<>(context, TournamentCatalogue.STORE_KEY, TournamentCatalogue.class)
                .write(new SavedData<>(updated, now));
    }

    private void givenPortrait(File directory, long playerId, String asset) throws Exception {
        Context tests = InstrumentationRegistry.getInstrumentation().getContext();
        try (var input = tests.getAssets().open("portraits/" + asset);
             var output = new FileOutputStream(new File(directory, playerId + ".png"))) {
            input.transferTo(output);
        }
    }

    private void givenSavedDoublesTournamentResults() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        long now = System.currentTimeMillis();
        EventCatalog catalog = new EventCatalog(asList(new EventCatalog.Event(905, DOUBLES_EVENT)));
        MatchSummary match = new MatchSummary(501, asList("İlqar Həsənov", "Əli Məmmədov"),
                asList("Şəms Quliyeva", "Işıl Əliyeva"), "15 Nov. 2026", "3-2", 905, DOUBLES_EVENT,
                false, false, TournamentStage.FINAL);
        ResultsFeed results = new ResultsFeed(asList(match), catalog, asList(), asList(), null);
        new SavedDataStore<>(context, "results-events-905", ResultsFeed.class).write(new SavedData<>(results, now));
        EventCatalog olderCatalogue = new EventCatalog(asList(new EventCatalog.Event(907, OLDER_DOUBLES_EVENT)));
        MatchSummary olderMatch = new MatchSummary(507, asList("İlqar Həsənov", "Əli Məmmədov"),
                asList("Şəms Quliyeva", "Işıl Əliyeva"), "12 Nov. 2026", "3-1", 907, OLDER_DOUBLES_EVENT, false);
        ResultsFeed olderResults = new ResultsFeed(asList(olderMatch), olderCatalogue, asList(), asList(), null);
        new SavedDataStore<>(context, "results-events-907", ResultsFeed.class).write(new SavedData<>(olderResults, now));
        MatchDetails details = new MatchDetails(501,
                asList(new MatchDetails.PlayerLink(12, "İlqar Həsənov"), new MatchDetails.PlayerLink(13, "Əli Məmmədov")),
                asList(new MatchDetails.PlayerLink(11, "Şəms Quliyeva"), new MatchDetails.PlayerLink(14, "Işıl Əliyeva")),
                "15 Nov. 2026", "18:00", "3-2", DOUBLES_EVENT);
        new SavedDataStore<>(context, "match-501", MatchDetails.class).write(new SavedData<>(details, now));
    }

    private void givenSavedResults() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        long now = System.currentTimeMillis();
        EventCatalog catalog = new EventCatalog(asList(new EventCatalog.Event(901, EVENT)));
        MatchSummary singles = new MatchSummary(502, asList("İlqar Həsənov"), asList("Şəms Quliyeva"),
                "16 Nov. 2026", "3-1", 901, EVENT, false, false, TournamentStage.THIRD_PLACE);
        ResultsFeed events = new ResultsFeed(asList(singles), catalog, asList(), asList(), null);
        new SavedDataStore<>(context, "results-events-901", ResultsFeed.class).write(new SavedData<>(events, now));
        MatchDetails details = new MatchDetails(502, asList(new MatchDetails.PlayerLink(12, "İlqar Həsənov")),
                asList(new MatchDetails.PlayerLink(11, "Şəms Quliyeva")), "16 Nov. 2026", "", "3-1", EVENT);
        new SavedDataStore<>(context, "match-502", MatchDetails.class).write(new SavedData<>(details, now));
    }

    private void assertTournamentFacts(String date, String entrants) {
        onData(anything()).inAdapterView(withId(R.id.tournament_cards)).atPosition(0)
                .check((card, missing) -> {
                    matches(allOf(hasDescendant(allOf(withText(date), isDisplayed())),
                            hasDescendant(allOf(withText(entrants), isDisplayed())),
                            hasDescendant(allOf(withText("0 AZN"), isDisplayed())))).check(card, missing);
                    int dateTop = factTop(card, date);
                    int prizeTop = factTop(card, "0 AZN");
                    Configuration config = card.getResources().getConfiguration();
                    if (config.screenWidthDp <= 320 && config.fontScale >= 1.5f && prizeTop <= dateTop) {
                        throw new AssertionError("Expected the prize to wrap below the date on a narrow screen with larger text");
                    }
                    if (config.screenWidthDp >= 400 && config.fontScale <= 1f && prizeTop != dateTop) {
                        throw new AssertionError("Expected the prize on the same row as the date at standard text size");
                    }
                });
    }

    private int factTop(View card, String text) {
        ArrayList<View> facts = new ArrayList<>();
        card.findViewsWithText(facts, text, View.FIND_VIEWS_WITH_TEXT);
        int[] location = new int[2];
        facts.get(0).getLocationOnScreen(location);
        return location[1];
    }

    private void framePodium() {
        onView(allOf(withId(R.id.tournament_podium), hasDescendant(withContentDescription(R.string.medal_gold))))
                .perform(new ViewAction() {
                    @Override public Matcher<View> getConstraints() { return isAssignableFrom(ViewGroup.class); }
                    @Override public String getDescription() { return "bring the podium into view"; }
                    @Override public void perform(UiController ui, View podium) {
                        Rect bounds = new Rect();
                        podium.getDrawingRect(bounds);
                        View list = podium.getRootView().findViewById(R.id.tournament_cards);
                        bounds.top = Math.max(bounds.top, bounds.bottom - list.getHeight());
                        podium.requestRectangleOnScreen(bounds, true);
                        ui.loopMainThreadUntilIdle();
                    }
                });
    }

    private void assertPodiumLayout() {
        onView(allOf(withId(R.id.tournament_podium), hasDescendant(withContentDescription(R.string.medal_gold))))
                .check((view, missing) -> {
                    if (missing != null) throw missing;
                    if ("narrow-large".equals(InstrumentationRegistry.getArguments().getString("capturePrefix"))) {
                        matches(isCompletelyDisplayed()).check(view, null);
                    }
                    View silver = medalBase(view, R.string.medal_silver);
                    View gold = medalBase(view, R.string.medal_gold);
                    View bronze = medalBase(view, R.string.medal_bronze);
                    View silverStep = (View) silver.getParent();
                    View goldStep = (View) gold.getParent();
                    View bronzeStep = (View) bronze.getParent();
                    if (silverStep.getRight() > goldStep.getLeft() || goldStep.getRight() > bronzeStep.getLeft()
                            || gold.getHeight() <= silver.getHeight() || silver.getHeight() <= bronze.getHeight()
                            || goldStep.getBottom() != silverStep.getBottom() || goldStep.getBottom() != bronzeStep.getBottom()) {
                        throw new AssertionError("Expected aligned silver, gold and bronze steps from left to right");
                    }
                });
    }

    private View medalBase(View podium, int medal) {
        ArrayList<View> bases = new ArrayList<>();
        podium.findViewsWithText(bases, podium.getContext().getString(medal), View.FIND_VIEWS_WITH_CONTENT_DESCRIPTION);
        if (bases.size() != 1) throw new AssertionError("Expected one base for " + podium.getContext().getString(medal));
        return bases.get(0);
    }

    private void assertStep(View base, NoMatchingViewException missing, String place, String... names) {
        if (missing != null) throw missing;
        matches(allOf(withText(place), isDisplayed())).check(base, null);
        View step = (View) base.getParent();
        for (String name : names) {
            matches(hasDescendant(withText(name))).check(step, null);
        }
    }

    private void awaitDoublesPortraits() {
        AtomicBoolean portraitsReady = new AtomicBoolean();
        long deadline = SystemClock.elapsedRealtime() + 10_000;
        do {
            onView(withContentDescription(R.string.medal_silver)).check((base, missing) -> {
                if (missing != null) throw missing;
                ViewGroup portraits = (ViewGroup) ((ViewGroup) base.getParent()).getChildAt(0);
                portraitsReady.set(portraits.getChildCount() == 2
                        && ((PlayerPortraitView) portraits.getChildAt(0)).getDrawable() instanceof BitmapDrawable
                        && ((PlayerPortraitView) portraits.getChildAt(1)).getDrawable() instanceof BitmapDrawable);
            });
            if (portraitsReady.get()) break;
            SystemClock.sleep(50);
        } while (SystemClock.elapsedRealtime() < deadline);
        if (!portraitsReady.get()) throw new AssertionError("Expected two available portraits on the silver step");
        onView(withContentDescription(R.string.medal_gold)).check((base, missing) -> {
            if (missing != null) throw missing;
            ViewGroup portraits = (ViewGroup) ((ViewGroup) base.getParent()).getChildAt(0);
            if (portraits.getChildCount() != 2
                    || !(((PlayerPortraitView) portraits.getChildAt(0)).getDrawable() instanceof VectorDrawable)
                    || !(((PlayerPortraitView) portraits.getChildAt(1)).getDrawable() instanceof VectorDrawable)) {
                throw new AssertionError("Expected both unavailable portraits to show silhouettes on the gold step");
            }
        });
    }

    private void capture(String name) throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File directory = new File(context.getExternalFilesDir(null), "issue27");
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IllegalStateException("Cannot save screenshots");
        String prefix = InstrumentationRegistry.getArguments().getString("capturePrefix", "light");
        File png = new File(directory, prefix + "-" + name + ".png");
        ScreenshotCheckpoint.captureWindow(png);
    }
}
