package az.tabletennis;

import static org.assertj.core.api.Assertions.assertThat;

import android.content.Context;
import android.widget.TextView;
import java.nio.charset.StandardCharsets;
import okhttp3.mockwebserver.MockResponse;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 26, qualifiers = "w411dp-h891dp", application = RankingsUiScenario.FixtureApplication.class)
public class RankingsMigrationTest extends RankingsUiScenario {
    @Test public void oldMixedRankingsStayHiddenUntilAnAmateurDownloadSucceedsAndRemainReplacedOffline() throws Exception {
        // arrange
        String mixed = "{\"updatedAt\":" + FixtureApplication.time.millis() + ",\"players\":["
                + "{\"id\":11,\"name\":\"Şəms Quliyeva\",\"points\":950},"
                + "{\"id\":12,\"name\":\"İlqar Həsənov\",\"points\":800}]}";
        try (var saved = RuntimeEnvironment.getApplication().openFileOutput("rankings.json", Context.MODE_PRIVATE)) {
            saved.write(mixed.getBytes(StandardCharsets.UTF_8));
        }
        FixtureApplication.online = false;

        // act
        openScreen();

        // assert
        awaitText("Həvəskar reytinqini yükləmək üçün internetə qoşulub yenidən cəhd edin.");
        assertThat(visibleText()).noneMatch(text -> text.matches("\\d+\\. .* — \\d+ xal"));
        assertThat(((TextView) screen.get().findViewById(R.id.updated)).getText().toString()).isEmpty();

        // arrange
        FixtureApplication.online = true;
        server.enqueue(new MockResponse().setResponseCode(503));

        // act
        screen.pause().resume().visible();

        // assert
        awaitText("Həvəskar reytinqini yükləmək üçün internetə qoşulub yenidən cəhd edin.");
        assertThat(visibleText()).noneMatch(text -> text.matches("\\d+\\. .* — \\d+ xal"));
        assertThat(((TextView) screen.get().findViewById(R.id.updated)).getText().toString()).isEmpty();

        // arrange
        server.enqueue(html("amateur.html"));

        // act
        screen.get().findViewById(R.id.refresh).performClick();

        // assert
        awaitText("1. İlqar Həsənov — 800 xal");
        assertThat(visibleText()).contains("1. Əli Məmmədov — 800 xal", "3. Işıl Əliyeva — 600 xal")
                .doesNotContain("1. Şəms Quliyeva — 950 xal");
        String updated = ((TextView) screen.get().findViewById(R.id.updated)).getText().toString();
        assertThat(updated).startsWith("Son uğurlu yenilənmə: ");

        // arrange
        FixtureApplication.online = false;
        server.shutdown();

        // act
        screen.pause().stop().destroy();
        openScreen();

        // assert
        awaitText("İnternet bağlantısı yoxdur. Saxlanmış məlumat göstərilir.");
        assertThat(visibleText()).contains("1. İlqar Həsənov — 800 xal", "1. Əli Məmmədov — 800 xal", "3. Işıl Əliyeva — 600 xal")
                .doesNotContain("1. Şəms Quliyeva — 950 xal");
        assertThat(((TextView) screen.get().findViewById(R.id.updated)).getText().toString()).isEqualTo(updated);
    }
}
