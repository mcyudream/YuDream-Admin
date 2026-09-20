package online.yudream.base.application.system.setting.cmd;

import lombok.Data;

/**
 * 对象存储集成配置保存命令。
 * 非密文字段：null 表示保持不变，空串表示清除（回落环境变量）；SecretKey（密文）：留空=保持已存值。
 */
@Data
public class StorageConfigCmd {

    private String endpoint;
    private String accessKey;
    private String secretKey;
    private String bucket;
    private String region;
    private Boolean pathStyle;
}
