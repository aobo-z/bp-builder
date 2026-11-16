package com.bpbuilder.bp_builder.workflow;

import com.bpbuilder.bp_builder.canonical.ArtifactType;
import com.bpbuilder.bp_builder.canonical.MappingTransform;
import com.bpbuilder.bp_builder.workflow.definition.FieldMappingSpec;
import com.bpbuilder.bp_builder.workflow.definition.HttpEndpointSpec;
import com.bpbuilder.bp_builder.workflow.definition.OperationSpec;
import com.bpbuilder.bp_builder.workflow.definition.OutcomeRuleSpec;
import com.bpbuilder.bp_builder.workflow.definition.WorkflowStage;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Operation snapshots that mirror the reviewed rows seeded by migrations V3 and
 * V4 (UpBrains extractor and the JSON-to-UBL converter), plus a hypothetical
 * validator and sender for exercising the later stages. Keep the two mirrored
 * operations in step with the SQL when the reviewed mappings change.
 */
public final class WorkflowFixtures {

    public static final String UPBRAINS_RESPONSE = "workflow/upbrains-standard-invoice-response.json";
    public static final String VALID_UBL = "workflow/valid-ubl-invoice.xml";
    public static final String UPBRAINS_URL_ENV = "UPBRAINS_INVOICE_EXTRACT_URL";
    public static final String VALIDATION_RULE = "Au-Nz peppol-1.0.10";

    private static final String DOC = "$.extractor_result.documents[0]";

    private WorkflowFixtures() {
    }

    public static OperationSpec upbrainsExtractor() {
        List<FieldMappingSpec> out = new ArrayList<>();
        out.add(output("invoice.id", DOC + ".fields.InvoiceId.content", MappingTransform.DIRECT));
        out.add(output("invoice.issue_date", DOC + ".fields.InvoiceDate.content", MappingTransform.DATE_FORMAT,
                Map.of("input", "YYYY-MM-DD", "output", "YYYY-MM-DD"), 0));
        out.add(output("invoice.due_date", DOC + ".fields.DueDate.content", MappingTransform.DATE_FORMAT,
                Map.of("input", "YYYY-MM-DD", "output", "YYYY-MM-DD"), 0));
        out.add(output("invoice.currency_code", DOC + ".fields.InvoiceTotal.details.code", MappingTransform.DIRECT));
        out.add(output("seller.name", DOC + ".fields.VendorName.content", MappingTransform.DIRECT));
        out.add(output("seller.address.street", DOC + ".fields.VendorAddress.details.street_address", MappingTransform.DIRECT));
        out.add(output("seller.address.city", DOC + ".fields.VendorAddress.details.city", MappingTransform.FIRST_NON_NULL, Map.of(), 0));
        out.add(output("seller.address.city", DOC + ".fields.VendorAddress.details.suburb", MappingTransform.FIRST_NON_NULL, Map.of(), 1));
        out.add(output("seller.address.state", DOC + ".fields.VendorAddress.details.state", MappingTransform.DIRECT));
        out.add(output("seller.address.postal_code", DOC + ".fields.VendorAddress.details.postal_code", MappingTransform.DIRECT));
        out.add(output("seller.address.country_code", DOC + ".fields.VendorAddress.details.country_region", MappingTransform.COUNTRY_NAME_TO_ISO2));
        out.add(output("buyer.name", DOC + ".fields.CustomerName.content", MappingTransform.DIRECT));
        out.add(output("buyer.address.street", DOC + ".fields.BillingAddress.details.street_address", MappingTransform.DIRECT));
        out.add(output("buyer.address.city", DOC + ".fields.BillingAddress.details.city", MappingTransform.FIRST_NON_NULL, Map.of(), 0));
        out.add(output("buyer.address.city", DOC + ".fields.BillingAddress.details.suburb", MappingTransform.FIRST_NON_NULL, Map.of(), 1));
        out.add(output("buyer.address.state", DOC + ".fields.BillingAddress.details.state", MappingTransform.DIRECT));
        out.add(output("buyer.address.postal_code", DOC + ".fields.BillingAddress.details.postal_code", MappingTransform.DIRECT));
        out.add(output("buyer.address.country_code", DOC + ".fields.BillingAddress.details.country_region", MappingTransform.COUNTRY_NAME_TO_ISO2));
        out.add(output("totals.line_extension_amount", DOC + ".fields.SubTotal.details.amount", MappingTransform.DECIMAL));
        out.add(output("totals.tax_exclusive_amount", DOC + ".fields.SubTotal.details.amount", MappingTransform.DECIMAL));
        out.add(output("totals.tax_amount", DOC + ".fields.TotalTax.details.amount", MappingTransform.DECIMAL));
        out.add(output("totals.tax_inclusive_amount", DOC + ".fields.InvoiceTotal.details.amount", MappingTransform.DECIMAL));
        out.add(output("totals.payable_amount", DOC + ".fields.AmountDue.details.amount", MappingTransform.DECIMAL));
        out.add(output("lines[*].id", DOC + ".items[*]._index", MappingTransform.DIRECT));
        out.add(output("lines[*].description", DOC + ".items[*].Description.content", MappingTransform.DIRECT));
        out.add(output("lines[*].quantity", DOC + ".items[*].Quantity.content", MappingTransform.DECIMAL));
        out.add(output("lines[*].net_amount", DOC + ".items[*].Amount.details.amount", MappingTransform.DECIMAL));
        out.add(output("lines[*].unit_price", DOC + ".items[*].UnitPrice.details.amount", MappingTransform.DECIMAL));
        return new OperationSpec(1, "UpBrains Invoice Extractor / extractInvoiceFromPdf", WorkflowStage.TRANSFORMATION,
                ArtifactType.PDF_DOCUMENT, ArtifactType.CANONICAL_INVOICE, "VERIFIED", "HTTP",
                new HttpEndpointSpec("POST", "https://agents.upbrains.ai", "/v1/webhooks/configured-at-runtime/sync",
                        UPBRAINS_URL_ENV, "application/json", 60),
                "$.fileBase64", DOC, List.of(), out, null);
    }

    /** A second JSON-to-UBL converter whose request root is $.invoice. */
    public static OperationSpec jsonToUblConverter() {
        return converter(2, "Test UBL Converter / convertInvoice", "http://converter.test", "/convert",
                "RUNNABLE_XSD_INVALID");
    }

    public static OperationSpec converter(int id, String name, String baseUrl, String path, String status) {
        String root = "$.invoice";
        List<FieldMappingSpec> in = new ArrayList<>();
        in.add(input("invoice.id", root + ".id", true));
        in.add(input("invoice.issue_date", root + ".issueDate", true));
        in.add(input("invoice.due_date", root + ".dueDate", false));
        in.add(input("invoice.type_code", root + ".invoiceTypeCode", false));
        in.add(input("invoice.currency_code", root + ".currencyCode", true));
        in.add(input("seller.name", root + ".supplier.name", true));
        in.add(input("seller.tax_id", root + ".supplier.tax.companyID", false));
        in.add(input("seller.address.street", root + ".supplier.address.street", false));
        in.add(input("seller.address.city", root + ".supplier.address.city", false));
        in.add(input("seller.address.state", root + ".supplier.address.state", false));
        in.add(input("seller.address.postal_code", root + ".supplier.address.postalCode", false));
        in.add(input("seller.address.country_code", root + ".supplier.address.country", false));
        in.add(input("buyer.name", root + ".customer.name", true));
        in.add(input("buyer.tax_id", root + ".customer.tax.companyID", false));
        in.add(input("buyer.address.street", root + ".customer.address.street", false));
        in.add(input("buyer.address.city", root + ".customer.address.city", false));
        in.add(input("buyer.address.state", root + ".customer.address.state", false));
        in.add(input("buyer.address.postal_code", root + ".customer.address.postalCode", false));
        in.add(input("buyer.address.country_code", root + ".customer.address.country", false));
        in.add(input("totals.line_extension_amount", root + ".totals.lineExtensionAmount", true));
        in.add(input("totals.tax_exclusive_amount", root + ".totals.taxExclusiveAmount", false));
        in.add(input("totals.tax_inclusive_amount", root + ".totals.taxInclusiveAmount", true));
        in.add(input("totals.payable_amount", root + ".totals.payableAmount", true));
        in.add(input("totals.tax_amount", root + ".taxTotal.taxAmount", false));
        in.add(input("lines[*].id", root + ".lines[*].id", true));
        in.add(input("lines[*].description", root + ".lines[*].description", true));
        in.add(input("lines[*].quantity", root + ".lines[*].quantity", true));
        in.add(input("lines[*].unit_code", root + ".lines[*].unitCode", false));
        in.add(input("lines[*].net_amount", root + ".lines[*].amount", true));
        in.add(input("lines[*].unit_price", root + ".lines[*].unitPrice", true));
        in.add(input("lines[*].tax_category_code", root + ".lines[*].taxCategory.id", false));
        in.add(input("lines[*].tax_rate", root + ".lines[*].taxRate", false));
        in.add(input("lines[*].tax_scheme_code", root + ".lines[*].taxCategory.taxScheme.id", false));
        return new OperationSpec(id, name, WorkflowStage.TRANSFORMATION, ArtifactType.CANONICAL_INVOICE,
                ArtifactType.UBL_XML, status, "HTTP",
                new HttpEndpointSpec("POST", baseUrl, path, null, "application/json", 30),
                root, "$.xml", in, List.of(), null);
    }

    /** A hypothetical validator: JSON body with Base64 UBL plus the MD5 of that Base64 (ESS-like). */
    public static OperationSpec validator() {
        List<FieldMappingSpec> in = List.of(
                new FieldMappingSpec(null, "$.filename", MappingTransform.CONSTANT, Map.of("value", "invoice.xml"), true, 0),
                new FieldMappingSpec(null, "$.content", MappingTransform.ARTIFACT,
                        Map.of("artifact", "UBL_XML", "encodings", List.of("BASE64")), true, 0),
                new FieldMappingSpec(null, "$.checksum", MappingTransform.ARTIFACT,
                        Map.of("artifact", "UBL_XML", "encodings", List.of("BASE64", "MD5_HEX")), true, 0),
                new FieldMappingSpec(null, "query.rules", MappingTransform.CONSTANT,
                        Map.of("value", VALIDATION_RULE), true, 0));
        return new OperationSpec(3, "Test Validator / validate", WorkflowStage.VALIDATION, ArtifactType.UBL_XML,
                ArtifactType.VALIDATION_REPORT, "VERIFIED", "HTTP",
                new HttpEndpointSpec("POST", "http://validator.test", "/validate", null, "application/json", 30),
                null, "$", in, List.of(),
                new OutcomeRuleSpec("$.valid", List.of("true"), "$.errors[*]", "$.warnings[*]", null));
    }

    public static OperationSpec sender() {
        return new OperationSpec(4, "Test Sender / send", WorkflowStage.COMMUNICATION, ArtifactType.UBL_XML,
                ArtifactType.DELIVERY_RECEIPT, "VERIFIED", "HTTP",
                new HttpEndpointSpec("POST", "http://sender.test", "/send", null, "application/json", 30),
                "$.document", "$", List.of(), List.of(),
                new OutcomeRuleSpec("$.status", List.of("ACCEPTED"), "$.errors[*]", null, "$.messageId"));
    }

    public static Map<String, Object> upbrainsResponse() {
        try (InputStream stream = resource(UPBRAINS_RESPONSE)) {
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = new ObjectMapper().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                    .readValue(stream, Map.class);
            return parsed;
        } catch (IOException error) {
            throw new IllegalStateException(error);
        }
    }

    public static String text(String path) {
        try (InputStream stream = resource(path)) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException error) {
            throw new IllegalStateException(error);
        }
    }

    private static InputStream resource(String path) {
        InputStream stream = WorkflowFixtures.class.getClassLoader().getResourceAsStream(path);
        if (stream == null) throw new IllegalStateException("Missing test resource " + path);
        return stream;
    }

    private static FieldMappingSpec output(String canonical, String path, MappingTransform transform) {
        return output(canonical, path, transform, Map.of(), 0);
    }

    private static FieldMappingSpec output(String canonical, String path, MappingTransform transform,
                                           Map<String, Object> config, int priority) {
        return new FieldMappingSpec(canonical, path, transform, config, false, priority);
    }

    private static FieldMappingSpec input(String canonical, String path, boolean required) {
        return new FieldMappingSpec(canonical, path, MappingTransform.DIRECT, Map.of(), required, 0);
    }
}
