package online.yudream.base.application.platform.ai.service;

import online.yudream.base.domain.platform.ai.aggregate.AiRun;

/** 各业务只更新自己的 UI 投影；不参与原生会话调度。 */
public interface AiRunResultProjector {
    Object project(AiRun run, Object result, boolean waiting);
    default void cancel(AiRun run) { }
}
