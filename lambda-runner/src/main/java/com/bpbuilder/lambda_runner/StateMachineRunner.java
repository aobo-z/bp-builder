package com.bpbuilder.lambda_runner;

/** Runs a Step Functions state machine to completion; the AWS implementation is SfnStateMachineRunner. */
public interface StateMachineRunner {

    /**
     * Starts an execution with this JSON input and returns its JSON output once
     * it has succeeded; any other ending is an IllegalStateException.
     */
    String run(String stateMachineArn, String input);
}
