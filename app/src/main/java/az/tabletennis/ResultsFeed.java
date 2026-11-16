package az.tabletennis;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

final class ResultsFeed implements Serializable {
    private static final long serialVersionUID = 1L;
    final List<MatchSummary> matches;
    final EventCatalog catalog;
    final List<Page> loaded;
    final List<Page> pendingEvents;
    final Page nextDuels;

    ResultsFeed(List<MatchSummary> matches, EventCatalog catalog, List<Page> loaded, List<Page> pendingEvents, Page nextDuels) {
        this.matches = new ArrayList<>(matches);
        this.catalog = catalog;
        this.loaded = new ArrayList<>(loaded);
        this.pendingEvents = new ArrayList<>(pendingEvents);
        this.nextDuels = nextDuels;
    }

    boolean hasMore() { return nextDuels != null || !pendingEvents.isEmpty(); }

    static final class Page implements Serializable {
        private static final long serialVersionUID = 1L;
        final String path;
        final long eventId;
        final String eventName;
        final boolean duel;

        Page(String path, long eventId, String eventName, boolean duel) {
            this.path = path;
            this.eventId = eventId;
            this.eventName = eventName;
            this.duel = duel;
        }
    }
}
