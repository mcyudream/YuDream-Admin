# npmjs 公网包发布

本文约定核心仓可以把前端公共包直接发布到 npmjs.com。

当前已接入的包：

- `@yudream/plugin-sdk`
- `@yudream/components`

## 1. 发布前提

1. npm 账号具备目标 scope 的发布权限
2. GitLab CI 或本地环境配置 `NPM_TOKEN`
3. 首次发布 scoped 公共包时使用 `public` 访问级别

当前仓库已经在包内声明：

- `publishConfig.registry = https://registry.npmjs.org/`
- `publishConfig.access = public`

## 2. 本地发布

把示例文件复制为临时 `.npmrc` 或写入你的用户级 `.npmrc`：

```ini
registry=https://registry.npmjs.org/
@yudream:registry=https://registry.npmjs.org/
//registry.npmjs.org/:_authToken=${NPM_TOKEN}
```

本地发布命令：

```bash
cd yudream-frontend
pnpm install --frozen-lockfile
pnpm --filter @yudream/plugin-sdk run build
pnpm --filter @yudream/plugin-sdk publish --no-git-checks --access public --registry https://registry.npmjs.org/
```

```bash
cd yudream-frontend
pnpm install --frozen-lockfile
pnpm --filter @yudream/components run build
pnpm --filter @yudream/components publish --no-git-checks --access public --registry https://registry.npmjs.org/
```

## 3. GitLab CI 发布

核心仓 `.gitlab-ci.yml` 已提供两个 npmjs 发布任务：

- `publish:npmjs-plugin-sdk`
- `publish:npmjs-components`

触发条件：

- Git tag 匹配 `^v`
- `NPMJS_PUBLISH_ENABLED=true`
- CI 变量中存在 `NPM_TOKEN`

推荐在 GitLab CI 中配置：

- `NPM_TOKEN`
- `NPMJS_PUBLISH_ENABLED`

## 4. 权限检查

如果 `npm publish` 报 scope 无权限，优先检查：

1. 目标 scope 是否已在 npm 上创建
2. 当前 token 对应账号是否为该 scope 的 owner 或具备发布权限
3. 包是否首次发布，且使用了 `--access public`
