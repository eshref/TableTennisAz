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

        Player(long id, String name, int points) {
            this.id = id;
            this.name = name;
            this.points = points;
        }
    }
}
