package de.zalando.nakadi.client.utils;


import com.google.common.base.MoreObjects;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HeaderMap;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpStatus;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class NakadiTestService {

    private final String host;
    private final int port;

    private ConcurrentHashMap<String, Handler> handlerMap;
    private Multimap<String, Request> collectedRequests;

    private Undertow server;

    private NakadiTestService(final String host,
                              final int port,
                              final List<Handler> handlerList) {
        this.host = host;
        this.port = port;

        this.handlerMap = new ConcurrentHashMap<>();
        handlerList.forEach(h -> handlerMap.put(h.getRequestPath(), h));

        this.collectedRequests = Multimaps.synchronizedListMultimap(ArrayListMultimap.create());
    }


    public void start() {
        server = Undertow.builder()
                .addHttpListener(port, host)
                .setHandler(new HttpHandler() {

                    @Override
                    public void handleRequest(final HttpServerExchange exchange) throws Exception {


                        final String receivedRequestPath = exchange.getRequestPath();
                        final HttpString receivedRequestMethod = exchange.getRequestMethod();


                        final Handler handler = handlerMap.get(receivedRequestPath);
                        if(handler == null || ! Objects.equals(handler.getRequestMethod(), receivedRequestMethod)) {
                            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, handler.getResponseContentType());
                            exchange.setResponseCode(HttpStatus.SC_NOT_FOUND);
                            exchange.getResponseSender().send("invalid request: " + receivedRequestPath + " with request method: " + receivedRequestMethod);
                        }
                        else {
                            final HeaderMap requestHeaders = exchange.getRequestHeaders();
                            final Map<String, Deque<String>> requestQueryParameters = exchange.getQueryParameters();
                            final HttpString requestMethod = exchange.getRequestMethod();

                            exchange.startBlocking();
                            final String requestBody = IOUtils.toString(exchange.getInputStream());

                            final Request request = new Request(handler.getRequestPath(),
                                    requestHeaders,
                                    requestQueryParameters,
                                    requestMethod,
                                    requestBody);

                            collectedRequests.put(receivedRequestPath, request);

                            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, handler.responseContentType);
                            exchange.setResponseCode(handler.getResponseStatusCode());
                            exchange.getResponseSender().send(handler.getResponsePayload());
                        }
                    }
                }).build();
        server.start();
    }


    public Map<String, Collection<Request>> getCollectedRequests() {
        return this.collectedRequests.asMap();
    }

    public void stop() {
        server.stop();
        server = null;
    }


    //-------------


    public static class Builder {

        private ArrayList<Handler> handlerList;

        private String host = "localhost";
        private int port = 8080;

        public Builder() {
            this.handlerList = Lists.newArrayList();
        }


        public Builder withHost(final String host) {
            this.host = checkNotNull(host);
            return this;
        }

        public Builder withPort(final int port) {
            this.port = port;
            return this;
        }

        public HandlerBuilder withHandler(final String requestPath) {
            return new HandlerBuilder(this, requestPath);
        }

        public Builder finalizeHandler(final HandlerBuilder handlerBuilder) {
            handlerList.add(handlerBuilder.buildHandler());
            return this;
        }

        public NakadiTestService build() {
            return new NakadiTestService(host, port, handlerList);
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("host", host)
                    .add("port", port)
                    .add("handlerList", handlerList)
                    .toString();
        }
    }

    //----------------

    public static final class HandlerBuilder{
        private final String requestPath;
        private final Builder mainBuilder;

        private HttpString requestMethod;
        private int responseStatusCode = 500;
        private String responsePayload = "{ \"content\": \"nix\" }";
        private String responseContentType = "application/json";

        HandlerBuilder(final Builder mainBuilder, final String requestPath) {
            this.mainBuilder = checkNotNull(mainBuilder);
            this.requestPath = checkNotNull(requestPath);
        }

        public HandlerBuilder withRequestMethod(final HttpString requestMethod) {
            this.requestMethod = checkNotNull(requestMethod);
            return this;
        }

        public HandlerBuilder withResponseStatusCode(final int responsStatusCode) {
            checkArgument(responsStatusCode > 0 && responsStatusCode < 1000,
                    "invalid response status code %s", responsStatusCode);
            this.responseStatusCode = responsStatusCode;
            return this;
        }

        public HandlerBuilder withResponsePayload(final String responsePayload) {
            this.responsePayload = checkNotNull(responsePayload);
            return this;
        }

        public HandlerBuilder withResponseContentType(final String responseContentType) {
            this.responseContentType = checkNotNull(responseContentType);
            return this;
        }

        public Builder and() {
            return mainBuilder.finalizeHandler(this);
        }


        public NakadiTestService build() {
            return mainBuilder.finalizeHandler(this).build();
        }

        public Handler buildHandler() {
            return new Handler(
                    requestPath,
                    requestMethod,
                    responseStatusCode,
                    responsePayload,
                    responseContentType);
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("requestPath", requestPath)
                    .add("requestMethod", requestMethod)
                    .add("responseStatusCode", responseStatusCode)
                    .add("responsePayload", responsePayload)
                    .add("responseContentType", responseContentType)
                    .toString();
        }
    }

    //-----------

    private static final class Handler {

        private final String requestPath;
        private final HttpString requestMethod;
        private final int responseStatusCode;
        private final String responsePayload;
        private final  String responseContentType;

        Handler(String requestPath, HttpString requestMethod, int responseStatusCode, String responsePayload, String responseContentType) {
            this.requestPath = requestPath;
            this.requestMethod = requestMethod;
            this.responseStatusCode = responseStatusCode;
            this.responsePayload = responsePayload;
            this.responseContentType = responseContentType;
        }

        public String getRequestPath() {
            return requestPath;
        }

        public HttpString getRequestMethod() {
            return requestMethod;
        }

        public int getResponseStatusCode() {
            return responseStatusCode;
        }

        public String getResponsePayload() {
            return responsePayload;
        }

        public String getResponseContentType() {
            return responseContentType;
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("requestPath", requestPath)
                    .add("requestMethod", requestMethod)
                    .add("responseStatusCode", responseStatusCode)
                    .add("responsePayload", responsePayload)
                    .add("responseContentType", responseContentType)
                    .toString();
        }
    }
}
