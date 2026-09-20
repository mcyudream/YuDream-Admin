package online.yudream.base.interfaces.installer.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 安装落盘请求。
 */
@Data
public class InstallerApplyRequest {

    @NotBlank(message = "MongoDB 连接串不能为空")
    private String mongoUri;
    @NotBlank(message = "Redis 地址不能为空")
    private String redisHost;
    private Integer redisPort;
    private String redisPassword;
    private Integer redisDatabase;
    private Boolean redisSsl;
    /** 留空则由服务端自动生成。 */
    private String credentialKey;
    private Integer snowflakeDataCenterId;
    private Integer snowflakeMachineId;
    private String setupToken;
}
