package online.yudream.base.interfaces.system.setting.request;

import lombok.Data;

/**
 * 候选对象存储配置测试请求（不落库，验证 bucket 连通性）。
 */
@Data
public class StorageTestRequest {

    private String endpoint;
    private String accessKey;
    /** 留空则使用已保存/环境变量中的密钥。 */
    private String secretKey;
    private String bucket;
    private String region;
    private Boolean pathStyle;
    /** bucket 不存在时是否自动创建，默认 true。 */
    private Boolean autoCreate;
    /** 仅安装窗口期匿名调用时需要。 */
    private String setupToken;
}
