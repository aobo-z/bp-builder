package com.bpbuilder.bp_builder.workflow.runtime;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Shortens long strings (Base64 files, XML) in payloads that are returned for display. */
final class Payloads {

    private static final int MAX_STRING = 300;

    private Payloads() {
    }

    static Object compact(Object value) {
        if (value instanceof String text) {
            return text.length() <= MAX_STRING ? text
                    : text.substring(0, 80) + "... (" + text.length() + " characters)";
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, item) -> result.put(String.valueOf(key), compact(item)));
            return result;
        }
        if (value instanceof List<?> list) {
            List<Object> result = new ArrayList<>(list.size());
            list.forEach(item -> result.add(compact(item)));
            return result;
        }
        return value;
    }
}
