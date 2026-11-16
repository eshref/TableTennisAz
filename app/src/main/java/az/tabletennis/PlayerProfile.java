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

    PlayerProfile(long id, String name, Integer points, List<MatchSummary> history, List<String> historyPages, String nextHistoryPage) {
        this.id = id;
        this.name = name;
        this.points = points;
        this.history = new java.util.ArrayList<>(history);
        this.historyPages = new java.util.ArrayList<>(historyPages);
        this.nextHistoryPage = nextHistoryPage;
    }
}
