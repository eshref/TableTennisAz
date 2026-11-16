package az.tabletennis;

import java.io.Serializable;

/** Published counts for one partnership. Null means the source supplied no count. */
final class PairMedals implements Serializable {
    private static final long serialVersionUID = 1L;
    final Integer gold;
    final Integer silver;
    final Integer bronze;

    PairMedals(Integer gold, Integer silver, Integer bronze) {
        this.gold = gold;
        this.silver = silver;
        this.bronze = bronze;
    }
}
