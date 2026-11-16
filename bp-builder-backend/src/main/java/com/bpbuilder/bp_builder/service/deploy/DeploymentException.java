package com.bpbuilder.bp_builder.service.deploy;

/**
 * A deployment that could not be carried out. unavailable means the backend is
 * not configured for it (no AWS settings, no runner jar, a missing secret);
 * otherwise AWS refused or failed the request.
 */
public class DeploymentException extends RuntimeException {

    private final boolean unavailable;

    private DeploymentException(String message, boolean unavailable, Throwable cause) {
        super(message, cause);
        this.unavailable = unavailable;
    }

    public static DeploymentException unavailable(String message) {
        return new DeploymentException(message, true, null);
    }

    public static DeploymentException failed(String message, Throwable cause) {
        return new DeploymentException(message, false, cause);
    }

    public boolean unavailable() {
        return unavailable;
    }
}
