# 契约发布校验

主体仓与插件仓拆开以后，关键不是“目录看起来分了”，而是：

1. 主体仓发布出来的契约包是完整可消费的
2. 插件仓可以在没有本地主体源码的情况下独立消费这些契约包

## 主体仓校验

主体仓当前有两层校验。

默认发布策略上也做了收口：

- `yudream-plugin-spi` 继续按 tag 流水线发布到核心仓 GitLab Maven Registry
- `@yudream/plugin-sdk` 与 `@yudream/components` 默认面向 npmjs
- GitLab npm 私包发布改为显式开关 `GITLAB_NPM_PUBLISH_ENABLED=true` 时才执行

### 1. 文本与入口校验

```powershell
& 'C:/Program Files/Git/bin/sh.exe' ci/verify-contract-packages.sh
```

```powershell
& 'C:/Program Files/Git/bin/sh.exe' ci/verify-core-contract-publish-pipeline.sh
```

- 检查 core 仓 `.gitlab-ci.yml` 仍然保留 Maven SPI、GitLab npm、npmjs 的发布与发布后回读校验链路

作用：

- 检查 `@yudream/plugin-sdk` 的 `vite-shared.js` / `vite-shared.d.ts`
- 检查 `sync-vite-shared` / `prepack` 是否存在
- 检查 `@yudream/plugin-sdk` 与 `@yudream/components` 默认发布 registry 是否为 npmjs

### 2. tarball 校验

```powershell
& 'C:/Program Files/Git/bin/sh.exe' ci/verify-contract-package-tarballs.sh
```

- tarball 鍐?`package.json` 宸蹭笉鍐嶄繚鐣?`workspace:` / `catalog:` / `link:` / `file:` 协议
- tarball 鍐呭涓嶅寘鍚湰鍦?core/workspace 路径引用

### 3. 已发布 npm registry 回读校验

```powershell
& 'C:/Program Files/Git/bin/sh.exe' ci/verify-published-npm-contracts.sh
```

- 从目标 npm registry 重新安装 `@yudream/plugin-sdk` 与 `@yudream/components`
- 检查安装结果中的关键入口文件是否存在
- 检查已发布包的 manifest 不包含 `workspace:` / `catalog:` / `link:` / `file:` 协议
- 检查已发布包内容中不包含本地 core/workspace 路径引用

作用：

- 对 `@yudream/plugin-sdk` 执行本地 `pnpm pack`
- 对 `@yudream/components` 执行本地 `pnpm pack`
- 检查打包结果里是否真的包含对外消费必须存在的文件

## 插件仓校验

插件仓当前也有两层远程消费校验。

### 1. Maven 契约校验

```powershell
& 'C:/Program Files/Git/bin/sh.exe' ci/verify-core-maven-registry.sh
```

作用：

- 在空白本地 Maven 仓中重新解析 `online.yudream.base:yudream-plugin-spi`

### 2. npm 契约校验

```powershell
& 'C:/Program Files/Git/bin/sh.exe' ci/verify-core-npm-contracts.sh
```

- 安装下来的包 manifest 不保留 `workspace:` / `catalog:` / `link:` / `file:` 协议
- 安装下来的包内容中不包含本地 core/workspace 路径引用

作用：

- 在临时空目录中重新安装 `@yudream/plugin-sdk`
- 在临时空目录中重新安装 `@yudream/components`
- 检查安装结果中是否存在 `vite-shared.js`、`vite-shared.d.ts`、`resolver.ts`
