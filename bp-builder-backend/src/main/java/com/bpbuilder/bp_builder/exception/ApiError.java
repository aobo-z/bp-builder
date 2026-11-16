package com.bpbuilder.bp_builder.exception;

import java.time.Instant;

/** Stable error shape that the frontend can display without parsing server logs. */
public record ApiError(
        int status,
        String code,
        String message,
        String path,
        Instant timestamp
) {
}
