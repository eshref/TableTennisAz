package az.tabletennis;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

final class PairHistory implements Serializable {
    private static final long serialVersionUID = 1L;
    final DoublesPair pair;
    final List<MatchSummary> matches;
    final List<String> pages;
    final String nextPage;
    // Absent in older saved histories; a missing count must never become a known zero.
    final PairMedals medals;

    PairHistory(DoublesPair pair, List<MatchSummary> matches, List<String> pages, String nextPage) {
        this(pair, matches, pages, nextPage, null);
    }

    PairHistory(DoublesPair pair, List<MatchSummary> matches, List<String> pages, String nextPage, PairMedals medals) {
        this.pair = pair;
        this.medals = medals;
        this.matches = new ArrayList<>(matches);
        this.pages = new ArrayList<>(pages);
        this.nextPage = nextPage;
    }
}
