package az.tabletennis;

import android.content.Context;
import android.util.AtomicFile;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

/** Private durable snapshots. Only complete, successfully parsed data replaces a file. */
final class SavedDataStore<T extends Serializable> {
    // On API 26, openRead can restore a backup and unlink a concurrent writer.
    // Lock whole transactions across store instances, including recreated screens.
    private static final Object IO_LOCK = new Object();
    private final AtomicFile file;
    private final Class<T> type;

    SavedDataStore(Context context, String key, Class<T> type) {
        if (!key.matches("[a-z0-9-]+")) throw new IllegalArgumentException("Invalid snapshot key");
        file = new AtomicFile(new File(context.getFilesDir(), "saved-" + key));
        this.type = type;
    }

    SavedData<T> read() {
        synchronized (IO_LOCK) {
            try (ObjectInputStream input = new ObjectInputStream(file.openRead())) {
                Object value = input.readObject();
                if (!(value instanceof SavedData<?> data) || data.updatedAt <= 0 || data.value == null) return null;
                return new SavedData<>(type.cast(data.value), data.updatedAt);
            } catch (IOException | ClassNotFoundException | ClassCastException failure) {
                return null;
            }
        }
    }

    void write(SavedData<T> data) throws IOException {
        synchronized (IO_LOCK) {
            FileOutputStream stream = null;
            try {
                stream = file.startWrite();
                ObjectOutputStream output = new ObjectOutputStream(stream);
                output.writeObject(data);
                output.flush();
                file.finishWrite(stream);
            } catch (IOException failure) {
                if (stream != null) file.failWrite(stream);
                throw failure;
            }
        }
    }
}
