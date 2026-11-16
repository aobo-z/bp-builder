package com.bpbuilder.bp_builder.service.deploy;

import java.util.Map;

/** Where deployed workflows live; the AWS implementation is AwsLambdaGateway. */
public interface LambdaGateway {

    /** A deployed function; url is set only for a function with a public function URL. */
    record DeployedFunction(String arn, String url) {
    }

    /**
     * Creates the function, or brings an existing one up to date. Code and
     * configuration that already match are left alone, so a component shared
     * by several workflows is only changed when its operation changed.
     */
    DeployedFunction deployFunction(String name, byte[] code, Map<String, String> environment, int timeoutSeconds,
                                    boolean publicUrl);

    /** Creates or updates a Standard state machine and returns its ARN. */
    String deployStateMachine(String name, String definition);

    /** Deletes the function; one that no longer exists is not an error. */
    void deleteFunction(String name);

    /** Deletes the state machine; one that no longer exists is not an error. */
    void deleteStateMachine(String name);

    String region();
}
