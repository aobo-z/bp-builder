package com.bpbuilder.bp_builder.workflow.mapping;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonPathTest {

    private final Map<String, Object> document = Map.of(
            "documents", List.of(Map.of(
                    "fields", Map.of("Invoice Number", Map.of("content", "INV-1")),
                    "items", List.of(Map.of("Quantity", 2), Map.of("Quantity", 5)))));

    @Test
    void readsKeysIndexesQuotedKeysAndWildcards() {
        assertEquals("INV-1", JsonPath.read(document, "$.documents[0].fields['Invoice Number'].content"));
        assertEquals(List.of(2, 5), JsonPath.read(document, "$.documents[0].items[*].Quantity"));
        assertNull(JsonPath.read(document, "$.documents[3].fields"));
        assertNull(JsonPath.read(document, "documents.missing.path"));
    }

    @Test
    void writesThroughWildcardsOneElementPerValue() {
        Map<String, Object> body = new LinkedHashMap<>();
        JsonPath.write(body, "$.invoice.lines[*].unitPrice", List.of(15, 2));
        JsonPath.write(body, "$.invoice.lines[*].description", List.of("Notebook", "Pen"));
        JsonPath.write(body, "$.invoice.id", "INV-1");

        assertEquals(Map.of("id", "INV-1", "lines", List.of(
                Map.of("unitPrice", 15, "description", "Notebook"),
                Map.of("unitPrice", 2, "description", "Pen"))), body.get("invoice"));
    }

    @Test
    void refusesToSpreadOneValueOverAWildcardOrToParseBrokenPaths() {
        Map<String, Object> body = new LinkedHashMap<>();
        assertThrows(MappingException.class, () -> JsonPath.write(body, "$.lines[*].taxRate", 10));
        assertThrows(MappingException.class, () -> JsonPath.read(body, "$.lines[abc]"));
        assertTrue(JsonPath.isRoot("$"));
    }
}
