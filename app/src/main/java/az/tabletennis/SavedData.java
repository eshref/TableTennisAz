package az.tabletennis;

import java.io.Serializable;
import java.time.Duration;

final class SavedData<T extends Serializable> implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final long FRESHNESS_THRESHOLD_MILLIS = Duration.ofHours(48).toMillis();
    final T value;
    final long updatedAt;

    SavedData(T value, long updatedAt) { this.value = value; this.updatedAt = updatedAt; }

    static boolean needsRefresh(long updatedAt, long now) { return now - updatedAt > FRESHNESS_THRESHOLD_MILLIS; }
}
