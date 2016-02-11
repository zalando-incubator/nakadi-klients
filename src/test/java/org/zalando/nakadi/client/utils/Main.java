package org.zalando.nakadi.client.utils;


import com.google.common.collect.Maps;
import org.zalando.nakadi.client.*;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;


import scala.Option;
import scala.collection.JavaConversions;
import scala.collection.mutable.AnyRefMap;
import scala.util.Either;


public class Main {


    public static void main(final String[] args) throws Exception {

        final Client client = new KlientBuilder()
                        .withEndpoint(new URI("localhost"))
                        .withPort(8080)
                        .withSecuredConnection(false)
                        .withJavaTokenProvider(() -> "b099a178-acf7-467b-b106-4c0656285153")
                        .buildJavaClient();

        System.out.println("-METRICS--> " + client.getMetrics().get());


        final HashMap<String,Object> meta = Maps.newHashMap();
        meta.put("id", "1234567890");

        final Future<Either<String, Void>> f =
                                            client.postEvent("test", new Event("eventType", "orderingKey", meta,
                                                    scala.collection.immutable.Map$.MODULE$.<String,Object>empty()  /*was: "{}"*/));

        // ^-- Above, see for creating an empty Scala map in Java -> http://stackoverflow.com/questions/14365048/how-do-i-create-an-empty-immutable-scala-map-in-java

        final Either<String, Void> postResult = f.get();

        if(postResult.isLeft())
            System.out.println(">>POST EVENT - LEFT>>>" + postResult.left().get());
        else
            System.out.println(">>POST EVENT - LEFT>>>" + postResult.right().get());

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
            System.out.println("onConnectionOpned " + topic + " " + partition);
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
