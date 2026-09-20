package online.yudream.base.domain.system.integration.valobj;

/**
 * 邮件服务器配置（运行时有效值，密码为明文）。
 * 来源可以是系统配置入库值，也可以是环境变量兜底值，由解析方保证优先级。
 */
public record MailServerConfig(
        String host,
        int port,
        String username,
        String password,
        String from,
        boolean ssl,
        boolean starttls
) {

    public MailServerConfig {
        host = host == null ? "" : host.trim();
        port = port <= 0 ? 465 : port;
        username = username == null ? "" : username.trim();
        password = password == null ? "" : password;
        from = from == null ? "" : from.trim();
    }

    /** 是否已具备可发送配置（至少要有主机）。 */
    public boolean configured() {
        return !host.isBlank();
    }
}
