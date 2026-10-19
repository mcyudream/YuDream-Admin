# 双仓同步地图（权威）

> **适用范围**：`yudream-admim`（主仓）与 `yudream-admin-plugins`（插件仓）。
> **目标**：规范事实一处定义、两仓一致；修改任一仓规范前查本图，命中即同工作项同步。

本文件是双仓同步关系的**唯一权威**，只存在于主仓；插件仓 `docs/dev/` 文件头部引用本图。

## 1. 强制规则（两仓 AGENTS.md 同文）

1. 修改任一仓的 `AGENTS.md`、`docs/dev/**`，或写入约束性记忆前，**必须**对照本图；命中映射的，**同一工作项内**同步对方仓对应文件，提交信息注明同步面。
2. 暂不能同步的：提交信息标 `[sync-pending]` 并记入本文件「待同步」节。
3. 两边冲突以主仓 `docs/dev` 为准，同工作项收敛。
4. 本图自身的变更同时更新两仓 AGENTS.md 的双仓同步条款。

## 2. 同步映射

| 规范事实 | 主仓（权威） | 插件仓（副本/对应） | 同步方向 |
|---|---|---|---|
| YD 视觉语言与组件选型 | `docs/yd-ui-guidelines.md` | `docs/yd-ui-guidelines.md`（同步副本） | 主仓 → 插件仓 |
| 权限动作词表 | `docs/dev/permission-codes.md` + `yudream-frontend/.../src/utils/permission-catalog.ts` | 插件仓 AGENTS/docs 对应节 + 各插件权限注册 | 主仓 → 插件仓 |
| 插件系统契约细则 | `docs/plugin-system/specification.md` | 插件仓 `docs/dev/plugin-development.md` 引用并遵守 | 主仓 → 插件仓 |
| 插件工程规范 | `docs/dev/plugin-system.md` | 插件仓 `docs/dev/plugin-development.md` | 主仓 → 插件仓（插件仓可补插件侧操作细则） |
| 契约发布流程 | `docs/dev/contract-release.md` | 插件仓发布/同步节 | 主仓 → 插件仓 |
| 后端分层硬规则 | `docs/dev/backend-architecture.md` | 插件仓后端规范节（插件内同套 DDD） | 主仓 → 插件仓 |
| AI 基座与 SPI 契约 | `docs/platform/ai-foundation.md` + `docs/plugin-system/spi-*.md` | 插件 AI 消费方对应文档/代码 | 主仓 → 插件仓（随契约发布节奏） |
| 硬规则与工程总纲 | `AGENTS.md` + `docs/dev/conventions.md` | 插件仓 `AGENTS.md` + `docs/dev/conventions.md` | 双向互检，冲突以主仓为准 |
| 组件库公开面 | `docs/yd-components.md` 等 yd-*.md | 插件仓经 SDK/components 契约消费 | 主仓 → 插件仓（随 components 发布） |

## 3. 操作要点

- 命中映射的修改：同一工作项改两边、分别提交，提交信息注明同步面（如「同步插件仓副本」）。
- 涉及未发布契约（SPI/SDK/components）的同步**必须**等发布验签通过后再落插件仓，但规范文档的同步不等待。
- 插件仓独有的操作细则（脚手架、热重载工作流、发布脚本用法）留在插件仓 `docs/dev/`，不回流主仓。
- 新增强规范事实时：先定主仓落点，再在本图登记映射，再同步插件仓。

## 4. 待同步

- 权限动作词表新增 `purge`（危险组，永久删除不可恢复；`docs/dev/permission-codes.md` §2、`docs/system/permission-management.md` 动作分类、`permission-catalog.ts` 三处已同步）：插件仓 `docs/dev/conventions.md` 按引用跟随主仓词表，本仓暂无插件注册 `purge`，待插件仓 WIP 落定后随下一次规范同步一并核对。
- `docs/dev/backend-architecture.md` 新增「账号级联删除（purge）」条目（级联回收范围、守卫与保留项）：待同步插件仓后端规范节（插件仓当前有大量未提交 WIP，先保留不动）。
- SPI 2.35.0/2.36.0/2.37.0 与 SDK `1.14.0`、components `1.10.0`（2026-10-19 v1.4.9-1 首发 SPI 2.37.0 并随 v1.4.10 升级 npm 契约；dataviz 0.1.3 随 v1.4.9-1）已发布 Nexus 并全部验签：插件仓根 pom SPI 属性、pnpm catalog/lockfile 与对应消费文档待其 WIP 落定后一并同步（catalog 直接升 1.14.0/1.10.0，跳过 1.13.0/1.9.0），验证走 `ci/verify-plugin-repo-readiness.sh`。
- SPI 2.36.0 新手引导契约（`docs/plugin-system/spi-2.36.0-guides.md` + SPI/SDK 消费）：已随主仓发布验签；待插件仓 WIP 落定后同步依赖与文档。
- `docs/yd-ui-guidelines.md` 设置页布局与响应式增强表格条目（YdSettingsLayout 居中/窄屏分区选择器、YdResponsiveEnhancedTable 卡片按 cell 渲染）：插件仓同名副本存在未提交改动，写入型 `pnpm sync:ui-docs` 会覆盖，待插件仓 WIP 落定后重跑同步。
- `docs/yd-ui-guidelines.md` 设置页保存按钮页头规范与 `YdSettingsLayout surface` 铺满白底（宿主设置页新约定），以及 YdMarkdownEditor 可控工具栏、内链插入和预览能力：插件仓同名副本存在未提交改动，先保留其 WIP，待落定后再一并 `pnpm sync:ui-docs`。
- SPI 2.37.0 飞书云文档（含 Wiki 知识空间节点列表）与站点目录文件端口（`docs/plugin-system/spi-2.37.0-feishu-and-site-files.md`、`AGENTS.md` 基线条目、`docs/plugin-system/specification.md` 第 11 节与 `docs/dev/plugin-system.md` 的对应条目 → 插件仓 `AGENTS.md`/`docs/dev/plugin-development.md` 引用与消费）：已随主仓发布验签；插件仓当前存在大量未提交 WIP（含 `docs/yd-ui-guidelines.md` 副本），待 WIP 落定后同工作项同步，先保留其 WIP 不动。
- `docs/yd-ui-guidelines.md` 的 `YdSortableTree` 拖拽落点提示与 `YdMarkdownEditor` 只读区域/错误呈现条目（本次宿主组件视觉细化）：插件仓同名副本仍有未提交改动，写入型 `pnpm sync:ui-docs` 会覆盖，待其 WIP 落定后一并重跑同步。
- `docs/yd-ui-guidelines.md` §7 目录新增 `YdTablePageLayout`、`YdDynamicSearchForm`、`YdNumberRange` 与表格页版式约定（宿主表格页规范落地），SDK `host-components.ts` 已同步导出：插件仓同名副本仍有未提交改动，待其 WIP 落定后一并 `pnpm sync:ui-docs`。
- `docs/dev/backend-architecture.md` 第 5.8 节内置基础设施（SSE/WebSocket/数据可视化直接内置为底层能力、取消双闸门与 sse 依赖声明）：待同步插件仓后端规范节（插件仓当前有大量未提交 WIP，先保留不动）。
- `docs/yd-ui-guidelines.md` §5 新增「关联实体触发器/跨页定位行」视觉约定（头像/图标 + 名称、`yd-entity-trigger` 自定义触发器交互语义、命中行主色呼吸提亮）：插件仓同名副本仍有未提交改动，待其 WIP 落定后一并 `pnpm sync:ui-docs`。
- 系统工具模块化与 MCP 服务接入（`docs/platform/ai-foundation.md` 新增「工具模块与按需加载」「MCP 服务接入」两节 + 宿主 `platform:mcp:server:*` 权限码与 `mcp:<serverCode>` 工具模块开关）：宿主侧新增能力，插件不新增 SPI/SDK 契约，插件工具只是被动归入 `plugin:<pluginCode>` 模块并受总开关/模块开关约束；待同步插件仓 AI 消费方文档一节（插件仓当前有大量未提交 WIP，先保留不动）。
- AI 助手统一网关与用户工作区（`docs/platform/ai-foundation.md`：每用户默认工作区 `platform:chat:workspace:view/edit` + `workspace.list`/`workspace.read` 只读工具；CMS 画布/站点工作区改走 chat 网关会话，删除 AG-UI WebSocket 与 CMS 专用生成/流式端点；新增平台工具模块 `workspace`）：本仓未发布新 SPI/SDK 契约，插件侧无需改代码，待插件仓 WIP 落定后同步 AI 消费方文档一节。
- `docs/yd-ui-guidelines.md` §7 新增 `YdFloatBall`（可拖拽圆球入口）与 `YdChatWindow` 非模态浮动窗口约定：插件仓同名副本仍有未提交改动，待其 WIP 落定后一并 `pnpm sync:ui-docs`。
