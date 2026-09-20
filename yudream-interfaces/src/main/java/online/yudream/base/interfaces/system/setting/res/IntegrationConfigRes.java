package online.yudream.base.interfaces.system.setting.res;

import lombok.Builder;
import lombok.Data;

/**
 * 系统集成配置响应：密码/密钥永不回显，只给「已设置」标记；
 * source = custom（入库覆盖）/ environment（环境变量兜底）。
 */
@Data
@Builder
public class IntegrationConfigRes {

    private MailSection mail;
    private StorageSection storage;

    @Data
    @Builder
    public static class MailSection {
        private String host;
        private Integer port;
        private String username;
        private String from;
        private Boolean ssl;
        private Boolean starttls;
        private boolean passwordSet;
        private String source;
    }

    @Data
    @Builder
    public static class StorageSection {
        private String endpoint;
        private String accessKey;
        private String bucket;
        private String region;
        private Boolean pathStyle;
        private boolean secretKeySet;
        private String source;
    }
}
