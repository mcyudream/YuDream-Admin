# 创建你的第一个插件

本教程从零创建一个具备权限、HTTP 接口和前端页面的完整插件。

## 0. 准备

- JDK 21、Maven 3.9+。
- 官方业务插件默认在独立仓 `yudream-admin-plugins` 中开发；本文中的 `yudream-plugins/yudream-plugin-demo/` 是"当前插件仓内部的相对路径"。

## 1. 创建 Maven 模块

插件只依赖 SPI（版本以 `yudream-plugin-spi/pom.xml` 为准，当前源码 **2.24.0**；下游只用已验证发布的版本）：

```xml
<dependency>
    <groupId>online.yudream.base</groupId>
    <artifactId>yudream-plugin-spi</artifactId>
</dependency>
```

推荐结构（中大型插件按职责分包）：

```text
yudream-plugin-demo/
  pom.xml
  src/main/java/online/yudream/base/plugin/demo/
    bootstrap/DemoPlugin.java     入口与装配
    domain/                       领域模型
    application/                  用例服务
    infrastructure/               持久化与外部适配
    interfaces/                   controller / request / res / assembler
  src/main/resources/
    plugin.yml                    插件清单（JAR 根）
    templates/                    插件私有 Thymeleaf 模板（可选）
```

## 2. 编写 plugin.yml

```yaml
name: demo-plugin
displayName: 演示插件
main: online.yudream.base.plugin.demo.bootstrap.DemoPlugin
version: 1.0.0
# depend:            # 硬依赖（提供方未启用则本插件不能加载）
#   - wallet-plugin
# softdepend:         # 可选依赖（缺失时须条件降级）
#   - coupon-plugin
```

`name` 是稳定插件 code（依赖与服务查找都用它）；`displayName` 仅展示。禁止打包 `META-INF/services/...YuDreamPlugin`——运行时以 `plugin.yml.main` 为唯一权威入口。

## 3. 编写入口类

```java
@PluginSpec(
        code = "demo-plugin",
        name = "演示插件",
        version = "1.0.0",
        description = "演示 YuDream 插件注册流程"
)
@PluginPermission(
        code = "plugin:demo:view",
        name = "查看演示插件",
        module = "平台插件",
        description = "访问演示插件页面和接口"
)
@PluginFrontend(
        moduleName = "demoPlugin",
        sdkVersion = "1.5.0",
        menuTitle = "演示插件",
        menuIcon = "i-ri:puzzle-2-line",
        menuSort = 20,
        styles = {"style.css"},
        routes = {
                @PluginRoute(
                        path = "/platform/plugins/demo",
                        name = "platform-plugin-demo",
                        title = "演示首页",
                        component = "Home",
                        permission = "plugin:demo:view",
                        sort = 10
                )
        }
)
public class DemoPlugin implements YuDreamPlugin {

    @Override
    public void onEnable(PluginContext context) {
        // 动态贡献在这里注册，disable/unload 时由宿主自动回收
        context.registerHttpController(new DemoController());
    }
}
```

静态元信息用注解声明（宿主扫描注册）；动态/条件性贡献才使用命令式 `registerXxx`。

## 4. 注册 HTTP 接口

```java
public class DemoController {

    @PluginHttpEndpoint(method = "GET", path = "/status", permission = "plugin:demo:view")
    public PluginHttpResponse status(PluginHttpRequest request, PluginContext context) {
        return PluginHttpResponse.ok(Map.of(
                "plugin", context.pluginCode(),
                "userId", request.principal().userId()
        ));
    }
}
```

最终访问地址：`GET /api/plugins/demo-plugin/status`。

## 5. 调用主系统能力

```java
PluginUserProfile profile = context.framework()
        .users()
        .findById(request.principal().userId())
        .orElse(null);
```

常用端口：`framework().users()` / `.security()` / `.mail()` / `.inboundMail()` / `.render()` / `.ai()` / `.messaging()` / `.filePreview()`，以及 `context.files()` / `context.documents()` / `context.templateRenderer()`。完整列表见 [FrameworkServices 参考](/plugin/spi/v1/framework-services)。

插件前端选择器不要再包一层 HTTP 去转发这些目录：消息连接/群用 `sdk.messaging`，用户/部门/角色用 `sdk.users`，Agent/供应商用 `sdk.ai`。详见 [@yudream/plugin-sdk](/plugin/sdk/)。

## 6. 插件前端

本地开发目录（相对当前插件仓）：

```text
yudream-frontend/packages/plugin-demo/
  package.json
  src/index.ts          # remote entry 导出
  src/pages/Home.vue
  src/components/
  src/api/              # 基于 @yudream/plugin-sdk 的请求封装；宿主目录用 sdk.messaging / sdk.users / sdk.ai
  src/types.ts
```

生产构建出 ESM `remoteEntry.js` 与 `style.css`（`vite.config.ts` 挂 `@yudream/plugin-sdk/uno-config` 的 `yuDreamPluginUnoCss()`、入口 `import 'virtual:uno.css'`、lib `cssFileName: 'style'`），并打进 JAR 的 `META-INF/yudream-plugin/frontend/demo-plugin/`。宿主按 frontend manifest 动态加载并注入 SDK。详见 [插件前端工程化](/plugin/frontend-remote) 与 [@yudream/plugin-sdk](/plugin/sdk/)。

## 7. 构建与安装

```bash
mvn -pl yudream-plugins/yudream-plugin-demo -am -DskipTests package
```

产物：`target/yudream-plugin-demo-1.0-SNAPSHOT.jar`。在后台 **插件管理** 中上传加载并启用：

- 权限注册进系统权限管理；
- HTTP 端点挂载生效；
- 前端路由出现在菜单；
- 禁用/卸载后全部贡献自动释放。

## 8. 下一步

- 阅读 [SPI 参考](/plugin/spi/) 了解全部接口能力。
- 阅读 [插件规范与检查清单](/plugin/specification) 保证工程质量。
- 上架走目标站点的本机市场，或自己托管一套源，见 [插件市场与第三方上架](/plugin/marketplace)。
