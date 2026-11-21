package az.tabletennis;

import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import java.util.ArrayList;
import java.util.List;

public class ResultsActivity extends MatchFeedActivity {
    private ResultsSelection selection;
    private SavedDataStore<EventCatalog> catalogStore;
    private EventCatalog catalog;
    private RadioGroup types;
    private Spinner events;
    private android.widget.TextView eventLabel;
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

    @Override protected MainScreenChrome.Destination mainDestination() { return MainScreenChrome.Destination.RESULTS; }

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
        types.setOrientation(RadioGroup.HORIZONTAL);
        for (ResultsSelection.Type type : ResultsSelection.Type.values()) {
            RadioButton choice = new RadioButton(this);
            choice.setId(View.generateViewId());
            choice.setTag(type);
            choice.setText(type.label);
            Presentation.segment(choice);
            styleFilter(choice);
            RadioGroup.LayoutParams spacing = new RadioGroup.LayoutParams(-2, -2);
            spacing.setMarginEnd(dp(6));
            spacing.bottomMargin = dp(6);
            types.addView(choice, spacing);
        }
        // Short chips can fit even at large text sizes; stack only when their measured content needs it.
        types.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        if (types.getMeasuredWidth() > dp(getResources().getConfiguration().screenWidthDp - 36)) {
            types.setOrientation(RadioGroup.VERTICAL);
            for (int i = 0; i < types.getChildCount(); i++) types.getChildAt(i).getLayoutParams().width = -1;
        }
        root.addView(types, 0);
        events = new Spinner(this);
        events.setId(R.id.event_filter);
        events.setContentDescription(getString(R.string.choose_event));
        events.setMinimumHeight(dp(48));
        eventLabel = label(getString(R.string.choose_event), 12);
        eventLabel.setTextColor(getColor(R.color.secondary));
        eventLabel.setLabelFor(R.id.event_filter);
        root.addView(eventLabel, 1);
        android.graphics.drawable.GradientDrawable eventSurface = Presentation.shape(this, R.color.surface);
        eventSurface.setStroke(dp(1), getColor(R.color.line));
        events.setBackground(new android.graphics.drawable.LayerDrawable(new android.graphics.drawable.Drawable[] {
                eventSurface, events.getBackground()}));
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

    private void styleFilter(RadioButton choice) {
        choice.setTextColor(new android.content.res.ColorStateList(
                new int[][] {new int[] {android.R.attr.state_checked}, new int[] {}},
                new int[] {getColor(R.color.primary), getColor(R.color.text)}));
        android.graphics.drawable.StateListDrawable background = new android.graphics.drawable.StateListDrawable();
        for (boolean checked : new boolean[] {true, false}) {
            android.graphics.drawable.GradientDrawable shape = Presentation.shape(this, checked ? R.color.soft : R.color.background);
            shape.setCornerRadius(dp(24));
            shape.setStroke(dp(1), getColor(checked ? R.color.soft : R.color.line));
            background.addState(checked ? new int[] {android.R.attr.state_checked} : new int[] {}, shape);
        }
        choice.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(getColor(R.color.line)), background, null));
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
        ArrayAdapter<EventCatalog.Event> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, choices) {
            @Override public View getView(int position, View recycled, android.view.ViewGroup parent) {
                return wrappingEventName(super.getView(position, recycled, parent));
            }

            @Override public View getDropDownView(int position, View recycled, android.view.ViewGroup parent) {
                return wrappingEventName(super.getDropDownView(position, recycled, parent));
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        events.setAdapter(adapter);
        int selected = 0;
        for (int i = 0; i < choices.size(); i++) if (choices.get(i).id == selection.eventId) selected = i;
        events.setSelection(selected, false);
        events.setVisibility(selection.type.events ? View.VISIBLE : View.GONE);
        eventLabel.setVisibility(events.getVisibility());
        binding = false;
    }

    private View wrappingEventName(View view) {
        android.widget.TextView label = (android.widget.TextView) view;
        label.setSingleLine(false);
        label.setMaxLines(Integer.MAX_VALUE);
        label.setEllipsize(null);
        label.setMinHeight(dp(48));
        return label;
    }

    @Override protected void render(ResultsFeed feed) {
        catalog = feed.catalog;
        bindFilters();
        android.widget.TextView coverage = label(getString(R.string.partial_coverage), 12);
        coverage.setTextColor(getColor(R.color.secondary));
        content.addView(coverage);
        if (feed.matches.isEmpty()) {
            content.addView(label(getString(R.string.no_results), 18));
            content.addView(label(getString(R.string.results_empty_hint), 14));
            if (selection.type != ResultsSelection.Type.ALL || selection.eventId != 0) {
                android.widget.Button clear = button(getString(R.string.clear_results_filters),
                        () -> choose(new ResultsSelection(ResultsSelection.Type.ALL, 0)));
                clear.setId(R.id.clear_results_filters);
                content.addView(clear);
            }
        }
        super.render(feed);
    }
}
