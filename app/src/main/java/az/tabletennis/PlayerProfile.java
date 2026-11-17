package az.tabletennis;

import java.io.Serializable;
import java.util.List;

final class PlayerProfile implements Serializable {
    private static final long serialVersionUID = 2L;
    final long id;
    final String name;
    final Integer points;
    final List<MatchSummary> history;
    final List<String> historyPages;
    final String nextHistoryPage;
    final String photoUrl;
    final Integer amateurPosition;

    PlayerProfile(long id, String name, Integer points, List<MatchSummary> history, List<String> historyPages, String nextHistoryPage) {
        this(id, name, points, history, historyPages, nextHistoryPage, null, null);
    }

    PlayerProfile(long id, String name, Integer points, List<MatchSummary> history, List<String> historyPages,
                  String nextHistoryPage, String photoUrl, Integer amateurPosition) {
        this.id = id;
        this.name = name;
        this.points = points;
        this.history = new java.util.ArrayList<>(history);
        this.historyPages = new java.util.ArrayList<>(historyPages);
        this.nextHistoryPage = nextHistoryPage;
        this.photoUrl = photoUrl;
        this.amateurPosition = amateurPosition;
    }
}
