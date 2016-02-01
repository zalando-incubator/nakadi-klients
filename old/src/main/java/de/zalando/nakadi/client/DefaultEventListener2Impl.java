package de.zalando.nakadi.client;


import de.zalando.nakadi.client.domain.Cursor;
import de.zalando.nakadi.client.domain.Event;

public class DefaultEventListener2Impl implements EventListener2 {

    @Override
    public void onConnectionClosed(String topic, String partitionId) {

    }

    @Override
    public void onConnectionClosed(String topic, String partitionId, Exception cause) {

    }

    @Override
    public void onReceive(Cursor cursor, Event event) {

    }
}
