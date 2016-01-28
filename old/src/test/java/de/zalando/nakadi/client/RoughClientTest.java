package de.zalando.nakadi.client;

import com.google.common.collect.Maps;
import de.zalando.nakadi.client.domain.Event;
import de.zalando.nakadi.client.domain.TopicPartition;
import org.junit.Test;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;


// TODO write automated tests
public class RoughClientTest {

//   @Test
    public void test() throws Exception {
        final String token = "<my OAUTH2 token>";

        final ClientBuilder builder = new ClientBuilder();
        Client c = builder.withOAuth2TokenProvider(() -> token)
                .withEndpoint(new URI("http://localhost:8080")).build();

        System.out.println("topics ---> " + c.getTopics());
        System.out.println("metrics ---> " + c.getMetrics());

        //-----------------------

        System.out.println("sending event...");

        final Event event = new Event();
        event.setEventType("test-type");
        event.setOrderingKey("someordering");

        final HashMap<String, String> myBody = Maps.newHashMap();
        myBody.put("key", "my_value");
        event.setBody(myBody);

        final HashMap<String, Object> meta = Maps.newHashMap();
        meta.put("tenantId", "123456789");
        event.setMetadata(meta);

        c.postEvent("test", event);

        //-----------------------

        System.out.println("-- partitions 'test' --> " + c.getPartitions("test"));
        System.out.println("-- partitions 'test2' --> " + c.getPartitions("test2"));
        System.out.println("-- partitions 'test3' --> " + c.getPartitions("test3"));


        // -----------------------

        // System.out.println("subscribing to topic 'test'");
        //c.subscribeToTopic("test", (cursor, eventX) -> System.out.println(cursor + " -----> " + eventX));


        System.out.println("reading partitions...");

        final List<TopicPartition> partitions =  c.getPartitions("test");

        for(TopicPartition partition : partitions) {
            final TopicPartition receviedPartition = c.getPartition("test", partition.getPartitionId());
            System.out.println(partition.getPartitionId() + " --> " + receviedPartition);
        }



        //------------------------

        final List<Future> futures = c.subscribeToTopic("test", (cursor, e) -> System.out.println(cursor + " ---> " + e));

        futures.forEach(f -> {
            try {
                f.get();
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (ExecutionException e) {
                e.printStackTrace();
            }
        });
    }
}
