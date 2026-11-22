package az.tabletennis;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

public final class TournamentMessagingService extends FirebaseMessagingService {
    @Override public void onMessageReceived(RemoteMessage message) {
        ((RankingsApplication) getApplication()).tournamentAlerts().receive(message.getData());
    }
}
