package com.bpbuilder.bp_builder.workflow.runtime;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

public final class JdkHttpTransport implements HttpTransport {

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    @Override
    public Response send(String method, String url, String contentType, byte[] body, Duration timeout)
            throws Exception {
        return send(method, url, contentType, Map.of(), body, timeout);
    }

    @Override
    public Response send(String method, String url, String contentType, Map<String, String> headers,
                         byte[] body, Duration timeout) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url)).timeout(timeout);
        if (body != null && contentType != null) request.header("Content-Type", contentType);
        headers.forEach(request::header);
        request.method(method.toUpperCase(), body == null
                ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofByteArray(body));
        HttpResponse<String> response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        return new Response(response.statusCode(),
                response.headers().firstValue("Content-Type").orElse(""), response.body());
    }
}
