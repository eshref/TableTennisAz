package az.tabletennis;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.AtomicFile;
import android.util.LruCache;
import android.widget.ImageView;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import okhttp3.HttpUrl;

/** Optional portraits have their own workers and durable storage, independent of saved rankings. */
final class PlayerPhotos implements AutoCloseable {
    private final File directory;
    private final WebsiteClient client;
    private final ExecutorService worker = Executors.newFixedThreadPool(3);
    private final Handler main = new Handler(Looper.getMainLooper());
    private final LruCache<Long, Bitmap> memory = new LruCache<Long, Bitmap>(8 * 1024 * 1024) {
        @Override protected int sizeOf(Long id, Bitmap image) { return image.getAllocationByteCount(); }
    };
    // Main-thread ownership; in-flight work and successful images survive Activity recreation.
    private final Map<String, Download> requests = new HashMap<>();
    private final Set<String> attempted = new HashSet<>();

    PlayerPhotos(Context context, HttpUrl website) {
        directory = new File(context.getFilesDir(), "player-portraits");
        client = new WebsiteClient(website);
    }

    void bind(ImageView view, long playerId, String suppliedUrl, boolean online) {
        Binding binding = new Binding(view);
        view.setTag(binding);
        Bitmap available = memory.get(playerId);
        if (available == null) view.setImageResource(R.drawable.ic_person);
        else view.setImageBitmap(available);
        String url = client.photoUrl(suppliedUrl);
        String key = playerId + ":" + (online ? url : "offline");
        Download download = requests.get(key);
        if (download == null) {
            boolean fetch = online && url != null && attempted.add(key);
            if (!fetch && available != null) return;
            download = new Download(playerId, fetch ? url : null);
            requests.put(key, download);
            Download started = download;
            download.finished.whenComplete((image, failure) -> main.post(() -> requests.remove(key, started)));
        }
        download.saved.thenAccept(image -> main.post(() -> binding.show(image, false)));
        download.finished.thenAccept(image -> main.post(() -> binding.show(image, true)));
    }

    void refreshed() {
        // A deliberate ranking refresh permits retrying optional failures, without duplicating pending requests.
        attempted.clear();
    }

    void trimMemory() {
        memory.evictAll();
        refreshed();
    }

    @Override public void close() { worker.shutdown(); }

    private final class Download {
        final CompletableFuture<Bitmap> saved;
        final CompletableFuture<Bitmap> finished;

        Download(long id, String url) {
            saved = CompletableFuture.supplyAsync(() -> {
                Bitmap image = memory.get(id);
                if (image == null) {
                    try { image = decode(file(id).readFully()); }
                    catch (IOException ignored) { /* The bundled silhouette remains usable. */ }
                }
                if (image != null) memory.put(id, image);
                return image;
            }, worker);
            finished = saved.thenApplyAsync(previous -> {
                if (url == null) return previous;
                try {
                    Bitmap image = decode(client.image(url));
                    if (image == null) return previous;
                    save(id, image);
                    memory.put(id, image);
                    return image;
                } catch (IOException | RuntimeException optionalFailure) {
                    return previous;
                }
            }, worker);
        }
    }

    private AtomicFile file(long id) { return new AtomicFile(new File(directory, id + ".png")); }

    private void save(long id, Bitmap image) throws IOException {
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Cannot create portrait storage");
        AtomicFile file = file(id);
        FileOutputStream output = null;
        try {
            output = file.startWrite();
            if (!image.compress(Bitmap.CompressFormat.PNG, 100, output)) throw new IOException("Cannot save portrait");
            file.finishWrite(output);
        } catch (IOException failure) {
            if (output != null) file.failWrite(output);
            throw failure;
        }
    }

    private Bitmap decode(byte[] bytes) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
        if (options.outWidth <= 0 || options.outHeight <= 0
                || (long) options.outWidth * options.outHeight > 64_000_000) return null;
        options.inSampleSize = 1;
        while (Math.max(options.outWidth, options.outHeight) / options.inSampleSize > 256) options.inSampleSize *= 2;
        options.inJustDecodeBounds = false;
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
    }

    private static final class Binding {
        private final WeakReference<ImageView> target;
        private boolean finished;

        Binding(ImageView target) { this.target = new WeakReference<>(target); }

        void show(Bitmap image, boolean complete) {
            ImageView view = target.get();
            if (view == null || view.getTag() != this || finished) return;
            if (image != null) view.setImageBitmap(image);
            finished = complete;
        }
    }
}
