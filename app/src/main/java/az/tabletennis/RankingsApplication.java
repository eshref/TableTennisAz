package az.tabletennis;

import android.app.Application;
import java.time.Clock;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import okhttp3.HttpUrl;

public class RankingsApplication extends Application {
    // Serialize storage writes, including work spanning Activity recreation.
    final ExecutorService rankingsWorker = Executors.newSingleThreadExecutor();
    // Full-name hydration can take hundreds of requests; keep other screens responsive.
    final ExecutorService doublesWorker = Executors.newSingleThreadExecutor();
    // Main-thread refresh ownership survives Activity recreation; workers report progress here.
    CompletableFuture<SavedData<DoublesRanking>> doublesRefresh;
    volatile DoublesRepository.Progress doublesProgress;

    protected HttpUrl websiteUrl() {
        return HttpUrl.get("https://tabletennis.az/");
    }

    protected Clock clock() { return Clock.systemUTC(); }

    protected boolean isOnline() {
        android.net.ConnectivityManager connectivity = getSystemService(android.net.ConnectivityManager.class);
        android.net.NetworkCapabilities capabilities = connectivity.getNetworkCapabilities(connectivity.getActiveNetwork());
        return capabilities != null && capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }
}
