package org.zalando.nakadi.client.util

object TestJsonEntity {
  val singleEvent="""{
        "name": "article",
        "owning_application": "article-producer",
        "category": "data",
        "partition_strategy": {
            "name": "random",
            "doc": "This strategy will put the event to a random partition. Use it only if your `EventType` has one partition or if you don't care about ordering of events."
        },
        "partition_key_fields": [],
        "schema": {
            "type": "json_schema",
            "schema": "{\"Article\": { \"properties\": { \"sku\": { \"type\": \"string\" } } }}"
        }
    }
    """
  val events = """
    [
    {
        "name": "article",
        "owning_application": "article-producer",
        "category": "data",
        "partition_strategy": {
            "name": "random",
            "doc": "This strategy will put the event to a random partition. Use it only if your `EventType` has one partition or if you don't care about ordering of events."
        },
        "partition_key_fields": [],
        "schema": {
            "type": "json_schema",
            "schema": "{\"Article\": { \"properties\": { \"sku\": { \"type\": \"string\" } } }}"
        }
    },
    {
        "name": "myarticle",
        "owning_application": "article-producer",
        "category": "data",
        "partition_strategy": {
            "name": "random",
            "doc": "This strategy will put the event to a random partition. Use it only if your `EventType` has one partition or if you don't care about ordering of events."
        },
        "partition_key_fields": [],
        "schema": {
            "type": "json_schema",
            "schema": "{\"Article\": { \"properties\": { \"sku\": { \"type\": \"string\" } } }}"
        }
    }]
    """
}