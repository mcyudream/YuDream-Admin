# YD 契约发布记录

此文档与组件/API/视觉规范同步维护，区分工作区能力、已发布能力和插件依赖，不能以源码版本代替远程验签结果。发布只使用 Nexus `npm-public`，GitLab 负责 tag 流水线，具体流程见 `.codex/skills/yudream-contract-release/`。

## 本次 YD 组件与 Agent 能力

状态：首次发布流水线在 tarball 验证前失败，发布作业未执行；修复 CI 依赖安装步骤后重试。未完成远程发布与验签前，不更新插件依赖或宣称生产可用。

| 契约 | 发布版本 | 变化 | 流水线 tag |
| --- | --- | --- | --- |
| `@yudream/components` | `1.4.0` | 主题化基础入口、FA Pro 参考通用组件、Agent 工作台和回答引用/工具卡协议；严格 YD 使用的表单与交互封装 | `pcomp-1.4.0-1`（重试标签） |
| `@yudream/plugin-sdk` | `1.9.0` | 同步全部 YD 公开组件与组合式能力的宿主共享桥 | `psdk-1.9.0-1`（重试标签） |

首次尝试 [components #377](https://gitlab.yudream.online/yudream/yudreamadmin/-/pipelines/377) / [SDK #378](https://gitlab.yudream.online/yudream/yudreamadmin/-/pipelines/378) 使用提交 `664d5791`。失败原因为 tarball 作业尚无依赖安装，pnpm 的 `verifyDepsBeforeRun: prompt` 在 CI 非交互环境退出；已添加 `pnpm install --frozen-lockfile`，保留原标签及失败记录，不移动标签或为尚未发布的包无意义改版本。

发布前验证组件/SDK 类型与构建、包结构、tarball 及 CI 发布配置；发布后验证对应 `publish:npm-*` 和 `verify:npm-contracts` jobs，再更新插件仓 catalog/lockfile 并执行下游检查。SPI 没有本次变更，不重复发布。

视觉和组件选型见 [YD 规范](yd-ui-guidelines.md)，详细 API 见 [通用组件](yd-components.md)与 [Agent 工作台](yd-agent-workbench.md)。严格 YD-only 的全局页面迁移在契约验证与下游同步完成后交给 GPT-6 Luna 执行；新增能力必须继续同步规范、预览与文档。
