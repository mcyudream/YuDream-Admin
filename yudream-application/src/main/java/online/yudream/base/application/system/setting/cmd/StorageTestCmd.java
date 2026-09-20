package online.yudream.base.application.system.setting.cmd;

import lombok.Data;

/**
 * 候选对象存储配置测试命令：验证 bucket 连通性（不落库）。
 */
@Data
public class StorageTestCmd {

    private String endpoint;
    private String accessKey;
    /** 留空则使用已保存/环境变量中的密钥。 */
    private String secretKey;
    private String bucket;
    private String region;
    private Boolean pathStyle;
    /** bucket 不存在时是否自动创建。 */
    private Boolean autoCreate;
}
