package az.tabletennis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.robolectric.Shadows.shadowOf;

import android.app.Instrumentation;
import android.app.NotificationManager;
import android.os.Looper;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.messaging.RemoteMessage;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowContextImpl;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {26, 36}, application = TournamentMessagingTest.FixtureApplication.class)
public class TournamentMessagingTest {
    private static final Instant PUBLISHED = Instant.parse("2026-11-22T10:00:00Z");

    public static class FixtureApplication extends RankingsApplication {
        static Instant now;
        @Override protected Clock clock() { return Clock.fixed(now, ZoneOffset.UTC); }
        @Override protected TournamentAlerts.Topic tournamentAlertTopic() { return subscribe -> Tasks.forResult(null); }
    }

    @Test public void aQueuedMessageAlertsBeforeExpiryAndCannotAlertAgainAfterRestart() throws Exception {
        // arrange
        var original = (FixtureApplication) RuntimeEnvironment.getApplication();
        var manager = original.getSystemService(NotificationManager.class);
        FixtureApplication.now = PUBLISHED.minusSeconds(1);
        var subscription = original.tournamentAlerts().enable();
        shadowOf(Looper.getMainLooper()).idle();
        if (!subscription.getResult()) throw new IllegalStateException("Alert subscription failed");
        FixtureApplication.now = PUBLISHED.plus(Duration.ofHours(24)).minusMillis(1);
        RemoteMessage queued = message("944", "BTTC Şəxsi Reytinq Turniri");
        var service = Robolectric.buildService(TournamentMessagingService.class).create();

        // act
        service.get().onMessageReceived(queued);
        service.get().onMessageReceived(queued);

        // assert
        assertThat(manager.getActiveNotifications()).singleElement().satisfies(alert -> {
            assertThat(alert.getTag()).isEqualTo("944");
            assertThat(alert.getNotification().extras.getCharSequence(android.app.Notification.EXTRA_TITLE))
                    .hasToString("BTTC Şəxsi Reytinq Turniri");
        });

        // arrange
        manager.cancelAll();
        service.destroy();
        ShadowContextImpl.reset();
        var restarted = Instrumentation.newApplication(FixtureApplication.class, original.getBaseContext());
        RuntimeEnvironment.application = restarted;
        try {
            var restartedService = Robolectric.buildService(TournamentMessagingService.class).create();

            // act
            restartedService.get().onMessageReceived(queued);
            FixtureApplication.now = PUBLISHED.plus(Duration.ofHours(24));
            restartedService.get().onMessageReceived(message("941", "BTTC Qoşa Reytinq Turniri #120"));

            // assert
            assertThat(manager.getActiveNotifications()).isEmpty();
            restartedService.destroy();
        } finally {
            RuntimeEnvironment.application = original;
        }
    }

    private RemoteMessage message(String id, String name) {
        return new RemoteMessage.Builder("fixture")
                .addData("eventId", id)
                .addData("name", name)
                .addData("publishedAt", Long.toString(PUBLISHED.toEpochMilli()))
                .build();
    }
}
