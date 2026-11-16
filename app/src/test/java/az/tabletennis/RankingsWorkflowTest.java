package az.tabletennis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.robolectric.Shadows.shadowOf;

import android.os.Looper;
import android.widget.EditText;
import android.widget.TextView;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {26, 36}, qualifiers = "w411dp-h891dp", application = RankingsUiScenario.FixtureApplication.class)
public class RankingsWorkflowTest extends RankingsUiScenario {
    @Test public void combinedRankingsSearchAndOfflineRelaunch() throws Exception {
        // arrange
        server.enqueue(html("amateur.html"));
        server.enqueue(html("pro.html"));

        // act
        openScreen();
        awaitText("1. Şəms Quliyeva — 950 xal");

        // assert
        assertThat(visibleText()).containsSubsequence(
                "1. Şəms Quliyeva — 950 xal", "2. İlqar Həsənov — 800 xal",
                "2. Əli Məmmədov — 800 xal", "4. Işıl Əliyeva — 600 xal");
        assertThat(visibleText()).doesNotContain("Yan panel");
        assertThat(visibleText().stream().filter(t -> t.contains("İlqar Həsənov"))).hasSize(1);
        assertThat(server.takeRequest(1, TimeUnit.SECONDS).getPath()).isEqualTo("/players?level=amateur");
        assertThat(server.takeRequest(1, TimeUnit.SECONDS).getPath()).isEqualTo("/players?level=pro");
        String updated = ((TextView) screen.get().findViewById(R.id.updated)).getText().toString();
        assertThat(updated).startsWith("Son uğurlu yenilənmə: ");

        // act
        ((EditText) screen.get().findViewById(R.id.search)).setText("ilqar");
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100));

        // assert
        assertThat(visibleText()).contains("2. İlqar Həsənov — 800 xal")
                .doesNotContain("1. Şəms Quliyeva — 950 xal", "2. Əli Məmmədov — 800 xal");

        // act
        ((EditText) screen.get().findViewById(R.id.search)).setText("ışıl");
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100));

        // assert
        assertThat(visibleText()).contains("4. Işıl Əliyeva — 600 xal");

        // act
        screen.pause().stop().destroy();
        server.shutdown();
        FixtureApplication.time = java.time.Clock.offset(FixtureApplication.time, Duration.ofDays(365));
        openScreen();
        screen.get().findViewById(R.id.refresh).performClick();
        awaitText("Yeniləmək mümkün olmadı. Saxlanmış reytinq göstərilir.");

        // assert
        assertThat(visibleText()).contains("1. Şəms Quliyeva — 950 xal",
                "2. İlqar Həsənov — 800 xal", "2. Əli Məmmədov — 800 xal", "4. Işıl Əliyeva — 600 xal");
        assertThat(((TextView) screen.get().findViewById(R.id.updated)).getText().toString()).isEqualTo(updated);
    }

}
