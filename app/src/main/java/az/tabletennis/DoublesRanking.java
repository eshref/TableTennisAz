package az.tabletennis;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

final class DoublesRanking implements Serializable {
    private static final long serialVersionUID = 1L;
    final List<Row> rows;

    DoublesRanking(List<Row> rows) { this.rows = new ArrayList<>(rows); }

    static final class Row implements Serializable {
        private static final long serialVersionUID = 1L;
        final DoublesPair pair;
        final int position;
        final int points;

        Row(DoublesPair pair, int position, int points) {
            this.pair = pair;
            this.position = position;
            this.points = points;
        }
    }
}
