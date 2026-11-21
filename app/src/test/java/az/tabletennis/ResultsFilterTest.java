package az.tabletennis;

import static org.assertj.core.api.Assertions.assertThat;

import android.widget.Spinner;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 26, qualifiers = "w411dp-h891dp", application = RankingsUiScenario.FixtureApplication.class)
public class ResultsFilterTest extends ProductUiScenario {
    @Test public void filtersFetchTheirOwnCoverageAndSelectedEventPagesAndKeepOfflineSelectionsDistinct() throws Exception {
        // arrange
        prepareResults();
        publishStage("/matches/results/event/901", 601, "Final");
        publishStage("/matches/results/event/901?page=2", 602, "1/4");
        publishStage("/matches/results/event/902", 603, "1/16");
        publishStage("/matches/results/event/903", 605, "3-cü yer oyunu");

        // act
        openDirect(ResultsActivity.class, "", 0);

        // assert
        awaitText("14 Nov. 2026 · Kubok A — Kubok B · 3-0 · Payız Kuboku · Tək · Turnir · Final");

        // act
        clickText("Turnirlər");

        // assert
        awaitText("13 Nov. 2026 · Liqa A — Liqa B · 3-1 · Şəhər Liqası · Tək · Turnir · 1/16");
        assertThat(visibleText()).contains("14 Nov. 2026 · Kubok A — Kubok B · 3-0 · Payız Kuboku · Tək · Turnir · Final");
        assertThat(visibleText()).noneMatch(text -> text.contains("· Duello"));
        requestedPaths();

        // act
        clickText("Duellər");

        // assert
        awaitText("16 Nov. 2026 · İlqar Həsənov — Şəms Quliyeva · 3-1 · Duello · Tək · Duel");
        assertThat(visibleText()).noneMatch(text -> text.contains("· Payız Kuboku") || text.contains("· Şəhər Liqası"));
        assertThat(requestedPaths()).containsExactly("/matches/duellos");

        // arrange
        FixtureApplication.time = Clock.offset(FixtureApplication.time, Duration.ofHours(48));
        website.compute("/matches/results", (path, body) -> body.replace("Uzaq Kubok", "Yenilənmiş Kubok"));

        // act
        clickText("Turnirlər");
        Spinner events = activity().findViewById(R.id.event_filter);
        events.setSelection(3);
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();

        // assert
        awaitText("12 Nov. 2026 · Uzaq A — Uzaq B · 2-0 · Uzaq Kubok · Tək · Turnir · 3-cü yer oyunu");
        assertThat(events.getSelectedItem().toString()).isEqualTo("Uzaq Kubok");
        assertThat(requestedPaths()).containsExactly("/matches/results/event/903");
        assertThat(visibleText()).noneMatch(text -> text.contains("· Payız Kuboku") || text.contains("· Şəhər Liqası") || text.contains("· Duello"));

        // arrange
        FixtureApplication.time = Clock.offset(FixtureApplication.time, Duration.ofMillis(1));

        // act
        events.setSelection(1);
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();

        // assert
        awaitText("14 Nov. 2026 · Kubok A — Kubok B · 3-0 · Payız Kuboku · Tək · Turnir · Final");
        assertThat(events.getItemAtPosition(3).toString()).isEqualTo("Yenilənmiş Kubok");
        assertThat(requestedPaths()).containsExactly("/matches/results", "/matches/results/event/901");
        assertThat(visibleText()).noneMatch(text -> text.contains("Səhifə 2"));

        // arrange
        FixtureApplication.time = Clock.offset(FixtureApplication.time, Duration.ofMinutes(1));
        String updatedLeague = website.get("/matches/results/event/902").replace("Liqa A", "Yenilənmiş Liqa A");
        responses.put("/matches/results/event/902", () -> new MockResponse().setBody(updatedLeague).setBodyDelay(1, TimeUnit.SECONDS));

        // act
        clickText("Hamısı");

        // assert
        assertThat(visibleText()).contains("Son uğurlu yenilənmə: 15 noy 2026 12:00",
                "13 Nov. 2026 · Liqa A — Liqa B · 3-1 · Şəhər Liqası · Tək · Turnir · 1/16");
        awaitText("13 Nov. 2026 · Yenilənmiş Liqa A — Liqa B · 3-1 · Şəhər Liqası · Tək · Turnir · 1/16");
        assertThat(visibleText()).contains("Son uğurlu yenilənmə: 17 noy 2026 12:01");
        assertThat(requestedPaths()).contains("/matches/results", "/matches/results/event/902");

        // act
        events.setSelection(1);
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();

        // assert
        assertThat(visibleText()).contains("Son uğurlu yenilənmə: 17 noy 2026 12:00",
                "14 Nov. 2026 · Kubok A — Kubok B · 3-0 · Payız Kuboku · Tək · Turnir · Final")
                .doesNotContain("Məlumat yüklənir…");
        assertThat(requestedPaths()).isEmpty();

        // act
        clickText("Daha çox yüklə");

        // assert
        awaitText("16 Nov. 2026 · Səhifə 2 A — Səhifə 2 B · 3-2 · Payız Kuboku · Tək · Turnir · 1/4");
        assertThat(((Spinner) activity().findViewById(R.id.event_filter)).getSelectedItem().toString()).isEqualTo("Payız Kuboku");

        // arrange
        requestedPaths();
        website.compute("/matches/results/event/901?page=2", (path, body) -> body.replace("Səhifə 2 A", "Təzə nəticə"));

        // act
        activity().findViewById(R.id.refresh).performClick();

        // assert
        awaitText("16 Nov. 2026 · Təzə nəticə — Səhifə 2 B · 3-2 · Payız Kuboku · Tək · Turnir · 1/4");
        assertThat(visibleText()).contains("14 Nov. 2026 · Kubok A — Kubok B · 3-0 · Payız Kuboku · Tək · Turnir · Final");
        assertThat(((Spinner) activity().findViewById(R.id.event_filter)).getSelectedItem().toString()).isEqualTo("Payız Kuboku");
        assertThat(requestedPaths()).contains("/matches/results", "/matches/results/event/901", "/matches/results/event/901?page=2")
                .doesNotContain("/matches/duellos", "/matches/results/event/902");

        // arrange
        FixtureApplication.online = false;

        // act
        activity().findViewById(R.id.refresh).performClick();

        // assert
        assertThat(visibleText()).contains("İnternet bağlantısı yoxdur. Saxlanmış məlumat göstərilir.",
                "16 Nov. 2026 · Təzə nəticə — Səhifə 2 B · 3-2 · Payız Kuboku · Tək · Turnir · 1/4");
        assertThat(activity().findViewById(R.id.refresh).isEnabled()).isFalse();

        // arrange
        server.shutdown();

        // act
        closeProductScreens();
        openDirect(ResultsActivity.class, "", 0);

        // assert
        assertThat(visibleText()).contains("16 Nov. 2026 · Təzə nəticə — Səhifə 2 B · 3-2 · Payız Kuboku · Tək · Turnir · 1/4",
                "İnternet bağlantısı yoxdur. Saxlanmış məlumat göstərilir.");

        // act
        ((Spinner) activity().findViewById(R.id.event_filter)).setSelection(2);
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();

        // assert
        assertThat(visibleText()).contains("Məlumat yüklənmədi. İnternet bağlantısını yoxlayın.")
                .doesNotContain("Bu seçim üzrə mənbədə nəticə tapılmadı.")
                .noneMatch(text -> text.contains("· Payız Kuboku") || text.contains("· Duello"));

        // act
        clickText("Duellər");

        // assert
        assertThat(visibleText()).contains("16 Nov. 2026 · İlqar Həsənov — Şəms Quliyeva · 3-1 · Duello · Tək · Duel")
                .noneMatch(text -> text.contains("· Payız Kuboku"));
    }

    @Test public void verifiedEmptyEventOffersClearFiltersWhileAnUnloadedSelectionShowsProgress() throws Exception {
        // arrange
        prepareResults();
        website.put("/matches/results/event/903", "<div id='center'><div class='matches-box'><div class='no-info'>Matç yoxdur</div></div></div>");
        responses.put("/matches/results/event/903", () -> new okhttp3.mockwebserver.MockResponse()
                .setBody(website.get("/matches/results/event/903")).setBodyDelay(1, java.util.concurrent.TimeUnit.SECONDS));

        // act
        openDirect(ResultsActivity.class, "", 0);

        // assert
        awaitText("14 Nov. 2026 · Kubok A — Kubok B · 3-0 · Payız Kuboku · Tək · Turnir");
        String updated = ((android.widget.TextView) activity().findViewById(R.id.updated)).getText().toString();

        // act
        ((Spinner) activity().findViewById(R.id.event_filter)).setSelection(3);
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();

        // assert
        assertThat(visibleText()).contains("Məlumat yüklənir…").doesNotContain("Bu seçim üzrə mənbədə nəticə tapılmadı.", "Kubok A");
        awaitText("Bu seçim üzrə mənbədə nəticə tapılmadı.");
        assertThat(visibleText()).contains("Filtrləri təmizlə", "Məlumat yeniləndi.");
        assertThat(requestedPaths()).contains("/matches/results/event/903");

        // act
        activity().findViewById(R.id.clear_results_filters).performClick();

        // assert
        assertThat(visibleText()).contains(updated, "14 Nov. 2026 · Kubok A — Kubok B · 3-0 · Payız Kuboku · Tək · Turnir")
                .doesNotContain("Bu seçim üzrə mənbədə nəticə tapılmadı.");
        assertThat(((android.widget.RadioButton) findText(activity().getWindow().getDecorView(), "Hamısı")).isChecked()).isTrue();
        assertThat(((Spinner) activity().findViewById(R.id.event_filter)).getSelectedItem().toString()).isEqualTo("Bütün tədbirlər");
    }

    @Test public void aDelayedPreviousSelectionCannotReplaceTheCurrentlySelectedDuelResults() throws Exception {
        // arrange
        prepareResults();
        responses.put("/matches/results/event/901", () -> new okhttp3.mockwebserver.MockResponse()
                .setBody(website.get("/matches/results/event/901")).setBodyDelay(1, java.util.concurrent.TimeUnit.SECONDS));

        // act
        openDirect(ResultsActivity.class, "", 0);

        // assert
        assertThat(requestTo("/matches/results/event/901")).isEqualTo("/matches/results/event/901");

        // act
        clickText("Duellər");

        // assert
        assertThat(visibleText()).noneMatch(text -> text.contains("· Payız Kuboku") || text.contains("· Şəhər Liqası"));
        awaitText("16 Nov. 2026 · İlqar Həsənov — Şəms Quliyeva · 3-1 · Duello · Tək · Duel");
        assertThat(visibleText()).noneMatch(text -> text.contains("· Payız Kuboku") || text.contains("· Şəhər Liqası"));
    }
}
