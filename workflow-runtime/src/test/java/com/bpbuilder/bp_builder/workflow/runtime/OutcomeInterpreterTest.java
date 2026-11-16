package com.bpbuilder.bp_builder.workflow.runtime;

import com.bpbuilder.bp_builder.workflow.definition.OutcomeRuleSpec;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OutcomeInterpreterTest {

    private final OutcomeInterpreter interpreter = new OutcomeInterpreter();

    /** The same field carries the success text and the error text, as Mailgun's $.message does. */
    private final OutcomeRuleSpec sharedMessageRule = new OutcomeRuleSpec("$.message",
            List.of("Queued. Thank you."), "$.message", null, "$.id");

    @Test
    void anAcceptedCommunicationReportsNoErrorsEvenWhenTheErrorPathIsTheSuccessField() {
        CommunicationOutcome outcome = interpreter.communication(sharedMessageRule,
                Map.of("id", "<msg-1@example.test>", "message", "Queued. Thank you."), 4);

        assertEquals(CommunicationOutcome.Status.ACCEPTED, outcome.status());
        assertEquals("<msg-1@example.test>", outcome.reference());
        assertTrue(outcome.errors().isEmpty());
    }

    @Test
    void aRejectedCommunicationReportsTheServiceMessageAsTheError() {
        CommunicationOutcome outcome = interpreter.communication(sharedMessageRule, Map.of("message", "Forbidden"), 4);

        assertEquals(CommunicationOutcome.Status.REJECTED, outcome.status());
        assertEquals(List.of("Forbidden"), outcome.errors());
    }
}
