package online.yudream.base.domain.platform.ai.valobj;

import java.util.Map;

/**
 * 模型发现请求：由管理端输入（可只填编码复用已保存端点）解析出的一次性探测目标。
 * <p>
 * 凭据只用于本次请求，不落库、不写日志、不回显；{@code providerCode} 与 {@code storedConfig}
 * 用于在管理端留空时回落已保存端点的基址/凭据/代理。
 */
public record AiModelDiscoveryQuery(
        String providerCode,
        String usage,
        String protocol,
        String baseUrl,
        String apiKey,
        String proxyUrl,
        Map<String, String> storedConfig
) {
    public AiModelDiscoveryQuery {
        storedConfig = storedConfig == null ? Map.of() : Map.copyOf(storedConfig);
    }
}
