package az.tabletennis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.robolectric.Shadows.shadowOf;

import android.Manifest;
import android.app.Instrumentation;
import android.app.NotificationManager;
import android.content.pm.PackageManager;
import android.os.Looper;
import android.widget.Switch;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.messaging.RemoteMessage;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
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
        TournamentAlerts.Topic topic = subscribe -> Tasks.forResult(null);
        @Override protected Clock clock() { return Clock.fixed(now, ZoneOffset.UTC); }
        @Override protected TournamentAlerts.Topic tournamentAlertTopic() { return topic; }
        @Override protected boolean isOnline() { return false; }
    }

    @Test public void aLateSubscriptionConfirmationCannotRestoreAlertsAfterOptOut() {
        // arrange
        var application = (FixtureApplication) RuntimeEnvironment.getApplication();
        var subscription = new TaskCompletionSource<Void>();
        List<Boolean> topicRequests = new ArrayList<>();
        application.topic = subscribe -> {
            topicRequests.add(subscribe);
            return subscribe ? subscription.getTask() : Tasks.forResult(null);
        };
        FixtureApplication.now = PUBLISHED.minusSeconds(1);
        var service = Robolectric.buildService(TournamentMessagingService.class).create();
        application.tournamentAlerts().enable();

        // act
        application.tournamentAlerts().disable();
        subscription.setResult(null);
        shadowOf(Looper.getMainLooper()).idle();
        FixtureApplication.now = PUBLISHED.plusSeconds(1);
        service.get().onMessageReceived(message("944", "BTTC Şəxsi Reytinq Turniri"));

        // assert
        assertThat(topicRequests).containsExactly(true, false);
        assertThat(application.getSystemService(NotificationManager.class).getActiveNotifications()).isEmpty();
        service.destroy();
    }

    @Test @Config(sdk = 36)
    public void grantingPermissionAgainThroughTheSwitchStartsAFreshOptIn() {
        // arrange
        var application = (FixtureApplication) RuntimeEnvironment.getApplication();
        var manager = application.getSystemService(NotificationManager.class);
        givenActiveAlerts(application);
        String permission = Manifest.permission.POST_NOTIFICATIONS;
        shadowOf(application).denyPermissions(permission);
        shadowOf(manager).setNotificationsEnabled(false);
        FixtureApplication.now = PUBLISHED.plusSeconds(1);
        var screen = Robolectric.buildActivity(TournamentAlertsActivity.class).setup();
        Switch toggle = screen.get().findViewById(R.id.tournament_alerts_switch);
        var service = Robolectric.buildService(TournamentMessagingService.class).create();

        // act
        toggle.performClick();
        int request = shadowOf(screen.get()).getLastRequestedPermission().requestCode;
        shadowOf(application).grantPermissions(permission);
        shadowOf(manager).setNotificationsEnabled(true);
        service.get().onMessageReceived(message("941", "BTTC Qoşa Reytinq Turniri #120"));
        screen.get().onRequestPermissionsResult(request, new String[] {permission},
                new int[] {PackageManager.PERMISSION_GRANTED});
        shadowOf(Looper.getMainLooper()).idle();
        service.get().onMessageReceived(message("944", "BTTC Şəxsi Reytinq Turniri"));

        // assert
        assertThat(toggle.isChecked()).isTrue();
        assertThat(manager.getActiveNotifications()).isEmpty();
        service.destroy();
        screen.pause().stop().destroy();
    }

    @Test public void aQueuedMessageAlertsOnceWithPermissionBeforeExpiryEvenAcrossRestart() throws Exception {
        // arrange
        var original = (FixtureApplication) RuntimeEnvironment.getApplication();
        var manager = original.getSystemService(NotificationManager.class);
        givenActiveAlerts(original);
        FixtureApplication.now = PUBLISHED.plus(Duration.ofHours(24)).minusMillis(1);
        RemoteMessage queued = message("944", "BTTC Şəxsi Reytinq Turniri");
        var service = Robolectric.buildService(TournamentMessagingService.class).create();
        shadowOf(manager).setNotificationsEnabled(false);

        // act
        service.get().onMessageReceived(queued);

        // assert
        assertThat(manager.getActiveNotifications()).isEmpty();

        // arrange
        shadowOf(manager).setNotificationsEnabled(true);

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

    private void givenActiveAlerts(FixtureApplication application) {
        FixtureApplication.now = PUBLISHED.minusSeconds(1);
        var subscription = application.tournamentAlerts().enable();
        shadowOf(Looper.getMainLooper()).idle();
        if (!subscription.getResult()) throw new IllegalStateException("Alert subscription failed");
    }

    private RemoteMessage message(String id, String name) {
        return new RemoteMessage.Builder("fixture")
                .addData("eventId", id)
                .addData("name", name)
                .addData("publishedAt", Long.toString(PUBLISHED.toEpochMilli()))
                .build();
    }
}
