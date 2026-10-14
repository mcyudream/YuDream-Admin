package online.yudream.base.infra.platform.ai.runtime;

import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;

/**
 * 按次构建 Agent 的模型绑定；连接身份用于模型缓存，规范化的代码用于私有恢复配置。
 */
public record AgentModelSelection(Model model, GenerateOptions options, String modelKey,
                                   String providerCode, String modelCode) {

    public AgentModelSelection(Model model, GenerateOptions options, String modelKey) {
        this(model, options, modelKey, null, null);
    }

    public AgentModelSelection(Model model, GenerateOptions options) {
        this(model, options, null, null, null);
    }
}
