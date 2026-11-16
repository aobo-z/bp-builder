package com.bpbuilder.bp_builder.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Locale;

/** Converts expected request failures into one frontend-facing JSON structure. */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> responseStatus(ResponseStatusException error, HttpServletRequest request) {
        String message = error.getReason() == null ? "Request failed" : error.getReason();
        return response(error.getStatusCode(), message, request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> validation(MethodArgumentNotValidException error, HttpServletRequest request) {
        String message = error.getBindingResult().getFieldErrors().stream().findFirst()
                .map(field -> field.getField() + ": " + field.getDefaultMessage())
                .orElse("Request validation failed");
        return response(HttpStatus.BAD_REQUEST, message, request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> unreadable(HttpMessageNotReadableException error, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "Request body is missing or malformed", request);
    }

    private static ResponseEntity<ApiError> response(HttpStatusCode status, String message,
                                                     HttpServletRequest request) {
        String code = HttpStatus.valueOf(status.value()).getReasonPhrase().toUpperCase(Locale.ROOT)
                .replace(' ', '_');
        return ResponseEntity.status(status).body(new ApiError(status.value(), code, message,
                request.getRequestURI(), Instant.now()));
    }
}
