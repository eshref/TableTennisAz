package az.tabletennis;

final class ResultsSelection {
    enum Type {
        ALL(R.string.all_results, true, true), EVENTS(R.string.tournament_results, true, false), DUELS(R.string.duel_results, false, true);
        final int label;
        final boolean events;
        final boolean duels;
        Type(int label, boolean events, boolean duels) { this.label = label; this.events = events; this.duels = duels; }
    }

    final Type type;
    final long eventId;

    ResultsSelection(Type type, long eventId) {
        this.type = type;
        this.eventId = type == Type.EVENTS ? eventId : 0;
    }

    String key() { return "results-" + type.name().toLowerCase(java.util.Locale.ROOT) + "-" + eventId; }

    boolean sameAs(ResultsSelection other) { return type == other.type && eventId == other.eventId; }
}
