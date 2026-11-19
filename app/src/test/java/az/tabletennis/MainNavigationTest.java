package az.tabletennis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.robolectric.Shadows.shadowOf;

import android.content.Intent;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {26, 36}, qualifiers = "w411dp-h891dp", application = RankingsUiScenario.FixtureApplication.class)
public class MainNavigationTest extends DoublesUiScenario {
    @Test public void privacyPolicyOpensInBrowser() throws Exception {
        // arrange
        openScreen();
        clickText("☰");

        // act
        clickText("Məxfilik siyasəti");

        // assert
        Intent browser = shadowOf(activity()).getNextStartedActivity();
        assertThat(browser).isNotNull();
        assertThat(browser.getAction()).isEqualTo(Intent.ACTION_VIEW);
        assertThat(browser.getDataString()).isEqualTo("https://eshref.github.io/TableTennisAz/privacy-policy.html");
    }

    @Test public void drawerRoundTripRetainsBothRankingModesAndTheResultsSelectionAcrossRecreation() throws Exception {
        // arrange
        prepareResults();
        StringBuilder matches = new StringBuilder();
        for (int i = 700; i < 725; i++) matches.append(pairResult(i, "14 Nov. 2026", "Payız Kuboku"));
        String eventPage = website.get("/matches/results/event/901");
        int endOfMatches = eventPage.indexOf("</div></div><ul");
        website.put("/matches/results/event/901", eventPage.substring(0, endOfMatches) + matches + eventPage.substring(endOfMatches));
        StringBuilder pairs = new StringBuilder();
        for (int i = 1; i <= 25; i++) {
            pairs.append(pairRow(i, i, 900 - i));
            website.put("/api/getDuoInfo?id=" + i, pairInfo(i, 12, "İlqar Həsənov", 100 + i, "Tərəfdaş " + i));
        }
        website.put("/players/duos", "<div id='center'><div class='ranking-page-list'>" + pairs + "</div></div>");
        openScreen();

        // assert
        awaitText("1. İlqar Həsənov — 800 xal");
        assertThat(((android.widget.RadioButton) activity().findViewById(R.id.players_mode)).isChecked()).isTrue();

        // act
        ((android.widget.EditText) activity().findViewById(R.id.search)).setText("ışıl");
        clickText("Qoşalar");

        // assert
        awaitText("1. İlqar Həsənov / Tərəfdaş 1 — 899 xal");

        // act
        ((android.widget.EditText) activity().findViewById(R.id.search)).setText("ilqar");
        android.widget.ListView list = activity().findViewById(android.R.id.list);
        list.setSelectionFromTop(12, -11);
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        activity().findViewById(R.id.open_menu).performClick();

        // assert
        assertThat(((android.widget.RadioButton) activity().findViewById(R.id.destination_rankings)).isChecked()).isTrue();

        // act
        clickText("Nəticələr");
        screen.pause().stop();
        followNavigation();

        // assert
        awaitText("14 Nov. 2026 · Kubok A — Kubok B · 3-0 · Payız Kuboku · Tək · Turnir");

        // act
        clickText("Turnirlər");
        ((android.widget.Spinner) activity().findViewById(R.id.event_filter)).setSelection(1);
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();

        // assert
        awaitText("14 Nov. 2026 · Kubok A — Kubok B · 3-0 · Payız Kuboku · Tək · Turnir");

        // act
        clickText("Daha çox yüklə");

        // assert
        awaitText("16 Nov. 2026 · Səhifə 2 A — Səhifə 2 B · 3-2 · Payız Kuboku · Tək · Turnir");

        // act
        android.widget.ScrollView resultsScroll = activity().findViewById(R.id.main_scroll);
        resultsScroll.scrollTo(0, 100);
        activity().findViewById(R.id.open_menu).performClick();
        recreateProductScreen();
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();

        // assert
        assertThat(((android.widget.RadioButton) activity().findViewById(R.id.destination_results)).isChecked()).isTrue();

        // act
        activity().onBackPressed();

        // assert
        assertThat(activity().isFinishing()).isFalse();
        assertThat(((android.widget.Spinner) activity().findViewById(R.id.event_filter)).getSelectedItem().toString()).isEqualTo("Payız Kuboku");
        assertThat(((android.widget.ScrollView) activity().findViewById(R.id.main_scroll)).getScrollY()).isEqualTo(100);

        // act
        activity().findViewById(R.id.open_menu).performClick();
        activity().findViewById(R.id.destination_rankings).performClick();

        // assert
        android.content.Intent returning = org.robolectric.Shadows.shadowOf(activity()).getNextStartedActivity();
        assertThat(returning.getComponent().getClassName()).isEqualTo(RankingsActivity.class.getName());
        assertThat(returning.getFlags() & android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP).isNotZero();
        assertThat(returning.getFlags() & android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP).isNotZero();

        // act
        closeProductScreens(); // Android clears Results when delivering the CLEAR_TOP intent.
        screen.restart().start().resume().visible();
        screen.recreate();
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();

        // assert
        assertThat(((android.widget.EditText) activity().findViewById(R.id.search)).getText().toString()).isEqualTo("ilqar");
        assertThat(((android.widget.ListView) activity().findViewById(android.R.id.list)).getFirstVisiblePosition()).isEqualTo(12);

        // act
        clickText("Oyunçular");

        // assert
        assertThat(((android.widget.EditText) activity().findViewById(R.id.search)).getText().toString()).isEqualTo("ışıl");
        awaitText("3. Işıl Əliyeva — 600 xal");

        // act
        activity().findViewById(R.id.open_menu).performClick();
        clickText("Nəticələr");
        followNavigation();
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();

        // assert
        assertThat(((android.widget.Spinner) activity().findViewById(R.id.event_filter)).getSelectedItem().toString()).isEqualTo("Payız Kuboku");
        assertThat(visibleText()).contains("16 Nov. 2026 · Səhifə 2 A — Səhifə 2 B · 3-2 · Payız Kuboku · Tək · Turnir");
        assertThat(((android.widget.ScrollView) activity().findViewById(R.id.main_scroll)).getScrollY()).isEqualTo(100);

        // arrange
        ((android.widget.ScrollView) activity().findViewById(R.id.main_scroll)).scrollTo(0, 200);
        FixtureApplication.online = false;

        // act
        activity().findViewById(R.id.refresh).performClick();
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();

        // assert
        assertThat(((android.widget.ScrollView) activity().findViewById(R.id.main_scroll)).getScrollY()).isEqualTo(200);
        assertThat(visibleText()).contains("İnternet bağlantısı yoxdur. Saxlanmış məlumat göstərilir.");
    }

    @Test public void drawerClosesBeforeBackLeavesRankingsAndSelectingTheActiveDestinationDoesNotNavigate() throws Exception {
        // arrange
        openScreen();

        // assert
        awaitText("1. İlqar Həsənov — 800 xal");
        assertThat(visibleText()).doesNotContain("Nəticələr");

        // act
        clickText("☰");

        // assert
        assertThat(visibleText()).contains("TableTennisAz", "Reytinq", "Nəticələr");

        // act
        activity().onBackPressed();

        // assert
        assertThat(activity().isFinishing()).isFalse();
        assertThat(visibleText()).doesNotContain("Nəticələr");

        // act
        clickText("☰");
        activity().findViewById(R.id.destination_rankings).performClick();

        // assert
        assertThat(visibleText()).doesNotContain("Nəticələr");
        assertThat(org.robolectric.Shadows.shadowOf(activity()).getNextStartedActivity()).isNull();

        // act
        clickText("☰");
        clickText("×");

        // assert
        assertThat(visibleText()).contains("1. İlqar Həsənov — 800 xal").doesNotContain("Nəticələr");

        // act
        ((android.widget.EditText) activity().findViewById(R.id.search)).setText("tapılmayan ad");

        // assert
        awaitText("Axtarışa uyğun oyunçu tapılmadı.");

        // act
        activity().findViewById(R.id.clear_search).performClick();

        // assert
        awaitText("1. İlqar Həsənov — 800 xal");

        // arrange
        FixtureApplication.online = false;

        // act
        activity().findViewById(R.id.refresh).performClick();

        // assert
        awaitText("İnternet bağlantısı yoxdur. Saxlanmış məlumat göstərilir.");
        assertThat(activity().findViewById(R.id.refresh).isEnabled()).isFalse();
        assertThat(visibleText()).contains("1. İlqar Həsənov — 800 xal");
    }
}
