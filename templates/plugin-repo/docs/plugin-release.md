# 插件仓发布说明

## 目标

独立插件仓除了独立构建，还应独立发布可部署插件 JAR。

## CI 流程

模板中的 `.gitlab-ci.yml.example` 默认拆成五段：

1. `validate`
   - `ci/verify-plugin-repo-independence.sh`
   - `ci/verify-core-maven-registry.sh`
   - `ci/verify-plugin-maven-boundary.sh`
   - `ci/verify-core-npm-contracts.sh`
   - `ci/verify-plugin-publish-pipeline.sh`
2. `build-frontend`
3. `package-plugin`
   - 使用单独的干净 Maven 本地仓目录重新解析依赖
   - 完成 Maven 打包后校验最终插件 JAR 内的 `remoteEntry.js`
4. `publish-plugin`
5. `verify-publish`

其中 `publish-plugin` 只在 Git tag 流水线执行，并调用 `ci/publish-plugin-jars.sh` 上传插件包。

`verify-publish` 会在发布完成后调用 `ci/verify-published-plugin-jars.sh`，重新从 GitLab Generic Package Registry 拉回：
- `sha256sum.txt`
- `plugins.manifest.tsv`
- 每个最终插件 JAR

然后逐个核对索引文件和 JAR 校验和，确认发布出去的内容与本次构建产物一致。

`ci/verify-plugin-jar-assets.sh` 还应额外保证最终插件 JAR 中不包含
`online/yudream/base/plugin/spi/*` 类文件，避免把主体 SPI 契约重新打进插件产物。

## 前端工作区边界

插件仓前端工作区应保持为：

```yaml
packages:
  - packages/plugin-*
```

不要恢复 `packages/*`，也不要把 `@yudream/plugin-sdk`、`@yudream/components` 的源码放进插件仓。

插件仓 CI 的前端构建入口也应只匹配：
`yudream-frontend/packages/plugin-*/package.json`

## 发布产物

发布脚本会为每个插件模块只选择一个最终包：

- 有 `*-shaded.jar` 就发布 `*-shaded.jar`
- 没有就发布普通 `*.jar`

并额外上传：

- `sha256sum.txt`
- `plugins.manifest.tsv`

## 默认发布地址

```text
${CI_API_V4_URL}/projects/${CI_PROJECT_ID}/packages/generic/yudream-admin-plugins/${CI_COMMIT_TAG}/
```

## 需要的变量

默认依赖：

- `CI_API_V4_URL`
- `CI_PROJECT_ID`
- `CI_COMMIT_TAG`
- `CI_JOB_TOKEN`

可选覆盖：

- `PLUGIN_GENERIC_PACKAGE_NAME`
- `PLUGIN_PACKAGE_VERSION`
