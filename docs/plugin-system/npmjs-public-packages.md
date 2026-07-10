# npmjs 发布迁移说明（已废弃）

本文保留旧文件名仅用于兼容历史链接。直接发布到 npmjs.com 的链路已经废弃，`@yudream` 公共包统一发布到并从 YuDream Nexus 拉取。

## 当前唯一 YuDream npm 仓库

- registry：`https://nexus.yudream.online/repository/npm-public/`
- scope：`@yudream`
- 包：`@yudream/plugin-sdk`、`@yudream/components`
- 匿名读取；发布使用受保护、掩码的 `NEXUS_USERNAME`、`NEXUS_PASSWORD`

包内必须声明：

```json
{
  "publishConfig": {
    "registry": "https://nexus.yudream.online/repository/npm-public/"
  }
}
```

本地或 CI 的 `.npmrc` 应让第三方依赖继续走 npmjs/npmmirror，仅将 `@yudream` scope 指向 Nexus：

```bash
printf '%s\n' \
  'registry=https://registry.npmmirror.com/' \
  '@yudream:registry=https://nexus.yudream.online/repository/npm-public/' > .npmrc
```

发布命令必须显式使用 Nexus：

```bash
cd yudream-frontend
pnpm install --frozen-lockfile
pnpm --filter @yudream/plugin-sdk run build
pnpm --filter @yudream/plugin-sdk publish --no-git-checks --registry https://nexus.yudream.online/repository/npm-public/

pnpm --filter @yudream/components run build
pnpm --filter @yudream/components publish --no-git-checks --registry https://nexus.yudream.online/repository/npm-public/
```

## 历史配置处置

以下迁移前配置已经废弃，不得重新启用：

- 将 npmjs registry 作为 `@yudream` 包的发布目标
- `NPM_TOKEN`
- npmjs 发布开关和 npmjs 专用发布任务
- GitLab npm Package Registry 及其 token

GitLab 只负责触发发布流水线和保存 CI artifacts，不保存 npm 包产物。
