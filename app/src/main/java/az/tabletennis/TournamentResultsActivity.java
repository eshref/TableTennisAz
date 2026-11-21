package az.tabletennis;

import java.util.concurrent.CompletableFuture;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import java.util.Collections;

public class TournamentResultsActivity extends MatchFeedActivity {
    private static final String EVENT_ID = "eventId";
    private static final String EVENT_NAME = "eventName";
    private String name;
    private ResultsSelection selection;
    private EventCatalog catalog;

    static Intent open(Context context, TournamentCatalogue.Tournament tournament) {
        return new Intent(context, TournamentResultsActivity.class)
                .putExtra(EVENT_ID, tournament.id).putExtra(EVENT_NAME, tournament.name);
    }

    @Override public void onCreate(Bundle state) {
        long id = getIntent().getLongExtra(EVENT_ID, 0);
        name = getIntent().getStringExtra(EVENT_NAME);
        super.onCreate(state);
        if (id <= 0 || name == null) {
            finish();
            return;
        }
        selection = new ResultsSelection(ResultsSelection.Type.EVENTS, id);
        catalog = new EventCatalog(Collections.singletonList(new EventCatalog.Event(id, name)));
        select(selection.key(), ResultsFeed.class);
    }

    @Override protected int detailTitle() { return R.string.tournament_match_results; }

    @Override protected String detailHeading() { return name == null ? super.detailHeading() : name; }

    @Override protected CompletableFuture<SavedData<ResultsFeed>> request(ResultsFeed previous, boolean more) {
        return fetch(() -> new WebsiteSource(application.websiteUrl()).feed(previous, more, catalog, selection), more);
    }

    @Override protected void render(ResultsFeed feed) {
        if (feed.matches.isEmpty()) content.addView(label(getString(R.string.no_results), 18));
        super.render(feed);
    }
}
