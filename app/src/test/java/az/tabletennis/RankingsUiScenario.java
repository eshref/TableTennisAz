package az.tabletennis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.robolectric.Shadows.shadowOf;

import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import okhttp3.HttpUrl;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.After;
import org.junit.Before;
import org.robolectric.Robolectric;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;

public abstract class RankingsUiScenario {
    protected MockWebServer server;
    protected ActivityController<RankingsActivity> screen;

    public static class FixtureApplication extends RankingsApplication {
        static HttpUrl url;
        static volatile java.time.Clock time;
        static volatile boolean online;
        @Override protected java.time.Clock clock() { return time; }
        @Override protected HttpUrl websiteUrl() { return url; }
        @Override protected boolean isOnline() { return online; }
    }

    @Before public void prepareWebsite() throws Exception {
        server = new MockWebServer();
        server.start();
        FixtureApplication.url = server.url("/");
        FixtureApplication.online = true;
        FixtureApplication.time = java.time.Clock.fixed(java.time.Instant.parse("2026-11-15T12:00:00Z"), java.time.ZoneOffset.UTC);
        RuntimeEnvironment.getApplication().deleteFile("rankings.json");
        RuntimeEnvironment.getApplication().deleteFile("rankings.json.bak");
    }

    @After public void closeScreenAndWebsite() throws Exception {
        if (screen != null) screen.pause().stop().destroy();
        server.shutdown();
        RankingsApplication application = (RankingsApplication) RuntimeEnvironment.getApplication();
        application.rankingsWorker.shutdown();
        application.doublesWorker.shutdown();
        application.rankingsWorker.awaitTermination(5, TimeUnit.SECONDS);
        application.doublesWorker.awaitTermination(5, TimeUnit.SECONDS);
    }

    protected void openScreen() {
        screen = Robolectric.buildActivity(RankingsActivity.class).setup().visible();
    }

    protected MockResponse html(String file) throws IOException {
        try (var stream = getClass().getResourceAsStream("/fixtures/" + file)) {
            return new MockResponse().setHeader("Content-Type", "text/html; charset=utf-8")
                    .setBody(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    protected void awaitText(String expected) throws InterruptedException {
        int attempts = 0;
        while (!visibleText().contains(expected) && attempts++ < 500) {
            shadowOf(Looper.getMainLooper()).idle();
            Thread.sleep(20);
        }
        assertThat(visibleText()).contains(expected);
    }

    protected List<String> visibleText() {
        List<String> text = new ArrayList<>();
        collectText(activity().getWindow().getDecorView(), text);
        return text;
    }

    protected android.app.Activity activity() { return screen.get(); }

    private void collectText(View view, List<String> text) {
        if (view.getVisibility() != View.VISIBLE) return;
        // Structured ranking rows expose their complete values as one TalkBack announcement.
        if (view instanceof ViewGroup && view.getParent() instanceof android.widget.ListView
                && view.getContentDescription() != null) {
            text.add(view.getContentDescription().toString());
            return;
        }
        if (view instanceof TextView label) text.add(label.getText().toString());
        if (view instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) collectText(group.getChildAt(i), text);
        }
    }
}
