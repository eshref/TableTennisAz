package az.tabletennis;

import static org.assertj.core.api.Assertions.assertThat;

import android.widget.Spinner;
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
        openScreen();

        // act
        activity().findViewById(R.id.open_menu).performClick();
        clickText("Nəticələr");
        followNavigation();

        // assert
        awaitText("14 Nov. 2026 · Kubok A — Kubok B · 3-0 · Payız Kuboku");

        // act
        clickText("Turnir matçları");

        // assert
        awaitText("13 Nov. 2026 · Liqa A — Liqa B · 3-1 · Şəhər Liqası");
        assertThat(visibleText()).noneMatch(text -> text.contains("· Duello"));
        requestedPaths();

        // act
        clickText("Duellolar");

        // assert
        awaitText("16 Nov. 2026 · İlqar Həsənov — Şəms Quliyeva · 3-1 · Duello");
        assertThat(visibleText()).noneMatch(text -> text.contains("· Payız Kuboku") || text.contains("· Şəhər Liqası"));
        assertThat(requestedPaths()).containsExactly("/matches/duellos");

        // act
        clickText("Turnir matçları");
        ((Spinner) activity().findViewById(R.id.event_filter)).setSelection(3);
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();

        // assert
        awaitText("12 Nov. 2026 · Uzaq A — Uzaq B · 2-0 · Uzaq Kubok");
        assertThat(requestedPaths()).contains("/matches/results/event/903");
        assertThat(visibleText()).noneMatch(text -> text.contains("· Payız Kuboku") || text.contains("· Şəhər Liqası") || text.contains("· Duello"));

        // act
        ((Spinner) activity().findViewById(R.id.event_filter)).setSelection(1);
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();

        // assert
        awaitText("14 Nov. 2026 · Kubok A — Kubok B · 3-0 · Payız Kuboku");
        assertThat(visibleText()).noneMatch(text -> text.contains("Səhifə 2"));

        // act
        clickText("Daha çox yüklə");

        // assert
        awaitText("16 Nov. 2026 · Səhifə 2 A — Səhifə 2 B · 3-2 · Payız Kuboku");
        assertThat(((Spinner) activity().findViewById(R.id.event_filter)).getSelectedItem().toString()).isEqualTo("Payız Kuboku");

        // arrange
        requestedPaths();
        website.compute("/matches/results/event/901?page=2", (path, body) -> body.replace("Səhifə 2 A", "Təzə nəticə"));

        // act
        activity().findViewById(R.id.refresh).performClick();

        // assert
        awaitText("16 Nov. 2026 · Təzə nəticə — Səhifə 2 B · 3-2 · Payız Kuboku");
        assertThat(visibleText()).contains("14 Nov. 2026 · Kubok A — Kubok B · 3-0 · Payız Kuboku");
        assertThat(((Spinner) activity().findViewById(R.id.event_filter)).getSelectedItem().toString()).isEqualTo("Payız Kuboku");
        assertThat(requestedPaths()).contains("/matches/results/event/901", "/matches/results/event/901?page=2")
                .doesNotContain("/matches/duellos", "/matches/results/event/902");

        // arrange
        FixtureApplication.online = false;

        // act
        activity().findViewById(R.id.refresh).performClick();

        // assert
        assertThat(visibleText()).contains("İnternet bağlantısı yoxdur. Saxlanmış məlumat göstərilir.",
                "16 Nov. 2026 · Təzə nəticə — Səhifə 2 B · 3-2 · Payız Kuboku");
        assertThat(activity().findViewById(R.id.refresh).isEnabled()).isFalse();

        // arrange
        server.shutdown();

        // act
        restart();
        activity().findViewById(R.id.open_menu).performClick();
        clickText("Nəticələr");
        followNavigation();

        // assert
        assertThat(visibleText()).contains("16 Nov. 2026 · Təzə nəticə — Səhifə 2 B · 3-2 · Payız Kuboku",
                "İnternet bağlantısı yoxdur. Saxlanmış məlumat göstərilir.");

        // act
        ((Spinner) activity().findViewById(R.id.event_filter)).setSelection(2);
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();

        // assert
        assertThat(visibleText()).contains("Məlumat yüklənmədi. İnternet bağlantısını yoxlayın.")
                .doesNotContain("Bu seçim üzrə mənbədə nəticə tapılmadı.")
                .noneMatch(text -> text.contains("· Payız Kuboku") || text.contains("· Duello"));

        // act
        clickText("Duellolar");

        // assert
        assertThat(visibleText()).contains("16 Nov. 2026 · İlqar Həsənov — Şəms Quliyeva · 3-1 · Duello")
                .noneMatch(text -> text.contains("· Payız Kuboku"));
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
        clickText("Duellolar");

        // assert
        assertThat(visibleText()).noneMatch(text -> text.contains("· Payız Kuboku") || text.contains("· Şəhər Liqası"));
        awaitText("16 Nov. 2026 · İlqar Həsənov — Şəms Quliyeva · 3-1 · Duello");
        assertThat(visibleText()).noneMatch(text -> text.contains("· Payız Kuboku") || text.contains("· Şəhər Liqası"));
    }
}
