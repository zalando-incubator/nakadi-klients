package de.zalando.nakadi.client.domain;


import com.google.common.base.MoreObjects;

import java.util.List;

public class SimpleStreamEvent {

    private Cursor cursor;
    private List<Event> events;
    private List<TopologyItem> topology;

    public Cursor getCursor() {
        return cursor;
    }

    public void setCursor(Cursor cursor) {
        this.cursor = cursor;
    }

    public List<Event> getEvents() {
        return events;
    }

    public void setEvents(List<Event> events) {
        this.events = events;
    }

    public List<TopologyItem> getTopology() {
        return topology;
    }

    public void setTopology(List<TopologyItem> topology) {
        this.topology = topology;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("cursor", cursor)
                .add("events", events)
                .add("topology", topology)
                .toString();
    }
}
