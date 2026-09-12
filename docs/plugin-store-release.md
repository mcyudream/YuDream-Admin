# 插件市场发布

插件市场把可安装的插件当作不可变发布物：`{code}@{pluginVersion}` 不可覆盖。第三方作者发到某套 YuDream 实例的本机市场源；官方仓仍可另走 Nexus catalog，那是官方内部契约，不是第三方投稿通道。

## 第三方投稿

作者选一条路：

1. **开放站点投稿**：向已开放公开市场的站点申请 `upload` 权限，在后台「插件发布」上传 JAR，或用 API Key / `publish:market` 打到该站。审核由该站 `reviewRequired` 与 `accept` / `publish` 权限决定。
2. **自托管市场源**：自己启用 `plugin-market-source`，把本机 LOCAL 当供给方，对外提供 v2。订阅方添加 `{origin}/api/public/plugin-market` 为 `V2_API` 源。

不要再通过主仓 MR 的 `submission/` 目录投稿，也不要给第三方 Nexus 写凭据。格式与步骤见[第三方插件投稿](third-party-plugin-submission.md)，供给端细节见[自托管市场源](../yd-docs/docs/plugin/market-source.md)。

## 官方发布契约

独立官方插件仓的 `publish-plugin` 与 `verify-publish` 链路仍然：

1. 在官方 tag 中仅选择 `release/plugins.txt` 明确列出的完整 `yudream-plugin-*` artifactId；
2. 使用 Maven `-pl <selected modules> -am` 打包，并仅将这些模块的最终 JAR 放入 staging，避免将 `-am` 前置依赖误发布；
3. 为相同选择结果生成并发布 `plugins.manifest.tsv` 与 `sha256sum.txt`；
4. 从 Nexus `maven-public` 回读 catalog 和选择的 JAR，核对校验和与前端资源。

`PLUGIN_RELEASE_ONLY=1` 启用该清单，`PLUGIN_RELEASE_MODULES` 可用逗号或空白分隔的严格白名单覆盖它；空项、重复或未知模块均失败。普通分支/本地未启用选择时仍保留全模块行为。此机制**不使用 changed-plugin detection**。

官方发布凭据只能配置为受保护、掩码变量，并且 Nexus 发布只能由受保护 `v*` tag/manual job 执行。可选的 `publish:market` 使用 `YUDREAM_MARKET_URL` + `YUDREAM_MARKET_API_KEY`，把同一份选择结果传到自托管市场源，不使用 Nexus 写凭据。
