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
    @Test public void amateurRankingsSearchAndOfflineRelaunch() throws Exception {
        // arrange
        String amateurs = html("amateur.html").getBody().readUtf8().replace("</div></body></html>",
                "<a href='/players/15'><div class='rank-name'>Nigar Əliyeva</div>"
                        + "<div class='rank-points'>900 Xal</div></a></div></body></html>");
        server.enqueue(new okhttp3.mockwebserver.MockResponse().setBody(amateurs));
        server.enqueue(html("pro.html"));

        // act
        openScreen();

        // assert
        awaitText("1. Nigar Əliyeva — 900 xal");
        assertThat(visibleText()).containsSubsequence(
                "1. Nigar Əliyeva — 900 xal", "2. İlqar Həsənov — 800 xal",
                "2. Əli Məmmədov — 800 xal", "4. Işıl Əliyeva — 600 xal");
        assertThat(visibleText()).contains("Həvəskar oyunçular. Yerlər xal üzrə tətbiqdə hesablanır; eyni xallar eyni yeri bölüşür.")
                .doesNotContain("Yan panel", "1. Şəms Quliyeva — 950 xal");
        assertThat(visibleText().stream().filter(t -> t.contains("İlqar Həsənov"))).hasSize(1);
        assertThat(server.takeRequest(1, TimeUnit.SECONDS).getPath()).isEqualTo("/players?level=amateur");
        assertThat(server.getRequestCount()).isEqualTo(1);
        String updated = ((TextView) screen.get().findViewById(R.id.updated)).getText().toString();
        assertThat(updated).startsWith("Son uğurlu yenilənmə: ");

        // act
        ((EditText) screen.get().findViewById(R.id.search)).setText("ilqar");
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100));

        // assert
        assertThat(visibleText()).contains("2. İlqar Həsənov — 800 xal")
                .doesNotContain("1. Nigar Əliyeva — 900 xal", "2. Əli Məmmədov — 800 xal");

        // act
        ((EditText) screen.get().findViewById(R.id.search)).setText("ışıl");
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100));

        // assert
        assertThat(visibleText()).contains("4. Işıl Əliyeva — 600 xal");

        // act
        screen.pause().stop().destroy();
        FixtureApplication.online = false;
        server.shutdown();
        FixtureApplication.time = java.time.Clock.offset(FixtureApplication.time, Duration.ofDays(365));
        openScreen();
        screen.get().findViewById(R.id.refresh).performClick();

        // assert
        awaitText("Yeniləmək mümkün olmadı. Saxlanmış reytinq göstərilir.");
        assertThat(visibleText()).contains("1. Nigar Əliyeva — 900 xal",
                "2. İlqar Həsənov — 800 xal", "2. Əli Məmmədov — 800 xal", "4. Işıl Əliyeva — 600 xal")
                .doesNotContain("1. Şəms Quliyeva — 950 xal");
        assertThat(((TextView) screen.get().findViewById(R.id.updated)).getText().toString()).isEqualTo(updated);
    }

}
