package az.tabletennis;

import static org.assertj.core.api.Assertions.assertThat;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.VectorDrawable;
import java.util.Arrays;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.ParameterizedRobolectricTestRunner;
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters;
import org.robolectric.annotation.Config;

@RunWith(ParameterizedRobolectricTestRunner.class)
@Config(sdk = 26, qualifiers = "w411dp-h891dp", application = RankingsUiScenario.FixtureApplication.class)
public class DoublesPhotosTest extends DoublesPhotoScenario {
    private static final String NAMES = "İlqar Həsənov\nƏli Məmmədov";
    private final boolean first;
    private final boolean second;

    public DoublesPhotosTest(boolean first, boolean second) { this.first = first; this.second = second; }

    @Parameters(name = "first portrait {0}, second portrait {1}") public static Collection<Object[]> portraits() {
        return Arrays.asList(new Object[][] {{true, true}, {true, false}, {false, true}, {false, false}});
    }

    @Test public void partnershipShowsIndependentPortraitHalvesAndReusesThemOffline() throws Exception {
        // arrange
        prepareDoubles();
        website.put("/matches/results/duo/110", pairHistory("<div class='no-info'>Matç yoxdur</div>"));
        website.compute("/api/getDuoInfo?id=110", (path, body) -> photos(body,
                first ? "/portraits/ilqar.png" : "/assets/img/person.png",
                second ? "/portraits/ali.png" : ""));
        responses.put("/portraits/ilqar.png", () -> portrait(Color.RED));
        responses.put("/portraits/ali.png", () -> portrait(Color.BLUE));

        // act
        openScreen();
        clickText("Qoşalar");

        // assert
        awaitText("1. İlqar Həsənov / Əli Məmmədov — 800 xal");
        if (first) awaitHalf(NAMES, 0, Color.RED);
        else assertThat(half(NAMES, 0).getDrawable()).isInstanceOf(VectorDrawable.class);
        if (second) awaitHalf(NAMES, 1, Color.BLUE);
        else assertThat(half(NAMES, 1).getDrawable()).isInstanceOf(VectorDrawable.class);
        assertThat(findText(activity().getWindow().getDecorView(), "#1")).isNotNull();
        assertThat(findText(activity().getWindow().getDecorView(), "800 xal")).isNotNull();

        // act
        Bitmap rendered = renderedAvatar(NAMES);

        // assert
        assertThat(Color.alpha(rendered.getPixel(0, 0))).isZero();
        assertThat(Color.alpha(rendered.getPixel(rendered.getWidth() - 1, rendered.getHeight() - 1))).isZero();
        assertThat(Color.alpha(rendered.getPixel(rendered.getWidth() / 4, rendered.getHeight() / 2))).isEqualTo(255);
        assertThat(Color.alpha(rendered.getPixel(3 * rendered.getWidth() / 4, rendered.getHeight() / 2))).isEqualTo(255);
        if (first) assertThat(rendered.getPixel(rendered.getWidth() / 4, rendered.getHeight() / 2)).isEqualTo(Color.RED);
        if (second) assertThat(rendered.getPixel(3 * rendered.getWidth() / 4, rendered.getHeight() / 2)).isEqualTo(Color.BLUE);
        assertThat(requestedPaths()).doesNotContain("/players/12", "/players/13", "/assets/img/person.png");
        String updated = ((android.widget.TextView) activity().findViewById(R.id.updated)).getText().toString();

        // act
        clickText("1. İlqar Həsənov / Əli Məmmədov — 800 xal");
        followNavigation();

        // assert
        awaitText("Matç tarixçəsi yoxdur.");
        assertProfilePortrait();
        assertThat(requestedPaths()).filteredOn(path -> !path.startsWith("/portraits/"))
                .containsExactly("/api/getDuoInfo?id=110", "/matches/results/duo/110");

        // act
        back();

        // arrange
        FixtureApplication.online = false;
        ((RankingsApplication) activity().getApplication()).onTrimMemory(android.content.ComponentCallbacks2.TRIM_MEMORY_COMPLETE);

        // act
        restart();
        clickText("Qoşalar");

        // assert
        awaitText("1. İlqar Həsənov / Əli Məmmədov — 800 xal");
        if (first) awaitHalf(NAMES, 0, Color.RED);
        else assertThat(half(NAMES, 0).getDrawable()).isInstanceOf(VectorDrawable.class);
        if (second) awaitHalf(NAMES, 1, Color.BLUE);
        else assertThat(half(NAMES, 1).getDrawable()).isInstanceOf(VectorDrawable.class);
        assertThat(visibleText()).contains(updated);
        assertThat(requestedPaths()).isEmpty();

        // act
        clickText("1. İlqar Həsənov / Əli Məmmədov — 800 xal");
        followNavigation();

        // assert
        assertProfilePortrait();
        assertThat(activity().findViewById(R.id.refresh).isEnabled()).isFalse();
        assertThat(requestedPaths()).isEmpty();
    }

    private void assertProfilePortrait() {
        android.view.ViewGroup avatar = activity().findViewById(android.R.id.icon);
        assertThat(avatar).as("Selected partnership's split portrait").isNotNull();
        assertThat(avatar.getChildCount()).isEqualTo(2);
        assertHalf((android.widget.ImageView) avatar.getChildAt(0), first, Color.RED);
        assertHalf((android.widget.ImageView) avatar.getChildAt(1), second, Color.BLUE);
    }

    private void assertHalf(android.widget.ImageView image, boolean supplied, int color) {
        if (supplied) {
            assertThat(image.getDrawable()).isInstanceOf(android.graphics.drawable.BitmapDrawable.class);
            assertThat(((android.graphics.drawable.BitmapDrawable) image.getDrawable()).getBitmap().getPixel(12, 12))
                    .isEqualTo(color);
        } else assertThat(image.getDrawable()).isInstanceOf(VectorDrawable.class);
    }
}
