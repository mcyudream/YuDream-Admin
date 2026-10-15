package online.yudream.base.interfaces.platform.ai.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 端点模型发现请求：只带当前编辑中的端点信息；凭据留空时复用已保存端点的密钥。
 */
public record AiModelDiscoveryRequest(
        @Size(max = 64) String providerCode,
        @NotBlank @Size(max = 32) String usage,
        @Size(max = 32) String protocol,
        @Size(max = 500) String baseUrl,
        @Size(max = 500) String apiKey,
        @Size(max = 500) String proxyUrl
) {
}
