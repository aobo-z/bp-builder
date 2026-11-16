package com.bpbuilder.bp_builder.service.interpret;

import com.bpbuilder.bp_builder.workflow.runtime.HttpTransport;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Calls Gemini generateContent with a response schema and returns the parsed
 * JSON answer. The API key is read from GEMINI_API_KEY on every call and is
 * only ever sent in the x-goog-api-key header.
 */
public class GeminiClient {

    static final String API_KEY_ENV = "GEMINI_API_KEY";
    static final String MODEL_ENV = "GEMINI_MODEL";
    static final String DEFAULT_MODEL = "gemini-3.8-flash";
    private static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/";
    private static final Duration TIMEOUT = Duration.ofSeconds(60);

    private final HttpTransport transport;
    private final Function<String, String> environment;
    private final ObjectMapper mapper = new ObjectMapper();

    public GeminiClient(HttpTransport transport, Function<String, String> environment) {
        this.transport = transport;
        this.environment = environment;
    }

    public String model() {
        String configured = environment.apply(MODEL_ENV);
        return configured == null || configured.isBlank() ? DEFAULT_MODEL : configured.trim();
    }

    public Map<String, Object> generateJson(String instruction, String userText, Map<String, Object> responseSchema) {
        return generateJson(instruction, List.of(Map.of("text", userText)), responseSchema);
    }

    /**
     * Same as {@link #generateJson(String, String, Map)}, but the user content is an
     * image instead of (or alongside) text — Gemini is natively multimodal, so an
     * inlineData part works the same way a text part does.
     */
    public Map<String, Object> generateJsonWithImage(String instruction, byte[] imageBytes, String imageMimeType,
            Map<String, Object> responseSchema) {
        return generateJson(instruction,
                List.of(Map.of("inlineData", Map.of("mimeType", imageMimeType, "data", imageBytes))),
                responseSchema);
    }

    private Map<String, Object> generateJson(String instruction, List<Map<String, Object>> parts,
            Map<String, Object> responseSchema) {
        String key = environment.apply(API_KEY_ENV);
        if (key == null || key.isBlank()) {
            throw new InterpretationUnavailableException(API_KEY_ENV + " is not configured for the backend.");
        }
        Map<String, Object> request = Map.of(
                "systemInstruction", Map.of("parts", List.of(Map.of("text", instruction))),
                "contents", List.of(Map.of("role", "user", "parts", parts)),
                "generationConfig", Map.of("responseMimeType", "application/json",
                        "responseSchema", responseSchema));
        HttpTransport.Response response;
        try {
            response = transport.send("POST", BASE_URL + model() + ":generateContent", "application/json",
                    Map.of("x-goog-api-key", key.trim()), mapper.writeValueAsBytes(request), TIMEOUT);
        } catch (Exception error) {
            throw new InterpretationUnavailableException("Gemini could not be reached: "
                    + error.getClass().getSimpleName(), error);
        }
        if (response.status() < 200 || response.status() >= 300) {
            throw new InterpretationUnavailableException("Gemini answered HTTP " + response.status()
                    + errorMessage(response.body()));
        }
        return answer(response.body());
    }

    private Map<String, Object> answer(String body) {
        try {
            JsonNode parts = mapper.readTree(body).path("candidates").path(0).path("content").path("parts");
            StringBuilder text = new StringBuilder();
            for (JsonNode part : parts) {
                if (!part.path("thought").asBoolean(false)) text.append(part.path("text").asText(""));
            }
            if (text.isEmpty()) throw new InterpretationUnavailableException("Gemini returned no answer.");
            return mapper.readValue(text.toString().getBytes(StandardCharsets.UTF_8), new TypeReference<>() {
            });
        } catch (InterpretationUnavailableException error) {
            throw error;
        } catch (Exception error) {
            throw new InterpretationUnavailableException("Gemini returned an answer that is not the expected JSON.",
                    error);
        }
    }

    private String errorMessage(String body) {
        try {
            String message = mapper.readTree(body).path("error").path("message").asText("");
            return message.isBlank() ? "." : ": " + message;
        } catch (Exception error) {
            return ".";
        }
    }
}
