package az.tabletennis;

import java.io.Serializable;

final class DoublesPair implements Serializable {
    private static final long serialVersionUID = 1L;
    final long id;
    final MatchDetails.PlayerLink first;
    final MatchDetails.PlayerLink second;

    DoublesPair(long id, MatchDetails.PlayerLink first, MatchDetails.PlayerLink second) {
        this.id = id;
        this.first = first;
        this.second = second;
    }

    String names() { return first.name + " / " + second.name; }
}
