# YuDream Admin

> **适用范围**：`D:/code/yudream-admim` 主仓全部工作。
> **目标**：提供唯一工程规则入口、最短必读路径与双仓同步纪律。

## 1. 项目卡

| 项 | 内容 |
|---|---|
| 类型 | Java 21 / Spring Boot 后端 + Vue 3 / TypeScript 前端，插件化后台平台 |
| 主仓 | `yudream-admim` |
| 插件仓 | `D:/code/yudream-admin-plugins`，官方业务插件前后端源码唯一归属 |
| 契约 | `yudream-plugin-spi`、`@yudream/plugin-sdk`、`@yudream/components` |
| 前端 | `yudream-frontend/`，宿主 app + 共享包；组件权威 `docs/yd-ui-guidelines.md` |

## 2. 开始工作前必读

- 全部任务：本文件 + `docs/dev/conventions.md`。
- Java 后端：`docs/dev/backend-architecture.md`；插件运行时再读 `docs/dev/plugin-system.md`。
- 前端 UI：`yudream-frontend/AGENTS.md`、`docs/dev/frontend.md`、`docs/yd-ui-guidelines.md`。
- 权限变更：`docs/dev/permission-codes.md`、`docs/system/permission-management.md`。
- 契约版本/发布/下游同步：`docs/dev/contract-release.md`。
- 跨仓规范变更或写入约束性记忆：先查 `docs/dev/repo-sync.md`。
- 处理已知故障：查 `docs/exp/README.md` 的分类索引；AI 运行另读 `docs/platform/ai-foundation.md`。
- **AGENTS.md 是硬规则唯一入口；`docs/dev/` 是规范正文；`docs/exp/` 是经验库。不得再建立 skill 作为项目规范权威。**

## 3. 硬规则

1. **双仓同步**：修改任一仓的 `AGENTS.md`、`docs/dev/**`，或写入约束性记忆前，必须对照主仓 `docs/dev/repo-sync.md`。命中映射的同一工作项内同步对方仓对应文件，提交注明同步面；暂不能同步则提交标 `[sync-pending]` 并登记待同步。规范冲突以主仓 `docs/dev/` 为准。两仓 AGENTS.md 本条规则保持同文并互检。
2. **保留用户改动**：不覆盖既有未提交修改；不顺手重构无关文件；提交只暂存本模块文件。
3. **分层**：应用 service 不接收接口 request、不返回 res；dataobj 不越出 infra；Controller 不构造 Cmd/Res/ExcelRow、不写 mapping helper/业务不变量。完整边界见 `docs/dev/backend-architecture.md`。
4. **平台能力双闸门**：项目配置闸门 + 每用例应用闸门；未通过不得注册端点/恢复/连接外部资源；能力依赖声明并按级联语义启停。
5. **插件隔离**：插件唯一 compile-time 宿主依赖是 SPI；不得依赖宿主内部层。JAR 根 `plugin.yml` 权威；依赖图无环；贡献经 PluginContext 并可完整回收。详见 `docs/dev/plugin-system.md` 与 `docs/plugin-system/specification.md`。
6. **ID 精度**：Java Long/Snowflake 跨 JSON、插件、TypeScript、表单与 URL 一律字符串，禁止 `Number(id)`。
7. **权限动作**：末段动作必须使用既定词表；按资源拆分查看/操作/危险权限。详见 `docs/dev/permission-codes.md`。
8. **中文与编码**：正常 UTF-8 中文；禁止 `\uXXXX` 文案和真实乱码。PowerShell 显示乱码不作为改文件依据。
9. **前端组件**：宿主与插件业务只用 `@yudream/components` 公开 YD 入口；缺能力先封装 YD 并同步公开面/文档/门禁，禁止 Fa/Arco 等业务直用。详见 `docs/dev/frontend.md`。
10. **提交与外部动作**：模块完成后先验证、再按模块创建中文提交；未经明确要求不 push/tag/publish。契约只发布至 Nexus，按 `docs/dev/contract-release.md` 执行。
11. **机密**：凭据、本机路径、token 只存被忽略的 `docs/local.md` 或系统凭据管理器；严禁提交或复制到规范、经验、issue、日志。计划放在被忽略的 `docs/plan/`；`docs/plans/` 为冻结历史归档。

## 4. 沟通与推进

- 用户明确要求实现/修复时，按规范自主推进到实现、验证与结果汇报；不得把常规实施选择反复交回用户。
- 用户在提问、评估或头脑风暴时，先给判断，不擅自改代码。
- 破坏性、不可逆或对外动作先确认；读取、分析、可逆本地改动及按明确要求的工作自主完成。
- 发现验证失败如实报告，不将单测描述成运行环境启动验收。

## 5. 当前工程基线（2026-10-19）

- 后端 AI 底座使用 AgentScope Java Harness，AI 持久运行/工具/额度规则见 `docs/platform/ai-foundation.md`。
- 插件持久 AI 运行契约见 `docs/plugin-system/spi-2.35.0-ai-runs.md`；SPI 2.35.0/2.36.0/2.37.0 已随主仓 `v1.4.9-1` 全量发布 Nexus 并验签（2026-10-19），插件可消费 2.37.0；插件仓依赖与文档同步待其 WIP 落定（见同步地图待同步节）。
- 插件新手引导契约见 `docs/plugin-system/spi-2.36.0-guides.md`（SPI 2.36.0 本仓已升版）；发布状态同上，插件仓同步待办。
- 插件飞书云文档与站点目录文件契约见 `docs/plugin-system/spi-2.37.0-feishu-and-site-files.md`（SPI 2.37.0 本仓已升版，根 pom 镜像同版）：飞书端口只读 Drive/Wiki（知识空间与空间节点列表）/文档/文档块 + 块级编辑原语，按用户分钟/日额度（额度只在宿主页面）；站点目录端口锚定 `sites/{siteCode}/plugins/{pluginCode}/`，写隔离、跨插件读需授权；发布状态同上，插件仓同步待办。
- 宿主前端 YD 零违规门禁以已提交基线为准；禁止从工作区生成基线或恢复历史例外。
- 双仓同步地图：`docs/dev/repo-sync.md`；待同步事项以该文件为准。

## 6. 现状与敏感信息

- 当前工作分支通常为 `master`；工作前核对 `git status`，尤其保留用户的未提交内容。
- 不在本文件记录账号、密码、token、私有 URL 或个人绝对目录之外的敏感信息；本机凭据位置见被忽略的 `docs/local.md`。

## 7. 规则分层

- 工程总纲与提交/验证：`docs/dev/conventions.md`
- 后端 DDD 与平台能力：`docs/dev/backend-architecture.md`
- SPI、插件生命周期、菜单、主题与 DevTools：`docs/dev/plugin-system.md`
- 权限码：`docs/dev/permission-codes.md`
- 契约发布：`docs/dev/contract-release.md`
- 前端与 YD 组件：`docs/dev/frontend.md` + `docs/yd-ui-guidelines.md`
- 双仓同步：`docs/dev/repo-sync.md`
- 已知坑：`docs/exp/README.md`
- AI 运行细则：`docs/platform/ai-foundation.md`
