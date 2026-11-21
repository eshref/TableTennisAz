package az.tabletennis;

import static org.assertj.core.api.Assertions.assertThat;

import android.widget.EditText;
import java.util.List;
import okhttp3.mockwebserver.MockResponse;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {26, 36}, qualifiers = "w411dp-h891dp", application = RankingsUiScenario.FixtureApplication.class)
public class SinglesTournamentsWorkflowTest extends ProductUiScenario {
    private static final String CATALOGUE = "/tournaments-v1.json";
    private static final String NEWER = "Bakı Şəxsi Kuboku 2026";
    private static final String OLDER = "Bakı Şəxsi Kuboku 2025";
    private static final String OTHER = "Gəncə Kuboku";
    private static final String MATCH = "14 Nov. 2026 · Kubok A — Kubok B · 3-0 · " + NEWER + " · Tək · Turnir · Final";
    private static final String OLDER_MATCH = "12 Nov. 2026 · Səhifə 2 A — Səhifə 2 B · 3-2 · " + NEWER + " · Tək · Turnir";

    @Test public void menuSearchAndCardsOpenOnlyTheSelectedTournamentsPublishedResults() throws Exception {
        // arrange
        givenPublishedTournaments();
        openScreen();
        awaitText("1. İlqar Həsənov — 800 xal");
        requestedPaths();

        // act
        openSingles();

        // assert
        awaitText(NEWER);
        assertThat(tournamentNames()).containsExactly(NEWER, OTHER, OLDER);
        assertThat(visibleText()).doesNotContain("Komanda Liqası", "Qoşa Kuboku", "Naməlum Kubok");
        assertThat(requestedPaths()).containsExactly(CATALOGUE);

        // act
        searchFor("bAKI");

        // assert
        assertThat(tournamentNames()).containsExactly(NEWER, OLDER);
        assertThat(visibleText()).contains("14.11.2026 – 16.11.2026", "08.11.2025", "Oyunçu sayı: 24",
                "Priz: 0 AZN", "Oyunçu sayı: —", "Priz: —");

        // act
        clickText(NEWER);
        followNavigation();

        // assert
        awaitText(MATCH);
        assertThat(visibleText()).contains(NEWER, "Final", "Daha çox yüklə").doesNotContain(OLDER_MATCH);
        assertThat(requestedPaths()).containsExactly("/matches/results/event/901");

        // act
        clickText("Daha çox yüklə");

        // assert
        awaitText(OLDER_MATCH);
        assertThat(visibleText()).contains(MATCH).doesNotContain("Daha çox yüklə");
        assertThat(requestedPaths()).containsExactly("/matches/results/event/901?page=2");

        // act
        clickText(MATCH);
        followNavigation();

        // assert
        awaitText("Matç haqqında");
        awaitText("İlqar Həsənov");
        assertThat(visibleText()).contains("Şəms Quliyeva", "3", "0");
        assertThat(requestedPaths()).containsExactly("/matches/601");

        // act
        back();
        back();

        // assert
        assertThat(tournamentNames()).containsExactly(NEWER, OLDER);
    }

    @Test public void failedRefreshAndOfflineRestartPreserveTheCatalogueAndLoadedMatchPages() throws Exception {
        // arrange
        givenPublishedTournaments();
        openScreen();
        awaitText("1. İlqar Həsənov — 800 xal");
        openSingles();
        awaitText(NEWER);
        clickText(NEWER);
        followNavigation();
        awaitText(MATCH);
        clickText("Daha çox yüklə");
        awaitText(OLDER_MATCH);
        back();
        requestedPaths();
        responses.put(CATALOGUE, () -> new MockResponse().setResponseCode(200).setBody(
                "{\"version\":1,\"tournaments\":[{\"id\":901,\"format\":\"singles\"}]}"));

        // act
        clickText("↻");

        // assert
        awaitText("Yeniləmək mümkün olmadı. Saxlanmış məlumat göstərilir.");
        assertThat(tournamentNames()).containsExactly(NEWER, OTHER, OLDER);
        assertThat(requestedPaths()).containsExactly(CATALOGUE);

        // arrange
        FixtureApplication.online = false;

        // act
        restart();
        openSingles();

        // assert
        assertThat(visibleText()).contains("İnternet bağlantısı yoxdur. Saxlanmış məlumat göstərilir.");
        assertThat(tournamentNames()).containsExactly(NEWER, OTHER, OLDER);

        // act
        clickText(NEWER);
        followNavigation();

        // assert
        assertThat(visibleText()).contains(MATCH, OLDER_MATCH,
                "İnternet bağlantısı yoxdur. Saxlanmış məlumat göstərilir.");
        assertThat(requestedPaths()).isEmpty();
    }

    private void givenPublishedTournaments() throws Exception {
        prepareResults();
        publishStage("/matches/results/event/901", 601, "Final");
        website.compute("/matches/results/event/901?page=2", (path, body) -> body.replace("16 Nov.", "12 Nov."));
        website.put("/matches/601", html("singles-detail.html").getBody().readUtf8()
                .replace("16 Nov.", "14 Nov.").replace("Duello", NEWER).replace(">1<", ">0<"));
        website.put(CATALOGUE, """
                {"version":1,"tournaments":[
                  {"id":902,"name":"Bakı Şəxsi Kuboku 2025","format":"singles","start":"2025-11-08","end":"2025-11-08"},
                  {"id":903,"name":"Gəncə Kuboku","format":"singles","start":"2026-11-15","end":"2026-11-15"},
                  {"id":906,"name":"Naməlum Kubok","format":"unknown","start":"2026-11-18","end":"2026-11-18"},
                  {"id":904,"name":"Komanda Liqası","format":"team","start":"2026-11-18","end":"2026-11-18"},
                  {"id":905,"name":"Qoşa Kuboku","format":"doubles","start":"2026-11-18","end":"2026-11-18"},
                  {"id":901,"name":"Bakı Şəxsi Kuboku 2026","format":"singles","start":"2026-11-14","end":"2026-11-16","players":24,"prize":"0 AZN"}
                ]}
                """);
    }

    private void openSingles() throws Exception {
        clickText("☰");
        clickText("Şəxsi turnirlər");
        followNavigation();
    }

    private void searchFor(String query) {
        ((EditText) activity().findViewById(R.id.search)).setText(query);
        layoutTournamentList();
    }

    private List<String> tournamentNames() {
        layoutTournamentList();
        return visibleText().stream().filter(List.of(NEWER, OTHER, OLDER)::contains)
                .collect(java.util.stream.Collectors.toList());
    }

    private void layoutTournamentList() {
        android.view.View root = activity().getWindow().getDecorView();
        root.measure(android.view.View.MeasureSpec.makeMeasureSpec(411, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(891, android.view.View.MeasureSpec.EXACTLY));
        root.layout(0, 0, 411, 891);
    }
}
