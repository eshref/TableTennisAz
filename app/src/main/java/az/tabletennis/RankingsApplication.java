package az.tabletennis;

import android.app.Application;
import java.time.Clock;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import okhttp3.HttpUrl;

public class RankingsApplication extends Application {
    // Serialize storage writes, including work spanning Activity recreation.
    final ExecutorService rankingsWorker = Executors.newSingleThreadExecutor();

    protected HttpUrl websiteUrl() {
        return HttpUrl.get("https://tabletennis.az/");
    }

    protected Clock clock() { return Clock.systemUTC(); }
}
