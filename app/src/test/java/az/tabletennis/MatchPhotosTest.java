package az.tabletennis;

import static org.assertj.core.api.Assertions.assertThat;

import android.graphics.Color;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 26, qualifiers = "w411dp-h891dp", application = RankingsUiScenario.FixtureApplication.class)
public class MatchPhotosTest extends PlayerPhotoScenario {
    @Test public void independentlyLinkedDoublesPortraitsDoNotDelayDetailsOrLinksAndSurviveOfflineRecreation() throws Exception {
        // arrange
        try (var input = getClass().getResourceAsStream("/fixtures/legacy-match-501");
             var output = RuntimeEnvironment.getApplication().openFileOutput("saved-match-501", android.content.Context.MODE_PRIVATE)) {
            input.transferTo(output);
        }
        FixtureApplication.online = false;

        // act
        openDirect(MatchActivity.class, "matchId", 501);

        // assert
        assertThat(visibleText()).contains("İlqar Həsənov", "Əli Məmmədov", "Şəms Quliyeva", "Işıl Əliyeva", "3", "2",
                "15 Nov. 2026 · 18:00", "İnternet bağlantısı yoxdur. Saxlanmış məlumat göstərilir.");
        assertThat(photo("İlqar Həsənov").getDrawable()).isInstanceOf(android.graphics.drawable.VectorDrawable.class);
        assertThat(requestedPaths()).isEmpty();

        // arrange
        closeProductScreens();
        FixtureApplication.online = true;
        String source = html("doubles-detail.html").getBody().readUtf8()
                .replace("href=\"/players/12\">", "href=\"/players/12\"><img src='/portraits/12.png'>")
                .replace("href=\"/players/13\">", "href=\"/players/13\"><img src='/portraits/13.png'>")
                .replace("href=\"/players/11\">", "href=\"/players/11\"><img src='/portraits/11.png'>");
        website.put("/matches/501", source);
        prepareProfile(14, "Işıl Əliyeva");
        responses.put("/portraits/12.png", () -> portrait(Color.RED));
        responses.put("/portraits/11.png", () -> new MockResponse().setBody("not an image"));
        CountDownLatch release = new CountDownLatch(1);
        responses.put("/portraits/13.png", () -> {
            try { release.await(10, TimeUnit.SECONDS); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            return portrait(Color.BLUE);
        });

        try {
            // act
            openDirect(MatchActivity.class, "matchId", 501);
            activity().findViewById(R.id.refresh).performClick();

            // assert
            awaitText("Məlumat yeniləndi.");
            assertThat(visibleText()).contains("İlqar Həsənov", "Əli Məmmədov", "Şəms Quliyeva", "Işıl Əliyeva", "3", "2");
            awaitPortrait("İlqar Həsənov", Color.RED);
            assertThat(requestTo("/portraits/13.png")).isEqualTo("/portraits/13.png");
            assertThat(photo("Əli Məmmədov").getDrawable()).isInstanceOf(android.graphics.drawable.VectorDrawable.class);
            assertThat(photo("Şəms Quliyeva").getDrawable()).isInstanceOf(android.graphics.drawable.VectorDrawable.class);
            assertThat(photo("Işıl Əliyeva").getDrawable()).isInstanceOf(android.graphics.drawable.VectorDrawable.class);
            String updated = ((TextView) activity().findViewById(R.id.updated)).getText().toString();
            assertThat(requestedPaths()).noneMatch(path -> path.startsWith("/players"));

            // act
            clickText("Işıl Əliyeva");
            followNavigation();

            // assert
            awaitText("600 xal");

            // act
            back();
            recreateProductScreen();
            release.countDown();

            // assert
            awaitPortrait("Əli Məmmədov", Color.BLUE);
            awaitPortrait("İlqar Həsənov", Color.RED);
            assertThat(visibleText()).contains(updated);

            // arrange
            closeProductScreens();
            FixtureApplication.online = false;
            ((RankingsApplication) RuntimeEnvironment.getApplication()).onTrimMemory(android.content.ComponentCallbacks2.TRIM_MEMORY_COMPLETE);
            requestedPaths();

            // act
            openDirect(MatchActivity.class, "matchId", 501);

            // assert
            awaitPortrait("İlqar Həsənov", Color.RED);
            awaitPortrait("Əli Məmmədov", Color.BLUE);
            assertThat(visibleText()).contains(updated, "3", "2", "İnternet bağlantısı yoxdur. Saxlanmış məlumat göstərilir.");
            assertThat(photo("Şəms Quliyeva").getDrawable()).isInstanceOf(android.graphics.drawable.VectorDrawable.class);
            assertThat(requestedPaths()).isEmpty();
        } finally { release.countDown(); }
    }

    @Override protected ImageView photo(String name) {
        View label = findText(activity().getWindow().getDecorView(), name);
        if (label == null) throw new IllegalStateException("No visible participant: " + name);
        return ((View) label.getParent()).findViewById(android.R.id.icon);
    }
}
