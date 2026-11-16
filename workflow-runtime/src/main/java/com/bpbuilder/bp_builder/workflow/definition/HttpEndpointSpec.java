package com.bpbuilder.bp_builder.workflow.definition;

/**
 * Where an operation is called. When endpointUrlRef is set, the complete URL
 * is read from that environment variable at call time, so secret webhook URLs
 * never appear in the definition, the database or any response.
 */
public record HttpEndpointSpec(
        String method,
        String baseUrl,
        String path,
        String endpointUrlRef,
        String contentType,
        int timeoutSeconds,
        /** NONE, X_API_KEY, or BASIC_API_KEY. */
        String authMethod,
        /** Environment variable containing the credential value. */
        String credentialRef
) {
    /** Keeps existing operation snapshots source-compatible. */
    public HttpEndpointSpec(String method, String baseUrl, String path, String endpointUrlRef,
                            String contentType, int timeoutSeconds) {
        this(method, baseUrl, path, endpointUrlRef, contentType, timeoutSeconds, null, null);
    }

    /** A description that is safe to show: never contains a secret URL. */
    public String displayName() {
        if (endpointUrlRef != null && !endpointUrlRef.isBlank()) {
            return method + " (URL from environment variable " + endpointUrlRef + ")";
        }
        String base = baseUrl == null ? "" : baseUrl.replaceAll("/+$", "");
        return method + " " + base + (path == null ? "" : path);
    }
}
