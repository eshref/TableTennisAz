package az.tabletennis;

import java.io.Serializable;

final class SavedData<T extends Serializable> implements Serializable {
    private static final long serialVersionUID = 1L;
    final T value;
    final long updatedAt;

    SavedData(T value, long updatedAt) { this.value = value; this.updatedAt = updatedAt; }

    static boolean needsRefresh(long updatedAt, long now) { return now - updatedAt > 15 * 60 * 1000; }
}
