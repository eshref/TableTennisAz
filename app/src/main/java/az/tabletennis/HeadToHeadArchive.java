package az.tabletennis;

import static az.tabletennis.HeadToHeadSearch.normalizeName;
import static az.tabletennis.HeadToHeadSearch.profileIds;

import az.tabletennis.HeadToHeadIndex.Comparison;
import az.tabletennis.HeadToHeadSearch.Meeting;
import az.tabletennis.HeadToHeadSearch.Player;
import az.tabletennis.HeadToHeadSearch.Suggestion;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/** Completed comparisons and their selected profiles are saved together in one atomic snapshot. */
final class HeadToHeadArchive {
    private static final Object SAVE_LOCK = new Object();
    private final RankingsApplication application;
    private final SavedDataStore<Archive> store;

    HeadToHeadArchive(RankingsApplication application) {
        this.application = application;
        store = new SavedDataStore<>(application, "head-to-head", Archive.class);
    }

    void save(List<Player> first, List<Player> second, Comparison comparison) throws IOException {
        synchronized (SAVE_LOCK) {
            Archive archive = read();
            Matchup matchup = matchup(first, second);
            archive.comparisons.remove(matchup);
            archive.comparisons.put(matchup, new Completed(new ArrayList<>(first), new ArrayList<>(second), comparison));
            store.write(new SavedData<>(archive, application.clock().millis()));
        }
    }

    Comparison comparison(List<Player> first, List<Player> second) {
        Completed saved = read().comparisons.get(matchup(first, second));
        if (saved == null) return null;
        if (first.stream().anyMatch(player -> player.id() == saved.first.get(0).id())) return saved.comparison;

        List<Meeting> reversed = new ArrayList<>();
        for (Meeting meeting : saved.comparison.meetings()) {
            reversed.add(new Meeting(meeting.match(), switch (meeting.winner()) {
                case FIRST -> HeadToHeadSearch.Winner.SECOND;
                case SECOND -> HeadToHeadSearch.Winner.FIRST;
                case NONE -> HeadToHeadSearch.Winner.NONE;
            }));
        }
        return new Comparison(saved.comparison.refreshedAt(), reversed);
    }

    List<Suggestion> suggestions(String query) {
        String normalized = normalizeName(query);
        var players = new LinkedHashMap<Long, Player>();
        for (Completed saved : read().comparisons.values()) {
            for (Player player : saved.first) players.put(player.id(), player);
            for (Player player : saved.second) players.put(player.id(), player);
        }
        List<Suggestion> matches = new ArrayList<>();
        for (Player player : players.values()) {
            if (normalizeName(player.name()).contains(normalized)) matches.add(new Suggestion(player, null));
        }
        return matches;
    }

    private Archive read() {
        SavedData<Archive> saved = store.read();
        return saved == null ? new Archive() : saved.value;
    }

    private Matchup matchup(List<Player> first, List<Player> second) {
        List<Long> firstIds = profileIds(first);
        List<Long> secondIds = profileIds(second);
        return firstIds.get(0) < secondIds.get(0) ? new Matchup(firstIds, secondIds) : new Matchup(secondIds, firstIds);
    }

    private record Matchup(List<Long> first, List<Long> second) implements Serializable {
        private static final long serialVersionUID = 1L;
    }

    private record Completed(List<Player> first, List<Player> second, Comparison comparison) implements Serializable {
        private static final long serialVersionUID = 1L;
    }

    private static final class Archive implements Serializable {
        private static final long serialVersionUID = 1L;
        final LinkedHashMap<Matchup, Completed> comparisons = new LinkedHashMap<>();
    }
}
