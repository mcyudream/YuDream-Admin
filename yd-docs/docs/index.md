---
layout: home

hero:
  name: YuDream Admin
  text: 企业级 DDD 后台框架与插件生态
  tagline: 可直接当成品部署的管理后台，也可作为主框架二次开发；17 项平台能力按需加载，插件机制无限扩展
  actions:
    - theme: brand
      text: 快速开始 →
      link: /guide/getting-started
    - theme: alt
      text: 框架概览
      link: /guide/introduction
    - theme: alt
      text: 插件开发
      link: /plugin/overview

features:
  - icon: 🧩
    title: 开箱即用
    details: 拉取 backend / frontend / render-server / kkfileview 官方镜像即可运行，业务差异全部通过插件 JAR 扩展，不改主框架源码。
    link: /guide/usage-modes
    linkText: 了解使用方式
  - icon: 🏗️
    title: 严谨的 DDD 分层
    details: domain / application / infrastructure / interfaces / bootstrap 五模块，聚合、仓储、应用服务边界清晰。
    link: /guide/architecture
    linkText: 查看系统架构
  - icon: ⚡
    title: 17 项平台能力双闸门
    details: SSE、WebSocket、MQ、Neo4j、AI/Agent、CMS、文件预览、入站邮箱等能力按需动态加载，项目闸门 + 应用闸门双重管控，不用的能力零开销。
    link: /guide/platform-capabilities
    linkText: 平台能力详解
  - icon: 🔌
    title: 一流的插件体系
    details: SPI 契约 + remoteEntry 动态前端 + SDK 注入，支持热加载、热卸载、硬/软依赖与依赖级联。
    link: /plugin/overview
    linkText: 插件开发指南
---

## 特性矩阵

| 维度 | 内容 |
|---|---|
| 平台能力（17 项） | api-docs、cms、wiki、form、document-template、integration、sse、websocket、rabbitmq、neo4j、ai、agent、dataviz、milky、message-render、file-preview、inbound-mail，全部受项目闸门（`yudream.platform.capabilities.<code>.enabled`）与应用闸门双重管控，支持依赖级联禁用 |
| 插件体系 | 第三方插件仅依赖 `yudream-plugin-spi` 契约模块；JAR 内置 `plugin.yml` + 前端 `remoteEntry.js`；支持热加载/热卸载、硬依赖（`depend`）/软依赖（`softdepend`）、依赖级联与插件商店 |
| 安全体系 | Sa-Token 双 Token 会话、接口加密、API Key、Passkey（WebAuthn）、OAuth，全部属于 system 基线能力，始终可用、不走动态开关 |
| AI / Agent | provider-first 多模型配置（`providerCode + modelCode`），Spring AI 原生 tool calling，端到端真流式 SSE 与可扩展事件信封（`ai.message` / `ai.tool` / `ai.result` / `ai.error`） |
| CMS 可视化建站 | GrapesJS 拖拽构建，完整发布闭环：权限菜单、管理/公开路由、发布/下线、SEO、页面与模板元数据 |
| 渲染服务 | 独立 render-server（Fastify 5 + Playwright Chromium），HTML / Markdown / URL → 图片，支撑消息卡片、证书证明等场景 |

## 两种使用方式

| | 方式一：成品 + 插件扩展 | 方式二：二次开发主框架 |
|---|---|---|
| 适用场景 | 需要一个成熟后台，业务差异用插件补齐 | 需要修改主框架本身：新增平台能力、深度定制 |
| 你要做的 | `docker compose up -d` 拉起官方镜像（含 kkFileView），后台安装插件 JAR | 克隆源码，JDK 21 + Maven / Node + pnpm 开发 |
| 与插件的关系 | 只维护插件，主框架持续升级不破坏插件（SPI 契约解耦） | 二次开发产物仍保留完整插件体系，插件无需重写 |

详见 [两种使用方式](/guide/usage-modes)。

## 技术栈

| 层 | 技术 |
|---|---|
| 后端 | YuDream 原创实现，基于 JDK 21、Spring Boot 3.5、Sa-Token 1.45、MongoDB、Redis、Spring AI 1.1、EasyExcel 4 |
| 前端 | 使用 [Fantastic-admin](https://fantastic-admin.hurui.me/)，基于 Vue 3.5、Vite 8、Arco Design Vue 2.58、Pinia 3、pnpm workspace；`Fa*` 为 Fantastic-admin 自带组件（`FaResponsiveTable` 除外），`Yd*` 为 YuDream 原创组件与 composable，不按 AI/非 AI 区分 |
| 渲染服务 | Node ≥ 22、Fastify 5、Playwright（Chromium headless）、markdown-it |
| 部署 | Docker Compose（backend / frontend / render-server / kkfileview + watchtower 自动更新） |
