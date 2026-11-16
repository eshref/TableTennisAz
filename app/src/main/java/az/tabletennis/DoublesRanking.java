package az.tabletennis;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class DoublesRanking implements Serializable {
    private static final long serialVersionUID = 1L;
    final List<Row> rows;
    // Null in snapshots saved before observation capture was introduced.
    private final List<Observation> observations;

    DoublesRanking(List<Row> rows) { this(rows, Collections.emptyList()); }

    private DoublesRanking(List<Row> rows, List<Observation> observations) {
        this.rows = Collections.unmodifiableList(new ArrayList<>(rows));
        this.observations = Collections.unmodifiableList(new ArrayList<>(observations));
    }

    List<Observation> observations() { return observations == null ? Collections.emptyList() : observations; }

    /** Build a candidate snapshot; publish it only after its atomic save succeeds. */
    DoublesRanking observedAfter(DoublesRanking previous, long observedAt) {
        List<Observation> history = new ArrayList<>(previous == null ? Collections.emptyList() : previous.observations());
        Map<Long, Integer> previousPositions = new HashMap<>();
        if (!history.isEmpty()) {
            for (Entry entry : history.get(history.size() - 1).entries) {
                previousPositions.put(entry.pairId, entry.position);
            }
        }
        List<Row> compared = new ArrayList<>();
        List<Entry> entries = new ArrayList<>();
        for (Row row : rows) {
            Integer baseline = previousPositions.get(row.pair.id);
            compared.add(new Row(row.pair, row.position, row.points, baseline == null ? 0 : baseline - row.position));
            entries.add(new Entry(row.pair.id, row.position, row.points));
        }
        history.add(new Observation(observedAt, entries));
        return new DoublesRanking(compared, history);
    }

    static final class Observation implements Serializable {
        private static final long serialVersionUID = 1L;
        final long observedAt;
        final List<Entry> entries;

        Observation(long observedAt, List<Entry> entries) {
            this.observedAt = observedAt;
            this.entries = Collections.unmodifiableList(new ArrayList<>(entries));
        }
    }

    static final class Entry implements Serializable {
        private static final long serialVersionUID = 1L;
        final long pairId;
        final int position;
        final int points;

        Entry(long pairId, int position, int points) {
            this.pairId = pairId;
            this.position = position;
            this.points = points;
        }
    }

    static final class Row implements Serializable {
        private static final long serialVersionUID = 1L;
        final DoublesPair pair;
        final int position;
        final int points;
        final int placesGained;

        Row(DoublesPair pair, int position, int points) {
            this(pair, position, points, 0);
        }

        private Row(DoublesPair pair, int position, int points, int placesGained) {
            this.pair = pair;
            this.position = position;
            this.points = points;
            this.placesGained = placesGained;
        }
    }
}
