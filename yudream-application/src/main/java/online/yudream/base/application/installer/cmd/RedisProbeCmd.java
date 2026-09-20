package online.yudream.base.application.installer.cmd;

import lombok.Data;

/**
 * Redis 探测命令；端口/库号缺省时由值对象补默认值。
 */
@Data
public class RedisProbeCmd {

    private String host;
    private Integer port;
    private String password;
    private Integer database;
    private Boolean ssl;
}
