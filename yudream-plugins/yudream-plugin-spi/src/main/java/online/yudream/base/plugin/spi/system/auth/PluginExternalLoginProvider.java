package online.yudream.base.plugin.spi.system.auth;

/**
 * 插件贡献的外部登录提供方（CAS、OIDC、OAuth2 等第三方认证协议）。
 * 通过 PluginContext.registerExtension(PluginExternalLoginProvider.class, provider) 注册。
 *
 * <p>宿主持有并复用统一的第三方登录流程：state 票据签发与核销、授权回调分发、
 * 外部账号绑定表、绑定凭证（bindingToken）、登录会话签发。插件只负责协议相关的两段：
 * 依据 state 构造跳转地址（authorizationUrl），以及用回调票据换取外部身份（exchange）。</p>
 *
 * <p>提供方的启用状态、客户端凭据等配置由插件自身设置页管理；descriptor() 与
 * enabled() 会被宿主高频调用，实现必须基于内存缓存快速返回，不得在其中做网络 IO。
 * 网络失败、票据无效等异常从 exchange(...) 直接抛出 RuntimeException 即可，
 * 宿主会以统一错误信息拒绝本次登录（fail-closed）。</p>
 */
public interface PluginExternalLoginProvider {

    /**
     * 提供方展示元数据。providerCode 必须全局唯一且稳定，建议使用插件 code；
     * supportedTypes 至少一项（如 cas、oidc），宿主按 code + type 组合路由。
     */
    PluginExternalLoginDescriptor descriptor();

    /**
     * 当前是否可用（配置完整且已启用）。不可用的提供方不会出现在登录页，
     * 其授权与回调请求也会被宿主拒绝。
     */
    boolean enabled();

    /**
     * 构造浏览器整页跳转的授权地址。request.state() 是宿主签发的 CSRF 关联票据，
     * 必须以协议允许的方式随回调带回（OIDC/OAuth2 用 state 参数；CAS 可编码进 service URL）。
     */
    String authorizationUrl(PluginExternalLoginAuthorizeRequest request);

    /**
     * 用回调票据换取外部身份。request.ticket() 是授权方回调带回的凭据
     * （OIDC/OAuth2 的 code，CAS 的 ticket）；request.state() 与授权阶段一致，
     * 需要精确回放 service/redirect_uri 的协议可据此重建。
     */
    PluginExternalLoginIdentity exchange(PluginExternalLoginExchangeRequest request);
}
