# YD 契约发布记录

此文档与组件/API/视觉规范同步维护，区分工作区能力、已发布能力和插件依赖，不能以源码版本代替远程验签结果。发布只使用 Nexus `npm-public`，GitLab 负责 tag 流水线，具体流程见 `.codex/skills/yudream-contract-release/`。

## 本次 YD 组件与 Agent 能力

状态：发布与远程验签均已通过。`@yudream/components 1.4.0` 与 `@yudream/plugin-sdk 1.9.0` 已发布到 Nexus `https://nexus.yudream.online/repository/npm-public/`。工作区随后新增的能力不属于这两个已发布版本，必须单独验证和发布后才能用于生产插件。

| 契约 | 发布版本 | 变化 | 流水线 tag |
| --- | --- | --- | --- |
| `@yudream/components` | `1.4.0` | 主题化基础入口、FA Pro 参考通用组件、Agent 工作台和回答引用/工具卡协议；严格 YD 使用的表单与交互封装 | `pcomp-1.4.0-1`（重试标签） |
| `@yudream/plugin-sdk` | `1.9.0` | 同步全部 YD 公开组件与组合式能力的宿主共享桥 | `psdk-1.9.0-1`（重试标签） |

首次尝试 [components #377](https://gitlab.yudream.online/yudream/yudreamadmin/-/pipelines/377) / [SDK #378](https://gitlab.yudream.online/yudream/yudreamadmin/-/pipelines/378) 使用提交 `664d5791`。失败原因为 tarball 作业尚无依赖安装，pnpm 的 `verifyDepsBeforeRun: prompt` 在 CI 非交互环境退出；已添加 `pnpm install --frozen-lockfile`，保留原标签及失败记录，不移动标签或为尚未发布的包无意义改版本。

修复提交 `00a2e408` 的重试结果：

| 包 | 流水线 | 发布作业 | 远程验签作业 | 结果 |
| --- | --- | --- | --- | --- |
| components `1.4.0` | [#379](https://gitlab.yudream.online/yudream/yudreamadmin/-/pipelines/379) | [#3495](https://gitlab.yudream.online/yudream/yudreamadmin/-/jobs/3495) | [#3496](https://gitlab.yudream.online/yudream/yudreamadmin/-/jobs/3496) | 8 个作业全部通过 |
| plugin-sdk `1.9.0` | [#380](https://gitlab.yudream.online/yudream/yudreamadmin/-/pipelines/380) | [#3503](https://gitlab.yudream.online/yudream/yudreamadmin/-/jobs/3503) | [#3504](https://gitlab.yudream.online/yudream/yudreamadmin/-/jobs/3504) | 8 个作业全部通过 |

发布前验证组件/SDK 类型与构建、包结构、tarball 及 CI 发布配置；发布后验证对应 `publish:npm-*` 和 `verify:npm-contracts` jobs，再更新插件仓 catalog/lockfile 并执行下游检查。SPI 没有本次变更，不重复发布。

视觉和组件选型见 [YD 规范](yd-ui-guidelines.md)，详细 API 见 [通用组件](yd-components.md)与 [Agent 工作台](yd-agent-workbench.md)。严格 YD-only 的全局页面迁移已由 GPT-6 Luna 在契约验证与下游同步完成后执行；新增能力必须继续同步规范、预览与文档。

## 全局迁移补充契约（已发布与验签）

`components 1.5.0` / `plugin-sdk 1.10.0` 已发布并验签，新增 `YdFilePicker`、`YdMultiSelect`、`YdCodeEditor` 与 `YdMarkdownEditor`，该版本公开组件数为 131。搜索多选保留历史隐藏/禁用 ID，表单属性绑定到实际输入；代码与内容编辑支持受控模型、只读、禁用、主题与销毁。图标的开发环境日志不再要求消费者声明 `ImportMeta.env`。

发布提交为 `746c5fe9`，tag 为 `pcomp-1.5.0` / `psdk-1.10.0`。[components #381](https://gitlab.yudream.online/yudream/yudreamadmin/-/pipelines/381) 与 [SDK #382](https://gitlab.yudream.online/yudream/yudreamadmin/-/pipelines/382) 的校验、发布和远程验签阶段均已通过。初次 PUT 返回 502；修复 Runner 网络后重试对应作业，保留原 tag 和版本，不覆盖发布物。

本机从 Nexus 回读的完整性值：

| 契约 | `dist.integrity` |
| --- | --- |
| components `1.5.0` | `sha512-jEKShr9F4bqDumjfTKZtiFJ8jB8WXJQyPp7WBfVPA2xrzo/RyNHD6PqN46Oke02R1mgR6Ih3gpu5DdaZmUj66w==` |
| SDK `1.10.0` | `sha512-3cCoBvLHd+IHqBZOiwy0ywY8OXkll8BHh/3r4OpBZV1w//dlm7eQGrePqX6NrI9MLr+2gH/QWjC1gF+m2PnNww==` |

插件仓 catalog/lockfile 已安装这两个已验签版本，后续迁移与下游验证单独提交。可排序树、工作流画布等契约不属于 `1.5.0` / `1.10.0`，已在下一轮 `1.6.0` / `1.11.0` 完成发布与验签。

首轮已验签契约的插件仓同步提交为 `5f00d0c`，catalog/lockfile 已更新并推送；下游就绪检查通过。该阶段由 GPT-6 Luna 分批实施；技术编辑器、拖拽树和页面交互在下一轮 1.6.0 / 1.11.0 契约及全局迁移中完成。

## 全局迁移收尾契约（已发布与验签）

源码版本为 components `1.6.0` / SDK `1.11.0`，重试 tag 为 `pcomp-1.6.0-1` / `psdk-1.11.0-1`。新增五个组件入口：`YdSortableTree`、`YdWorkflowCanvas`、`YdWorkflowHandle`、`YdLoadingOverlay`、`YdPageBuilder`，总数 136；同时补充 `useYdFilePicker`（含目录选择）、`useYdDomAction`、可创建搜索选择和跨断点受控表格选择。发布、远程验签与插件依赖安装均已完成。

首次提交 `6676e2d5` 的 [#383](https://gitlab.yudream.online/yudream/yudreamadmin/-/pipelines/383) / [#384](https://gitlab.yudream.online/yudream/yudreamadmin/-/pipelines/384) 在 tarball 的目录一致性检查失败，尚未上传：提交遗漏了权威 `yd-ui-guidelines.md` 中的五个新增入口。补齐目录、语法树扫描及规范同步后，在完整暂存源码的隔离副本执行同一目录检查与历史基线检查通过，保留失败标签并新建重试标签。

页面构建器消费 FA 主题及三套 YD 预设的变量，编辑器实例、上传与延迟初始化均由组件清理；流程画布保留受控节点、连线、节点插槽及只读/禁用语义。动态挂载使用真实 YD 动作，不能在业务中创建原生按钮。Markdown 更新官方渲染器并以实例标识隔离安全 HTML 解析，回答预览继续保留安全默认值。

重试提交 `17d10660` 的 [components #385](https://gitlab.yudream.online/yudream/yudreamadmin/-/pipelines/385) / [SDK #386](https://gitlab.yudream.online/yudream/yudreamadmin/-/pipelines/386) 均通过校验、发布、远程验签和新增 YD 规范门禁。Nexus 回读完整性值分别为 `sha512-f36im/1IyhyeIBfD8sJYuR6KVdVS6rn4hmdsYa6fTVJVGBsXMCWpvF5YWOAcVP9PPDIjmOCXoJ0LjKGQqCFY2w==`、`sha512-3x7BwXXaH2To2miKgH6m+3V3UZqK+5YkZy3iCtiKtHdPGK0gLsZQZIQZDYdZKZoZD4Hza1h8r6hUspYscKd02w==`。插件 catalog/lockfile 已安装这组验签版本；全局迁移和下游验证已独立完成，见下节。

## GPT-6 Luna 全局规范验收

宿主 UI 迁移提交为 `1710e220`，新增实时预览为 `228195ef`，均已推送；插件 `main` 按 30 个模块分别提交迁移，门禁收尾提交为 `07153a4`，已推送。保留原业务 API 与其他任务未提交修改，官方 Web 插件消费已验签的 components `1.6.0` / SDK `1.11.0`。

- 宿主已提交快照 `228195ef`：412 个界面源文件、0 条 YD 规范违规；核心类型检查、生产构建与组件预览类型/构建通过。
- 插件已提交快照 `e7073161063638460c8d5a7c59f8f2cbd4939f57`：650 个 Web 源文件、0 条违规；25 个类型检查脚本和 33 个 Web 构建通过，下游就绪检查通过。
- 两仓基线保持 `findings: {}`，保留验收 SHA；Git 固定快照测试确认中文文件、缺少主题元数据和未提交内容不会绕过检查。规范、AGENTS、skills 与公开文档同步维护。

插件迁移交付为源码及依赖同步；此轮没有发布业务插件 JAR 到市场。后续插件发布须按各模块发布流程构建、验签与上架，不能以 Web 构建通过代替部署结果。
