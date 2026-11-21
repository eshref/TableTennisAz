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
import static androidx.test.espresso.matcher.ViewMatchers.withSpinnerText;
import static java.util.Arrays.asList;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.anything;
import static org.hamcrest.Matchers.not;

import android.content.Context;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.VectorDrawable;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.test.espresso.NoMatchingViewException;
import androidx.test.espresso.UiController;
import androidx.test.espresso.ViewAction;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
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
            onView(withText("14.11.2026 – 16.11.2026")).check(matches(isDisplayed()));
            onView(withText("Priz: 0 AZN")).check(matches(isDisplayed()));

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
            onView(withText("8 qoşa")).check(matches(isDisplayed()));
            onView(withText("Priz: 0 AZN")).check(matches(isDisplayed()));

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
        }
    }

    @Test public void resultsCardsFiltersAndBackRetainTheSelectedFeed() throws Exception {
        // arrange
        givenSavedResults();

        // act
        try (ActivityScenario<ResultsActivity> scenario = ActivityScenario.launch(ResultsActivity.class)) {
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
            onView(withText("Turnirlər")).perform(scrollTo(), click());
            scenario.onActivity(activity -> ((android.widget.Spinner) activity.findViewById(R.id.event_filter)).setSelection(1));
            onView(withContentDescription(CARD)).perform(scrollTo(), click());

            // assert
            onView(withId(R.id.match_score_left)).check(matches(withText("3")));
            onView(withId(R.id.match_score_right)).check(matches(withText("1")));

            // act
            pressBack();

            // assert
            onView(withText("Turnirlər")).check(matches(isChecked()));
            onView(withId(R.id.event_filter)).check(matches(withSpinnerText(EVENT)));
            onView(withContentDescription(CARD)).check(matches(hasDescendant(withText(EVENT))));

            // act
            onView(withText("Hamısı")).perform(scrollTo(), click());
            onView(withText("İlqar Həsənov\n" + FIRST)).perform(scrollTo());

            // assert
            onView(withText("İlqar Həsənov\n" + FIRST)).check(matches(withText("İlqar Həsənov\n" + FIRST)));
            capture("long-names");

            // act
            onView(withText("Şəms Quliyeva\n" + SECOND)).perform(scrollTo());

            // assert
            onView(withText("Şəms Quliyeva\n" + SECOND)).check(matches(withText("Şəms Quliyeva\n" + SECOND)));
            capture("long-names-second-side");

            // act
            onView(withText("Duellər")).perform(scrollTo(), click());

            // assert
            onView(withText(R.string.no_results)).check(matches(withText(R.string.no_results)));
            // act
            onView(withText(R.string.no_results)).perform(scrollTo());

            // assert
            capture("empty");

            // act
            onView(withId(R.id.clear_results_filters)).perform(scrollTo(), click());
            scenario.recreate();

            // assert
            onView(withText("Hamısı")).check(matches(isChecked()));
            onView(withContentDescription(CARD)).check(matches(hasDescendant(withText("1"))));
        }
    }

    @Test public void switchingFromResultsToDuelsKeepsRankingsAsTheBackDestination() throws Exception {
        // arrange
        givenSavedDuels();

        try (ActivityScenario<RankingsActivity> scenario = ActivityScenario.launch(RankingsActivity.class)) {
            // act
            onView(withId(R.id.open_menu)).perform(click());
            onView(withId(R.id.destination_results)).perform(scrollTo(), click());
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

            // assert
            onView(withId(R.id.match_score_left)).check(matches(withText("3")));
            onView(withId(R.id.match_score_right)).check(matches(withText("2")));
            onView(withText("Işıl Əliyeva")).check(matches(withText("Işıl Əliyeva")));
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
        new RankingsStore(context).write(new RankingSnapshot(asList(new RankingSnapshot.Player(12, "İlqar Həsənov", 800)), now));
        MatchSummary singles = new MatchSummary(502, asList("İlqar Həsənov"), asList("Şəms Quliyeva"),
                "16 Nov. 2026", "3-1", 0, "Duello", true);
        MatchSummary doubles = new MatchSummary(501, asList("İlqar Həsənov", "Əli Məmmədov"), asList("Şəms Quliyeva", "Işıl Əliyeva"),
                "15 Nov. 2026", "3-2", 0, "Duello", true);
        ResultsFeed duels = new ResultsFeed(asList(singles, doubles), new EventCatalog(asList()), asList(), asList(), null);
        new SavedDataStore<>(context, "results-all-0", ResultsFeed.class).write(new SavedData<>(duels, now));
        new SavedDataStore<>(context, "results-duels-0", ResultsFeed.class).write(new SavedData<>(duels, now));
        context.getSharedPreferences("results-selection", Context.MODE_PRIVATE).edit().clear().commit();
        MatchDetails details = new MatchDetails(501,
                asList(new MatchDetails.PlayerLink(12, "İlqar Həsənov"), new MatchDetails.PlayerLink(13, "Əli Məmmədov")),
                asList(new MatchDetails.PlayerLink(11, "Şəms Quliyeva"), new MatchDetails.PlayerLink(14, "Işıl Əliyeva")),
                "15 Nov. 2026", "18:00", "3-2", "Duello");
        new SavedDataStore<>(context, "match-501", MatchDetails.class).write(new SavedData<>(details, now));
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
        MatchSummary doubles = new MatchSummary(501, asList("İlqar Həsənov", FIRST), asList("Şəms Quliyeva", SECOND),
                "15 Nov. 2026", "1-3", 0, "Duello", true);
        ResultsFeed all = new ResultsFeed(asList(singles, doubles), catalog, asList(), asList(), null);
        ResultsFeed events = new ResultsFeed(asList(singles), catalog, asList(), asList(), null);
        ResultsFeed empty = new ResultsFeed(asList(), catalog, asList(), asList(), null);
        new SavedDataStore<>(context, "events", EventCatalog.class).write(new SavedData<>(catalog, now));
        new SavedDataStore<>(context, "results-all-0", ResultsFeed.class).write(new SavedData<>(all, now));
        new SavedDataStore<>(context, "results-events-0", ResultsFeed.class).write(new SavedData<>(events, now));
        new SavedDataStore<>(context, "results-events-901", ResultsFeed.class).write(new SavedData<>(events, now));
        new SavedDataStore<>(context, "results-duels-0", ResultsFeed.class).write(new SavedData<>(empty, now));
        MatchDetails details = new MatchDetails(502, asList(new MatchDetails.PlayerLink(12, "İlqar Həsənov")),
                asList(new MatchDetails.PlayerLink(11, "Şəms Quliyeva")), "16 Nov. 2026", "", "3-1", EVENT);
        new SavedDataStore<>(context, "match-502", MatchDetails.class).write(new SavedData<>(details, now));
        context.getSharedPreferences("results-selection", Context.MODE_PRIVATE).edit().clear().commit();
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
