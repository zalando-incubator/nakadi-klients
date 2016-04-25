package org.zalando.nakadi.client.utils;


import com.google.common.collect.Maps;
import org.zalando.nakadi.client.*;

import java.net.URI;
import java.util.HashMap;
import java.util.Optional;
import java.util.concurrent.Future;


public class Main {


    public static void main(final String[] args) throws Exception {

        final Client client = new KlientBuilder()
                        .withEndpoint(new URI("localhost"))
                        .withPort(8080)                         // test config?
                        .withSecuredConnection(false)
                        .withJavaTokenProvider(() -> "<your token>")
                        .buildJavaClient();

        System.out.println("-METRICS--> " + client.getMetrics().get());


        final HashMap<String,Object> meta = Maps.newHashMap();
        meta.put("id", "1234567890");

        final Future<Void> f = client.postEvent("test", new Event("eventType", "orderingKey", meta, "{}"));

        f.get();

        client.subscribeToTopic("test", ListenParametersUtils.defaultInstance(), new JListenerWrapper(new MyListener()), true);


        Thread.sleep(Long.MAX_VALUE);
    }

    private static final class MyListener implements JListener{

        @Override
        public String id() {
            return getClass().getName();
        }

        @Override
        public void onReceive(String topic, String partition, Cursor cursor, JEvent event) {
            System.out.printf("onReceive -> " + event);
        }

        @Override
        public void onConnectionOpened(String topic, String partition) {
            System.out.println("onConnectionOpened " + topic + " " + partition);
        }

        @Override
        public void onConnectionFailed(String topic, String partition, int status, String error) {
            System.out.println("onConnectionFailed " + topic + " " + partition + " " + status + " " + error);
        }

        @Override
        public void onConnectionClosed(String topic, String partition, Optional<Cursor> lastCursor) {
            System.out.println("onConnectionClosed " + topic + " " + partition + " " + lastCursor);
        }
    }
}
