# 契约发布校验

主体仓与插件仓拆开以后，关键不是“目录看起来分了”，而是：

1. 主体仓发布出来的契约包完整且可消费
2. 插件仓在没有本地主体源码的情况下，可以从 Nexus 独立消费这些契约包

## 统一仓库约定

- 通用 Maven 依赖和插件优先从阿里云公共仓库拉取，未命中时回退 `https://nexus.yudream.online/repository/maven-public/`；YuDream Maven 契约最终从 Nexus 拉取
- Maven release 发布到 `https://nexus.yudream.online/repository/maven-releases/`
- Maven snapshot 发布到 `https://nexus.yudream.online/repository/maven-snapshots/`
- `@yudream/plugin-sdk` 与 `@yudream/components` 的发布和拉取统一使用 `https://nexus.yudream.online/repository/npm-public/`
- Maven/npm 契约允许匿名拉取；发布写入使用受保护、掩码的 `NEXUS_USERNAME`、`NEXUS_PASSWORD`
- GitLab 只负责源码托管、CI 流水线和 CI artifacts，不再作为 Maven/npm 包仓库
- GitLab Package Registry 和 npmjs 发布链路已经废弃，不得作为回退目的地

## 主体仓校验

### 1. 文本与入口校验

```powershell
& 'C:/Program Files/Git/bin/sh.exe' ci/verify-contract-packages.sh
& 'C:/Program Files/Git/bin/sh.exe' ci/verify-core-contract-publish-pipeline.sh
```

校验内容：

- `@yudream/plugin-sdk` 的 `vite-shared.js` / `vite-shared.d.ts` 等发布入口存在
- `sync-vite-shared` / `prepack` 存在
- 两个 `@yudream` 包的 `publishConfig.registry` 都是 Nexus `npm-public`
- CI 只保留 Nexus Maven/npm 发布及发布后回读链路

### 2. tarball 校验

```powershell
& 'C:/Program Files/Git/bin/sh.exe' ci/verify-contract-package-tarballs.sh
```

- tarball 内 `package.json` 不得保留 `workspace:` / `catalog:` / `link:` / `file:` 协议
- tarball 内容不得包含本地 core/workspace 路径引用

### 3. Nexus npm 回读校验

```powershell
& 'C:/Program Files/Git/bin/sh.exe' ci/verify-published-npm-contracts.sh
```

- 从 Nexus `npm-public` 重新安装 `@yudream/plugin-sdk` 与 `@yudream/components`
- 检查安装结果中的关键入口文件
- 检查发布包 manifest 和内容不依赖本地 workspace

## 插件仓消费校验

### 1. Maven 契约

```powershell
& 'C:/Program Files/Git/bin/sh.exe' ci/verify-core-maven-registry.sh
```

在空白本地 Maven 仓中，从阿里云解析 Maven 插件，并通过 Nexus `maven-public` 重新解析 YuDream 契约
`online.yudream.base:yudream-plugin-spi`。

## Graph 完整投影 SPI

`PluginGraphService.replaceCompleteProjection(PluginGraphCompleteProjection)` 仅接收 namespace、version、节点和关系；它不接受 tableCode 或 Cypher。宿主每次调用都会检查 Neo4j capability，并自动解析调用插件唯一的 ACTIVE 且已授权图表绑定。完整投影上限为 20,000 个节点、50,000 条关系和 8 MiB UTF-8 JSON 负载；超出时返回结构化 `PROJECTION_LIMIT_EXCEEDED`，不会触发图数据库 gateway。普通 `replaceProjection` 仍保持每类 1,000 条限制。

### 2. npm 契约

```powershell
& 'C:/Program Files/Git/bin/sh.exe' ci/verify-core-npm-contracts.sh
```

在临时空目录中，通过 Nexus `npm-public` 重新安装两个 `@yudream` 包，并检查：

- 安装后的 manifest 不含本地依赖协议
- 包内容不含 core/workspace 路径引用
- `vite-shared.js`、`vite-shared.d.ts`、`resolver.ts` 等约定入口存在
