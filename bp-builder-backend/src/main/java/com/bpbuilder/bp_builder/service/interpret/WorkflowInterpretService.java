package com.bpbuilder.bp_builder.service.interpret;

import com.bpbuilder.bp_builder.canonical.ArtifactType;
import com.bpbuilder.bp_builder.dto.OperationCatalogItem;
import com.bpbuilder.bp_builder.dto.SketchDescription;
import com.bpbuilder.bp_builder.dto.WorkflowInterpretation;
import com.bpbuilder.bp_builder.service.workflow.OperationCatalogService;
import com.bpbuilder.bp_builder.workflow.definition.WorkflowStage;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Turns a free-text requirement into the input, goal and optional validator
 * of a plan request. The model only fills a fixed schema; every value is
 * checked here, and choosing operations is left to the planner.
 */
@Service
public class WorkflowInterpretService {

    private static final String UNCLEAR = "UNCLEAR";
    private static final String NONE = "NONE";
    private static final List<ArtifactType> INPUTS = List.of(ArtifactType.PDF_DOCUMENT,
            ArtifactType.IMAGE_DOCUMENT, ArtifactType.CANONICAL_INVOICE, ArtifactType.UBL_XML);
    private static final List<ArtifactType> GOALS =
            List.of(ArtifactType.CANONICAL_INVOICE, ArtifactType.UBL_XML, ArtifactType.VALIDATION_REPORT);
    /** The order artifacts appear in along a workflow. */
    private static final List<ArtifactType> ORDER = List.of(ArtifactType.PDF_DOCUMENT,
            ArtifactType.IMAGE_DOCUMENT, ArtifactType.CANONICAL_INVOICE, ArtifactType.UBL_XML, ArtifactType.VALIDATION_REPORT,
            ArtifactType.DELIVERY_RECEIPT);

    private final GeminiClient gemini;
    private final OperationCatalogService catalog;

    public WorkflowInterpretService(GeminiClient gemini, OperationCatalogService catalog) {
        this.gemini = gemini;
        this.catalog = catalog;
    }

    public WorkflowInterpretation interpret(String text) {
        Map<String, OperationCatalogItem> validators = catalog.items().stream()
                .filter(item -> item.stage() == WorkflowStage.VALIDATION && item.chainable())
                .collect(Collectors.toMap(item -> String.valueOf(item.id()), item -> item,
                        (first, second) -> first, LinkedHashMap::new));
        Map<String, Object> answer = gemini.generateJson(instruction(validators.values()), text,
                schema(validators.keySet()));

        List<String> questions = new ArrayList<>(strings(answer.get("questions")));

        ArtifactType input = choice(answer.get("inputArtifact"), INPUTS);
        if (input == null) questions.add("What does the workflow start from: a PDF invoice, an invoice image, "
                + "invoice data (JSON), or an existing UBL XML invoice?");

        ArtifactType goal = choice(answer.get("goalArtifact"), GOALS);
        String deliveryPhrase = null;
        if (Boolean.TRUE.equals(answer.get("deliveryRequested"))) {
            String phrase = answer.get("deliveryPhrase") instanceof String value ? value.trim() : "";
            if (!phrase.isEmpty() && text.toLowerCase(Locale.ROOT).contains(phrase.toLowerCase(Locale.ROOT))) {
                deliveryPhrase = phrase;
                goal = ArtifactType.DELIVERY_RECEIPT;
            }
        }
        if (goal == null) questions.add("Should the workflow stop after extracting the invoice data, after "
                + "converting it to UBL XML, or after validating it?");
        if (input != null && goal != null && ORDER.indexOf(goal) <= ORDER.indexOf(input)) {
            questions.add("The requested result (" + goal + ") does not come after the input (" + input
                    + "); please check the requirement.");
            goal = null;
        }

        OperationCatalogItem validator = null;
        boolean validates = goal == ArtifactType.VALIDATION_REPORT || goal == ArtifactType.DELIVERY_RECEIPT;
        if (validates) {
            Object chosen = answer.get("validatorOperationId");
            validator = chosen == null ? null : validators.get(String.valueOf(chosen));
        }
        return new WorkflowInterpretation(input == null ? null : input.name(), goal == null ? null : goal.name(),
                validator == null ? null : validator.id(), validator == null ? null : validator.name(),
                deliveryPhrase, questions, gemini.model());
    }

    /**
     * Reads a hand-drawn or photographed sketch and turns it into the same shape
     * of plain-English description a typed requirement would be, so the user can
     * correct it before it goes through {@link #interpret(String)} unchanged.
     * This never picks artifacts or operations itself — same division as the BPMN
     * upload path, just with a vision call standing in for the file's own structure.
     */
    public SketchDescription describeSketch(byte[] imageBytes, String mimeType) {
        Map<String, Object> schema = Map.of("type", "OBJECT",
                "properties", Map.of("description", Map.of("type", "STRING")),
                "required", List.of("description"), "propertyOrdering", List.of("description"));
        Map<String, Object> answer = gemini.generateJsonWithImage(sketchInstruction(), imageBytes, mimeType, schema);
        Object value = answer.get("description");
        String description = value instanceof String text ? text.trim() : "";
        if (description.isEmpty()) {
            throw new InterpretationUnavailableException("Gemini could not read a workflow out of this image.");
        }
        return new SketchDescription(description);
    }

    private static String sketchInstruction() {
        return """
                You read a hand-drawn or photographed sketch of an e-invoicing workflow and
                describe it in English, so a later step can turn that description into
                structured fields. You do not choose artifacts, standards or operations
                yourself.

                Describe only what is actually drawn or written: the boxes/steps in the
                order the arrows connect them, and any labels naming a document type, a
                validation standard, or a send/email action. Do not invent a step that is
                not shown, and do not guess intent beyond what is visibly drawn.

                Write it as one short sentence per step, joined with ", then ", for example
                "Extract the PDF invoice, then convert it to UBL XML, then validate it,
                then send it." If the sketch has no clear steps or arrows, describe as
                plainly as possible what the image does show.

                description: your one- or two-sentence description, in English.
                """;
    }

    private static String instruction(Iterable<OperationCatalogItem> validators) {
        StringBuilder list = new StringBuilder();
        for (OperationCatalogItem validator : validators) {
            list.append("- ").append(validator.id()).append(": ").append(validator.name());
            if (validator.description() != null) list.append(" (").append(validator.description()).append(')');
            list.append('\n');
        }
        return """
                You turn an e-invoicing workflow requirement into structured fields for BP Builder.
                A workflow runs in this fixed order: transformation (PDF or image invoice -> invoice data ->
                UBL XML), then validation (UBL XML -> validation report), then communication (send the
                validated UBL invoice). Not every stage is needed.

                Fill the fields as follows:
                - inputArtifact: what the workflow starts from. PDF_DOCUMENT for a PDF or scanned PDF invoice,
                  IMAGE_DOCUMENT for a photo or image of an invoice (PNG, JPG), CANONICAL_INVOICE for invoice
                  data such as JSON fields, UBL_XML for an existing UBL or XML e-invoice. UNCLEAR if the
                  requirement does not say.
                - goalArtifact: the last result before any sending. CANONICAL_INVOICE if the invoice data is
                  only extracted, UBL_XML if it is converted without validation, VALIDATION_REPORT if it is
                  validated. Sending always requires validation first, so use VALIDATION_REPORT when sending
                  is requested. UNCLEAR if the requirement does not say.
                - deliveryRequested: true only when the requirement explicitly asks to send, email, deliver
                  or transmit the invoice. Never infer it. deliveryPhrase: copy the exact words of the
                  requirement that ask for it, or an empty string.
                - validatorOperationId: the id of the validator below whose standard the requirement names,
                  or NONE when it names no standard or none of these matches:
                %s- questions: short questions about other details the user must still clarify. Do not ask
                  about the input or the result; mark those UNCLEAR instead. Do not ask whether the user also
                  wants stages they did not mention, and do not ask about endpoints, credentials, email
                  addresses or message content: those are fixed by the reviewed operations.
                Write the questions in English.
                """.formatted(list);
    }

    private static Map<String, Object> schema(Iterable<String> validatorIds) {
        List<String> validatorChoices = new ArrayList<>();
        validatorIds.forEach(validatorChoices::add);
        validatorChoices.add(NONE);
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("inputArtifact", enumeration(INPUTS));
        properties.put("goalArtifact", enumeration(GOALS));
        properties.put("deliveryRequested", Map.of("type", "BOOLEAN"));
        properties.put("deliveryPhrase", Map.of("type", "STRING"));
        properties.put("validatorOperationId", Map.of("type", "STRING", "enum", validatorChoices));
        properties.put("questions", Map.of("type", "ARRAY", "items", Map.of("type", "STRING")));
        return Map.of("type", "OBJECT", "properties", properties,
                "required", List.copyOf(properties.keySet()),
                "propertyOrdering", List.copyOf(properties.keySet()));
    }

    private static Map<String, Object> enumeration(List<ArtifactType> types) {
        List<String> values = new ArrayList<>(types.stream().map(Enum::name).toList());
        values.add(UNCLEAR);
        return Map.of("type", "STRING", "enum", values);
    }

    private static ArtifactType choice(Object value, List<ArtifactType> allowed) {
        if (!(value instanceof String text)) return null;
        ArtifactType type = ArtifactType.from(text);
        return allowed.contains(type) ? type : null;
    }

    private static List<String> strings(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        return list.stream().filter(String.class::isInstance).map(String.class::cast)
                .map(String::trim).filter(item -> !item.isEmpty()).toList();
    }
}
