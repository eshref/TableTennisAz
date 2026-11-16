package az.tabletennis;

import static org.assertj.core.api.Assertions.assertThat;

import android.content.Context;
import android.graphics.Color;
import android.widget.TextView;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.ParameterizedRobolectricTestRunner;
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(ParameterizedRobolectricTestRunner.class)
@Config(sdk = 26, qualifiers = "w411dp-h891dp", application = RankingsUiScenario.FixtureApplication.class)
public class ProfilePhotosTest extends PlayerPhotoScenario {
    private final String profilePhoto;
    private final String rankingPhoto;

    public ProfilePhotosTest(String source, String profilePhoto, String rankingPhoto) {
        this.profilePhoto = profilePhoto;
        this.rankingPhoto = rankingPhoto;
    }

    @Parameters(name = "{0}") public static Collection<Object[]> sources() {
        return Arrays.asList(new Object[][] {
                {"profile portrait takes precedence", "/portraits/selected.png", "/portraits/unused.png"},
                {"ranking portrait when profile uses a placeholder", "/assets/img/person.png", "/portraits/selected.png"}
        });
    }

    @Test public void legacyProfileEnrichesWithoutWaitingForPhotosAndKeepsItsPortraitAcrossFailureAndOfflineRestart() throws Exception {
        // arrange
        try (var input = getClass().getResourceAsStream("/fixtures/legacy-profile-12");
             var output = RuntimeEnvironment.getApplication().openFileOutput("saved-profile-12", Context.MODE_PRIVATE)) {
            input.transferTo(output);
        }
        FixtureApplication.online = false;

        // act
        openDirect(ProfileActivity.class, "playerId", 12);

        // assert
        assertThat(visibleText()).contains("İlqar Həsənov", "800 xal", "Mövqe mövcud deyil", "Xal tarixçəsi",
                "İnternet bağlantısı yoxdur. Saxlanmış məlumat göstərilir.",
                "15 Nov. 2026 · İlqar Həsənov / Əli Məmmədov — Şəms Quliyeva / Işıl Əliyeva · 3-2 · Kupa · Qoşalar · Turnir");
        assertThat(photo("İlqar Həsənov").getDrawable()).isInstanceOf(android.graphics.drawable.VectorDrawable.class);
        assertThat(activity().findViewById(R.id.refresh).isEnabled()).isFalse();
        assertThat(requestedPaths()).isEmpty();

        // arrange
        closeProductScreens();
        FixtureApplication.online = true;
        prepareProfile(12, "İlqar Həsənov");
        website.compute("/players/12", (path, body) -> body.replace("<div class='name-div'>",
                "<div class='player-block-picture'><img src='" + profilePhoto + "'></div><div class='name-div'>"));
        website.put("/players?level=amateur", ranking(rankingPhoto, "/assets/img/person.png"));
        CountDownLatch release = new CountDownLatch(1);
        responses.put("/portraits/selected.png", () -> {
            try { release.await(10, TimeUnit.SECONDS); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            return portrait(Color.RED);
        });

        try {
            // act
            openDirect(ProfileActivity.class, "playerId", 12);
            activity().findViewById(R.id.refresh).performClick();

            // assert
            awaitText("Məlumat yeniləndi.");
            assertThat(requestTo("/portraits/selected.png")).isEqualTo("/portraits/selected.png");
            assertThat(visibleText()).contains("800 xal", "#1", "Həvəskar oyunçu", "Xal tarixçəsi");
            assertThat(photo("İlqar Həsənov").getDrawable()).isInstanceOf(android.graphics.drawable.VectorDrawable.class);
            String updated = ((TextView) activity().findViewById(R.id.updated)).getText().toString();

            // act
            recreateProductScreen();
            release.countDown();

            // assert
            awaitPortrait("İlqar Həsənov", Color.RED);
            assertThat(visibleText()).contains(updated);
            assertThat(requestedPaths()).doesNotContain("/portraits/unused.png", "/assets/img/person.png");

            // arrange
            FixtureApplication.time = java.time.Clock.offset(FixtureApplication.time, java.time.Duration.ofMinutes(2));
            responses.put("/portraits/selected.png", () -> new MockResponse().setBody("not an image"));

            // act
            activity().findViewById(R.id.refresh).performClick();

            // assert
            awaitText("Məlumat yeniləndi.");
            assertThat(requestTo("/portraits/selected.png")).isEqualTo("/portraits/selected.png");
            awaitPortrait("İlqar Həsənov", Color.RED);
            updated = ((TextView) activity().findViewById(R.id.updated)).getText().toString();

            // arrange
            closeProductScreens();
            FixtureApplication.online = false;
            ((RankingsApplication) RuntimeEnvironment.getApplication()).onTrimMemory(android.content.ComponentCallbacks2.TRIM_MEMORY_COMPLETE);
            requestedPaths();

            // act
            openDirect(ProfileActivity.class, "playerId", 12);

            // assert
            awaitPortrait("İlqar Həsənov", Color.RED);
            assertThat(visibleText()).contains("#1", "800 xal", updated, "İnternet bağlantısı yoxdur. Saxlanmış məlumat göstərilir.");
            assertThat(requestedPaths()).isEmpty();
        } finally { release.countDown(); }
    }
}
