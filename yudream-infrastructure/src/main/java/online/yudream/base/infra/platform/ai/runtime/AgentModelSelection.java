package online.yudream.base.infra.platform.ai.runtime;

import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;

/**
 * RuntimeContext 中的按次调用模型选择：会话可指定 provider/model，
 * 经 PerCallModelMiddleware 在 onModelCall 时替换 agent 构建期绑定的默认模型。
 */
public record AgentModelSelection(Model model, GenerateOptions options) {
}
