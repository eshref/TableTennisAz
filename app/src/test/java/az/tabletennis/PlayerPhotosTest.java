package az.tabletennis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.robolectric.Shadows.shadowOf;

import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.os.Looper;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.EditText;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 26, qualifiers = "w411dp-h891dp", application = RankingsUiScenario.FixtureApplication.class)
public class PlayerPhotosTest extends PlayerPhotoScenario {
    @Test public void aSlowOldPhotoUrlCannotReplaceTheNewPortraitOnAnOfflineRelaunch() throws Exception {
        // arrange
        CountDownLatch release = new CountDownLatch(1);
        website.put("/players?level=amateur", ranking("/portraits/old.png", "/assets/img/person.png"));
        responses.put("/portraits/old.png", () -> {
            try { release.await(15, TimeUnit.SECONDS); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            return portrait(Color.RED);
        });

        try {
            // act
            openScreen();

            // assert
            awaitText("Məlumat yeniləndi.");
            assertThat(requestTo("/portraits/old.png")).isEqualTo("/portraits/old.png");

            // arrange
            website.put("/players?level=amateur", ranking("/portraits/new.png", "/assets/img/person.png"));
            responses.put("/portraits/new.png", () -> portrait(Color.BLUE));

            // act
            activity().findViewById(R.id.refresh).performClick();

            // assert
            awaitPortrait("İlqar Həsənov", Color.BLUE);

            // act
            release.countDown();

            // assert
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            do {
                shadowOf(Looper.getMainLooper()).idle();
                assertThat(((BitmapDrawable) photo("İlqar Həsənov").getDrawable()).getBitmap().getPixel(12, 12)).isEqualTo(Color.BLUE);
                Thread.sleep(20);
            } while (System.nanoTime() < deadline);

            // arrange
            FixtureApplication.online = false;
            ((RankingsApplication) activity().getApplication()).onTrimMemory(android.content.ComponentCallbacks2.TRIM_MEMORY_COMPLETE);

            // act
            restart();

            // assert
            awaitPortrait("İlqar Həsənov", Color.BLUE);
        } finally {
            release.countDown();
        }
    }

    @Test public void aDelayedImageCannotOverwriteAnotherPlayerInTheRecycledSearchRow() throws Exception {
        // arrange
        website.put("/players?level=amateur", ranking("/portraits/slow.png", "/portraits/ali.png"));
        responses.put("/portraits/slow.png", () -> portrait(Color.RED).setBodyDelay(1, TimeUnit.SECONDS));
        responses.put("/portraits/ali.png", () -> portrait(Color.BLUE));

        // act
        openScreen();

        // assert
        awaitText("Məlumat yeniləndi.");
        ImageView first = photo("İlqar Həsənov");
        awaitPortrait("Əli Məmmədov", Color.BLUE);

        // act
        ((EditText) activity().findViewById(R.id.search)).setText("əli");
        shadowOf(Looper.getMainLooper()).idle();

        // assert
        assertThat(photo("Əli Məmmədov")).isSameAs(first);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        do {
            shadowOf(Looper.getMainLooper()).idle();
            assertThat(((BitmapDrawable) photo("Əli Məmmədov").getDrawable()).getBitmap().getPixel(12, 12)).isEqualTo(Color.BLUE);
            Thread.sleep(20);
        } while (System.nanoTime() < deadline);

        // act
        ((EditText) activity().findViewById(R.id.search)).setText("");

        // assert
        awaitPortrait("İlqar Həsənov", Color.RED);
        awaitPortrait("Əli Məmmədov", Color.BLUE);
    }

    @Test public void rankingShowsSeparateNamesPointsRanksAndSourcePortraitsWithoutProfileRequests() throws Exception {
        // arrange
        website.put("/players?level=amateur", ranking("/portraits/ilqar.png", "/portraits/ali.png"));
        responses.put("/portraits/ilqar.png", () -> portrait(Color.RED));
        responses.put("/portraits/ali.png", () -> portrait(Color.BLUE));

        // act
        openScreen();

        // assert
        awaitText("Məlumat yeniləndi.");
        assertThat(findText(activity().getWindow().getDecorView(), "İlqar Həsənov")).isNotNull();
        assertThat(findText(activity().getWindow().getDecorView(), "800 xal")).isNotNull();
        awaitPortrait("İlqar Həsənov", Color.RED);
        awaitPortrait("Əli Məmmədov", Color.BLUE);
        // arrange
        android.graphics.Bitmap rendered = android.graphics.Bitmap.createBitmap(photo("İlqar Həsənov").getWidth(),
                photo("İlqar Həsənov").getHeight(), android.graphics.Bitmap.Config.ARGB_8888);

        // act
        photo("İlqar Həsənov").draw(new android.graphics.Canvas(rendered));

        // assert
        assertThat(Color.alpha(rendered.getPixel(0, 0))).isZero();
        assertThat(rendered.getPixel(rendered.getWidth() / 2, rendered.getHeight() / 2)).isEqualTo(Color.RED);
        assertThat(requestedPaths()).containsExactlyInAnyOrder("/players?level=amateur", "/portraits/ilqar.png", "/portraits/ali.png");
    }

    @Test public void delayedPortraitStaysWithItsPlayerThroughRecyclingModeChangesRecreationAndNavigation() throws Exception {
        // arrange
        CountDownLatch release = new CountDownLatch(1);
        website.put("/players?level=amateur", ranking("/portraits/slow.png", "/portraits/ali.png"));
        responses.put("/portraits/slow.png", () -> {
            try {
                if (!release.await(8, TimeUnit.SECONDS)) return new MockResponse().setResponseCode(504);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return new MockResponse().setResponseCode(503);
            }
            return portrait(Color.RED);
        });
        responses.put("/portraits/ali.png", () -> portrait(Color.BLUE));
        prepareProfile(13, "Əli Məmmədov");
        prepareDoubles();

        try {
            // act
            openScreen();

            // assert
            awaitText("Məlumat yeniləndi.");
            assertThat(photo("İlqar Həsənov").getDrawable()).isInstanceOf(android.graphics.drawable.VectorDrawable.class);
            awaitPortrait("Əli Məmmədov", Color.BLUE);

            // act
            ((EditText) activity().findViewById(R.id.search)).setText("əli");
            shadowOf(Looper.getMainLooper()).idle();

            // assert
            awaitPortrait("Əli Məmmədov", Color.BLUE);
            assertThat(visibleText()).contains("2. Əli Məmmədov — 700 xal").doesNotContain("1. İlqar Həsənov — 800 xal");

            // act
            clickText("Qoşalar");
            clickText("Oyunçular");
            screen.recreate();
            shadowOf(Looper.getMainLooper()).idle();
            clickText("Əli Məmmədov");
            followNavigation();

            // assert
            awaitText("Əli Məmmədov");
            assertThat(((TextView) activity().findViewById(R.id.profile_name)).getText().toString()).isEqualTo("Əli Məmmədov");

            // act
            release.countDown();
            back();
            ((EditText) activity().findViewById(R.id.search)).setText("");

            // assert
            awaitPortrait("İlqar Həsənov", Color.RED);
            awaitPortrait("Əli Məmmədov", Color.BLUE);
            assertThat(requestedPaths()).doesNotContain("/players/12");
        } finally {
            release.countDown();
        }
    }

    @Test public void failedReplacementKeepsTheDownloadedPortraitAndOfflineRelaunchReadsItFromDisk() throws Exception {
        // arrange
        website.put("/players?level=amateur", ranking("/portraits/old.png", "/assets/img/person.png"));
        responses.put("/portraits/old.png", () -> portrait(Color.RED));

        // act
        openScreen();

        // assert
        awaitText("Məlumat yeniləndi.");
        awaitPortrait("İlqar Həsənov", Color.RED);

        // arrange
        FixtureApplication.time = java.time.Clock.offset(FixtureApplication.time, java.time.Duration.ofMinutes(1));
        website.put("/players?level=amateur", ranking("/portraits/new.png", "/assets/img/person.png").replace("800 Xal", "850 Xal"));
        responses.put("/portraits/new.png", () -> new MockResponse().setResponseCode(503));

        // act
        activity().findViewById(R.id.refresh).performClick();

        // assert
        awaitText("1. İlqar Həsənov — 850 xal");
        assertThat(requestTo("/portraits/new.png")).isEqualTo("/portraits/new.png");
        awaitPortrait("İlqar Həsənov", Color.RED);
        String updated = ((TextView) activity().findViewById(R.id.updated)).getText().toString();

        // arrange
        FixtureApplication.online = false;
        requestedPaths();
        // Android may discard all memory while retaining app files; recreation must use the durable image.
        ((RankingsApplication) activity().getApplication()).onTrimMemory(android.content.ComponentCallbacks2.TRIM_MEMORY_COMPLETE);

        // act
        restart();

        // assert
        awaitPortrait("İlqar Həsənov", Color.RED);
        assertThat(photo("Əli Məmmədov").getDrawable()).isInstanceOf(android.graphics.drawable.VectorDrawable.class);
        assertThat(visibleText()).contains("1. İlqar Həsənov — 850 xal", updated);
        assertThat(requestedPaths()).isEmpty();
    }

}
