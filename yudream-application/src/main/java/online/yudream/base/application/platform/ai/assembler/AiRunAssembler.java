package online.yudream.base.application.platform.ai.assembler;

import online.yudream.base.application.platform.ai.dto.AiRunDTO;
import online.yudream.base.domain.platform.ai.aggregate.AiRun;
import java.util.List;
import java.util.Map;

public final class AiRunAssembler {
    private AiRunAssembler() { }
    public static AiRunDTO run(AiRun run, List<Map<String, Object>> pending, List<Object> tools, List<Object> activities, String structuredContent,
                               String inputText, List<Map<String, Object>> generations) {
        return new AiRunDTO(run.getId(), run.getSessionId(), run.getModule(), run.getStatus(), run.getCursor(),
                run.getContent(), run.getReasoning(), run.getResult(), run.getError(), run.getCreatedAt(), run.getUpdatedAt(), pending, tools, activities,
                structuredContent, run.isNativeResumeSupported() && run.getNativeReference() != null && run.getNativeProfile() != null
                && run.getNativeProfile().exactToolScopeCaptured()
                && run.getNativeProfile().nativeActorCaptured()
                && run.getNativeProfile().allowedToolNames().stream().noneMatch(name -> name.startsWith("ui.") || name.startsWith("ui_"))
                && run.getStatus() == online.yudream.base.domain.platform.ai.enumerate.AiRunStatus.FAILED,
                run.getStatus().terminal() || run.getNativeSnapshot() == null ? List.of() : run.getNativeSnapshot().pendingInteractions(), inputText, generations);
    }
}
