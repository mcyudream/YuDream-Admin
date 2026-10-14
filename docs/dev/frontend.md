# 前端工程：YD 唯一入口、外观体系与构建纪律

> **适用范围**：`yudream-frontend` 宿主前端、共享包与插件前端。
> **目标**：业务 UI 只走公开 YD 组件，主题/主色统一受控，组件变更按完整清单同步。

视觉语言、组件分类与场景选型的权威文档是 `docs/yd-ui-guidelines.md`（宿主权威，插件仓保留同步副本），开始 UI 工作前必读；API 边界见 `docs/yd-components.md`、`docs/yd-agent-workbench.md`、`docs/yd-form-components.md`、`docs/yd-select-components.md`、`docs/frontend-appearance.md`。

## 1. YD 唯一入口

- 宿主和插件业务 UI **只使用 `@yudream/components` 的公开 `Yd*` / `useYd*` 入口**。
- **禁止**业务直接使用 `Fa*` / `useFa*`、Arco/TDesign/Reka 等通用 UI 原生入口；**禁止**自造通用按钮、输入、选择器、弹层、表格、加载与通知；**禁止**别名绕过。
- ✅ 允许原生语义内容与布局元素；技术编辑器/图形引擎**必须**经有类型的 YD 适配组件承载。
- Fa 与第三方仅是共享组件内部实现及明确的框架启动装配边界，不能作为页面例外。
- 缺少组件时先在共享组件库封装为 YD，补齐属性、模型、事件、插槽、ref 方法、键盘/焦点、主题与弹出层，再使用；不得只改标签或把不兼容的 Arco API 硬套到 YD。

## 2. 组件变更同步清单（每次增改废弃必做）

1. 根导出、resolver、SDK host-components 桥、实时预览、组件文档同步更新。
2. 同一工作项更新：`docs/yd-ui-guidelines.md` 选型文档、检查映射、受影响的 AGENTS/docs/dev 条目与插件仓副本。
3. 完成前运行严格 UI 检查和目标 typecheck/build，验收三套外观 × 浅/深模式及窄屏。
4. **不得**通过 eslint-disable、全局忽略、未发布依赖或虚假版本掩盖缺口。
- 宿主与插件已迁移的 Vue Web 规范门禁保持零违规；保留 `findings={}` 与验收 Git SHA，**不得**重新放回历史例外；基线只能从明确已提交版本生成。
- 严格门禁用完整 SFC/TS/JSX 语法树、真实公开入口及逐文件逐规则历史基线；**禁止**工作区基线掩盖新增。
- 插件仅从契约包公开入口显式导入，不复制宿主实现。

## 3. 外观与主题

- 不在业务页面自行指定主色、品牌色或固定色板；主题、主色、深浅模式由后台主题配置与组件体系统一控制。业务样式只用中性语义变量（`--color-bg-*`、`--color-text-*`、`--color-border-*`、`--color-fill-*`），不直接模拟主题组件的激活/强调态。
- YD 外观默认值统一在共享 `@yudream/components/appearance.css` 的 `yudream-base` 根节点层声明 `--yd-*` tokens；宿主 `admin-appearance.css` 仅导入；宿主和预览共用 `@fantastic-admin/themes/runtime` 主色转换。ADMIN 插件可在 `:root`/`.dark` 覆盖根变量，**禁止**内联写入或在子布局重定义默认值。
- 后台内置灵动/雅致/紧凑预设存 `settings.appearance`（既有 appSettings JSON，宿主 normalizer 兼容旧值）。三套外观均保留已启用的功能动画：灵动完整；雅致与紧凑的减少动态效果偏好仅柔化动作、速度与装饰密度，**禁止**统一冻结动画；GridStack 只动画内部卡片。
- 插件主题声明的 `style.css` 资产可按其唯一 scope 声明 `--primary-N`：SITE 使用明确 SITE 锚点，ADMIN 限 `:root`/`.dark`；变量消费仍禁止。未能证明 SITE/ADMIN 双 scope 资产隔离时扫描器 fail-closed。详见 `docs/yd-ui-guidelines.md`。
- 宿主主题变量是 OKLCH 三通道，插件 CSS 用 `hsl()` 会纯黑。

## 4. 组件选型速查

普通业务优先基础 YD；阶段用 `YdSteps`、异步进度 `YdMultiStepLoader`/`YdProgress`、履历 `YdTimeline`；层级 `YdTree`；少量选项 `YdSelect`、搜索单选 `YdCombobox`、级联 `YdCascader`、远程分页资源 `YdTablePicker`；短任务 `YdModal`、上下文详情 `YdDrawer`、轻量锚点信息 `YdPopover`、独立长流程用路由页；Coding 会话 `YdAgentWorkbench`。装饰动效仅用于迎宾、重点提示或可视化，不遮挡表单、表格、错误和危险确认。

- 后台管理列表用 `YdResponsiveTable` + `#card` 窄屏视图，不另造双端分页/选中逻辑，不以装饰卡片替代管理表格；系统可查询的内部 ID 用带标签选择器，长 ID 保持字符串。
- 移动端断点统一 768px；`YdPageMain` 手机端已全出血，页面不重复加外边距。
- `FaInput/FaTextarea/FaSelect` 不渲染 label 属性：业务表单**必须**显式 label/字段标题并提供 placeholder 或帮助文本。
- `YdSelect` 按真实模型回显：空值不回落第一项；multiple 全部标签在触发器内展示并可取消；`allowSearch` 默认关、开启仅本地过滤不清已选；保留分组/禁用/标量/数组与字符串长 ID；远程分页用 `YdTablePicker`。详见 `docs/yd-select-components.md`。
- 固定高度 Grid/Flex 工作台：侧栏根节点 `height:100%; min-height:0; overflow:hidden`，内容区 `min-height:0; overflow:auto`，否则长列表被裁切。
- 图标经 `YdIcon` 用 Morphicons（`morph:<lucide-kebab-name>`，`lucide:` 等价兼容），缺失回退 Iconify；新页面/菜单优先 `YdIconPicker`。
- 动态表单：Vue3 + Arco 场景用 `@form-create/arco-design` 与 `form-create-designer-arco-design`；`main.ts` 全局 `app.use(formCreate)`/`app.use(FcDesigner)`；设计器 `fc-designer.getRule/getOption` 存 JSON，公开端 form-create 渲染并自定义提交按钮。

## 5. 工程纪律

- 目录：`apps/core-arco-design-vue` 为默认应用；`packages/` 只留共享包（components/plugin-sdk/settings/themes/types/dataviz 等），**禁止**把 `packages/plugin-*` 留在默认 workspace manifest（核心 install/build 不得隐式耦合已迁出的插件源码）。
- `<script setup lang="ts">`；样式优先 UnoCSS 原子类、复杂样式 SCSS；组件 PascalCase 且文件名一致；API 模块按业务拆分在 `src/api/modules/`；store 用 Pinia composition API。
- 共享包改名时，源码 import 与 `core-arco-design-vue` 的 auto-import/components 生成 d.ts 引用**都要**更新，否则 CI vue-tsc 报缺模块。
- `pnpm lint:eslint` 在仓库根带 `--fix` 且 `eslint.config.js` 变更会使缓存失效：全量跑会批量改写无关文件。验证规则用 `npx eslint <paths> --no-cache` 定向跑，只暂存本工作项文件。
- `test:typecheck` 不覆盖 src 业务源码：前端改动**必须**跑目标 app 的 build（vue-tsc -b）验证。
- 本地兄弟组件**必须**显式 import（全局只注册 Fa*/Yd*）；漏导入时 Vue 静默降级为原生元素，页面缺结构但内容还在——先查模板里每个非 Fa/Yd 大写标签的 import。
- Mock 用 `vite-plugin-fake-server`，文件放对应 api 模块旁；Node 版本以根 `package.json` 为准。

## 6. AI 前端边界

- 所有 AI 前端用公开 YD Agent/chat 组件；推理面板只渲染真实推理（OpenAI 兼容 `reasoningContent`/`reasoning_content`），自然语言增量、工具与生命周期进度分开；禁止业务页直接引 TDesign Chat。
- `YdAgentWorkbench`/`YdAgentAnswer` 是受控 UI 投影：运行、权限、上下文压缩与持久化由宿主 Harness 管理；version 1 渲染协议含 markdown/reference/tool 块；入口先校验协议并局部清理 Markdown HTML，引用外链仅 HTTP(S)，文件引用与工具重试交宿主事件；Skills/MCP/工作区/审批无适配器时不得伪造连接，演示明确标识本地状态。详见 `docs/yd-agent-workbench.md`。
- 生产 AI 不用阻塞结果整段回放模拟流式；统一根级会话 store 与后端运行快照/cursor；关窗/切页只解除观察，取消走显式 API。全局助手仅登录且有权限可用；路由/页面感知、导航、高亮分别可关；不抓 DOM/表单/query/token，前端工具按稳定路由/操作标识校验并回传真实结果，禁止任意 selector、脚本或自动点击。
