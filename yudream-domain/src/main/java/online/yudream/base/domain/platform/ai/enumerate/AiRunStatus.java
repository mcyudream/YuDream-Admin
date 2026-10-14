package online.yudream.base.domain.platform.ai.enumerate;

public enum AiRunStatus {
    RUNNING, WAITING, COMPLETED, FAILED, CANCELLED;

    public boolean terminal() { return this != RUNNING && this != WAITING; }
}
