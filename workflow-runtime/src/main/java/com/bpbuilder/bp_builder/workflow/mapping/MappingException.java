package com.bpbuilder.bp_builder.workflow.mapping;

/** A reviewed mapping could not be applied to the actual data; never silently ignored. */
public class MappingException extends RuntimeException {

    private final String field;

    public MappingException(String field, String message) {
        super(field == null ? message : field + ": " + message);
        this.field = field;
    }

    public String field() {
        return field;
    }
}
