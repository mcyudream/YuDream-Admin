# SPI 2.32.0：插件登录入口以登录 Tab 呈现

此版本新增一个向后兼容的契约，SDK 与 components 无需升级。

> 版本号说明：2.30.0 已由 `PluginDescriptor.gitUrl` 占用，2.31.0 已由插件 HTTP 上传契约（`PluginHttpPart` / `PluginHttpRequest.parts`）占用，本次登录入口契约因此使用 2.32.0。

```java
public enum PluginExternalLoginPresentation { ICON, TAB }

default PluginExternalLoginPresentation PluginExternalLoginProvider.presentation() { return PluginExternalLoginPresentation.ICON; }
```

`presentation()` 是接口默认方法：既有插件不实现也照常加载，宿主按缺省 `ICON` 处理，行为与 2.29.0 完全一致。实现异常（抛 RuntimeException）同样回落 `ICON`，不会让登录页整体不可用。

## 呈现方式的语义

- `ICON`：`descriptor()` 的每个 `supportedType` 在登录表单下方渲染成一个圆形图标按钮，标题取 `displayName`（历史行为）。
- `TAB`：每个 `supportedType` 渲染成一个与「账号密码登录 / Passkey 登录」并列的登录方式 Tab，标签取 `displayName`、图标取 `icon`；选中该 Tab 后展示整行「使用 <通道名>登录」按钮，并隐藏账号密码表单、记住账号与 Passkey 入口。第三方账号绑定流程（`externalLoginBindingToken`）下与 Passkey 一致，不参与 Tab 渲染。

## Tab 顺序

登录 Tab 按 `descriptor().sort()` 升序排列，内置 Tab 参与同一排序，基线为：

| Tab | 基线 sort |
| --- | --- |
| 账号密码登录 | 100 |
| Passkey 登录 | 200 |
| 插件入口 | `descriptor().sort()` |

因此 `sort < 100` 的插件入口排到「账号密码登录」之前，`100 ~ 199` 落在账号密码与 Passkey 之间。等值时保持「内置在前、插件按接口返回顺序」的稳定插入顺序。以统一身份认证为主入口的站点把插件 `sort` 设成 0 即可置顶；`sort` 仍照旧决定图标按钮之间的排序。

## 首选登录方式（后台可配置）

管理员可在「系统设置」页配置**默认登录方式**：`password`、`passkey`，或任一**已启用且以 TAB 呈现**的插件入口 `external:{providerCode}:{type}`（如 `external:cas:cas`）。

- 存储：`site` 分类的通用 Setting，键 `loginDefaultMethod`（空 = 账号密码）；写入时只接受上述三种形态，其它值回落为空，脏配置不会让登录页选中渲染不出来的登录方式。
- 匿名下发：随 `/api/settings/public` 一起返回（登录页本来就在引导前读它），字段 `loginDefaultMethod`。
- 登录页行为：打开时默认选中该登录方式；配置的入口未启用、已下线或不是 Tab 型时静默回落账号密码。绑定第三方账号的流程（`externalLoginBindingToken`）忽略该配置。

协议的授权、回调、state 核销、绑定与登录会话完全走既有 `/api/external-login/{providerCode}/{type}/authorize` 流程，`presentation()` 只影响登录页的呈现，不改变任何服务端行为。

## 宿主侧改动

- SPI：新增 `PluginExternalLoginPresentation` 与 `PluginExternalLoginProvider.presentation()`。
- 应用层：`ExternalLoginProviderDTO.presentation`（宿主托管提供方为 null）。
- 接口层：`ExternalLoginProviderRes.presentation` 与 `ExternalLoginProviderRes.sort`，由 `/api/external-login/providers` 公开下发；未声明或异常时为 `ICON`。
- 前端：`views/login.vue` 把 `presentation` / `sort` 交给登录表单组件（`ExternalLoginEntry`），`components/AppAccountForm/login.vue` 据此生成并按 `sort` 排列 Tab。

## 下游接入

```java
@Override
public PluginExternalLoginPresentation presentation() {
    return PluginExternalLoginPresentation.TAB;
}
```

发布顺序：验证并发布 SPI 2.32.0 → 升级运行中的宿主 → 确认 Nexus 可解析该版本 → 下游插件把依赖升到 2.32.0 并在 `pom.xml` / `plugin.yml` 升版、`store.json` 记 releaseNotes。仅更换 SPI JAR 不能替代宿主代码升级：宿主前端不认识 `presentation` 字段时，插件入口仍按图标按钮渲染。
