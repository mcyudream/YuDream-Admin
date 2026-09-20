package online.yudream.base.domain.installer.valobj;

/**
 * 引导配置：安装向导生成、独立于 .env 的应用引导文件内容
 * （数据库/Redis 连接与部署级密钥），重启后经最高优先级属性源生效。
 */
public record BootstrapConfig(
        String mongoUri,
        String redisHost,
        Integer redisPort,
        String redisPassword,
        Integer redisDatabase,
        Boolean redisSsl,
        String credentialKey,
        Integer snowflakeDataCenterId,
        Integer snowflakeMachineId
) {

    public BootstrapConfig {
        if (mongoUri != null) {
            mongoUri = mongoUri.trim();
        }
        if (redisHost != null) {
            redisHost = redisHost.trim();
        }
        redisPort = redisPort == null || redisPort <= 0 ? 6379 : redisPort;
        redisDatabase = redisDatabase == null || redisDatabase < 0 ? 0 : redisDatabase;
        redisPassword = redisPassword == null || redisPassword.isBlank() ? null : redisPassword;
        snowflakeDataCenterId = snowflakeDataCenterId == null ? 1 : snowflakeDataCenterId;
        snowflakeMachineId = snowflakeMachineId == null ? 1 : snowflakeMachineId;
    }
}
