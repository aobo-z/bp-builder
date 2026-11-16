package com.bpbuilder.bp_builder.canonical;

import java.util.Locale;

public enum MappingTransform {
    DIRECT,
    CONSTANT,
    FIRST_NON_NULL,
    DATE_FORMAT,
    DECIMAL,
    COUNTRY_NAME_TO_ISO2,
    ARRAY_ITEM,
    /** Removes all whitespace, e.g. an ABN printed as "76 200 300 401" becomes "76200300401". */
    REMOVE_WHITESPACE,
    /** Input-only: reads a non-secret runtime value from an environment variable. */
    ENVIRONMENT,
    /**
     * Input-only: copies a workflow artifact (transformConfig.artifact, e.g.
     * UBL_XML) into the request, optionally applying transformConfig.encodings
     * in order (BASE64, MD5_HEX).
     */
    ARTIFACT;

    public static MappingTransform from(String raw) {
        if (raw == null || raw.isBlank()) return DIRECT;
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return DIRECT;
        }
    }
}
