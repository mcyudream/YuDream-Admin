package online.yudream.base.domain.system.backup.aggregate;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.common.base.BaseDomain;
import online.yudream.base.domain.system.backup.enumerate.RemoteTargetType;

import java.util.regex.Pattern;

/**
 * 异地备份目标：一台 FTP/FTPS/WebDAV 服务器的连接描述。
 * 密码以密文形态持久化（由基础设施映射器经主密钥加密），聚合内仅承载解密后的运行时值。
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class RemoteTarget extends BaseDomain {

    private static final Pattern CODE_PATTERN = Pattern.compile("[a-z0-9][a-z0-9-]{0,63}");

    private String code;
    private String name;
    private RemoteTargetType type;
    private String host;
    private Integer port;
    private String username;
    private String password;
    private String basePath;
    /** FTP 是否使用被动模式（默认 true）。 */
    private boolean passiveMode;
    private boolean enabled;

    public static RemoteTarget create(String code, String name, RemoteTargetType type, String host, Integer port,
                                      String username, String password, String basePath, boolean passiveMode) {
        RemoteTarget target = RemoteTarget.builder()
                .code(code)
                .name(name)
                .type(type)
                .host(host)
                .port(port)
                .username(username)
                .password(password)
                .basePath(basePath)
                .passiveMode(passiveMode)
                .enabled(true)
                .build();
        target.ensureValid();
        return target;
    }

    public void update(String name, RemoteTargetType type, String host, Integer port,
                       String username, String password, String basePath, boolean passiveMode) {
        this.name = name;
        this.type = type;
        this.host = host;
        this.port = port;
        this.username = username;
        if (password != null) {
            this.password = password;
        }
        this.basePath = basePath;
        this.passiveMode = passiveMode;
        ensureValid();
    }

    public void enable() {
        this.enabled = true;
    }

    public void disable() {
        this.enabled = false;
    }

    /** 域不变量校验：编码、地址、端口与基础路径合法。 */
    public void ensureValid() {
        if (code == null || !CODE_PATTERN.matcher(code).matches()) {
            throw new BizException("目标编码须为小写字母/数字/连字符且以字母或数字开头：" + code);
        }
        if (type == null) {
            throw new BizException("请选择目标协议类型");
        }
        if (host == null || host.isBlank()) {
            throw new BizException("请填写目标主机地址");
        }
        if (port == null) {
            this.port = defaultPort();
        }
        if (port < 1 || port > 65535) {
            throw new BizException("端口须在 1-65535 之间");
        }
        if (basePath == null || basePath.isBlank()) {
            this.basePath = "/";
        }
        if (!basePath.startsWith("/")) {
            throw new BizException("基础路径必须以 / 开头");
        }
    }

    public int defaultPort() {
        return switch (type) {
            case FTP, FTPS -> 21;
            case WEBDAV -> 443;
        };
    }

    /** 目标是否使用加密通道（FTPS/WebDAV 的 https）。 */
    public boolean secureTransport() {
        return type == RemoteTargetType.FTPS || (type == RemoteTargetType.WEBDAV && port == 443);
    }

    /** WebDAV 目标使用的 URL scheme：显式端口 80 视为 http，其余 https。 */
    public String webDavScheme() {
        return port != null && port == 80 ? "http" : "https";
    }

    /** 凭据加密的 AAD 配置键。 */
    public String passwordConfigKey() {
        return "target:" + code;
    }
}
