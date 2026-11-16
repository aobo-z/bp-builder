package com.bpbuilder.lambda_runner;

import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sfn.SfnClient;
import software.amazon.awssdk.services.sfn.model.DescribeExecutionResponse;
import software.amazon.awssdk.services.sfn.model.ExecutionStatus;

import java.time.Duration;

/**
 * Starts a Standard state machine execution and polls it until it ends. The
 * function's own role must allow states:StartExecution and DescribeExecution.
 */
final class SfnStateMachineRunner implements StateMachineRunner {

    private static final Duration POLL = Duration.ofSeconds(1);
    private static final Duration LIMIT = Duration.ofSeconds(280);

    private final SfnClient sfn = SfnClient.builder()
            .httpClient(UrlConnectionHttpClient.create())
            .region(Region.of(System.getenv("AWS_REGION")))
            .build();

    @Override
    public String run(String stateMachineArn, String input) {
        String execution = sfn.startExecution(request -> request.stateMachineArn(stateMachineArn).input(input))
                .executionArn();
        long deadline = System.nanoTime() + LIMIT.toNanos();
        while (System.nanoTime() < deadline) {
            DescribeExecutionResponse state = sfn.describeExecution(request -> request.executionArn(execution));
            if (state.status() == ExecutionStatus.SUCCEEDED) return state.output();
            if (state.status() != ExecutionStatus.RUNNING) {
                throw new IllegalStateException("The workflow execution ended " + state.status()
                        + (state.error() == null ? "" : ": " + state.error())
                        + (state.cause() == null ? "" : " (" + state.cause() + ")"));
            }
            try {
                Thread.sleep(POLL.toMillis());
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for the workflow execution");
            }
        }
        throw new IllegalStateException("The workflow execution did not finish within " + LIMIT.toSeconds() + " seconds");
    }
}
