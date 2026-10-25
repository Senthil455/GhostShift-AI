package ai.ghostshift.workflow;

import ai.ghostshift.domain.WorkflowDefinition;
import ai.ghostshift.domain.WorkflowInput;
import ai.ghostshift.domain.WorkflowStep;
import ai.ghostshift.domain.WorkflowValidation;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Singleton
public class WorkflowValidator {
    private final ActionRegistry registry;

    public WorkflowValidator(ActionRegistry registry) {
        this.registry = registry;
    }

    public WorkflowValidation validate(WorkflowDefinition definition) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        Set<String> permissions = new LinkedHashSet<>();
        List<String> risks = new ArrayList<>();

        if (definition == null) {
            errors.add("A workflow definition is required.");
            return new WorkflowValidation(false, errors, warnings, List.of(), List.of());
        }
        if (definition.schemaVersion() != 1) {
            errors.add("schemaVersion must be 1.");
        }
        if (definition.name() == null || definition.name().isBlank()) {
            errors.add("The workflow needs a name.");
        } else if (definition.name().length() > 120) {
            errors.add("The workflow name must be 120 characters or fewer.");
        }
        if (definition.description() != null && definition.description().length() > 2000) {
            errors.add("The description must be 2000 characters or fewer.");
        }
        List<WorkflowStep> steps = definition.steps() == null ? List.of() : definition.steps();
        if (steps.isEmpty()) {
            errors.add("Add at least one workflow step.");
        }
        if (steps.size() > 40) {
            errors.add("A workflow may contain at most 40 steps.");
        }
        Set<String> ids = new LinkedHashSet<>();
        Set<String> enabledTypes = new LinkedHashSet<>();
        boolean listed = false;
        boolean proposed = false;
        boolean previewed = false;
        boolean hasApprovalStep = false;
        boolean hasMove = false;
        int index = 0;
        for (WorkflowStep step : steps) {
            index++;
            if (step == null) {
                errors.add("Step " + index + " is empty.");
                continue;
            }
            String type = step.type() == null ? "" : step.type().toUpperCase(Locale.ROOT);
            String id = step.id() == null ? "" : step.id();
            if (id.isBlank() || !id.matches("[A-Za-z0-9._-]{1,64}")) {
                errors.add("Step " + index + " needs an id using letters, numbers, dot, dash, or underscore.");
            } else if (!ids.add(id)) {
                errors.add("Duplicate step id: " + id);
            }
            if (!registry.exists(type)) {
                errors.add("Step " + index + " uses unknown action type '" + step.type() + "'. Allowed: "
                        + String.join(", ", registry.allowedTypes()) + ".");
                continue;
            }
            ActionRegistry.ActionDescriptor descriptor = registry.require(type);
            permissions.add(descriptor.permission());
            risks.add(descriptor.type() + " (" + descriptor.risk().name().toLowerCase(Locale.ROOT) + ")");
            for (String error : registry.validateParameters(type, step.parameters())) {
                errors.add("Step " + index + " (" + type + "): " + error);
            }
            if (step.description() == null || step.description().isBlank()) {
                warnings.add("Step " + index + " has no plain-language description. The UI will use the action label.");
            }
            if (!step.enabled()) {
                continue;
            }
            enabledTypes.add(type);
            switch (type) {
                case "LIST_FILES" -> listed = true;
                case "CLASSIFY_FILES", "PROPOSE_DESTINATION", "PREVIEW_FILE_MOVE", "MOVE_FILE" -> {
                    if (!listed) {
                        errors.add("Step " + index + " (" + type + ") must come after LIST_FILES.");
                    }
                    if (type.equals("PROPOSE_DESTINATION")) {
                        proposed = true;
                    }
                    if (type.equals("PREVIEW_FILE_MOVE")) {
                        if (!proposed) {
                            errors.add("PREVIEW_FILE_MOVE must come after PROPOSE_DESTINATION.");
                        }
                        previewed = true;
                    }
                    if (type.equals("MOVE_FILE")) {
                        hasMove = true;
                        if (!proposed) {
                            errors.add("MOVE_FILE must come after PROPOSE_DESTINATION.");
                        }
                        if (!previewed) {
                            errors.add("MOVE_FILE must come after PREVIEW_FILE_MOVE.");
                        }
                    }
                }
                case "REQUEST_USER_APPROVAL" -> hasApprovalStep = true;
                case "ANALYZE_LOG" -> {
                    if (!enabledTypes.contains("READ_APPROVED_TEXT")) {
                        errors.add("ANALYZE_LOG must come after READ_APPROVED_TEXT.");
                    }
                }
                default -> {
                }
            }
        }
        if (hasMove && !hasApprovalStep && !definition.requiresApproval()) {
            warnings.add("File moves will still be gated by the backend, but adding REQUEST_USER_APPROVAL makes the pause visible in the workflow.");
        }
        if (hasMove && !steps.stream().anyMatch(step -> step.enabled()
                && step.type() != null && step.type().equalsIgnoreCase("REQUEST_USER_APPROVAL"))) {
            warnings.add("The workflow moves files without an explicit approval step. GhostShift will insert a backend approval gate before the move.");
        }
        String failurePolicy = definition.failurePolicy() == null ? "STOP" : definition.failurePolicy().toUpperCase(Locale.ROOT);
        if (!failurePolicy.equals("STOP") && !failurePolicy.equals("CONTINUE_SAFE")) {
            errors.add("failurePolicy must be STOP or CONTINUE_SAFE.");
        }
        List<WorkflowInput> inputs = definition.inputs() == null ? List.of() : definition.inputs();
        Set<String> inputNames = new LinkedHashSet<>();
        for (WorkflowInput input : inputs) {
            if (input == null || input.name() == null || !input.name().matches("[A-Za-z][A-Za-z0-9_]{0,40}")) {
                errors.add("Every workflow input needs a simple name.");
                continue;
            }
            if (!inputNames.add(input.name())) {
                errors.add("Duplicate workflow input: " + input.name());
            }
            if (input.type() == null || !Set.of("DIRECTORY", "TEXT", "NUMBER", "BOOLEAN").contains(input.type())) {
                errors.add("Input '" + input.name() + "' must have type DIRECTORY, TEXT, NUMBER, or BOOLEAN.");
            }
        }
        boolean needsDirectory = enabledTypes.stream().anyMatch(type -> Set.of(
                "LIST_FILES", "READ_APPROVED_TEXT", "ANALYZE_LOG", "MOVE_FILE").contains(type));
        if (needsDirectory && !inputNames.contains("directoryId")) {
            errors.add("This workflow needs an input named directoryId with type DIRECTORY.");
        }
        if (inputs.stream().filter(input -> input.name().equals("directoryId")).findFirst()
                .map(input -> !"DIRECTORY".equals(input.type())).orElse(false)) {
            errors.add("The directoryId input must have type DIRECTORY.");
        }
        if (enabledTypes.contains("MOVE_FILE") && !enabledTypes.contains("CLASSIFY_FILES")) {
            warnings.add("MOVE_FILE has no CLASSIFY_FILES step; destinations may be based on default categories.");
        }
        if (enabledTypes.contains("CLASSIFY_FILES")) {
            steps.stream().filter(step -> step.enabled() && step.type().equalsIgnoreCase("CLASSIFY_FILES")).findFirst()
                    .ifPresent(step -> {
                        Object useAi = step.parameters() == null ? null : step.parameters().get("useAi");
                        if (!Boolean.FALSE.equals(useAi)) {
                            warnings.add("Classification may call the configured local model. Only file names and extensions are sent.");
                        }
                    });
        }
        return new WorkflowValidation(errors.isEmpty(), errors, warnings,
                new ArrayList<>(permissions), risks);
    }
}
