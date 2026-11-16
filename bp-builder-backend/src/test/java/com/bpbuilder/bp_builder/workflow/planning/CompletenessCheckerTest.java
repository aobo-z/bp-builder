package com.bpbuilder.bp_builder.workflow.planning;

import com.bpbuilder.bp_builder.canonical.ArtifactType;
import com.bpbuilder.bp_builder.canonical.MappingTransform;
import com.bpbuilder.bp_builder.workflow.WorkflowFixtures;
import com.bpbuilder.bp_builder.workflow.definition.FieldMappingSpec;
import com.bpbuilder.bp_builder.workflow.definition.OperationSpec;
import com.bpbuilder.bp_builder.workflow.definition.OutcomeRuleSpec;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompletenessCheckerTest {

    private final CompletenessChecker checker = new CompletenessChecker(new ChainPlanner());
    private final List<OperationSpec> pdfToUbl = List.of(WorkflowFixtures.upbrainsExtractor(),
            WorkflowFixtures.jsonToUblConverter());

    @Test
    void reviewedOperationMappingsAreEnoughToAssessTheChain() {
        ChainAssessment assessment = checker.assess(pdfToUbl, ArtifactType.PDF_DOCUMENT);

        assertEquals(ChainAssessment.Status.READY, assessment.status());
        assertTrue(assessment.warnings().stream().anyMatch(warning -> warning.contains("fails the UBL 2.1 XSD")));
    }

    @Test
    void aRequiredFieldNoEarlierStepProducesMeansTheOperationsCannotBeConnected() {
        OperationSpec requiringTaxId = requiringTaxId();

        ChainAssessment assessment = checker.assess(List.of(WorkflowFixtures.upbrainsExtractor(), requiringTaxId),
                ArtifactType.PDF_DOCUMENT);

        assertEquals(ChainAssessment.Status.NOT_SUPPORTED, assessment.status());
        ChainIssue issue = assessment.issues().get(0);
        assertEquals(ChainIssue.Type.FIELD_NOT_PRODUCED, issue.type());
        assertEquals(2, issue.step());
        assertEquals("seller.tax_id", issue.field());
    }

    @Test
    void aCanonicalInvoiceInputSuppliesTheFieldsTheFirstOperationRequires() {
        ChainAssessment assessment = checker.assess(List.of(requiringTaxId(), WorkflowFixtures.validator()),
                ArtifactType.CANONICAL_INVOICE);

        assertEquals(ChainAssessment.Status.READY, assessment.status(), assessment.issues().toString());
    }

    @Test
    void aValidatorNeedsAReviewedOutcomeRule() {
        OperationSpec validator = WorkflowFixtures.validator();
        OperationSpec withoutOutcome = copy(validator, validator.inputMappings(), null);

        ChainAssessment assessment = checker.assess(List.of(WorkflowFixtures.upbrainsExtractor(),
                WorkflowFixtures.jsonToUblConverter(), withoutOutcome), ArtifactType.PDF_DOCUMENT);

        assertEquals(ChainAssessment.Status.NOT_SUPPORTED, assessment.status());
        assertTrue(assessment.issues().stream().anyMatch(issue -> issue.message().contains("no reviewed outcome rule")));
    }

    private static OperationSpec requiringTaxId() {
        OperationSpec converter = WorkflowFixtures.jsonToUblConverter();
        List<FieldMappingSpec> inputs = new ArrayList<>(converter.inputMappings());
        inputs.add(new FieldMappingSpec("seller.tax_id", "$.invoice.supplier.tax.companyID",
                MappingTransform.DIRECT, Map.of(), true, 0));
        return copy(converter, inputs, converter.outcomeRule());
    }

    private static OperationSpec copy(OperationSpec source, List<FieldMappingSpec> inputs,
                                      OutcomeRuleSpec outcomeRule) {
        return new OperationSpec(source.operationId(), source.name(), source.stage(), source.inputArtifact(),
                source.outputArtifact(), source.verificationStatus(), source.executionType(), source.endpoint(),
                source.inputArtifactPath(), source.outputArtifactPath(), inputs, source.outputMappings(),
                outcomeRule);
    }
}
