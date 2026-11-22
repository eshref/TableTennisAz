package az.tabletennis;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import java.time.Clock;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import okhttp3.HttpUrl;

public class RankingsApplication extends Application {
    private PlayerPhotos photos;
    private TournamentCatalogueRepository tournamentCatalogues;
    private final ExecutorService tournamentWorker = Executors.newSingleThreadExecutor();

    @Override public void onCreate() {
        super.onCreate();
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            private final Set<Activity> startedActivities = new HashSet<>();

            @Override public void onActivityStarted(Activity activity) {
                if (startedActivities.add(activity) && startedActivities.size() == 1)
                    tournamentCatalogues().refreshIfStale();
            }

            @Override public void onActivityStopped(Activity activity) { startedActivities.remove(activity); }
            @Override public void onActivityCreated(Activity activity, Bundle state) { }
            @Override public void onActivityResumed(Activity activity) { }
            @Override public void onActivityPaused(Activity activity) { }
            @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) { }
            @Override public void onActivityDestroyed(Activity activity) { startedActivities.remove(activity); }
        });
    }

    TournamentCatalogueRepository tournamentCatalogues() {
        if (tournamentCatalogues == null) tournamentCatalogues = new TournamentCatalogueRepository(this, tournamentWorker);
        return tournamentCatalogues;
    }

    PlayerPhotos photos() {
        if (photos == null) photos = new PlayerPhotos(this, websiteUrl());
        return photos;
    }

    @Override public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (photos != null) photos.trimMemory();
    }

    @Override public void onTerminate() {
        if (photos != null) photos.close();
        tournamentWorker.shutdown();
        super.onTerminate();
    }
    // Serialize storage writes, including work spanning Activity recreation.
    final ExecutorService rankingsWorker = Executors.newSingleThreadExecutor();
    // Full-name hydration can take hundreds of requests; keep other screens responsive.
    final ExecutorService doublesWorker = Executors.newSingleThreadExecutor();
    // Main-thread refresh ownership survives Activity recreation; workers report progress here.
    CompletableFuture<SavedData<DoublesRanking>> doublesRefresh;
    android.os.Bundle resultsScrollPositions;
    volatile DoublesRepository.Progress doublesProgress;

    protected HttpUrl websiteUrl() {
        return HttpUrl.get("https://tabletennis.az/");
    }

    WebsiteClient websiteClient() { return new WebsiteClient(websiteUrl()); }

    protected HttpUrl tournamentCatalogueUrl() {
        return HttpUrl.get("https://eshref.github.io/TableTennisAz/tournaments-v1.json");
    }

    protected Clock clock() { return Clock.systemUTC(); }

    protected boolean isOnline() {
        android.net.ConnectivityManager connectivity = getSystemService(android.net.ConnectivityManager.class);
        android.net.NetworkCapabilities capabilities = connectivity.getNetworkCapabilities(connectivity.getActiveNetwork());
        return capabilities != null && capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }
}
