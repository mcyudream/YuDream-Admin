package online.yudream.base.application.installer.cmd;

import lombok.Data;

/**
 * 安装落盘命令：向导最终提交的数据库/Redis 连接与部署级密钥。
 */
@Data
public class InstallerApplyCmd {

    private String mongoUri;
    private String redisHost;
    private Integer redisPort;
    private String redisPassword;
    private Integer redisDatabase;
    private Boolean redisSsl;
    /** 留空则自动生成 Base64 32 字节主密钥。 */
    private String credentialKey;
    private Integer snowflakeDataCenterId;
    private Integer snowflakeMachineId;
    /** 部署侧设置了 YUDREAM_SETUP_TOKEN 时必填。 */
    private String setupToken;
}
