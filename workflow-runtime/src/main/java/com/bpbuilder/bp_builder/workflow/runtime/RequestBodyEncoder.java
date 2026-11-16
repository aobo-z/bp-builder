package com.bpbuilder.bp_builder.workflow.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

/** Encodes the reviewed JSON and Mailgun-style multipart request contracts. */
final class RequestBodyEncoder {

    record Encoded(String contentType, byte[] body) {
    }

    private final ObjectMapper objectMapper;

    RequestBodyEncoder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    Encoded encode(String requestedContentType, Object body) throws Exception {
        String contentType = requestedContentType == null || requestedContentType.isBlank()
                ? "application/json" : requestedContentType;
        if (!contentType.toLowerCase().startsWith("multipart/form-data")) {
            return new Encoded(contentType, objectMapper.writeValueAsBytes(body));
        }
        if (!(body instanceof Map<?, ?> fields)) {
            throw new IllegalArgumentException("multipart/form-data request body must be an object");
        }
        String boundary = "----BPBuilder" + UUID.randomUUID().toString().replace("-", "");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        for (Map.Entry<?, ?> entry : fields.entrySet()) {
            String name = String.valueOf(entry.getKey());
            Object value = entry.getValue();
            if (value instanceof Map<?, ?> file && file.containsKey("filename") && file.containsKey("content")) {
                writeFile(output, boundary, name, file);
            } else {
                writeField(output, boundary, name, value);
            }
        }
        write(output, "--" + boundary + "--\r\n");
        return new Encoded("multipart/form-data; boundary=" + boundary, output.toByteArray());
    }

    private static void writeField(ByteArrayOutputStream output, String boundary, String name, Object value) {
        write(output, "--" + boundary + "\r\n");
        write(output, "Content-Disposition: form-data; name=\"" + quoted(name) + "\"\r\n\r\n");
        write(output, value == null ? "" : String.valueOf(value));
        write(output, "\r\n");
    }

    private static void writeFile(ByteArrayOutputStream output, String boundary, String name, Map<?, ?> file) {
        String filename = String.valueOf(file.get("filename"));
        Object configuredContentType = file.get("contentType");
        String fileContentType = configuredContentType == null
                ? "application/octet-stream" : String.valueOf(configuredContentType);
        Object content = file.get("content");
        write(output, "--" + boundary + "\r\n");
        write(output, "Content-Disposition: form-data; name=\"" + quoted(name) + "\"; filename=\""
                + quoted(filename) + "\"\r\n");
        write(output, "Content-Type: " + fileContentType + "\r\n\r\n");
        write(output, content == null ? "" : String.valueOf(content));
        write(output, "\r\n");
    }

    private static String quoted(String value) {
        return value.replace("\\", "_").replace("\"", "_").replace("\r", "_").replace("\n", "_");
    }

    private static void write(ByteArrayOutputStream output, String value) {
        output.writeBytes(value.getBytes(StandardCharsets.UTF_8));
    }
}
