package com.bpbuilder.bp_builder.canonical;

import java.util.Locale;

/** Logical payloads exchanged by reviewed invoice operations. */
public enum ArtifactType {
    PDF_DOCUMENT,
    IMAGE_DOCUMENT,
    CANONICAL_INVOICE,
    UBL_XML,
    VALIDATION_REPORT,
    DELIVERY_RECEIPT,
    API_REFERENCE,
    UNKNOWN;

    /** Whether the artifact is a file passed around as Base64 text. */
    public boolean isFile() {
        return this == PDF_DOCUMENT || this == IMAGE_DOCUMENT;
    }

    public static ArtifactType from(String raw) {
        if (raw == null || raw.isBlank()) return UNKNOWN;
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return UNKNOWN;
        }
    }
}
