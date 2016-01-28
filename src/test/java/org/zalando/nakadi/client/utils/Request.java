package org.zalando.nakadi.client.utils;

import com.google.common.base.MoreObjects;
import io.undertow.util.HeaderMap;
import io.undertow.util.HttpString;

import java.util.Deque;
import java.util.Map;

public final class Request {
    private final String requestPath;
    private final HeaderMap requestHeaders;
    private final Map<String, Deque<String>> requestQueryParameters;
    private final HttpString requestMethod;
    private final String requestBody;


    public Request( final String requestPath,
                    final HeaderMap requestHeaders,
                    final Map<String, Deque<String>> requestQueryParameters,
                    final HttpString requestMethod,
                    final String requestBody) {
        this.requestPath = requestPath;
        this.requestHeaders = requestHeaders;
        this.requestQueryParameters = requestQueryParameters;
        this.requestMethod = requestMethod;
        this.requestBody = requestBody;
    }

    public String getRequestPath() {
        return requestPath;
    }

    public HeaderMap getRequestHeaders() {
        return requestHeaders;
    }

    public Map<String, Deque<String>> getRequestQueryParameters() {
        return requestQueryParameters;
    }

    public HttpString getRequestMethod() {
        return requestMethod;
    }

    public String getRequestBody() {
        return requestBody;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("requestPath", requestPath)
                .add("requestHeaders", requestHeaders)
                .add("requestQueryParameters", requestQueryParameters)
                .add("requestMethod", requestMethod)
                .add("requestBody", requestBody)
                .toString();
    }
}
