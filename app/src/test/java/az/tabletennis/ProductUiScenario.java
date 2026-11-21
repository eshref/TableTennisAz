package az.tabletennis;

import android.app.Activity;
import android.content.Intent;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.After;
import org.junit.Before;
import org.robolectric.Robolectric;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;

public abstract class ProductUiScenario extends RankingsUiScenario {
    protected final Map<String, String> website = new ConcurrentHashMap<>();
    protected final Map<String, java.util.function.Supplier<MockResponse>> responses = new ConcurrentHashMap<>();
    private final Deque<ActivityController<? extends Activity>> navigation = new ArrayDeque<>();

    @Before public void prepareProductWebsite() throws Exception {
        website.put("/players?level=amateur", html("amateur.html").getBody().readUtf8());
        website.put("/players?level=pro", html("pro.html").getBody().readUtf8());
        Dispatcher fixtures = new Dispatcher() {
            @Override public MockResponse dispatch(RecordedRequest request) {
                java.util.function.Supplier<MockResponse> response = responses.get(request.getPath());
                if (response != null) return response.get();
                String body = website.get(request.getPath());
                return body == null ? new MockResponse().setResponseCode(404)
                        : new MockResponse().setHeader("Content-Type", "text/html; charset=utf-8").setBody(body);
            }
        };
        server.setDispatcher(fixtures);
        catalogueServer.setDispatcher(fixtures);
        for (java.io.File file : org.robolectric.RuntimeEnvironment.getApplication().getFilesDir().listFiles()) {
            if (file.getName().startsWith("saved-")) file.delete();
        }
    }

    @After public void closeProductScreens() {
        while (!navigation.isEmpty()) navigation.pop().pause().stop().destroy();
    }

    @Override protected Activity activity() { return navigation.isEmpty() ? super.activity() : navigation.peek().get(); }

    protected void clickText(String text) {
        android.view.View view = findText(activity().getWindow().getDecorView(), text);
        if (view == null) throw new IllegalStateException("No visible control: " + text);
        android.view.View item = view;
        while (item.getParent() instanceof android.view.View parent && !(parent instanceof android.widget.ListView)) item = parent;
        if (item.getParent() instanceof android.widget.ListView list) {
            int position = list.getPositionForView(item);
            list.performItemClick(item, position, list.getItemIdAtPosition(position));
        } else view.performClick();
    }

    protected void followNavigation() throws Exception {
        Intent intent = Shadows.shadowOf(activity()).getNextStartedActivity();
        if (intent == null) throw new IllegalStateException("The selected item did not open a screen");
        @SuppressWarnings("unchecked")
        Class<? extends Activity> target = (Class<? extends Activity>) Class.forName(intent.getComponent().getClassName());
        navigation.push(Robolectric.buildActivity(target, intent).setup().visible());
    }

    protected void back() {
        activity().onBackPressed();
        if (!activity().isFinishing()) throw new IllegalStateException("Back did not close the screen");
        navigation.pop().pause().stop().destroy();
    }

    protected void recreateProductScreen() { navigation.peek().recreate(); }

    protected void prepareProfile(long id, String name) throws Exception {
        website.put("/players/" + id, "<div id='center'><div class='player-block'><div class='name-div'>" + name
                + "</div></div><div id='chart-user-id'>" + id + "</div></div>");
        website.put("/matches/results/player/" + id, html("history.html").getBody().readUtf8());
    }

    protected void restart() {
        closeProductScreens();
        screen.pause().stop().destroy();
        openScreen();
    }

    protected void openDirect(Class<? extends Activity> type, String identity, long id) {
        Intent intent = new Intent(org.robolectric.RuntimeEnvironment.getApplication(), type).putExtra(identity, id);
        navigation.push(Robolectric.buildActivity(type, intent).setup().visible());
    }

    protected java.util.List<String> requestedPaths() throws InterruptedException {
        java.util.List<String> paths = new java.util.ArrayList<>();
        RecordedRequest request;
        while ((request = server.takeRequest(50, java.util.concurrent.TimeUnit.MILLISECONDS)) != null) paths.add(request.getPath());
        return paths;
    }

    protected String requestTo(String path) throws InterruptedException {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(2);
        while (System.nanoTime() < deadline) {
            RecordedRequest request = server.takeRequest(100, java.util.concurrent.TimeUnit.MILLISECONDS);
            if (request != null && path.equals(request.getPath())) return request.getPath();
        }
        return null;
    }

    protected void prepareSavedScreens() throws Exception {
        // A known identity makes metadata refresh independent of name hydration.
        try (var input = getClass().getResourceAsStream("/fixtures/legacy-duo-110");
             var output = org.robolectric.RuntimeEnvironment.getApplication()
                     .openFileOutput("saved-duo-110", android.content.Context.MODE_PRIVATE)) {
            input.transferTo(output);
        }
        website.put("/api/getDuoInfo?id=110", html("pair-info.json").getBody().readUtf8());
        website.put("/matches/results/duo/110", "<div id='center'><div class='matches-box'>"
                + "<div class='no-info'>Matç yoxdur</div></div></div>");
        prepareProfile(12, "İlqar Həsənov");
        prepareResults();
        website.put("/matches/501", html("doubles-detail.html").getBody().readUtf8());
        website.put("/api/getPlayerPoints?id=12", "[{\"day\":\"31.12\",\"point\":435.55002}]");
    }

    protected void prepareResults() throws Exception {
        website.put("/matches/results", html("events.html").getBody().readUtf8());
        website.put("/matches/results/event/901", resultPage(601, "14 Nov. 2026", "Kubok A", "Kubok B", "3-0", "Payız Kuboku")
                + "<ul class='pagination'><li><a href='/matches/results/event/901?page=2'>&gt;</a></li></ul>");
        website.put("/matches/results/event/901?page=2", resultPage(602, "16 Nov. 2026", "Səhifə 2 A", "Səhifə 2 B", "3-2", "Payız Kuboku"));
        String overlap = html("duels.html").getBody().readUtf8();
        // Two legitimate date groups in one league page, including a match also published as a duel.
        website.put("/matches/results/event/902", "<div id='center'><div class='matches-box'>"
                + resultGroup(603, "13 Nov. 2026", "Liqa A", "Liqa B", "3-1", "Şəhər Liqası")
                + resultGroup(502, "16 Nov. 2026", "İlqar Həsənov", "Şəms Quliyeva", "3-1", "Şəhər Liqası") + "</div></div>");
        website.put("/matches/results/event/903", resultPage(605, "12 Nov. 2026", "Uzaq A", "Uzaq B", "2-0", "Uzaq Kubok"));
        website.put("/matches/duellos", overlap + "<ul class='pagination'><li><a href='/matches/duellos?page=2'>&gt;</a></li></ul>");
        website.put("/matches/duellos?page=2", overlap.replace("16 Nov. 2026", "17 Nov. 2026").replace("/matches/502", "/matches/604")
                .replace("3-1", "4-1").replace("<div>İlqar Həsənov</div></div>", "<div>Yeni A</div></div>")
                .replace("<div>Şəms Quliyeva</div></div>", "<div>Yeni B</div></div>"));
    }

    protected void publishStage(String path, long matchId, String stage) {
        website.compute(path, (key, body) -> {
            var page = org.jsoup.Jsoup.parse(body);
            var match = page.selectFirst("a[href='/matches/" + matchId + "'] > .match-box");
            match.appendElement("div").addClass("match-box-stage").appendElement("div").text(stage);
            return page.outerHtml();
        });
    }

    private String resultPage(long id, String date, String left, String right, String score, String event) {
        return "<div id='center'><div class='matches-box'>" + resultGroup(id, date, left, right, score, event) + "</div></div>";
    }

    private String resultGroup(long id, String date, String left, String right, String score, String event) {
        return "<div><div class='divs-header'>" + date + "</div><div><a href='/matches/" + id + "'><div class='match-box'>"
                + "<div class='col-3'><div>" + left + "</div></div><div class='col-2'><div class='center'>" + score
                + "</div></div><div class='col-3'><div>" + right + "</div></div><div class='col-4'>" + event + "</div></div></a></div></div>";
    }

    protected android.view.View findText(android.view.View view, String text) {
        if (view.getVisibility() != android.view.View.VISIBLE) return null;
        if (view.getContentDescription() != null && text.contentEquals(view.getContentDescription())) return view;
        if (view instanceof android.widget.TextView label && label.getText().toString().equals(text)) return view;
        if (view instanceof android.view.ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) {
                android.view.View found = findText(group.getChildAt(i), text);
                if (found != null) return found;
            }
        }
        return null;
    }
}
