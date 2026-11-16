package com.bpbuilder.bp_builder.workflow.runtime;

import java.time.Duration;
import java.util.Map;

/** The only network-touching dependency of the executor, so tests can replace it. */
public interface HttpTransport {

    Response send(String method, String url, String contentType, byte[] body, Duration timeout) throws Exception;

    default Response send(String method, String url, String contentType, Map<String, String> headers,
                          byte[] body, Duration timeout) throws Exception {
        return send(method, url, contentType, body, timeout);
    }

    record Response(int status, String contentType, String body) {
    }
}
