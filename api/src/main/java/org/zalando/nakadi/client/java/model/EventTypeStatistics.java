package org.zalando.nakadi.client.java.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Operational statistics for an EventType. This data is generated by Nakadi based on the runtime and might be used to
 * guide changes in internal parameters.
 *
 */
public class EventTypeStatistics {
    private final Integer messagesPerMinute;
    private final Integer messageSize;
    private final Integer readParallelism;
    private final Integer writeParallelism;

    @JsonCreator
    public EventTypeStatistics(@JsonProperty("messages_per_minute") Integer messagesPerMinute, @JsonProperty("message_size") Integer messageSize,
            @JsonProperty("read_parallelism") Integer readParallelism, @JsonProperty("write_parallelism") Integer writeParallelism) {
        this.messagesPerMinute = messagesPerMinute;
        this.messageSize = messageSize;
        this.readParallelism = readParallelism;
        this.writeParallelism = writeParallelism;
    }

    public Integer getMessagesPerMinute() {
        return messagesPerMinute;
    }

    public Integer getMessageSize() {
        return messageSize;
    }

    public Integer getReadParallelism() {
        return readParallelism;
    }

    public Integer getWriteParallelism() {
        return writeParallelism;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((messageSize == null) ? 0 : messageSize.hashCode());
        result = prime * result + ((messagesPerMinute == null) ? 0 : messagesPerMinute.hashCode());
        result = prime * result + ((readParallelism == null) ? 0 : readParallelism.hashCode());
        result = prime * result + ((writeParallelism == null) ? 0 : writeParallelism.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        EventTypeStatistics other = (EventTypeStatistics) obj;
        if (messageSize == null) {
            if (other.messageSize != null)
                return false;
        } else if (!messageSize.equals(other.messageSize))
            return false;
        if (messagesPerMinute == null) {
            if (other.messagesPerMinute != null)
                return false;
        } else if (!messagesPerMinute.equals(other.messagesPerMinute))
            return false;
        if (readParallelism == null) {
            if (other.readParallelism != null)
                return false;
        } else if (!readParallelism.equals(other.readParallelism))
            return false;
        if (writeParallelism == null) {
            if (other.writeParallelism != null)
                return false;
        } else if (!writeParallelism.equals(other.writeParallelism))
            return false;
        return true;
    }

    @Override
    public String toString() {
        return "EventTypeStatistics [messagesPerMinute=" + messagesPerMinute + ", messageSize=" + messageSize + ", readParallelism=" + readParallelism + ", writeParallelism=" + writeParallelism + "]";
    }

}
