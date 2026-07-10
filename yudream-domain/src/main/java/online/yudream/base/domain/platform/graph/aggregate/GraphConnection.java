package online.yudream.base.domain.platform.graph.aggregate;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import online.yudream.base.domain.common.base.BaseDomain;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.graph.enumerate.GraphConnectionStatus;

@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class GraphConnection extends BaseDomain {

    private String name;
    private String code;
    private String uri;
    private String username;
    private String password;
    private String database;
    private GraphConnectionStatus status;

    public static GraphConnection create(String name, String code, String uri, String username, String password, String database) {
        GraphConnection connection = new GraphConnection();
        connection.name = required(name, "连接名称不能为空");
        connection.code = required(code, "连接编码不能为空");
        connection.uri = required(uri, "连接地址不能为空");
        connection.username = required(username, "用户名不能为空");
        connection.password = required(password, "密码不能为空");
        connection.database = normalizeDatabase(database);
        connection.status = GraphConnectionStatus.ACTIVE;
        return connection;
    }

    public void update(String name, String uri, String username, String password, String database, GraphConnectionStatus status) {
        this.name = required(name, "连接名称不能为空");
        this.uri = required(uri, "连接地址不能为空");
        this.username = required(username, "用户名不能为空");
        if (password != null && !password.isBlank()) {
            this.password = password;
        }
        this.database = normalizeDatabase(database);
        this.status = status == null ? GraphConnectionStatus.ACTIVE : status;
    }

    public void disable() {
        this.status = GraphConnectionStatus.DISABLED;
    }

    public void activate() {
        this.status = GraphConnectionStatus.ACTIVE;
    }

    public boolean active() {
        return GraphConnectionStatus.ACTIVE == status;
    }

    private static String normalizeDatabase(String value) {
        return value == null || value.isBlank() ? "neo4j" : value.trim();
    }

    private static String required(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BizException(message);
        }
        return value.trim();
    }
}
