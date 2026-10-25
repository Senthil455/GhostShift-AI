package ai.ghostshift.workflow;

import ai.ghostshift.util.Json;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@MicronautTest
@io.micronaut.context.annotation.Property(name = "ghostshift.data-dir", value = "build/test-data/serde")
class WorkflowContextSerdeTest {
    @Inject
    Json json;

    @Test
    void emptyCollectionsSurviveAJsonRoundTrip() {
        WorkflowContext original = WorkflowContext.empty(7L, "Folder")
                .withFiles(List.of(new ContextFile("f1", "notes.txt", "notes.txt", ".txt", 10, "now")));

        String raw = json.write(original);
        WorkflowContext restored = json.read(raw, WorkflowContext.class);

        assertNotNull(restored.files());
        assertNotNull(restored.selectedFileIds());
        assertNotNull(restored.classifications());
        assertNotNull(restored.proposedMoves());
        assertNotNull(restored.moveResults());
    }
}
