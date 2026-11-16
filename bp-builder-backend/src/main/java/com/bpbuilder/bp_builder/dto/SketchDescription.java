package com.bpbuilder.bp_builder.dto;

/**
 * A hand-drawn or photographed workflow sketch, read back as one plain-English
 * description — the same shape of text a typed or dictated requirement would
 * be, so it goes through the same interpretation step as any other input
 * method, with the user able to correct it first.
 */
public record SketchDescription(
        String description
) {
}
