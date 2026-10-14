# YuDream Admin Frontend

> **适用范围**：`yudream-frontend/` 宿主前端应用与共享包。
> **目标**：给前端工作提供目录与命令入口；项目级硬约束以主仓根 `AGENTS.md` 为准。

## 1. 项目概览

- Vue 3 + TypeScript + Vite，pnpm monorepo，Pinia，Vue Router，Axios，UnoCSS。
- 默认宿主 app：`apps/core-arco-design-vue/`；共享契约/组件包：`packages/components`、`packages/plugin-sdk` 等。
- 官方业务插件源码在相邻仓 `D:/code/yudream-admin-plugins`，主仓 workspace 不得隐式依赖其源码。

## 2. 目录

```text
apps/core-arco-design-vue/
  src/api/          API 请求模块
  src/assets/       静态资源
  src/components/   应用级业务组件
  src/composables/  组合式函数
  src/layouts/      布局组件
  src/router/       路由配置
  src/store/        Pinia 状态
  src/types/        TypeScript 类型
  src/utils/        工具函数
  src/views/        页面
packages/
  components/       公开 YD 组件库
  plugin-sdk/       插件前端 SDK
  settings/         框架设置
  themes/           主题配置
```

## 3. 必须遵守

1. 开始前端 UI 工作前读主仓 `../docs/dev/frontend.md` 与 `../docs/yd-ui-guidelines.md`；组件 API 细节读 `../docs/yd-components.md`、`../docs/yd-agent-workbench.md`。
2. 业务 UI **只用 `@yudream/components` 公开 `Yd*`/`useYd*`**。禁止业务直接用 Fa/useFa、裸 Arco/TDesign/Reka，禁止自造通用交互；缺组件先封装 YD，再同步根导出、resolver、SDK host-components、预览、文档、检查映射与插件仓副本。
3. 主题/主色由宿主主题与组件变量控制；业务只消费中性语义变量。YD 外观默认值在共享 `appearance.css` 的 `yudream-base` 层，ADMIN 插件只能按规则覆盖根变量。
4. 管理列表用 `YdResponsiveTable` + `#card` 窄屏视图；长 ID 保持字符串，人员/资源用带业务标签的选择器，不让用户手输内部 ID。
5. 组件变更按主仓 `docs/dev/frontend.md` 的同步清单更新全部公共面与规范；跨仓同步查 `../docs/dev/repo-sync.md`。严格 UI 门禁保持零违规，不能增加例外或从工作区重做基线。
6. 前端改动须跑目标 app build（typecheck 不覆盖业务 src），并按变更风险验收三套外观、浅/深模式、窄屏。根目录 `pnpm lint:eslint` 带 `--fix`，规则验证用定向 `npx eslint <paths> --no-cache`。
7. 本地组件必须显式 import；API 放 `src/api/modules/`，Store 用 Pinia composition API，页面使用 `<script setup lang="ts">`，优先 UnoCSS、复杂样式用 SCSS。

## 4. 常用命令

```bash
pnpm install --frozen-lockfile
pnpm --filter @fantastic-admin/core-arco-design-vue run test:typecheck
pnpm --filter @fantastic-admin/core-arco-design-vue run build
```

Node 与 pnpm 版本要求见根 `package.json` / 主仓 `docs/dev/conventions.md`。全局开发规范唯一入口是主仓根 `AGENTS.md`，不在此复制后端或插件仓规则。
