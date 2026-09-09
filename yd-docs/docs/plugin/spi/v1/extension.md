# 扩展点 ExtensionSpi

> SPI v1 · 当前源码 2.24.0 · 包 `online.yudream.base.plugin.spi.system.extension`、`system.auth`

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
| `AuthEventListener` | 登录成功 / 用户已注册事件 | 提交后监听，不可否决 |

```java
@Override
public void onEnable(PluginContext context) {
    context.registerExtension(LoginInterceptor.class, attempt -> ExtensionVeto.allow());
    context.registerExtension(IdentityVerificationProvider.class, new EduEmailVerifier(), 10);
}
```

核验交互（发起、回调、审核队列）由插件自身端点承载；不要在扩展实现里访问邮箱凭据或绕过 SPI 读宿主 Bean。
