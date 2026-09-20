package online.yudream.base.domain.installer.valobj;

/**
 * Redis 探测规格：安装向导对给定地址做连通性与认证探测。
 */
public record RedisProbeSpec(
        String host,
        Integer port,
        String password,
        Integer database,
        Boolean ssl
) {

    public RedisProbeSpec {
        host = host == null ? "" : host.trim();
        port = port == null || port <= 0 ? 6379 : port;
        database = database == null || database < 0 ? 0 : database;
        ssl = Boolean.TRUE.equals(ssl);
        password = password == null || password.isBlank() ? null : password;
    }

    public String target() {
        return host + ":" + port;
    }
}
