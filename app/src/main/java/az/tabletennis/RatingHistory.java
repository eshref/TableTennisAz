package az.tabletennis;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

final class RatingHistory implements Serializable {
    private static final long serialVersionUID = 1L;
    final List<Observation> observations;

    RatingHistory(List<Observation> observations) { this.observations = new ArrayList<>(observations); }

    static final class Observation implements Serializable {
        private static final long serialVersionUID = 1L;
        final String date;
        final String points;

        Observation(String date, String points) { this.date = date; this.points = points; }
    }
}
