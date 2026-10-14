# Fantastic-admin

## 项目概述

Fantastic-admin 是一个基于 Vue 3 的后台管理框架。

## 技术栈

- **框架**: Vue 3.6 + TypeScript + Vite 8
- **UI 组件**: YD 公开组件；Fa / Arco Design Vue / Reka UI 仅作组件内部实现；UnoCSS 负责布局
- **状态管理**: Pinia + pinia-plugin-persistedstate
- **路由**: Vue Router 5
- **包管理器**: pnpm（必须使用 pnpm，禁止使用 npm/yarn）
- **HTTP**: Axios
- **Mock**: vite-plugin-fake-server

## 目录结构

采用 pnpm monorepo 架构：

```
├── apps/
│   └── core-arco-design-vue/ # 默认应用（Arco Design Vue）
│       └── src/
│           ├── api/          # API 请求模块
│           ├── assets/       # 静态资源
│           ├── components/   # 全局业务组件
│           ├── composables/  # 组合式函数
│           ├── layouts/      # 布局组件
│           ├── router/       # 路由配置
│           ├── slots/        # 插槽组件
│           ├── store/        # Pinia store
│           ├── types/        # TypeScript 类型定义
│           ├── ui/           # 框架内建 UI 组件（本地）
│           ├── utils/        # 工具函数
│           └── views/        # 页面视图
├── packages/
│   ├── components/           # 框架内建 UI 组件库（子包，跨应用共享）
│   ├── settings/             # 框架设置（子包，跨应用共享）
│   └── themes/               # 主题配置（子包，跨应用共享）
├── scripts/                  # 工程脚本
└── pnpm-workspace.yaml       # monorepo workspace 配置
```

## 常用命令

```bash
pnpm dev          # 启动开发服务器
pnpm build        # 构建
pnpm lint         # 运行全量 lint（tsc + eslint + stylelint）
```

## 开发规范

- 使用 `<script setup lang="ts">` 语法
- 样式优先使用 UnoCSS 原子类，复杂样式用 SCSS
- 组件命名使用 PascalCase，文件名与组件名一致
- API 模块放在 `apps/<app>/src/api/modules/`，按业务模块拆分
- Store 模块放在 `apps/<app>/src/store/modules/`，使用 Pinia composition API 风格
- 路由配置在 `apps/<app>/src/router/`，meta 属性可借助 fa-route-generator skill

## 注意事项

- 图标通过 `YdIcon` 使用 Morphicons，名称为 `morph:<lucide-kebab-name>`（`lucide:` 等价兼容）；缺失图标回退 Iconify。新页面/菜单优先从 `YdIconPicker` 选择，既有图标名称保持兼容。
- 菜单默认按主要业务大类（如 MC、待办、社交）分组，一级大图标使用共享分类，插件模块与功能归入分类；菜单管理新建根节点默认 `CATEGORY`。分类由宿主持久化菜单树决定，前端不按插件名称自行猜分类。旧插件、显式独立入口与管理员已有结构保持兼容，根菜单/外链不得在转换菜单或路由时丢失；新插件声明方式见宿主 `docs/plugin-system/specification.md` 第 10 节。
- **业务唯一入口是 `@yudream/components` 的公开 `Yd*` / `useYd*`**。禁止 Fa/useFa、Arco/TDesign/Reka 原生控件和手写通用交互。允许原生语义内容与布局元素；业务组件组合 YD，技术编辑器/图形引擎先做 YD 适配。第三方和 Fa 仅允许在共享组件内部及明确的框架启动装配边界。
- 宿主与插件已迁移的 Vue Web 规范门禁保持零违规；保留 findings={} 与验收 Git SHA，不得重新放回历史例外。基线只能从明确已提交版本生成。
- 缺少能力先封装 YD，保证 props/模型/事件/插槽/ref、键盘焦点、主题与弹层；同步根导出、resolver、SDK桥、预览、文档和严格检查映射后再迁移。不能只替名、复制组件或禁用规则。
- **开始 UI 工作前读取 `../../docs/yd-ui-guidelines.md`**。完整分类和场景选型持续维护；API 边界见 `../../docs/yd-components.md`、`../../docs/yd-agent-workbench.md`。
- 主题、主色和深浅模式由后台主题配置与组件体系统一控制。业务页面不得自行声明品牌色、固定色板或手写主色强调态；只使用中性语义变量和组件内建状态样式。
- 插件主题可按 `@PluginTheme` 唯一 scope 在其声明的 `style.css` 中声明 `--primary-N`：SITE 需 SITE 锚点，ADMIN 仅 `:root`/`.dark`；消费仍禁止，未证明隔离的双 scope 资产 fail-closed，详见同步规范。
- 权限管理使用 `apps/core-arco-design-vue/src/utils/permission-catalog.ts` 的集中分类，按业务域 → 稳定资源码 → 动作分类 → 权限展示，列表/权限树/矩阵/溯源不得各自维护动作词表。整理分组保留历史权限码与搜索隐藏、暂失效插件授权，`assign-*` 覆盖完整规范动作家族；细则见 `../docs/system/permission-management.md`。
- Agent 页面复用 `YdAgentWorkbench` / `YdAgentAnswer` 的受控状态与 version 1 渲染协议；引用原文、工具卡片、运行轨迹与真实推理分开呈现。协议先校验，Markdown HTML 局部清理，外链仅 HTTP(S)，工作区/审批/Skills/MCP 的执行与授权仍由宿主 Harness 负责。参见 `../docs/yd-agent-workbench.md`。
- 生产 AI 不使用阻塞结果或整段回放模拟流式。统一根级会话 store 与后端运行快照/cursor；关闭窗口或切页只解除观察，取消走显式 API。工作台按后端能力配置 availableModes，上传附件通过 attachments 受控回显。全局助手仅登录且具权限可用，路由/页面感知和导航/高亮分别可关闭，不抓 DOM/表单/query/token，不允许任意脚本/选择器或自动点击。
- 后台预制外观由宿主 `settings.appearance` 管理（灵动/雅致/紧凑），统一消费 `--yd-*` 表面与动效变量。默认值只在共享 `@yudream/components/appearance.css` 的 `yudream-base` 层根节点声明，宿主 `admin-appearance.css` 仅导入；允许 ADMIN 主题插件覆盖，禁止内联写入这些变量或在子布局重新定义默认值。三套外观均保留已启用的功能动画；灵动完整，雅致与紧凑的减少动态效果设置仅柔化动作、速度与装饰密度，禁止统一冻结动画。详见 `../docs/frontend-appearance.md` 与 `../docs/yd-components.md`。
- 用户已授权的组件封装与修复在工作范围内执行；保持 Fa/YD 契约兼容，禁止顺手改动无关组件或破坏行为。
- 移动端断点统一 768px。`YdPageMain` 手机端已全出血，页面不重复加外边距；后台列表用 `YdResponsiveTable` + `#card`，不使用裸 `YdTable` 承载需要双端管理的列表，不另造分页/选中状态。
- 场景速查：少量选项→`YdSelect`；搜索单选→`YdCombobox`；层级→`YdTree`/`YdCascader`；远程分页资源→`YdTablePicker`；短任务→`YdModal`；上下文详情→`YdDrawer`；轻提示→`YdPopover`；用户步骤→`YdSteps`；异步任务→`YdMultiStepLoader`；普通等待→`YdLoading`；履历→`YdTimeline`；Coding 会话→`YdAgentWorkbench`。动效表达状态或突出少量重点，不给每个列表和按钮叠加装饰。
- 每次组件增改废弃，实时更新选型文档、预览、导出/resolver/SDK桥、检查映射、宿主/插件 AGENTS/skill；同一工作项通过严格 UI 检查与目标 typecheck/build，验收三套外观×浅/深模式和窄屏。不得通过 eslint-disable、全局忽略或虚假版本绕过缺口。
- Mock 数据使用 `vite-plugin-fake-server`，文件放在 `apps/<app>/src/api/modules/` 对应模块旁
- 代码提交前会自动运行 lint-staged，确保代码符合规范
- Node.js 版本要求以根目录下 `package.json` 中定义的为准

## 反复修改检测

在使用任何 fa-* 系列技能时，如果用户针对同一功能点已经要求修改 3 次及以上仍未达到预期（例如连续说"不对"、"再改改"、"还是不行"），必须触发 fa-feedback 技能，询问用户是否将问题反馈给框架作者。
