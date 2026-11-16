package az.tabletennis;

import static org.assertj.core.api.Assertions.assertThat;

import android.content.Context;
import android.graphics.Color;
import android.widget.TextView;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.ParameterizedRobolectricTestRunner;
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(ParameterizedRobolectricTestRunner.class)
@Config(sdk = 26, qualifiers = "w411dp-h891dp", application = RankingsUiScenario.FixtureApplication.class)
public class PlayerPhotoMigrationTest extends PlayerPhotoScenario {
    private final String metadata;

    public PlayerPhotoMigrationTest(String scenario, String metadata) { this.metadata = metadata; }

    @Parameters(name = "{0}") public static Collection<Object[]> optionalMetadata() {
        return Arrays.asList(new Object[][] {
                {"legacy snapshot", ""},
                {"null photo", ",\"photoUrl\":null"},
                {"invalid photo type", ",\"photoUrl\":{\"unexpected\":true}"}
        });
    }

    @Test public void olderSavedRankingWorksOfflineAndAcquiresOptionalPhotosOnNormalRefresh() throws Exception {
        // arrange
        String snapshot = "{\"category\":\"amateur\",\"updatedAt\":" + FixtureApplication.time.millis()
                + ",\"players\":[{\"id\":12,\"name\":\"İlqar Həsənov\",\"points\":800" + metadata + "}]}";
        try (var output = RuntimeEnvironment.getApplication().openFileOutput("rankings.json", Context.MODE_PRIVATE)) {
            output.write(snapshot.getBytes(StandardCharsets.UTF_8));
        }
        FixtureApplication.online = false;

        // act
        openScreen();

        // assert
        awaitText("1. İlqar Həsənov — 800 xal");
        assertThat(photo("İlqar Həsənov").getDrawable()).isInstanceOf(android.graphics.drawable.VectorDrawable.class);
        assertThat(((TextView) activity().findViewById(R.id.updated)).getText().toString()).startsWith("Son uğurlu yenilənmə: ");
        assertThat(requestedPaths()).isEmpty();

        // arrange
        FixtureApplication.online = true;
        website.put("/players?level=amateur", ranking("/portraits/ilqar.png", "/assets/img/person.png"));
        responses.put("/portraits/ilqar.png", () -> portrait(Color.RED));

        // act
        screen.pause().resume().visible();
        activity().findViewById(R.id.refresh).performClick();

        // assert
        awaitText("Məlumat yeniləndi.");
        awaitPortrait("İlqar Həsənov", Color.RED);
        assertThat(requestedPaths()).containsExactlyInAnyOrder("/players?level=amateur", "/portraits/ilqar.png");
    }
}
