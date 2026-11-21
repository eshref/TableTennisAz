package az.tabletennis;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import okhttp3.HttpUrl;

/** Owns the shared refresh and per-category acknowledgement on the main thread. */
final class TournamentCatalogueRepository {
    private final RankingsApplication application;
    private final Executor worker;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final SavedDataStore<TournamentCatalogue> store;
    private final SharedPreferences acknowledgements;
    private final Set<Consumer<SavedData<TournamentCatalogue>>> listeners = new HashSet<>();
    private CompletableFuture<SavedData<TournamentCatalogue>> inFlight;

    TournamentCatalogueRepository(RankingsApplication application, Executor worker) {
        this.application = application;
        this.worker = worker;
        store = new SavedDataStore<>(application, TournamentCatalogue.STORE_KEY, TournamentCatalogue.class);
        acknowledgements = application.getSharedPreferences("tournament-acknowledgements", Context.MODE_PRIVATE);
    }

    void refreshIfStale() {
        if (!application.isOnline()) return;
        SavedData<TournamentCatalogue> cached = store.read();
        if (cached == null || SavedData.needsRefresh(cached.updatedAt, application.clock().millis())) refresh();
    }

    CompletableFuture<SavedData<TournamentCatalogue>> refresh() {
        if (inFlight != null) return inFlight;
        var pending = new CompletableFuture<SavedData<TournamentCatalogue>>();
        inFlight = pending;
        worker.execute(() -> {
            try {
                HttpUrl url = application.tournamentCatalogueUrl();
                SavedData<TournamentCatalogue> previous = store.read();
                TournamentCatalogue next = TournamentCatalogue.parse(new WebsiteClient(url).get(url.encodedPath()))
                        .withDeviceHistory(previous == null ? null : previous.value);
                var fresh = new SavedData<>(next, application.clock().millis());
                store.write(fresh);
                main.post(() -> {
                    inFlight = null;
                    pending.complete(fresh);
                    publish(fresh);
                });
            } catch (IOException failure) {
                main.post(() -> {
                    inFlight = null;
                    pending.completeExceptionally(failure);
                });
            }
        });
        return pending;
    }

    void observe(Consumer<SavedData<TournamentCatalogue>> listener) {
        listeners.add(listener);
        listener.accept(store.read());
    }

    void removeObserver(Consumer<SavedData<TournamentCatalogue>> listener) {
        listeners.remove(listener);
    }

    boolean hasNew(SavedData<TournamentCatalogue> snapshot, TournamentCatalogue.Format format) {
        return snapshot != null && !acknowledged(format).containsAll(snapshot.value.newIds(format));
    }

    void acknowledge(TournamentCatalogue catalogue, TournamentCatalogue.Format format) {
        Set<String> viewed = new HashSet<>(acknowledged(format));
        if (!viewed.addAll(catalogue.newIds(format))) return;
        acknowledgements.edit().putStringSet(format.name(), viewed).apply();
        publish(store.read());
    }

    private Set<String> acknowledged(TournamentCatalogue.Format format) {
        return acknowledgements.getStringSet(format.name(), Collections.emptySet());
    }

    private void publish(SavedData<TournamentCatalogue> snapshot) {
        for (var listener : listeners) listener.accept(snapshot);
    }
}
