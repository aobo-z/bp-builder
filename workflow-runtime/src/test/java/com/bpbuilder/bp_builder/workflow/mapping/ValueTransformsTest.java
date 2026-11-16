package com.bpbuilder.bp_builder.workflow.mapping;

import com.bpbuilder.bp_builder.canonical.MappingTransform;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ValueTransformsTest {

    @Test
    void decimalAcceptsNumbersAndPlainNumericText() {
        assertEquals(new BigDecimal("40"), ValueTransforms.apply(MappingTransform.DECIMAL, 40, Map.of(), "f"));
        assertEquals(new BigDecimal("15.00"), ValueTransforms.apply(MappingTransform.DECIMAL, " 15.00 ", Map.of(), "f"));
    }

    @Test
    void decimalRejectsTextWithCurrencyInsteadOfGuessing() {
        MappingException error = assertThrows(MappingException.class,
                () -> ValueTransforms.apply(MappingTransform.DECIMAL, "44.00 AUD", Map.of(), "totals.payable_amount"));
        assertEquals("totals.payable_amount", error.field());
    }

    @Test
    void dateFormatReallyConvertsAndRejectsImpossibleDates() {
        Map<String, Object> config = Map.of("input", "DD/MM/YYYY", "output", "YYYY-MM-DD");
        assertEquals("2026-10-02", ValueTransforms.apply(MappingTransform.DATE_FORMAT, "02/10/2026", config, "d"));
        assertThrows(MappingException.class, () -> ValueTransforms.apply(MappingTransform.DATE_FORMAT, "2026-13-01",
                Map.of("input", "YYYY-MM-DD", "output", "YYYY-MM-DD"), "d"));
    }

    @Test
    void countryAcceptsEnglishNamesAndCodesAndRejectsAnythingElse() {
        assertEquals("AU", ValueTransforms.apply(MappingTransform.COUNTRY_NAME_TO_ISO2, "Australia", Map.of(), "c"));
        assertEquals("NZ", ValueTransforms.apply(MappingTransform.COUNTRY_NAME_TO_ISO2, "new zealand", Map.of(), "c"));
        assertEquals("DE", ValueTransforms.apply(MappingTransform.COUNTRY_NAME_TO_ISO2, "Germany", Map.of(), "c"));
        assertEquals("AU", ValueTransforms.apply(MappingTransform.COUNTRY_NAME_TO_ISO2, "au", Map.of(), "c"));
        assertThrows(MappingException.class,
                () -> ValueTransforms.apply(MappingTransform.COUNTRY_NAME_TO_ISO2, "Narnia", Map.of(), "c"));
    }

    @Test
    void artifactEncodingsApplyInOrder() throws Exception {
        String xml = "<Invoice/>";
        String base64 = Base64.getEncoder().encodeToString(xml.getBytes(StandardCharsets.UTF_8));
        String md5OfBase64 = HexFormat.of().formatHex(
                MessageDigest.getInstance("MD5").digest(base64.getBytes(StandardCharsets.UTF_8)));

        assertEquals(base64, ValueTransforms.apply(MappingTransform.ARTIFACT, xml,
                Map.of("encodings", List.of("BASE64")), "content"));
        assertEquals(md5OfBase64, ValueTransforms.apply(MappingTransform.ARTIFACT, xml,
                Map.of("encodings", List.of("BASE64", "MD5_HEX")), "checksum"));
        assertThrows(MappingException.class, () -> ValueTransforms.apply(MappingTransform.ARTIFACT, xml,
                Map.of("encodings", List.of("ROT13")), "content"));
    }

    @Test
    void removeWhitespaceStripsTheSpacesOfAPrintedAbn() {
        assertEquals("76200300401", ValueTransforms.apply(MappingTransform.REMOVE_WHITESPACE, " 76 200\t300 401 ",
                Map.of(), "seller.tax_id"));
    }

    @Test
    void listsAreTransformedElementWiseKeepingGaps() {
        Object result = ValueTransforms.apply(MappingTransform.DECIMAL, Arrays.asList("2", null, 5), Map.of(), "q");
        assertEquals(Arrays.asList(new BigDecimal("2"), null, new BigDecimal("5")), result);
        assertTrue(ValueTransforms.apply(MappingTransform.DIRECT, null, Map.of(), "x") == null);
    }
}
