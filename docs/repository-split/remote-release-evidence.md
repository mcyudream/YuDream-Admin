# 远端发布证据

本文用于记录主体仓与插件仓已经独立构建，并统一通过 Nexus 发布和消费产物的证据。

## 1. 主体仓发布证据

主体仓 tag 流水线至少应证明：

1. `publish:maven-plugin-spi` 成功
2. `verify:maven-plugin-spi` 成功，并从 Nexus `maven-public` 回读 SPI
3. `publish:npm-plugin-sdk` 成功
4. `publish:npm-components` 成功
5. `verify:npm-contracts` 成功，并从 Nexus `npm-public` 回读两个包

触发前确认：

- 需要发布的 Maven/npm 版本已经更新
- CI 已配置 `NEXUS_USERNAME`、`NEXUS_PASSWORD`
- Maven release 写入 `maven-releases`，snapshot 写入 `maven-snapshots`
- `@yudream` 包发布和拉取均指向 `npm-public`

建议保存：

- tag、pipeline URL 和上述 job URL
- Maven 拉取地址：`https://nexus.yudream.online/repository/maven-public/`
- Maven 发布地址：`https://nexus.yudream.online/repository/maven-releases/` 或 `https://nexus.yudream.online/repository/maven-snapshots/`
- npm 发布/拉取地址：`https://nexus.yudream.online/repository/npm-public/`
- 从空白 Maven/npm 缓存完成回读的日志

## 2. 插件仓发布证据

插件仓远端至少应证明：

1. Maven 和 npm 契约均从 Nexus 拉取成功
2. 插件仓在没有主体仓源码的情况下可以独立构建
3. 插件 JAR 发布到 Nexus `maven-releases`
4. 已发布 JAR 可以从 Nexus 重新解析并校验前端资产

建议保存 tag、pipeline URL、构建/发布/回读 job URL，以及最终 Maven 坐标。

## 3. GitLab 边界

GitLab 只保留源码仓库、CI 流水线和流水线 artifacts。以下地址不能再作为发布证据：

- GitLab Maven Package Registry 地址
- GitLab npm Package Registry 地址
- GitLab Generic Package Registry 地址
- npmjs 包页面或 npmjs registry

上述链路仅属于迁移前历史，任何新版本必须以 Nexus 中可回读的产物为准。

## 4. 建议执行顺序

1. 主体仓运行本地拆仓审计
2. 推送主体仓改动并打 `v*` tag
3. 等待主体仓 Nexus 发布与回读成功
4. 插件仓运行本地独立性审计
5. 推送插件仓改动并打 `v*` tag
6. 等待插件仓 Nexus 发布与回读成功

只有主体仓契约包和插件仓 JAR 都能从空白缓存经 Nexus 重新拉取，才算远端发布闭环完成。
