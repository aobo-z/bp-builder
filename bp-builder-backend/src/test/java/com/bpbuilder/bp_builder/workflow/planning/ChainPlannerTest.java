package com.bpbuilder.bp_builder.workflow.planning;

import com.bpbuilder.bp_builder.canonical.ArtifactType;
import com.bpbuilder.bp_builder.workflow.WorkflowFixtures;
import com.bpbuilder.bp_builder.workflow.definition.OperationSpec;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChainPlannerTest {

    private final ChainPlanner planner = new ChainPlanner();
    private final OperationSpec extractor = WorkflowFixtures.upbrainsExtractor();
    private final OperationSpec converterA = WorkflowFixtures.jsonToUblConverter();
    private final OperationSpec converterB = WorkflowFixtures.converter(5, "Amit / convert", "http://amit.test",
            "/api/convert", "RUNNABLE_XSD_INVALID");
    private final OperationSpec validator = WorkflowFixtures.validator();
    private final OperationSpec sender = WorkflowFixtures.sender();
    private final List<OperationSpec> catalog = List.of(sender, validator, converterB, converterA, extractor);

    @Test
    void everyConverterGivesItsOwnChainFromPdfToUbl() {
        List<List<Integer>> chains = ids(planner.findChains(catalog, ArtifactType.PDF_DOCUMENT, ArtifactType.UBL_XML, 4));

        // Same length, so ordered by operation name: "Amit / convert" before "Test UBL Converter".
        assertEquals(List.of(List.of(1, 5), List.of(1, 2)), chains);
    }

    @Test
    void communicationIsOnlyReachedThroughValidationAndUsesTheEarlierUbl() {
        List<List<Integer>> chains = ids(planner.findChains(catalog, ArtifactType.PDF_DOCUMENT,
                ArtifactType.DELIVERY_RECEIPT, 4));

        assertEquals(List.of(List.of(1, 5, 3, 4), List.of(1, 2, 3, 4)), chains);
    }

    @Test
    void searchStopsAtTheStepLimit() {
        assertTrue(planner.findChains(catalog, ArtifactType.PDF_DOCUMENT, ArtifactType.DELIVERY_RECEIPT, 3).isEmpty());
    }

    @Test
    void userSelectedSequencesAreCheckedAgainstTheBusinessRules() {
        assertTrue(planner.sequenceIssues(List.of(extractor, converterA, validator, sender),
                ArtifactType.PDF_DOCUMENT).isEmpty());

        List<ChainIssue> skipsValidation = planner.sequenceIssues(List.of(extractor, converterA, sender),
                ArtifactType.PDF_DOCUMENT);
        assertEquals(1, skipsValidation.size());
        assertEquals(3, skipsValidation.get(0).step());
        assertTrue(skipsValidation.get(0).message().contains("validation step must come first"));

        List<ChainIssue> missingConversion = planner.sequenceIssues(List.of(extractor, validator),
                ArtifactType.PDF_DOCUMENT);
        assertTrue(missingConversion.get(0).message().contains("needs UBL_XML"));

        List<ChainIssue> twoConverters = planner.sequenceIssues(List.of(extractor, converterA, converterB),
                ArtifactType.PDF_DOCUMENT);
        assertTrue(twoConverters.get(0).message().contains("would produce UBL_XML again"));
    }

    private static List<List<Integer>> ids(List<List<OperationSpec>> chains) {
        return chains.stream().map(chain -> chain.stream().map(OperationSpec::operationId).toList()).toList();
    }
}
