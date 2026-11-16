package com.bpbuilder.bp_builder.workflow.mapping;

import com.bpbuilder.bp_builder.workflow.mapping.CanonicalInvoice.FieldSource;
import com.bpbuilder.bp_builder.workflow.mapping.CanonicalInvoice.SourceType;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CanonicalInvoiceTest {

    @Test
    void inputValuesWinOverOperationOutputsRegardlessOfOrder() {
        CanonicalInvoice invoice = new CanonicalInvoice();
        invoice.set("invoice.type_code", "380", FieldSource.input());
        invoice.set("invoice.type_code", "381", FieldSource.step(1, "extractor"));
        invoice.set("invoice.id", "FROM-USER", FieldSource.input());
        invoice.set("invoice.id", "FROM-PDF", FieldSource.step(1, "extractor"));

        assertEquals("380", invoice.get("invoice.type_code"));
        assertEquals("FROM-USER", invoice.get("invoice.id"));
        assertEquals(SourceType.USER_INPUT, invoice.fieldSources().get("invoice.id").type());
    }

    @Test
    void aCanonicalJsonObjectLoadsIntoConcretePathsAndListsItsFieldPaths() {
        Map<String, Object> json = Map.of(
                "invoice", Map.of("id", "INV-1"),
                "lines", List.of(Map.of("quantity", 2), Map.of("quantity", 3)));
        CanonicalInvoice invoice = new CanonicalInvoice();
        invoice.load(json, FieldSource.input());

        assertEquals("INV-1", invoice.get("invoice.id"));
        assertEquals(List.of(2, 3), invoice.get("lines[*].quantity"));
        assertEquals(SourceType.USER_INPUT, invoice.fieldSources().get("lines[1].quantity").type());
        assertEquals(java.util.Set.of("invoice.id", "lines[*].quantity"), CanonicalInvoice.fieldPaths(json));
    }

    @Test
    void aLaterStepMayOverwriteAnEarlierStep() {
        CanonicalInvoice invoice = new CanonicalInvoice();
        invoice.set("seller.name", "Old", FieldSource.step(1, "a"));
        invoice.set("seller.name", "New", FieldSource.step(2, "b"));
        assertEquals("New", invoice.get("seller.name"));
        assertEquals(2, invoice.fieldSources().get("seller.name").stepSequence());
    }

    @Test
    void aWildcardValueAppliesToEveryExtractedLine() {
        CanonicalInvoice invoice = new CanonicalInvoice();
        invoice.set("lines[*].description", List.of("Notebook", "Pen"), FieldSource.step(1, "extractor"));
        invoice.set("lines[*].tax_rate", 10, FieldSource.input());

        assertEquals(List.of(10, 10), invoice.get("lines[*].tax_rate"));
        assertTrue(invoice.isComplete("lines[*].tax_rate"));
        Map<String, Object> json = invoice.toJson();
        assertEquals(List.of(Map.of("description", "Notebook", "tax_rate", 10),
                Map.of("description", "Pen", "tax_rate", 10)), json.get("lines"));
    }

    @Test
    void aLineFieldIsIncompleteWhenAnyLineLacksIt() {
        CanonicalInvoice invoice = new CanonicalInvoice();
        invoice.set("lines[*].description", List.of("Notebook", "Pen"), FieldSource.step(1, "extractor"));
        invoice.set("lines[*].unit_price", Arrays.asList(15, null), FieldSource.step(1, "extractor"));

        assertFalse(invoice.isComplete("lines[*].unit_price"));
        assertEquals(Arrays.asList(15, null), invoice.get("lines[*].unit_price"));
        assertNull(new CanonicalInvoice().get("lines[*].unit_price"));
    }
}
