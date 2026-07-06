# YuDream 插件开发教程

本文面向插件开发者，说明如何从零创建一个 YuDream 插件，并接入权限、HTTP 接口、前端页面和主系统能力。

## 1. 插件能做什么

插件可以向主系统注册：

- 插件元信息：编码、名称、版本、依赖。
- 权限：用于 HTTP 接口、前端按钮和菜单访问控制。
- HTTP 接口：统一挂载到 `/api/plugins/{pluginCode}/**`。
- 前端页面：通过 ESM remote entry 加载到后台布局内。
- 首页卡片：参与首页 DIY 卡片。
- 平台能力或扩展点：向其他插件或主系统提供扩展实现。
- 框架能力调用：通过 `FrameworkServices` 访问用户、文件、文档、安全等稳定端口。

插件不能直接依赖主系统的 `domain`、`application`、`infrastructure`、`interfaces` 或 `bootstrap` 模块。

## 2. 创建 Maven 模块

插件模块只依赖 `yudream-plugin-spi`：

```xml
<dependency>
    <groupId>online.yudream.base</groupId>
    <artifactId>yudream-plugin-spi</artifactId>
</dependency>
```

最小结构：

```text
yudream-plugin-demo/
  pom.xml
  src/main/java/online/yudream/base/plugin/demo/bootstrap/DemoPlugin.java
```

业务插件推荐结构：

```text
src/main/java/online/yudream/base/plugin/demo/
  bootstrap/        插件入口与运行时装配
  domain/           插件自身领域模型
  application/      插件用例服务
  infrastructure/   插件持久化、外部服务、文件适配
  interfaces/       插件 HTTP controller、request、res、assembler
```

## 3. 编写插件入口

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
        sdkVersion = "1.0.0",
        menuTitle = "演示插件",
        menuIcon = "i-ri:puzzle-2-line",
        menuSort = 20,
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
        context.registerHttpController(new DemoController());
    }
}
```

如果只是一个非常小的接口，也可以像样例插件一样在入口类方法上直接声明 `@PluginHttpEndpoint`。真实业务插件建议拆到 `interfaces/controller`。

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

最终访问地址：

```text
GET /api/plugins/demo-plugin/status
```

`@PluginHttpEndpoint` 常用字段：

- `method`：HTTP 方法，如 `GET`、`POST`、`PUT`、`DELETE`。
- `path`：插件内路径，以 `/` 开头。
- `permission`：访问该接口需要的权限，可为空。
- `wrapResult`：是否使用系统统一响应包装；需要兼容外部协议时可设为 `false`。

## 5. 调用主系统能力

插件通过 `PluginContext.framework()` 调用稳定端口：

```java
PluginUserProfile profile = context.framework()
        .users()
        .findById(request.principal().userId())
        .orElse(null);
```

常用入口：

- `framework().users()`：系统用户、角色、部门等用户侧能力。
- `framework().security()`：当前主体、安全校验等能力。
- `context.files()`：插件私有文件存储。
- `context.documents()`：插件私有文档存储。
- `framework().extension(pluginCode, type)` / `extensions(type)`：扩展点发现。

需要新的主系统能力时，应先扩展 `yudream-plugin-spi` 的稳定端口，再由主系统实现适配。不要直接引用主系统 Spring Bean 或仓储实现。

## 6. 插件前端

本地开发时，插件前端放在：

```text
yudream-frontend/packages/plugin-demo/
  package.json
  src/index.ts
  src/pages/Home.vue
  src/components/
  src/api/
  src/types.ts
```

生产形态需要构建出 ESM remote entry，并打进插件 JAR：

```text
META-INF/yudream-plugin/frontend/demo-plugin/remoteEntry.js
```

主前端会根据插件 frontend manifest 加载 remote entry，并把 SDK 和路由上下文传给插件页面。插件前端应使用宿主提供的 SDK/client，不要捆绑私有 axios 实例。

`@PluginRoute.component` 应指向插件导出的页面组件，例如 `Home`、`AdminSettings`、`pages/Home`。一个主要页面对应一个真实组件，不要把多个大功能都塞进一个 tab 页面。

## 7. 菜单和排序

推荐使用 `@PluginFrontend` 的菜单字段：

- `menuTitle`：插件顶级菜单名。
- `menuIcon`：插件顶级菜单图标。
- `menuSort`：插件顶级菜单排序，值越大越靠前。

推荐使用 `@PluginRoute` 的路由字段：

- `sort`：当前页面排序，值越大越靠前。
- `parentTitle` / `parentPath` / `parentIcon` / `parentSort`：把多个页面归到插件目录下。

运行后可在插件管理中查看插件注册的菜单路由，并调整排序。

## 8. 构建与安装

打包插件：

```powershell
mvn -pl yudream-plugin-demo -am -DskipTests package
```

产物一般位于：

```text
yudream-plugin-demo/target/yudream-plugin-demo-1.0-SNAPSHOT.jar
```

在后台“插件管理”中加载 JAR，启用插件后：

- 插件权限会注册到系统权限。
- 插件 HTTP 端点会挂载。
- 插件前端路由会出现在菜单或可访问路由中。
- 插件卸载或禁用时，运行时贡献应被释放。

## 9. 开发检查清单

- 插件是否只依赖 `yudream-plugin-spi`。
- 插件 code 是否全局唯一，路径是否使用 `/api/plugins/{pluginCode}` 下的相对路径。
- 权限编码是否使用 `plugin:{code}:xxx` 风格。
- HTTP Controller 是否委托到应用服务，避免在 Controller 写业务规则。
- 插件前端是否拆分为页面、组件、API、类型文件。
- 生产 manifest 是否不依赖 workspace alias。
- JAR 内是否包含前端 `remoteEntry.js` 和相关 assets。
- 启用、禁用、卸载后资源是否正确注册和释放。

