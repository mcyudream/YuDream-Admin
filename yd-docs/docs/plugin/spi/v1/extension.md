# 扩展点 ExtensionSpi

> SPI v1 · 当前源码 2.27.0 · 包 `online.yudream.base.plugin.spi.system.extension`、`system.auth`

扩展点让插件向宿主（或其它插件）插入可回收的实现。注册走 `PluginContext.registerExtension(...)`，disable/unload 时自动从管线移除。宿主通过 `PluginExtensionQuery` 或 `context.extensions(Class)` 按优先级从小到大消费。

## PluginContext 注册与查询

| 方法 | 签名 | 说明 |
|---|---|---|
| `registerExtension(type, impl)` | `default <I> void registerExtension(Class<I> extensionPoint, I extension)` | 以默认优先级 0 注册 |
| `registerExtension(type, impl, priority)` | `<I> void registerExtension(Class<I> extensionPoint, I extension, int priority)` | 数值越小越先被消费；同优先级按注册先后 |
| `extensions(type)` | `<I> List<I> extensions(Class<I> extensionPoint)` | 当前已启用插件注册的全部实现 |

扩展点接口既可以由宿主定义（如 `system.auth`），也可以由 provider 插件在自己的 `*.api` 包中定义。

## 登录 / 注册扩展点（system.auth）

通过 `registerExtension(Xxx.class, impl)` 注册。失败策略为 **fail-closed**：实现抛出未捕获异常时宿主拒绝本次操作。

| 接口 | 回调 | 说明 |
|---|---|---|
| `LoginInterceptor` | `ExtensionVeto onBeforeLogin(LoginAttempt)` | 凭据校验前的 veto 拦截 |
| `RegisterInterceptor` | `ExtensionVeto onBeforeRegister(RegisterAttempt)` | 注册前的 veto 拦截；多个拦截器按优先级依次执行，任一否决即终止 |
| `IdentityVerificationProvider` | `IdentityVerificationMethod method()`、`IdentityVerificationResult check(VerificationSubject)` | 注册门禁核验（学信网、教育邮箱、人工审核等）。核验交互由插件自己的 HTTP/前端承载；宿主只在注册时询问是否已完成。站点设置 `system.auth.registration.required-verifications` 声明必需方式 |
| `PluginExternalLoginProvider` | `descriptor()`、`enabled()`、`authorizationUrl(request)`、`exchange(request)` | 插件贡献的第三方登录提供方（CAS、OIDC、OAuth2 等）。宿主持有 state 票据、回调分发、外部账号绑定与登录签发；插件只构造授权地址并用回调票据换身份。`descriptor()` / `enabled()` 必须基于内存快速返回，禁止网络 IO。失败 fail-closed |
| `AuthEventListener` | 登录成功 / 用户已注册事件 | 提交后监听，不可否决 |

```java
@Override
public void onEnable(PluginContext context) {
    context.registerExtension(LoginInterceptor.class, attempt -> ExtensionVeto.allow());
    context.registerExtension(IdentityVerificationProvider.class, new EduEmailVerifier(), 10);
}
```

核验交互（发起、回调、审核队列）由插件自身端点承载；不要在扩展实现里访问邮箱凭据或绕过 SPI 读宿主 Bean。

### PluginExternalLoginProvider

```java
@Override
public void onEnable(PluginContext context) {
    context.registerExtension(PluginExternalLoginProvider.class, new CampusCasLoginProvider(settings));
}
```

- `providerCode` 必须全局唯一且稳定，建议使用插件 code。
- `supportedTypes` 至少一项（如 `cas`、`oidc`）。宿主按 `code + type` 组合路由：`GET /api/external-login/{providerCode}/{type}/authorize`。
- 回调统一走 `GET /api/external-login/callback`，接受 OIDC `code` 或 CAS `ticket`，用 `state` 反查提供方。CAS 没有独立 state 参数时，把 state 编码进 `service` URL，校验必须精确回放同一 service。
- 登录页会合并插件提供方与后台配置的 WWOYUN 提供方；插件托管的 code 禁止在系统「第三方登录」里再保存一份。
- 未绑定本站账号时走既有 `BIND_REQUIRED` 流程，插件不要自行开户。
