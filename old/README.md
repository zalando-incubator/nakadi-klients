# Nakadi Klients

Implementation of a client accessing the low level API of the [Nakadi event bus](https://github.com/zalando/nakadi).

## Prerequisites
- Java >= 1.8
- Maven >= 3.x

## Tutorial

### Instantiate client
```java
ClientBuilder builder = new ClientBuilder();
String token = "<OAUTH Token>";
URI nakadiHost = new URI("http://localhost:8080")
Client client = builder.withOAuth2TokenProvider(() -> token)
                       .withEndpoint(nakadiHost)
                       .build();
```

### Get monitoring metrics
```java   
// NOTE: metrics format is not defined / fixed
Map<String, Object> metrics = client.getMetrics();
```
    
### List all known topics
```java
List<Topic> topics = client.getTopics();
```

### Post a single event to the given topic
Partition selection is done using the defined partition resolution. The partition resolution strategy is defined per 
topic and is managed by the event bus (currently resolved from a hash over `ordering_key`).

```java
final Event event = new Event();
event.setEventType("test-type");
event.setOrderingKey("someordering");

final HashMap<String, String> myBody = Maps.newHashMap();
myBody.put("key", "my_value");
event.setBody(myBody);

final HashMap<String, Object> meta = Maps.newHashMap();
meta.put("tenantId", "123456789");
event.setMetadata(meta);

final String topic = "test";
client.postEvent(topic, event);
```

### Get partition information of a given topic
```java
List<TopicPartition> partitions = client.getPartitions("myTopic");
```
    
### Subscribe to a given topic
Non-blocking subscription to a topic requires a `EventListener` implementation. The event listener must be thread-safe because
it listens to all partitions of a topic (one thread each). Note: there is a variant which allows to utilize a specific thread pool.

```java
// Listens to all NEW events posted to the specified topic
List<Future> partitionThreads = client.subscribeToTopic("myTopic", 
                                                        (cursor, event) -> System.out.println(cursor + " ---> " + event));
```

### Subscribe to a specific partition
Blocking subscription to events of specified topic and partition.

```java
List<TopicPartition> partitions = client.getPartitions("myTopic");
client.listenForEvents("myTopic",
                       partitions.get(0).getPartitionId(),
                       "0",
                       (cursor, event) -> System.out.println(cursor + " ---> " + event);
```

### Scoop integration
`Nakadi-Klients` has [Scoop](https://github.com/zalando/scoop) integrated to reduce the consumption of the same event by multiple instances of an application where each instance subscribes to `Nakadi` This feature is rather specific for [STUPS](https://github.com/zalando-stups) deployments. Please checkout the [Scoop documentation](https://github.com/zalando/scoop), if you want to use this feature.

```java
Scoop scoop = new Scoop();
scoop = scoop.withAwsConfig()
             .withBindHostName("hecate")
             .withClusterPort(25551)
             .withPort(25551);

ClientBuilder builder = new ClientBuilder();
return builder.withOAuth2TokenProvider(() -> tokens.get("tokenId"))
              .withEndpoint(nakadiHost)
              .withScoop(scoop)
              .withScoopTopic("system")
              .build();
```

## See
- [Nakadi event bus](https://github.com/zalando/nakadi)
- [STUPS](https://github.com/zalando-stups)
- [STUPS' tokens library](https://github.com/zalando-stups/tokens)
- [Scoop](https://github.com/zalando/scoop)

## TODO
- [ ] handle case where separate clusters consisting of 1 member are built
- [ ] automated tests for Scoop integration

## License
http://opensource.org/licenses/MIT
