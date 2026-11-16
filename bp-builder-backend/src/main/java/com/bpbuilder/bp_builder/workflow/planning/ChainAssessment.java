package com.bpbuilder.bp_builder.workflow.planning;

import java.util.List;

/**
 * READY: the operations connect and every step can be executed. NOT_SUPPORTED:
 * the chain cannot run as built; the issues say why. Whether a particular input
 * document contains the data is only known when the workflow runs.
 */
public record ChainAssessment(Status status, List<ChainIssue> issues, List<String> warnings) {

    public enum Status {
        READY,
        NOT_SUPPORTED
    }

    public ChainAssessment {
        issues = List.copyOf(issues);
        warnings = List.copyOf(warnings);
    }

    static ChainAssessment of(List<ChainIssue> issues, List<String> warnings) {
        return new ChainAssessment(issues.isEmpty() ? Status.READY : Status.NOT_SUPPORTED, issues, warnings);
    }
}
