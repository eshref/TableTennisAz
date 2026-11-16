package az.tabletennis;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import java.util.ArrayList;
import java.util.List;

public class ResultsActivity extends SavedScreen<ResultsFeed> {
    private ResultsSelection selection;
    private SavedDataStore<EventCatalog> catalogStore;
    private EventCatalog catalog;
    private RadioGroup types;
    private Spinner events;
    private boolean binding;
    private final List<EventCatalog.Event> choices = new ArrayList<>();

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        android.content.SharedPreferences preferences = getSharedPreferences("results-selection", MODE_PRIVATE);
        ResultsSelection.Type type;
        try { type = ResultsSelection.Type.valueOf(preferences.getString("type", "ALL")); }
        catch (IllegalArgumentException invalid) { type = ResultsSelection.Type.ALL; }
        selection = new ResultsSelection(type, preferences.getLong("event", 0));
        catalogStore = new SavedDataStore<>(this, "events", EventCatalog.class);
        SavedData<EventCatalog> savedCatalog = catalogStore.read();
        catalog = savedCatalog == null ? new EventCatalog(new ArrayList<>()) : savedCatalog.value;
        createFilters();
        select(selection.key(), ResultsFeed.class);
    }

    @Override protected Fetch<ResultsFeed> request(ResultsFeed previous, boolean more) {
        ResultsSelection requested = selection;
        SavedData<EventCatalog> cachedCatalog = catalogStore.read();
        return () -> {
            WebsiteSource source = new WebsiteSource(application.websiteUrl());
            EventCatalog available = cachedCatalog == null ? new EventCatalog(new ArrayList<>()) : cachedCatalog.value;
            if (more && previous != null) available = previous.catalog;
            else if (requested.type.events && (previous != null || cachedCatalog == null
                    || SavedData.needsRefresh(cachedCatalog.updatedAt, application.clock().millis()))) {
                available = source.events();
                catalogStore.write(new SavedData<>(available, application.clock().millis()));
            }
            return source.feed(previous, more, available, requested);
        };
    }

    private void createFilters() {
        types = new RadioGroup(this);
        for (ResultsSelection.Type type : ResultsSelection.Type.values()) {
            RadioButton choice = new RadioButton(this);
            choice.setId(View.generateViewId());
            choice.setTag(type);
            choice.setText(type.label);
            types.addView(choice);
        }
        root.addView(types, 1);
        events = new Spinner(this);
        events.setId(R.id.event_filter);
        events.setContentDescription(getString(R.string.choose_event));
        root.addView(events, 2);
        bindFilters();
        types.setOnCheckedChangeListener((group, checkedId) -> {
            if (!binding) choose(new ResultsSelection((ResultsSelection.Type) group.findViewById(checkedId).getTag(), 0));
        });
        events.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onNothingSelected(AdapterView<?> parent) { }
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (binding || position != parent.getSelectedItemPosition() || position >= choices.size()) return;
                long selectedId = choices.get(position).id;
                if (selectedId != selection.eventId) choose(new ResultsSelection(ResultsSelection.Type.EVENTS, selectedId));
            }
        });
    }

    private void choose(ResultsSelection next) {
        if (selection.sameAs(next)) return;
        selection = next;
        getSharedPreferences("results-selection", MODE_PRIVATE).edit().putString("type", next.type.name()).putLong("event", next.eventId).apply();
        bindFilters();
        openSelection(next.key(), ResultsFeed.class);
    }

    private void bindFilters() {
        binding = true;
        for (int i = 0; i < types.getChildCount(); i++) {
            RadioButton choice = (RadioButton) types.getChildAt(i);
            if (choice.getTag() == selection.type) types.check(choice.getId());
        }
        choices.clear();
        choices.add(new EventCatalog.Event(0, getString(R.string.all_events)));
        choices.addAll(catalog.events);
        ArrayAdapter<EventCatalog.Event> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, choices);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        events.setAdapter(adapter);
        int selected = 0;
        for (int i = 0; i < choices.size(); i++) if (choices.get(i).id == selection.eventId) selected = i;
        events.setSelection(selected, false);
        events.setVisibility(selection.type.events ? View.VISIBLE : View.GONE);
        binding = false;
    }

    @Override protected void render(ResultsFeed feed) {
        catalog = feed.catalog;
        bindFilters();
        content.addView(label(getString(R.string.results_title), 26));
        content.addView(label(getString(R.string.partial_coverage), 14));
        if (feed.matches.isEmpty()) content.addView(label(getString(R.string.no_results), 16));
        android.widget.LinearLayout rows = new android.widget.LinearLayout(this);
        rows.setId(R.id.result_rows);
        rows.setOrientation(android.widget.LinearLayout.VERTICAL);
        for (MatchSummary match : feed.matches) {
            rows.addView(button(match.display(), () -> startActivity(new Intent(this, MatchActivity.class).putExtra("matchId", match.id))));
        }
        content.addView(rows);
        if (feed.hasMore()) {
            android.widget.Button more = button(getString(R.string.load_more), () -> refresh(true));
            more.setEnabled(application.isOnline());
            content.addView(more);
        }
    }

    @Override protected long retrievalTime(long previousUpdate, boolean more) {
        // Loading another page does not make the older displayed pages newly refreshed.
        return more && previousUpdate > 0 ? previousUpdate : application.clock().millis();
    }
}
