package online.yudream.base.application.platform.ai.cmd;

import lombok.Data;

/**
 * 端点模型发现命令：管理端可只传端点编码复用已保存的基址/凭据/代理。
 */
@Data
public class AiModelDiscoveryCmd {
    private String providerCode;
    private String usage;
    private String protocol;
    private String baseUrl;
    private String apiKey;
    private String proxyUrl;
}
