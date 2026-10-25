package ai.ghostshift.workflow;

import ai.ghostshift.domain.LogAnalysis;
import io.micronaut.serde.annotation.Serdeable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Serializable workflow state. Only metadata and bounded excerpts are stored; full
 * approved files are re-read from disk when an action needs them.
 */
@Serdeable
public record WorkflowContext(
        int schemaVersion,
        Long directoryId,
        String directoryName,
        List<ContextFile> files,
        List<String> selectedFileIds,
        Map<String, ContextClassification> classifications,
        List<ContextMove> proposedMoves,
        String textRelativePath,
        LogAnalysis analysis,
        String reportId,
        List<ContextMoveResult> moveResults,
        String warning,
        boolean explicitApprovalGranted
) {
    public static WorkflowContext empty(Long directoryId, String directoryName) {
        return new WorkflowContext(1, directoryId, directoryName, new ArrayList<>(), new ArrayList<>(),
                new LinkedHashMap<>(), new ArrayList<>(), null, null, null, new ArrayList<>(), null, false);
    }

    public WorkflowContext withDirectory(Long id, String name) {
        return new WorkflowContext(schemaVersion, id, name, files, selectedFileIds, classifications,
                proposedMoves, textRelativePath, analysis, reportId, moveResults, warning, explicitApprovalGranted);
    }

    public WorkflowContext withFiles(List<ContextFile> newFiles) {
        return new WorkflowContext(schemaVersion, directoryId, directoryName, new ArrayList<>(newFiles),
                newFiles.stream().map(ContextFile::id).toList(), classifications, proposedMoves,
                textRelativePath, analysis, reportId, moveResults, warning, explicitApprovalGranted);
    }

    public WorkflowContext withSelected(List<String> selected) {
        return new WorkflowContext(schemaVersion, directoryId, directoryName, files,
                new ArrayList<>(selected), classifications, proposedMoves, textRelativePath, analysis,
                reportId, moveResults, warning, explicitApprovalGranted);
    }

    public WorkflowContext withClassifications(Map<String, ContextClassification> newClassifications) {
        return new WorkflowContext(schemaVersion, directoryId, directoryName, files, selectedFileIds,
                new LinkedHashMap<>(newClassifications), proposedMoves, textRelativePath, analysis,
                reportId, moveResults, warning, explicitApprovalGranted);
    }

    public WorkflowContext withMoves(List<ContextMove> moves) {
        return new WorkflowContext(schemaVersion, directoryId, directoryName, files, selectedFileIds,
                classifications, new ArrayList<>(moves), textRelativePath, analysis, reportId,
                moveResults, warning, explicitApprovalGranted);
    }

    public WorkflowContext withText(String relativePath) {
        return new WorkflowContext(schemaVersion, directoryId, directoryName, files, selectedFileIds,
                classifications, proposedMoves, relativePath, analysis, reportId, moveResults, warning,
                explicitApprovalGranted);
    }

    public WorkflowContext withAnalysis(LogAnalysis newAnalysis, String newReportId) {
        return new WorkflowContext(schemaVersion, directoryId, directoryName, files, selectedFileIds,
                classifications, proposedMoves, textRelativePath, newAnalysis, newReportId, moveResults,
                warning, explicitApprovalGranted);
    }

    public WorkflowContext withReport(String newReportId) {
        return new WorkflowContext(schemaVersion, directoryId, directoryName, files, selectedFileIds,
                classifications, proposedMoves, textRelativePath, analysis, newReportId, moveResults,
                warning, explicitApprovalGranted);
    }

    public WorkflowContext withMoveResults(List<ContextMoveResult> results) {
        return new WorkflowContext(schemaVersion, directoryId, directoryName, files, selectedFileIds,
                classifications, proposedMoves, textRelativePath, analysis, reportId, new ArrayList<>(results),
                warning, explicitApprovalGranted);
    }

    public WorkflowContext withWarning(String newWarning) {
        return new WorkflowContext(schemaVersion, directoryId, directoryName, files, selectedFileIds,
                classifications, proposedMoves, textRelativePath, analysis, reportId, moveResults,
                newWarning, explicitApprovalGranted);
    }

    public WorkflowContext withExplicitApproval(boolean granted) {
        return new WorkflowContext(schemaVersion, directoryId, directoryName, files, selectedFileIds,
                classifications, proposedMoves, textRelativePath, analysis, reportId, moveResults,
                warning, granted);
    }

    public ContextFile file(String id) {
        return files.stream().filter(file -> file.id().equals(id)).findFirst().orElse(null);
    }

    public List<ContextFile> selectedFiles() {
        LinkedHashSet<String> selected = new LinkedHashSet<>(selectedFileIds);
        return files.stream().filter(file -> selected.contains(file.id())).toList();
    }
}
