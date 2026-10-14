package online.yudream.base.domain.platform.ai.valobj;

import java.util.List;

/** 原生日志的只读恢复摘要；读取不会提交任务或触发工具。 */
public record HarnessSessionSnapshot(long cursor, String turnId, String runId, String status,
                                     List<PendingToolConfirmation> pendingConfirmations,
                                     List<String> uncertainToolCallIds,
                                     List<HarnessPendingInteraction> pendingInteractions) {
    public HarnessSessionSnapshot {
        pendingConfirmations = pendingConfirmations == null ? List.of() : List.copyOf(pendingConfirmations);
        uncertainToolCallIds = uncertainToolCallIds == null ? List.of() : List.copyOf(uncertainToolCallIds);
        pendingInteractions = pendingInteractions == null ? List.of() : List.copyOf(pendingInteractions);
    }

    public HarnessSessionSnapshot(long cursor, String turnId, String runId, String status,
                                   List<PendingToolConfirmation> pendingConfirmations, List<String> uncertainToolCallIds) {
        this(cursor, turnId, runId, status, pendingConfirmations, uncertainToolCallIds, List.of());
    }
}
