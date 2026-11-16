package com.bpbuilder.bp_builder.workflow.definition;

import java.util.List;

/** A selected chain; a validation step is identified by its own operation. */
public record WorkflowDefinition(
        List<OperationSpec> steps
) {
    public WorkflowDefinition {
        steps = steps == null ? List.of() : List.copyOf(steps);
    }
}
