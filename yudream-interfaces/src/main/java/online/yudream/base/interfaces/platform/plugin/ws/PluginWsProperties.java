package online.yudream.base.interfaces.platform.plugin.ws;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 插件 WebSocket 桥接配置（yudream.plugin.ws.*）。
 */
@ConfigurationProperties(prefix = "yudream.plugin.ws")
public class PluginWsProperties {

    /** 是否启用插件 WebSocket 桥接。 */
    private boolean enabled = true;

    /**
     * 额外放行的精确 Origin（如部署在反向代理之后的公网地址，例如 https://example.com）。
     * 校验只依据直连请求的 scheme/host/port，不读取 X-Forwarded-* / Forwarded 头；
     * 反代部署必须在此显式追加对外 Origin。
     */
    private List<String> allowedOrigins = new ArrayList<>();

    /** 单会话发送缓冲上限（字节），超限强制关闭会话。 */
    private int sendBufferLimitBytes = 512 * 1024;

    /** 单次底层发送阻塞超时（毫秒），超时视为连接不可靠并关闭。 */
    private long sendTimeLimitMs = 5000;

    /** 单条文本/二进制消息最大字节数（超出由容器以 1009 关闭）。 */
    private int maxMessageBufferSizeBytes = 1024 * 1024;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins == null ? new ArrayList<>() : new ArrayList<>(allowedOrigins);
    }

    public int getSendBufferLimitBytes() {
        return sendBufferLimitBytes;
    }

    public void setSendBufferLimitBytes(int sendBufferLimitBytes) {
        this.sendBufferLimitBytes = sendBufferLimitBytes;
    }

    public long getSendTimeLimitMs() {
        return sendTimeLimitMs;
    }

    public void setSendTimeLimitMs(long sendTimeLimitMs) {
        this.sendTimeLimitMs = sendTimeLimitMs;
    }

    public int getMaxMessageBufferSizeBytes() {
        return maxMessageBufferSizeBytes;
    }

    public void setMaxMessageBufferSizeBytes(int maxMessageBufferSizeBytes) {
        this.maxMessageBufferSizeBytes = maxMessageBufferSizeBytes;
    }
}
