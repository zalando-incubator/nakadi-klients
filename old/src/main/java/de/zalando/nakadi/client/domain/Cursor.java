package de.zalando.nakadi.client.domain;


import com.google.common.base.MoreObjects;

public final class Cursor {
    private String partition;
    private String offset;

    public String getPartition() {
        return partition;
    }

    public void setPartition(String partition) {
        this.partition = partition;
    }

    public String getOffset() {
        return offset;
    }

    public void setOffset(String offset) {
        this.offset = offset;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("partition", partition)
                .add("offset", offset)
                .toString();
    }
}
