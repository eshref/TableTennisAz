package az.tabletennis;

import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import okhttp3.HttpUrl;

public class SinglesTournamentsActivity extends SavedScreen<TournamentCatalogue> {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.uuuu", Locale.ROOT);

    private EditText search;
    private ListView list;
    private TextView empty;
    private final TournamentCards cards = new TournamentCards();

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        search = new EditText(this);
        search.setId(R.id.search);
        search.setSingleLine();
        search.setInputType(InputType.TYPE_CLASS_TEXT);
        search.setHint(R.string.tournament_search_hint);
        search.setTextColor(getColor(R.color.text));
        search.setHintTextColor(getColor(R.color.secondary));
        search.setBackground(Presentation.shape(this, R.color.surface));
        search.setPadding(dp(12), dp(8), dp(12), dp(8));
        search.setMinHeight(dp(48));
        LinearLayout.LayoutParams searchSize = new LinearLayout.LayoutParams(-1, -2);
        searchSize.bottomMargin = dp(8);
        root.addView(search, 0, searchSize);

        empty = label("", 16);
        empty.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        list = new ListView(this);
        list.setId(R.id.tournament_cards);
        list.setDivider(null);
        list.setAdapter(cards);
        list.setOnItemClickListener((parent, row, position, id) ->
                startActivity(TournamentResultsActivity.open(this, cards.getItem(position))));
        list.setEmptyView(empty);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence text, int start, int before, int count) {
                if (saved == null) return;
                updateCards(saved.value);
                list.setSelection(0);
            }
            @Override public void afterTextChanged(Editable text) { }
        });
        select(TournamentCatalogue.STORE_KEY, TournamentCatalogue.class);
    }

    @Override protected MainScreenChrome.Destination mainDestination() {
        return MainScreenChrome.Destination.SINGLES_TOURNAMENTS;
    }

    @Override protected boolean usesScrollView() { return false; }

    @Override protected Fetch<TournamentCatalogue> request(TournamentCatalogue previous, boolean more) {
        HttpUrl url = application.tournamentCatalogueUrl();
        return () -> TournamentCatalogue.parse(new WebsiteClient(url).get(url.encodedPath()));
    }

    @Override protected void render(TournamentCatalogue catalogue) {
        content.addView(empty);
        content.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        updateCards(catalogue);
    }

    private void updateCards(TournamentCatalogue catalogue) {
        String query = search.getText().toString();
        empty.setText(query.trim().isEmpty() ? R.string.no_singles_tournaments : R.string.no_matching_tournaments);
        List<TournamentCatalogue.Tournament> matches = catalogue.findSingles(query);
        if (!cards.tournaments.equals(matches)) {
            cards.tournaments = matches;
            cards.notifyDataSetChanged();
        }
    }

    private final class TournamentCards extends BaseAdapter {
        private List<TournamentCatalogue.Tournament> tournaments = Collections.emptyList();

        @Override public int getCount() { return tournaments.size(); }
        @Override public TournamentCatalogue.Tournament getItem(int position) { return tournaments.get(position); }
        @Override public long getItemId(int position) { return getItem(position).id; }
        @Override public boolean hasStableIds() { return true; }

        @Override public View getView(int position, View recycled, ViewGroup parent) {
            TournamentCard card = recycled instanceof TournamentCard existing ? existing : new TournamentCard();
            card.bind(getItem(position));
            return card;
        }
    }

    private final class TournamentCard extends LinearLayout {
        private final TextView name;
        private final TextView dates;
        private final TextView players;
        private final TextView prize;

        TournamentCard() {
            super(SinglesTournamentsActivity.this);
            setOrientation(VERTICAL);
            setPadding(0, 0, 0, dp(8));
            LinearLayout surface = new LinearLayout(getContext());
            surface.setOrientation(VERTICAL);
            surface.setPadding(dp(14), dp(12), dp(14), dp(12));
            GradientDrawable background = Presentation.shape(getContext(), R.color.surface);
            background.setStroke(dp(1), getColor(R.color.line));
            surface.setBackground(new RippleDrawable(ColorStateList.valueOf(getColor(R.color.soft)), background, null));
            addView(surface, new LayoutParams(-1, -2));

            name = label("", 17);
            name.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            name.setPadding(0, 0, 0, dp(4));
            surface.addView(name);
            dates = fact();
            players = fact();
            prize = fact();
            if (getResources().getConfiguration().screenWidthDp >= 600) {
                LinearLayout facts = new LinearLayout(getContext());
                facts.setOrientation(HORIZONTAL);
                facts.addView(dates, new LayoutParams(0, -2, 2));
                facts.addView(players, new LayoutParams(0, -2, 1));
                facts.addView(prize, new LayoutParams(0, -2, 1));
                surface.addView(facts, new LayoutParams(-1, -2));
            } else {
                surface.addView(dates);
                surface.addView(players);
                surface.addView(prize);
            }
        }

        void bind(TournamentCatalogue.Tournament tournament) {
            name.setText(tournament.name);
            String start = DATE.format(tournament.start);
            dates.setText(tournament.start.equals(tournament.end) ? start
                    : getString(R.string.tournament_date_range, start, DATE.format(tournament.end)));
            String unknown = getString(R.string.tournament_unknown_value);
            players.setText(getString(R.string.tournament_players,
                    tournament.players == null ? unknown : tournament.players.toString()));
            prize.setText(getString(R.string.tournament_prize, tournament.prize == null ? unknown : tournament.prize));
        }

        private TextView fact() {
            TextView view = label("", 14);
            view.setTextColor(getColor(R.color.secondary));
            view.setPadding(0, dp(2), 0, dp(2));
            return view;
        }
    }
}
