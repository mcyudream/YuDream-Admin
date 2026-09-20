package online.yudream.base.interfaces.system.setting.request;

import lombok.Data;

/**
 * 系统集成配置保存请求。
 * 非密文字段：null=保持不变，空串=清除回落环境变量；密码/密钥：留空=保持已存值。
 */
@Data
public class IntegrationConfigSaveRequest {

    private MailConfig mail;
    private StorageConfig storage;
    /** 仅安装窗口期匿名调用时需要。 */
    private String setupToken;

    @Data
    public static class MailConfig {
        private String host;
        private Integer port;
        private String username;
        private String password;
        private String from;
        private Boolean ssl;
        private Boolean starttls;
    }

    @Data
    public static class StorageConfig {
        private String endpoint;
        private String accessKey;
        private String secretKey;
        private String bucket;
        private String region;
        private Boolean pathStyle;
    }
}
