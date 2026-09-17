# SPI 2.29.0：外部认证后的本站开户与自动绑定

此版本新增两个向后兼容的契约，SDK 与 components 无需升级。

```java
new PluginExternalLoginIdentity(socialUid, nickname, avatarUrl, gender, location, authenticatedUserId);
Optional<PluginUserProfile> PluginUserService.findByExternalIdentity(String providerCode, String platformType, String socialUid);
```

`authenticatedUserId` 是字符串形式的本站用户 ID，仅由可信插件的服务端 `exchange` 返回。它只能来自本次外部认证后调用 `users().create(...)` 创建的新用户，或已通过本站凭据验证的用户。不得信任浏览器传来的用户 ID，不得按邮箱相同、昵称相同自动绑定已有账号。旧的五参数构造器继续可用，默认 ID 为 null，维持 `BIND_REQUIRED` 行为。

典型 eduroam 流程：

1. 插件校验校园密码，通过后签发与宿主 state 绑定的短期一次性票据；不保存校园密码。
2. 用 `findByExternalIdentity` 检查既有绑定。已绑定用户继续原回调；不存在绑定但本站邮箱已有用户时，仍走原有登录绑定流程。
3. 新用户设置并确认独立的本站密码。插件从认证票据取得学校身份和按管理员配置映射的邮箱，使用 `users().create(...)` 创建普通用户。
4. 插件将新用户 ID 绑定到新的回调票据；在 `exchange` 核销票据后通过 `authenticatedUserId` 返回。票据、密码及 ID 不从浏览器任意指定，不保存明文密码。
5. 宿主先核销登录 state，再检查用户是否存在、停用、与登录中的绑定意图或既有第三方绑定是否冲突；通过后绑定并返回标准 `LOGIN` 会话。前端沿用既有外部登录回调，无需再输入本站密码。

没有外部账号绑定时查询返回 empty；绑定指向已删除用户或查询服务异常时抛错，不能当作“尚未开户”。返回停用用户的 profile 是为了阻止重复开户，最终登录仍由宿主拒绝。

发布顺序：验证并发布 SPI 2.29.0 → 升级运行中的宿主 → 确认 Nexus 可解析该版本 → 下游插件升级依赖并接入。仅更换 SPI JAR 不能替代宿主代码升级。发布前的下游插件保持已发布依赖，不可声称自动绑定已经上线。
