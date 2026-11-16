package com.bpbuilder.bp_builder.workflow.mapping;

import com.bpbuilder.bp_builder.canonical.ArtifactType;
import com.bpbuilder.bp_builder.workflow.WorkflowFixtures;
import com.bpbuilder.bp_builder.workflow.mapping.CanonicalInvoice.FieldSource;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Uses the real UpBrains response captured on 2026-10-02 for a synthetic invoice. */
class CanonicalMapperTest {

    private final CanonicalMapper mapper = new CanonicalMapper();

    private CanonicalInvoice extracted() {
        CanonicalInvoice invoice = new CanonicalInvoice();
        mapper.applyOutput(WorkflowFixtures.upbrainsResponse(), WorkflowFixtures.upbrainsExtractor().outputMappings(),
                invoice, FieldSource.step(1, "UpBrains"));
        return invoice;
    }

    @Test
    void upbrainsOutputBecomesACanonicalInvoice() {
        CanonicalInvoice invoice = extracted();

        assertEquals("TEST-BP-20261002-002", invoice.get("invoice.id"));
        assertEquals("2026-10-02", invoice.get("invoice.issue_date"));
        assertEquals("AUD", invoice.get("invoice.currency_code"));
        assertEquals("Sydney", invoice.get("seller.address.city"), "city falls back to the suburb UpBrains returned");
        assertEquals("AU", invoice.get("seller.address.country_code"));
        assertEquals("Melbourne", invoice.get("buyer.address.city"));
        assertEquals(0, new BigDecimal("4.00").compareTo((BigDecimal) invoice.get("totals.tax_amount")));
        assertEquals(2, invoice.size("lines"));
        assertEquals(List.of("Notebook", "Pen"), invoice.get("lines[*].description"));
        assertEquals(CanonicalInvoice.SourceType.STEP_OUTPUT, invoice.fieldSources().get("invoice.id").type());
        assertFalse(invoice.isComplete("lines[*].tax_rate"), "UpBrains does not extract the GST rate");
    }

    @Test
    void converterRequestCarriesTaxTotalAndUnitPrices() {
        CanonicalInvoice invoice = extracted();
        invoice.set("lines[*].tax_rate", 10, FieldSource.input());

        CanonicalMapper.RequestBuild request = mapper.buildRequest(WorkflowFixtures.jsonToUblConverter(), invoice,
                new EnumMap<>(ArtifactType.class));

        assertTrue(request.missing().isEmpty(), () -> "unexpected missing: " + request.missing());
        Object body = request.body();
        assertEquals(0, new BigDecimal("4").compareTo((BigDecimal) JsonPath.read(body, "$.invoice.taxTotal.taxAmount")));
        @SuppressWarnings("unchecked")
        List<Object> prices = (List<Object>) JsonPath.read(body, "$.invoice.lines[*].unitPrice");
        assertEquals(0, new BigDecimal("15").compareTo((BigDecimal) prices.get(0)));
        assertEquals(0, new BigDecimal("2").compareTo((BigDecimal) prices.get(1)));
        assertEquals(List.of(10, 10), JsonPath.read(body, "$.invoice.lines[*].taxRate"));
        assertEquals("AU", JsonPath.read(body, "$.invoice.supplier.address.country"));
    }

    @Test
    void missingRequiredValuesAreReportedInsteadOfSent() {
        CanonicalMapper.RequestBuild request = mapper.buildRequest(WorkflowFixtures.jsonToUblConverter(),
                new CanonicalInvoice(), new EnumMap<>(ArtifactType.class));

        assertTrue(request.missing().containsAll(List.of("invoice.id", "seller.name", "lines[*].unit_price")));
        assertEquals(Map.of(), request.body());
    }

    @Test
    void artifactInputsAreTakenFromTheWorkflowContext() {
        Map<ArtifactType, Object> artifacts = new EnumMap<>(ArtifactType.class);
        artifacts.put(ArtifactType.UBL_XML, "<Invoice/>");
        CanonicalMapper.RequestBuild request = mapper.buildRequest(WorkflowFixtures.validator(), new CanonicalInvoice(),
                artifacts);

        assertEquals("invoice.xml", JsonPath.read(request.body(), "$.filename"));
        assertEquals("PEludm9pY2UvPg==", JsonPath.read(request.body(), "$.content"));
        assertTrue(request.missing().isEmpty());
    }
}
