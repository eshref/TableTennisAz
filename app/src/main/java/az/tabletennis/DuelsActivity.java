package az.tabletennis;

import android.os.Bundle;
import java.util.Collections;

public class DuelsActivity extends MatchFeedActivity {
    private static final ResultsSelection DUELS = new ResultsSelection(ResultsSelection.Type.DUELS, 0);

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        select(DUELS.key(), ResultsFeed.class);
    }

    @Override protected MainScreenChrome.Destination mainDestination() { return MainScreenChrome.Destination.DUELS; }

    @Override protected Fetch<ResultsFeed> request(ResultsFeed previous, boolean more) {
        return () -> new WebsiteSource(application.websiteUrl())
                .feed(previous, more, new EventCatalog(Collections.emptyList()), DUELS);
    }

    @Override protected void render(ResultsFeed feed) {
        if (feed.matches.isEmpty()) content.addView(label(getString(R.string.no_duels), 18));
        super.render(feed);
    }
}
