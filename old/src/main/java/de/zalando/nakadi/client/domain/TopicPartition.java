package de.zalando.nakadi.client.domain;


import com.google.common.base.MoreObjects;

import java.util.Objects;

public final class TopicPartition {

    private String partitionId;
    private String oldestAvailableOffset;
    private String newestAvailableOffset;

    public String getPartitionId() {
        return partitionId;
    }

    public void setPartitionId(String partitionId) {
        this.partitionId = partitionId;
    }

    public String getOldestAvailableOffset() {
        return oldestAvailableOffset;
    }

    public void setOldestAvailableOffset(String oldestAvailableOffset) {
        this.oldestAvailableOffset = oldestAvailableOffset;
    }

    public String getNewestAvailableOffset() {
        return newestAvailableOffset;
    }

    public void setNewestAvailableOffset(String newestAvailableOffset) {
        this.newestAvailableOffset = newestAvailableOffset;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TopicPartition that = (TopicPartition) o;
        return Objects.equals(partitionId, that.partitionId) &&
                Objects.equals(oldestAvailableOffset, that.oldestAvailableOffset) &&
                Objects.equals(newestAvailableOffset, that.newestAvailableOffset);
    }

    @Override
    public int hashCode() {
        return Objects.hash(partitionId, oldestAvailableOffset, newestAvailableOffset);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("partitionId", partitionId)
                .add("oldestAvailableOffset", oldestAvailableOffset)
                .add("newestAvailableOffset", newestAvailableOffset)
                .toString();
    }
}
