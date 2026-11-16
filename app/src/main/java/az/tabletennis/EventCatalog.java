package az.tabletennis;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

final class EventCatalog implements Serializable {
    private static final long serialVersionUID = 1L;
    final List<Event> events;

    EventCatalog(List<Event> events) { this.events = new ArrayList<>(events); }

    static final class Event implements Serializable {
        private static final long serialVersionUID = 1L;
        final long id;
        final String name;
        Event(long id, String name) { this.id = id; this.name = name; }
        @Override public String toString() { return name; }
    }
}
