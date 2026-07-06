# YuDream Admin

YuDream Admin 是一个基于 Spring Boot 3、Java 21、DDD 分层和 Fantastic Admin Vue 前端的可扩展管理平台。项目核心目标是把系统能力、平台能力和业务插件分开：主应用提供用户、权限、菜单、配置、监控、文档、CMS、动态表单等基础能力，插件通过稳定 SPI 接入菜单、权限、HTTP 接口、前端页面、首页卡片和框架服务。

## 技术栈

- 后端：Java 21、Spring Boot 3.5、Sa-Token、MapStruct、Lombok、EasyExcel、Springdoc、Spring AI
- 前端：Vue 3、Vite、TypeScript、Arco Design Vue、Fantastic Admin、pnpm workspace
- 插件：独立 Maven 模块 + `yudream-plugin-spi` 编译期契约 + 运行时 JAR 加载
- 架构：DDD 四层结构，插件和主应用通过 SPI 端口交互

## 模块结构

```text
yudream-domain/                 领域层：聚合、值对象、枚举、领域仓储接口、领域服务
yudream-application/            应用层：用例编排、命令、查询、DTO、应用装配
yudream-infrastructure/         基础设施层：持久化、外部服务、菜单种子、插件运行时实现
yudream-interfaces/             接口层：HTTP Controller、请求/响应模型、Web 装配
yudream-bootstrap/              启动模块：Spring Boot 入口和运行时装配
yudream-plugins/yudream-plugin-spi/     插件 SPI：第三方插件唯一允许依赖的主应用契约
yudream-plugins/yudream-sample-plugin/  插件样例
yudream-plugins/yudream-plugin-*/       内置业务插件
yudream-frontend/               前端 workspace，包含主应用和插件前端包
docs/                           项目设计文档、计划和规范
```

## 后端开发

推荐使用 JDK 21。根目录是 Maven 聚合工程，常用命令：

```powershell
mvn -pl yudream-bootstrap -am -DskipTests compile
mvn -pl yudream-bootstrap -am spring-boot:run
mvn -pl yudream-plugins/yudream-plugin-yudream-skin -am -DskipTests package
```

如果本机有多个 JDK，可以在 PowerShell 中临时指定：

```powershell
$env:JAVA_HOME='C:/Users/SiberianHusky/.jdks/ms-21.0.10'
$env:Path="$env:JAVA_HOME/bin;$env:Path"
```

配置文件参考根目录 `.env.template`、`yudream-bootstrap/src/main/resources` 和实际运行环境变量。提交前至少运行：

```powershell
mvn -pl yudream-bootstrap -am -DskipTests compile
```

## 前端开发

前端位于 `yudream-frontend`，包管理器为 pnpm。

```powershell
cd yudream-frontend
pnpm install
pnpm dev
pnpm --filter @fantastic-admin/core-arco-design-vue exec vue-tsc --noEmit --pretty false --skipLibCheck --ignoreDeprecations 6.0
```

当前 workspace 配置了 `engineStrict`，建议使用 `package.json` 中声明的 Node 版本范围。开发环境默认应用为 `apps/core-arco-design-vue`。

## 架构约定

后端遵循四层 DDD：

- `domain` 只表达业务概念和规则，不依赖 Web、Spring MVC、持久化实现。
- `application` 负责用例编排，不接收接口层 Request，不返回接口层 Response。
- `infrastructure` 负责技术适配，不向应用层泄漏 DO、Mapper、第三方 SDK 细节。
- `interfaces` 只做 HTTP 边界，Controller 不写业务规则，不手写大段转换逻辑。

菜单、权限、系统种子和插件注册都应通过领域/应用服务完成，避免在 Controller 或启动类里散落业务逻辑。

## 插件系统

插件只依赖 `yudream-plugin-spi`，通过注解和 `PluginContext` 注册能力。插件 HTTP 接口统一挂载在：

```text
/api/plugins/{pluginCode}/**
```

插件前端生产形态是 ESM remote entry，打包进插件 JAR：

```text
META-INF/yudream-plugin/frontend/{pluginCode}/remoteEntry.js
```

更多细节见：

- [插件开发教程](docs/plugin-system/tutorial.md)
- [插件开发规范](docs/plugin-system/specification.md)

## 内置插件

- `yudream-plugins/yudream-sample-plugin`：最小样例，演示权限、前端路由、HTTP 接口和框架用户服务。
- `yudream-plugins/yudream-plugin-wallet`：钱包与资产插件。
- `yudream-plugins/yudream-plugin-alipay`：支付宝支付插件。
- `yudream-plugins/yudream-plugin-yudream-skin`：皮肤站插件。
- `yudream-plugins/yudream-plugin-authlib-injector`：Authlib Injector 兼容插件。

样例插件用于学习，不建议在生产部署中启用。

## 常用验证

```powershell
# 后端编译
mvn -pl yudream-bootstrap -am -DskipTests compile

# 前端类型检查
cd yudream-frontend
pnpm --filter @fantastic-admin/core-arco-design-vue exec vue-tsc --noEmit --pretty false --skipLibCheck --ignoreDeprecations 6.0

# 插件打包
mvn -pl yudream-plugins/yudream-plugin-wallet -am -DskipTests package
```

## 编码规范

- 所有源码文件使用 UTF-8。
- 中文文案直接写正常中文，不使用 Unicode 转义。
- 不把插件业务页面写进主前端 `apps/*/src/views`，插件页面应放在 `yudream-frontend/packages/plugin-*`。
- Controller 不直接创建 Cmd/Res，不做复杂映射，使用接口层 Assembler。
- 新增菜单按钮权限时，同步补充菜单种子和前端 `v-auth`。
