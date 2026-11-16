package az.tabletennis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class RankingSnapshot {
    final List<Player> players;
    final long updatedAt;

    RankingSnapshot(List<Player> players, long updatedAt) {
        this.players = Collections.unmodifiableList(new ArrayList<>(players));
        this.updatedAt = updatedAt;
    }

    static final class Player {
        final long id;
        final String name;
        final int points;
        final String photoUrl;

        Player(long id, String name, int points) {
            this(id, name, points, null);
        }

        Player(long id, String name, int points, String photoUrl) {
            this.id = id;
            this.name = name;
            this.points = points;
            this.photoUrl = photoUrl;
        }
    }
}
