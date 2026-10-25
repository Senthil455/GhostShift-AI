package ai.ghostshift.workflow;

import ai.ghostshift.domain.Category;
import ai.ghostshift.domain.WorkflowDefinition;
import ai.ghostshift.domain.WorkflowInput;
import ai.ghostshift.domain.WorkflowStep;
import ai.ghostshift.domain.WorkflowValidation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkflowValidatorTest {
    private final WorkflowValidator validator = new WorkflowValidator(new ActionRegistry());

    @Test
    void acceptsASafeOrganizeWorkflow() {
        WorkflowValidation validation = validator.validate(new WorkflowDefinition(
                1, "Safe organize", "Preview, approve, then move.",
                List.of(
                        step("list", "LIST_FILES", Map.of("maxFiles", 20)),
                        step("classify", "CLASSIFY_FILES", Map.of("useAi", false)),
                        step("propose", "PROPOSE_DESTINATION", Map.of()),
                        step("preview", "PREVIEW_FILE_MOVE", Map.of()),
                        step("approval", "REQUEST_USER_APPROVAL", Map.of()),
                        step("move", "MOVE_FILE", Map.of("maxFiles", 20))
                ),
                List.of(new WorkflowInput("directoryId", "Folder", "DIRECTORY", true, "Folder to organize")),
                true, "STOP"));

        assertTrue(validation.valid(), () -> String.join("; ", validation.errors()));
        assertTrue(validation.requiredPermissions().contains("files:write"));
    }

    @Test
    void rejectsUnknownActions() {
        WorkflowValidation validation = validator.validate(definition(
                step("shell", "RUN_SHELL_COMMAND", Map.of())));

        assertFalse(validation.valid());
        assertTrue(validation.errors().stream().anyMatch(error -> error.contains("unknown action type")));
    }

    @Test
    void rejectsUnknownParameters() {
        WorkflowValidation validation = validator.validate(definition(
                step("list", "LIST_FILES", Map.of("command", "rm -rf /"))));

        assertFalse(validation.valid());
        assertTrue(validation.errors().stream().anyMatch(error -> error.contains("Unknown parameter")));
    }

    @Test
    void requiresPreviewBeforeMove() {
        WorkflowValidation validation = validator.validate(definition(
                step("list", "LIST_FILES", Map.of()),
                step("propose", "PROPOSE_DESTINATION", Map.of()),
                step("move", "MOVE_FILE", Map.of())));

        assertFalse(validation.valid());
        assertTrue(validation.errors().stream().anyMatch(error -> error.contains("PREVIEW_FILE_MOVE")));
    }

    @Test
    void requiresADirectoryInputForFileWork() {
        WorkflowValidation validation = validator.validate(new WorkflowDefinition(
                1, "No folder", "Missing input.",
                List.of(step("list", "LIST_FILES", Map.of())),
                List.of(), false, "STOP"));

        assertFalse(validation.valid());
        assertTrue(validation.errors().stream().anyMatch(error -> error.contains("directoryId")));
    }

    @Test
    void rejectsDuplicateStepIds() {
        WorkflowValidation validation = validator.validate(definition(
                step("same", "LIST_FILES", Map.of()),
                step("same", "CLASSIFY_FILES", Map.of("useAi", false))));

        assertFalse(validation.valid());
        assertTrue(validation.errors().stream().anyMatch(error -> error.contains("Duplicate step id")));
    }

    @Test
    void categoriesAreAClosedSet() {
        assertEquals(Category.DEVELOPMENT_LOGS, Category.fromModel("development logs").orElseThrow());
        assertTrue(Category.fromModel("totally-new-category").isEmpty());
    }

    private WorkflowDefinition definition(WorkflowStep... steps) {
        return new WorkflowDefinition(1, "Test", "Test workflow", List.of(steps),
                List.of(new WorkflowInput("directoryId", "Folder", "DIRECTORY", true, "Folder")),
                false, "STOP");
    }

    private WorkflowStep step(String id, String type, Map<String, Object> parameters) {
        return new WorkflowStep(id, type, type + " step", parameters, true);
    }
}
