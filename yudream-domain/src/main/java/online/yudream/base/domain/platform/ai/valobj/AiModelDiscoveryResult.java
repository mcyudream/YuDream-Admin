package online.yudream.base.domain.platform.ai.valobj;

import java.util.List;

/**
 * 模型发现结果：{@code listed=false} 表示该端点/协议没有可用的模型列表接口，
 * 管理端应回落到手工登记模型；{@code message} 用于说明来源或失败原因。
 */
public record AiModelDiscoveryResult(List<AiDiscoveredModel> models, boolean listed, String message) {

    public AiModelDiscoveryResult {
        models = models == null ? List.of() : List.copyOf(models);
    }

    public static AiModelDiscoveryResult listed(List<AiDiscoveredModel> models, String message) {
        return new AiModelDiscoveryResult(models, true, message);
    }

    public static AiModelDiscoveryResult unsupported(String message) {
        return new AiModelDiscoveryResult(List.of(), false, message);
    }
}
