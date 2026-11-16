package com.bpbuilder.bp_builder.workflow.planning;

/** One reason a chain cannot run as it stands. step is 1-based, or null for the whole chain. */
public record ChainIssue(Type type, Integer step, String field, String message) {

    public enum Type {
        /**
         * An operation requires a canonical field that no earlier step produces, so the two operations
         * cannot be connected. (A canonical invoice input supplies every field, so it never causes this.)
         */
        FIELD_NOT_PRODUCED,
        /** The operation cannot be executed by this workflow engine as reviewed. */
        UNSUPPORTED_OPERATION,
        /** The steps do not form a valid chain (artifacts, stage order, business rules). */
        INVALID_SEQUENCE
    }
}
