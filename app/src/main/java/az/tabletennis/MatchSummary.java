package az.tabletennis;

import java.io.Serializable;
import java.util.List;

final class MatchSummary implements Serializable {
    private static final long serialVersionUID = 1L;
    final long id;
    final List<String> left;
    final List<String> right;
    final String date;
    final String score;
    final long eventId;
    final String eventName;
    final boolean duel;
    final boolean teamChild;

    MatchSummary(long id, List<String> left, List<String> right, String date, String score,
                 long eventId, String eventName, boolean duel) {
        this(id, left, right, date, score, eventId, eventName, duel, false);
    }

    MatchSummary(long id, List<String> left, List<String> right, String date, String score,
                 long eventId, String eventName, boolean duel, boolean teamChild) {
        this.id = id;
        this.left = new java.util.ArrayList<>(left);
        this.right = new java.util.ArrayList<>(right);
        this.date = date;
        this.score = score;
        this.eventId = eventId;
        this.eventName = eventName;
        this.duel = duel;
        this.teamChild = teamChild;
    }

    String display() {
        return date + " · " + String.join(" / ", left) + " — " + String.join(" / ", right)
                + " · " + score + " · " + eventName;
    }
}
