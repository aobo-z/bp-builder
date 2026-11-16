package com.bpbuilder.bp_builder.service.interpret;

/** The language model could not be reached or did not return a usable answer. */
public class InterpretationUnavailableException extends RuntimeException {

    public InterpretationUnavailableException(String message) {
        super(message);
    }

    public InterpretationUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
