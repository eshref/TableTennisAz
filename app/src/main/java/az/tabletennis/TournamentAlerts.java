package az.tabletennis;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import java.time.Duration;
import java.util.Map;

final class TournamentAlerts {
    static final String TOPIC = "tournament-results-test";
    private static final String CHANNEL = "tournament-results";
    private static final String ENABLED_AT = "enabled-at";
    private static final long MAX_AGE = Duration.ofHours(24).toMillis();

    interface Topic {
        Task<Void> update(boolean subscribe);
    }

    private final RankingsApplication application;
    private final Topic topic;
    private final NotificationManager notifications;
    private final SharedPreferences preferences;
    private Task<Boolean> enabling;

    TournamentAlerts(RankingsApplication application, Topic topic) {
        this.application = application;
        this.topic = topic;
        notifications = application.getSystemService(NotificationManager.class);
        preferences = application.getSharedPreferences("tournament-alerts", 0);
    }

    boolean isAvailable() { return topic != null; }

    boolean isAllowed() {
        NotificationChannel channel = notifications.getNotificationChannel(CHANNEL);
        return notifications.areNotificationsEnabled()
                && (channel == null || channel.getImportance() != NotificationManager.IMPORTANCE_NONE);
    }

    boolean isEnabled() {
        return isAvailable() && isAllowed() && preferences.contains(ENABLED_AT);
    }

    synchronized Task<Boolean> pendingEnable() { return enabling; }

    synchronized Task<Boolean> enable() {
        if (enabling != null) return enabling;
        if (!isAvailable() || !isAllowed()) return Tasks.forResult(false);
        if (isEnabled()) return Tasks.forResult(true);
        enabling = topic.update(true).continueWith(result -> {
            synchronized (this) {
                enabling = null;
                return result.isSuccessful() && isAllowed()
                        && preferences.edit().putLong(ENABLED_AT, application.clock().millis()).commit();
            }
        });
        return enabling;
    }

    synchronized void disable() {
        preferences.edit().remove(ENABLED_AT).apply();
        if (isAvailable()) topic.update(false);
    }

    synchronized void receive(Map<String, String> data) {
        if (!isEnabled()) return;
        long id;
        long publishedAt;
        try {
            id = Long.parseLong(data.get("eventId"));
            publishedAt = Long.parseLong(data.get("publishedAt"));
        } catch (NumberFormatException invalid) {
            return;
        }
        String name = data.get("name");
        long now = application.clock().millis();
        if (id <= 0 || name == null || name.trim().isEmpty()
                || publishedAt <= preferences.getLong(ENABLED_AT, 0)
                || publishedAt <= now - MAX_AGE) return;

        notifications.createNotificationChannel(new NotificationChannel(CHANNEL,
                application.getString(R.string.tournament_alerts), NotificationManager.IMPORTANCE_DEFAULT));
        Intent results = TournamentResultsActivity.open(application, id, name)
                .setData(Uri.fromParts("tournament-results", Long.toString(id), null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent tap = PendingIntent.getActivity(application, 0, results,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification alert = new Notification.Builder(application, CHANNEL)
                .setSmallIcon(R.drawable.ic_trophy)
                .setContentTitle(name)
                .setContentText(application.getString(R.string.tournament_alert_open_results))
                .setContentIntent(tap)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .build();
        notifications.notify(Long.toString(id), 0, alert);
    }
}
