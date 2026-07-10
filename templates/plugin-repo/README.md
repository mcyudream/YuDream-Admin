# 插件仓模板

这个目录用于初始化未来的独立插件仓，例如：

- `yudream-admin-plugins`

## 模板包含

- `.gitlab-ci.yml.example`
- `.npmrc.example`
- `settings.xml.example`
- `pnpm-workspace.yaml.example`
- `ci/verify-plugin-repo-independence.sh`
- `ci/stage-plugin-repo-foundation.sh`
- `ci/stage-plugin-source-migration.sh`
- `ci/verify-core-maven-registry.sh`
- `ci/verify-plugin-maven-boundary.sh`
- `ci/verify-core-npm-contracts.sh`
- `ci/verify-plugin-jar-assets.sh`
- `ci/publish-plugin-jars.sh`
- `ci/verify-published-plugin-jars.sh`
- `docs/plugin-release.md`

## 默认定位

这个模板面向“官方业务插件独立仓”场景。

建议放在独立插件仓中的内容：

- 官方业务插件后端模块
- 官方业务插件前端 remote 包
- 插件仓自己的独立 GitLab CI

`core` 仓更适合保留：

- `yudream-plugin-spi`
- 宿主运行时
- 共享前端包，例如 `@yudream/plugin-sdk`、`@yudream/components`
- 样例插件
- 迁移期间必要的兼容层

换句话说，新建官方业务插件时，优先在这个模板初始化出的独立仓中开发，而不是继续把源码回填到主体仓。

## 使用建议

1. 复制模板到新仓根目录
2. 在新仓 GitLab CI variables 中配置受保护、掩码的发布凭据，并保护 `v*` tag：
   - `NEXUS_USERNAME`
   - `NEXUS_PASSWORD`
3. Maven 优先从阿里云公共仓库拉取通用依赖，缺失时回退 Nexus；YuDream 契约从 `maven-public` 拉取，插件 JAR 发布到 `maven-releases`
4. 让插件仓只依赖正式发布的：
   - Maven: `online.yudream.base:yudream-plugin-spi`
   - npm: `@yudream/plugin-sdk`
   - npm: `@yudream/components`
5. 第三方 npm 包继续使用公共源，`@yudream` scope 统一从 Nexus `npm-public` 拉取
6. 前端 workspace 只保留 `packages/plugin-*`，不要恢复成 `packages/*`
7. 插件仓 CI 的前端构建入口也只匹配 `yudream-frontend/packages/plugin-*/package.json`
8. 共享包只使用公开入口，例如 `@yudream/plugin-sdk`、`@yudream/plugin-sdk/vite-shared`，不要依赖 `src/*` 内部路径
