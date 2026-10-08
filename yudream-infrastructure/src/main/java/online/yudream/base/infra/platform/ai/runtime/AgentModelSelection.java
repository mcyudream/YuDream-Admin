package online.yudream.base.infra.platform.ai.runtime;

import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;

/**
 * RuntimeContext 中的按次调用模型选择（携带模型配置身份键）。
 * 模型绑定已前移到 agent 实例缓存键（(profile, modelKey) 多实例），
 * 本记录保留用于会话寻址与兼容。
 */
public record AgentModelSelection(Model model, GenerateOptions options, String modelKey) {

    public AgentModelSelection(Model model, GenerateOptions options) {
        this(model, options, null);
    }
}
