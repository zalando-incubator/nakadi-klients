package de.zalando.nakadi.client;


import com.fasterxml.jackson.databind.ObjectMapper;
import de.zalando.nakadi.client.domain.Cursor;
import de.zalando.nakadi.client.domain.Event;
import de.zalando.nakadi.client.domain.SimpleStreamEvent;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpRequestBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Strings.isNullOrEmpty;
import static java.util.Objects.requireNonNull;

final class StreamReader {

    public static final int INITIAL_RECEIVE_BUFFER_SIZE = 1024; // 1 KB

    private static final Logger LOGGER = LoggerFactory.getLogger(StreamReader.class);

    public static void read(final InputStream in,
                            final ObjectMapper objectMapper,
                            final Consumer<SimpleStreamEvent> eventConsumer) throws IOException {

        requireNonNull(in, "InputStream must not be null");
        requireNonNull(objectMapper, "ObjectMapper must not be null");
        requireNonNull(eventConsumer, "event consumer must not be null");

        final ByteArrayOutputStream bout = new ByteArrayOutputStream(INITIAL_RECEIVE_BUFFER_SIZE);

        /*
         * We can not simply rely on EOL for the end of each JSON object as
         * Nakadi puts the in the middle of the response body sometimes.
         * For this reason, we need to apply some very simple JSON parsing logic.
         *
         * See also http://json.org/ for string parsing semantics
         */
        int byteItem;
        int stack = 0;
        boolean hasOpenString = false;

        while ((byteItem = in.read()) != -1) {
            bout.write(byteItem);

            if(byteItem == '"'){
                hasOpenString = !hasOpenString;
            }
            if(!hasOpenString && byteItem == '{'){
                stack++;
            }
            else if(!hasOpenString && byteItem == '}'){
                stack--;

                if(stack == 0 && bout.size() != 0){
                    final byte[] receiveBuffer = bout.toByteArray();
                    final SimpleStreamEvent streamingEvent = objectMapper.readValue(receiveBuffer, SimpleStreamEvent.class);
                    LOGGER.debug("received [streamingEvent={}]", streamingEvent);
                    eventConsumer.accept(streamingEvent);
                    bout.reset();
                }
            }
        }
    }
}
