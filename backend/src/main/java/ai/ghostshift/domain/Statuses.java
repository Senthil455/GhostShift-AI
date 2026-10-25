package ai.ghostshift.domain;

public final class Statuses {
    private Statuses() {
    }

    public enum PlanStatus {
        DRAFT,
        READY_FOR_REVIEW,
        APPROVED,
        EXECUTING,
        COMPLETED,
        PARTIALLY_COMPLETED,
        FAILED,
        CANCELLED,
        UNDONE
    }

    public enum PlanActionStatus {
        PROPOSED,
        APPROVED,
        REJECTED,
        MOVED,
        SKIPPED,
        FAILED,
        UNDONE
    }

    public enum WorkflowStatus {
        DRAFT,
        READY_FOR_REVIEW,
        APPROVED,
        DISABLED,
        ARCHIVED
    }

    public enum RunStatus {
        QUEUED,
        RUNNING,
        WAITING_FOR_APPROVAL,
        COMPLETED,
        PARTIALLY_COMPLETED,
        FAILED,
        CANCELLED
    }

    public enum StepStatus {
        PENDING,
        RUNNING,
        WAITING_FOR_APPROVAL,
        SUCCEEDED,
        FAILED,
        SKIPPED,
        CANCELLED
    }

    public enum ApprovalStatus {
        PENDING,
        APPROVED,
        REJECTED,
        EXPIRED
    }

    public enum RecordingStatus {
        ACTIVE,
        PAUSED,
        STOPPED,
        SAVED
    }

    public enum RiskLevel {
        LOW,
        MODERATE,
        HIGH
    }
}
