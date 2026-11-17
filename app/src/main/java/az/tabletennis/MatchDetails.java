package az.tabletennis;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

final class MatchDetails implements Serializable {
    private static final long serialVersionUID = 1L;
    final long id;
    final List<PlayerLink> left;
    final List<PlayerLink> right;
    final String date;
    final String time;
    final String score;
    final String eventName;

    MatchDetails(long id, List<PlayerLink> left, List<PlayerLink> right, String date, String time, String score, String eventName) {
        this.id = id;
        this.left = new ArrayList<>(left);
        this.right = new ArrayList<>(right);
        this.date = date;
        this.time = time;
        this.score = score;
        this.eventName = eventName;
    }

    static final class PlayerLink implements Serializable {
        private static final long serialVersionUID = 1L;
        final long id;
        final String name;
        final String photoUrl;

        PlayerLink(long id, String name) { this(id, name, null); }

        PlayerLink(long id, String name, String photoUrl) {
            this.id = id;
            this.name = name;
            this.photoUrl = photoUrl;
        }
    }
}
