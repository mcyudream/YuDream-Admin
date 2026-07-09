# 远端发布证据

这份文档只服务一个目标：

在真正推送并打 `v*` tag 之后，怎样拿到“主体仓与插件仓已经真正拆开并且能独立发布”的远端证据。

## 1. 主体仓需要证明什么

主体仓远端至少要拿到下面这些直接证据：

1. `publish:maven-plugin-spi` 成功
2. `verify:maven-plugin-spi` 成功
3. 如果开启 GitLab npm：
   - `publish:npm-plugin-sdk` 成功
   - `publish:npm-components` 成功
   - `verify:gitlab-npm-contracts` 成功
4. 如果开启 npmjs：
   - `publish:npmjs-plugin-sdk` 成功
   - `publish:npmjs-components` 成功
   - `verify:npmjs-contracts` 成功

## 2. 主体仓触发前准备

- 确认 `.gitlab-ci.yml` 已包含 `verify-packages` 阶段
- 确认需要发布的版本号已经写入：
  - `yudream-plugins/yudream-plugin-spi/pom.xml`
  - `yudream-frontend/packages/plugin-sdk/package.json`
  - `yudream-frontend/packages/components/package.json`
- 如果要发 GitLab npm，确认：
  - `GITLAB_NPM_PUBLISH_ENABLED=true`
- 如果要发 npmjs，确认：
  - `NPMJS_PUBLISH_ENABLED=true`
  - `NPM_TOKEN` 已配置

## 3. 主体仓建议保留的证据

至少截图或记录：

- tag 名称
- pipeline URL
- `publish:maven-plugin-spi` job URL
- `verify:maven-plugin-spi` job URL
- `verify:gitlab-npm-contracts` job URL（如果启用）
- `verify:npmjs-contracts` job URL（如果启用）

还建议记录最终包地址：

- Maven:
  - `https://gitlab.yudream.online/api/v4/projects/12/packages/maven`
- GitLab npm:
  - `${CI_API_V4_URL}/projects/${CI_PROJECT_ID}/packages/npm/`
- npmjs:
  - [@yudream/plugin-sdk](https://www.npmjs.com/package/@yudream/plugin-sdk)
  - [@yudream/components](https://www.npmjs.com/package/@yudream/components)

## 4. 插件仓需要证明什么

插件仓远端至少要拿到下面这些直接证据：

1. `validate:core-maven-registry` 成功
2. `validate:core-npm-contracts` 成功
3. `package:plugins` 成功
4. `publish:plugin-jars` 成功
5. `verify:published-plugin-jars` 成功

## 5. 插件仓触发前准备

- 确认主体仓对应版本已经发布
- 确认插件仓 `pnpm-workspace.yaml` 的 contract 版本与主体仓一致
- 确认插件仓 `pom.xml` 中 `yudream.plugin.spi.version` 与主体仓一致
- 如果插件仓要通过 Job Token 读主体仓，确认 allowlist 仍然有效
- 如果不用 Job Token，确认：
  - `CORE_PACKAGE_USER`
  - `CORE_PACKAGE_TOKEN`

## 6. 插件仓建议保留的证据

至少截图或记录：

- tag 名称
- pipeline URL
- `validate:core-maven-registry` job URL
- `validate:core-npm-contracts` job URL
- `package:plugins` job URL
- `publish:plugin-jars` job URL
- `verify:published-plugin-jars` job URL

还建议记录 Generic Package Registry 地址：

```text
${CI_API_V4_URL}/projects/${CI_PROJECT_ID}/packages/generic/yudream-admin-plugins/${CI_COMMIT_TAG}/
```

## 7. 建议执行顺序

1. 在主体仓跑本地审计：

```powershell
& 'C:/Program Files/Git/bin/sh.exe' ci/verify-core-split-readiness.sh
```

2. 推送主体仓拆仓相关改动并打 `v*` tag
3. 等主体仓远端发布与回读校验成功
4. 在插件仓跑本地审计：

```powershell
& 'C:/Program Files/Git/bin/sh.exe' ci/verify-plugin-repo-readiness.sh
```

5. 推送插件仓拆仓相关改动并打 `v*` tag
6. 等插件仓远端发布与回读校验成功

## 8. 完成标准

只有当下面两件事都成立，才算远端证据闭环：

1. 主体仓远端已经证明契约包能发布、能被重新安装/解析
2. 插件仓远端已经证明可以只消费这些已发布契约，并独立发布自己的 JAR
