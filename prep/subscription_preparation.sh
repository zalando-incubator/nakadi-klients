#! /bin/sh

HOST=https://nakadi-sandbox.aruha-test.zalan.do
TOKEN=$(zign token)

http POST $HOST/event-types "Authorization: Bearer $TOKEN"  @process_group.json

http --timeout=60000 POST $HOST/event-types/de.zalando.logistics.laas.hecate.test.process_status_changed/events  "Authorization: Bearer $TOKEN"  @event.json

http POST $HOST/subscriptions "Authorization: Bearer $TOKEN" @subscription.json

