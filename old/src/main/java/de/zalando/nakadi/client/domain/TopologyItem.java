package de.zalando.nakadi.client.domain;


import com.google.common.base.MoreObjects;

import java.util.List;

public final class TopologyItem {
    private String clientId;
    private List<String> partitions;

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public List<String> getPartitions() {
        return partitions;
    }

    public void setPartitions(List<String> partitions) {
        this.partitions = partitions;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("clientId", clientId)
                .add("partitions", partitions)
                .toString();
    }
}
