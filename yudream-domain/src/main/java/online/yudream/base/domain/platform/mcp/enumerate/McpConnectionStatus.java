package online.yudream.base.domain.platform.mcp.enumerate;

public enum McpConnectionStatus {
    /** 尚未连接过。 */
    UNKNOWN,
    /** 最近一次连接成功。 */
    CONNECTED,
    /** 最近一次连接失败。 */
    ERROR
}
