package online.yudream.base.application.platform.ai.service;

import online.yudream.base.domain.platform.ai.aggregate.AiRun;
import online.yudream.base.domain.platform.ai.service.AiAgentTool;
import online.yudream.base.domain.platform.ai.valobj.HarnessRunResult;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** 宿主在原生任务完成后接续自己持久保存的业务语义，不调度或替代模型运行。 */
public interface AiRunContinuation {
    default Optional<List<AiAgentTool>> restoreTools(AiRun run, Set<String> permissions, AiRunAppService.RunEmitter emitter) {
        return Optional.empty();
    }
    Optional<Object> continueAfterNative(AiRun run, HarnessRunResult nativeResult, Set<String> permissions,
                                          AiRunAppService.RunEmitter emitter);
    default void cancel(AiRun run) { }
}
