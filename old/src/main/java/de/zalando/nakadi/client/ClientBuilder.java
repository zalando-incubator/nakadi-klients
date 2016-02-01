package de.zalando.nakadi.client;


import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.deser.DeserializationProblemHandler;
import com.google.common.base.Strings;
import de.zalando.scoop.Scoop;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Strings.isNullOrEmpty;

// TODO should we make it immutable?
public class ClientBuilder {
    private static final Logger LOGGER = LoggerFactory.getLogger(NakadiClientImpl.class);

    private OAuth2TokenProvider tokenProvider;
    private URI endpoint;
    private ObjectMapper objectMapper;
    private Scoop scoop;
    private String scoopTopic;

    public ClientBuilder() {
    }

    public ClientBuilder withEndpoint(final URI endpoint) {
        checkState(this.endpoint == null, "endpoint is already set");
        this.endpoint = checkNotNull(endpoint, "endpoint must not be null");

        return this;
    }

    public ClientBuilder withOAuth2TokenProvider(OAuth2TokenProvider provider) {
        checkState(tokenProvider == null, "OAut2TokenProvider is already set");
        tokenProvider = checkNotNull(provider, "OAuth2TokenProvider must not be null");

        return this;
    }

    public ClientBuilder withObjectMapper(final ObjectMapper objectMapper) {
        checkState(this.objectMapper == null, "ObjectMapper is already set");
        this.objectMapper = checkNotNull(objectMapper, "ObjectMapper must not be null");

        return this;
    }

    public ClientBuilder withScoop(final Scoop scoop) {
        checkState(this.scoop == null, "Scoop instance is already set");
        this.scoop = scoop;
        return this;
    }

    public ClientBuilder withScoopTopic(final String scoopTopic) {
        checkState(this.scoopTopic == null, "Scoop topic is already set");
        checkArgument(!isNullOrEmpty(scoopTopic), "scoop topic must not be null or empty");
        this.scoopTopic = scoopTopic;
        return this;
    }


    public Client build() {
        checkState(tokenProvider != null, "no OAuth2 token provider set -> try withOAuth2TokenProvider(myProvider)");
        checkState(endpoint != null, "endpoint is set -> try withEndpoint(new URI(\"http://localhost:8080\"))");
        if (objectMapper == null) {
            objectMapper = defaultObjectMapper();
        }

        if(scoop == null) {
            return new NakadiClientImpl(endpoint, tokenProvider, objectMapper);
        }
        else {
            checkState(scoopTopic != null, "scoop topic is  is not set -> try withScoopTopic(\"topic\")");
            return new ScoopAwareNakadiClientImpl(endpoint, tokenProvider, objectMapper, scoop, scoopTopic);
        }
    }

    private ObjectMapper defaultObjectMapper(){
        final ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.setPropertyNamingStrategy(PropertyNamingStrategy.CAMEL_CASE_TO_LOWER_CASE_WITH_UNDERSCORES);

        mapper.addHandler(new DeserializationProblemHandler() {
            @Override
            public boolean handleUnknownProperty(final DeserializationContext ctxt,
                                                 final JsonParser jp,
                                                 final JsonDeserializer<?> deserializer,
                                                 final Object beanOrClass,
                                                 final String propertyName) throws IOException {
                LOGGER.warn("unknown property occurred in JSON representation: [beanOrClass={}, property={}]",
                        beanOrClass, propertyName);

                return true; // problem is considered as resolved
            }
        });

        return mapper;
    }
}
