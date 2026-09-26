package online.yudream.base.interfaces.platform.plugin.ws;

import jakarta.servlet.http.HttpServletRequest;
import online.yudream.base.plugin.spi.system.security.PluginPrincipal;

import java.util.List;
import java.util.function.Consumer;

/**
 * 握手鉴权支撑缝隙：把“HTTP 请求上下文主体解析”与“按 userId 实时权限查询”
 * 抽成小接口，宿主实现桥接 Sa-Token/API Key/OAuth，测试用 Lambda 替身。
 */
public interface WsAuthSupport {

    /** 凭据通道。 */
    enum CredentialSource {
        /** Authorization 头/API Key/OAuth（HTTP 请求上下文解析，非浏览器与浏览器 header 均可）。 */
        AUTHORIZATION_CONTEXT,
        /** 单次票据（浏览器通道）。 */
        TICKET,
        /** 匿名。 */
        ANONYMOUS
    }

    /**
     * 从当前 HTTP 请求上下文解析主体（Sa-Token 会话 / API Key / OAuth ThreadLocal）。
     * 解析不到返回 null，不抛异常。credentialSource 回写实际使用的通道。
     */
    PluginPrincipal fromRequestContext(HttpServletRequest request, Consumer<CredentialSource> credentialSource);

    /** 按 userId 实时查询权限（用于票据通道的身份/权限撤销复查）。 */
    List<String> permissions(Long userId);
}
