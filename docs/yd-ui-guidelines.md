# YD 视觉与组件使用规范

本文件是宿主页面、共享业务组件、官方插件和主题插件的组件选型规范。组件参数以源码和对应 API 文档为准；本文件约束的是何时选用哪个组件、视觉边界和维护流程。根 `AGENTS.md`、前端 `AGENTS.md` 和插件仓 `AGENTS.md` 必须引用本文件，并同步影响自身的规则。

## 1. 唯一公开入口与实现边界

业务界面统一使用公开的 `Yd*` 组件及 `useYd*` 组合式入口。宿主从 `@yudream/components` 导入或通过官方 resolver 自动导入；插件使用 SDK 的宿主共享组件桥接，不另打包组件库副本。`Fa*` 导出继续用于兼容已有契约，不能作为新增或迁移业务界面的选型入口。

禁止在业务页面、业务组件和插件中直接使用 Arco、Reka、shadcn、其他通用 UI 库的控件，或以局部别名绕过该要求。原生 `button`、`input`、`select`、`textarea` 等通用交互控件也必须由 YD 封装后使用。缺少能力时，先补充组件、类型、主题与可验证示例，再接入业务。不能仅因 YD 存在近似名称，就丢掉原控件的能力。

品牌色令牌检查对业务样式中的 `--primary-N` 声明和消费一律报错。注册的插件主题可在其 `@PluginTheme` 声明的 `style.css` 资产中声明主题变量：`SITE` 仅允许明确 SITE 页面/公开路由锚点选择器（`.dark` 可与 SITE 锚点组合）；`ADMIN` 仅允许 `:root` 或 `.dark`。例外必须由元数据、入口 `theme.css` 导入和构建输出配置共同确认，且变量消费始终违规。作用域同时声明 SITE/ADMIN 时，除非分别证明资产与作用域隔离，否则扫描器 fail-closed，不豁免；跨域选择器和普通业务 CSS 也不获豁免。历史 SITE 主题根类由扫描器按明确集合兼容，不能任意扩张。

允许原生结构与内容元素，例如 `main`、`section`、`div`、`span`、`p`、`ol`、`img`，以及有真实 `href` 的内容链接。禁止用带 `@click` 的结构元素模拟按钮、选择器或菜单。组件内部可以使用原生控件、Fa、Reka、Arco；框架主题运行时、根 provider 和路由布局的基础装配属于实现边界，不能据此在业务区继续引入裸控件。

代码编辑器、Markdown 编辑器、图表引擎、流程画布和可视化构建器属于专门技术能力。它们应被 YD 适配器封装；适配器负责公开模型、事件、主题、尺寸、键盘、清理与错误状态。业务仍从 YD 入口调用。Wiki 作者可使用 `YdMarkdownEditor` 已公开的格式工具栏、插入入口与只读预览；Wiki 内链、保存/发布、脏状态、冲突处理和访问控制仍由宿主业务管理。`YdCode` 是代码展示，`YdGraphCanvas` 是关系图，不能当作可编辑代码工具或工作流编辑器的替代品。

`YdPageBuilder` 是 GrapesJS 的公开技术适配入口，受控管理 `htmlContent`、`cssContent`、`builderProjectJson` 与只读/禁用状态；GrapesJS 选项、初始 setup、上传回调和 editor facade 有类型边界。CMS 页面只把业务 blocks、主题配置和授权上传回调注入适配器，不能在业务组件中自行初始化/销毁引擎。

## 2. 主题与三套视觉方案

FA 主题引擎继续管理主色、字体与明暗；YD 通过共享外观令牌形成统一圆角、留白、层次与动效。业务不能另定蓝色默认品牌、固定色板或自行绘制激活强调态。信息层级先使用组件的 `variant`、`size` 和状态属性，再用中性布局样式。

| 外观 | 根属性 | 适用表现 | 页面取舍 |
| --- | --- | --- | --- |
| 灵动 | `data-admin-style="vibrant"` | 年轻、丰富、明显的弹性与悬停反馈 | 允许少量重点装饰；保持正文和操作清晰 |
| 雅致 | `data-admin-style="calm"` | 柔和留白、较温和的过渡 | 使用同一结构与功能，减少装饰密度 |
| 紧凑 | `data-admin-style="compact"` | 高频管理、更高信息密度 | 缩小留白与控件，保留状态反馈与动画 |

页面不能按预设复制三份模板，也不能在子布局覆盖一整套默认令牌。外观默认值只维护在 `packages/components/src/theme/appearance.css` 的 `yudream-base` 根节点层，宿主与预览共同加载 `@yudream/components/appearance.css`。页面可消费 `--yd-content-gap`、`--yd-workspace-gap` 等布局变量；控件圆角、强调色与选中态由组件自己管理。

业务表面、正文、分隔与填充只使用 `--color-bg-*`、`--color-text-*`、`--color-border-*`、`--color-fill-*` 等中性语义变量。不得直接使用 `--primary-N`，也不得在业务 CSS 中拼出按钮、复选框或选择器的主色激活态。图表类别、风险级别等确有语义颜色时，应由 YD 组件的语义属性/配置表达，避免页面私有色板。

ADMIN 主题插件在其声明式 `style.css` 中覆盖根变量，继承宿主的三套外观和组件状态；SITE 主题使用既定 SITE 变量与作用域，不污染 ADMIN。插件样式遵守 `yudream-base < yudream-plugin < 宿主工具类` 的既定层级。禁止给每个组件手写一套主题，禁止内联设置系统主色或重建主题引擎。具体约定见 [前端外观](frontend-appearance.md)。

## 3. 场景选型

先确定任务类型，再选择交互，不从视觉特效出发倒推页面结构。

管理导航按最主要的业务大类分组，例如 MC、待办、社交。默认一级大图标入口只展示共享分类，插件模块、功能目录和页面放在分类下；同类插件复用一个入口，不因新增功能持续扩张一级导航。菜单管理新建根节点默认 `CATEGORY`，分类选择来自宿主持久化菜单树。明确要求的独立入口、个例、旧插件和管理员已有结构保持兼容；新插件通过 `plugin.yml` 的 `menu.category` 声明分类，**不提供「其他」兜底**，内置分类为 mc/social/play/todo/content/campus/client/trade/tools/appearance。**一级菜单名称（分类名、独立一级模块名与宿主种子根节点名）最多 5 个字**：一级图标栏标签区只容纳 5 个汉字，超长在 manifest 解析与菜单投影时直接失败；二级及以下名称由子侧栏承载，不受此限。具体协议见宿主 `docs/plugin-system/specification.md` 第 10 节。

| 用户任务 | 首选组件 | 使用判断 |
| --- | --- | --- |
| 普通管理页面 | `YdPageHeader`、`YdPageMain`、`YdCard`、`YdListToolbar` | 一个清晰标题、查询区、数据区和主操作;不要多层空卡片套卡片；列表页筛选区统一用 `YdListToolbar`，下拉筛选用 `YdFilterSelect` 指定宽度，不要各页自建工具栏 |
| 内容行列表 | `YdList`、`YdListRow` | 活动、通知、收藏等非表格内容；管理数据使用 `YdResponsiveTable` / `YdEnhancedTable` |
| 统计指标块 | `YdStats`、`YdStat` | 展示标题、数值、说明与图表/操作；动效数字可组合 `YdAnimatedCountTo` |
| 页面角落快捷操作 | `YdFab` | 少量高频动作可用图标与短标签；不替代表单提交、批量操作或危险确认 |
| 静态浏览器/窗口预览 | `YdMockupBrowser`、`YdMockupWindow` | 外框中的导航/窗口状态只驱动插槽演示，不访问 URL 或系统窗口 |
| 表格数据管理 | `YdResponsiveEnhancedTable`、`YdPagination` | 有行级操作的后台管理列表使用增强响应式表格：名称身份列固定左侧、唯一操作菜单列固定右侧，行操作合并进该菜单；桌面与移动卡片共享行操作配置。只读或需要完全自绘卡片的列表可用 `YdResponsiveTable` + `#card` |
| 图文并茂数据列表 | `YdEnhancedTable`、`YdResponsiveEnhancedTable` | 用户列表、成员管理等需要身份列+状态徽章+操作下拉;响应式版本在 768px 以下自动切卡片并复用 `#cell-*` 渲染,管理列表默认选它 |
| 桌面专属、内部小型表格 | `YdTable` | 不代替业务列表的移动端方案;列与插槽遵循 TanStack 契约 |
| 只读详情 | `YdDescriptions`、`YdTag`、`YdBadge` | 属性值与状态；避免用可编辑输入框展示不可编辑内容 |
| 少量枚举单选 | `YdRadioGroup`、`YdSelect` 或 `YdSegmented` | 所有选项需直观看见用 radio；紧凑的同级视图切换用 segmented；节省空间用 select |
| 行内破坏性操作确认 | `YdPopconfirm` | 删除、停用等邻近按钮的小范围确认；长说明或多阶段确认仍用 `useYdModal` |
| 颜色选择 | `YdColorPicker` | 支持 HEX/RGB/HSL/OKLCH 与透明度；不要用裸 `input[type=color]` 替代完整颜色编辑 |
| 页面分区导航 | `YdAnchor`、`YdBreadcrumb` | 长页面目录使用 Anchor；路由层级使用 Breadcrumb |
| 分页信息流加载 | `YdInfiniteScroll` | 仅用于天然连续的信息流；管理数据表仍使用分页 |
| 树节点作为字段值 | `YdTreeSelect` | 需要下拉选择组织/分类时使用；复杂勾选管理仍用 `YdTree` |
| 本地大集合搜索单选 | `YdCombobox` | 需要输入筛选、分组或虚拟列表；可选 `allowCreate` 显示明确的“使用这个编码”候选并发出 `create(string)`；不是通用远程多选 |
| 少量多选 | `YdCheckboxGroup` | 选项直接可见；已有多选选择器需求先核对模型与展示能力 |
| 按层级逐级选一个值 | `YdCascader` | 省市、分类路径等；单值，`leafOnly` 决定允许选父项还是叶子 |
| 浏览、筛选或勾选层级 | `YdTree` | 部门、菜单、目录、权限等；数据不是关系网络 |
| 可保存的树节点重排 | `YdSortableTree` | 受控目录/导航拖拽、键盘重排、父级元数据和可选异步子节点；禁用、环及深度上限由组件拒绝 |
| 远程、分页、跨页数据选择 | `YdTablePicker` | 用户、角色、资源等，需要更多列帮助辨别；确认后提交所选 ID |
| 图标选择 | `YdIconPicker` | 优先 Morphicons；不自己搭一个图标下拉网格 |
| 日期 API 字符串输入 | `YdDatePicker`、`YdRangePicker`、`YdTimePicker` | 普通表单日期/时间，值格式与 API 对齐 |
| 日历浏览、多值或周期限制 | `YdCalendarDate` 等、`YdDate` | 使用 DateValue，支持对应粒度与限制；存储前显式序列化 |
| 用户分步填写或审批阶段 | `YdSteps` | 当前步骤由业务控制；步骤切换不是后台任务完成 |
| 顺序异步任务 | `YdMultiStepLoader` | 连续动作、结果、错误和取消；不是一般表单步骤条 |
| 历史与事件记录 | `YdTimeline` | 按已有事件顺序展示；不是可执行流程图 |
| 关系图与知识网络 | `YdGraphCanvas`、`YdChatGraphView` | 节点/边关系；不用于替换树或工作流编辑器 |
| 技术工作流画布 | `YdWorkflowCanvas` | 受控流程节点/连线、可类型化节点 renderer 和引擎 ref facade；VueFlow 只在共享 YD 适配内部出现 |
| 普通等待 | `YdLoading`、`YdProgress`、`YdSkeleton` | 不确定时长用 loading；有可信进度用 progress；内容结构明确但数据未到时用 skeleton |
| 敏感页面标记 | `YdWatermark` | 使用者/时间等追溯性标记；水印不是数据访问控制或防截屏安全措施 |
| 结果页面状态 | `YdResult`、`YdEmpty` | 任务完成/失败/权限结果用 result；无数据与无搜索结果用 empty |
| 短反馈 | `useYdToast` | 保存成功等非阻断反馈；不能承载必须处理的失败与确认 |
| 需要持续可见的提示 | `YdAlert` | 权限限制、配置问题、可恢复错误；与对应操作放在一起 |
| 没有数据或结果 | `YdEmpty` | 区分初始为空、筛选无结果与加载失败；需要时给下一步动作 |
| Chat / Coding Agent | `YdAgentWorkbench` 或既有 `YdChat*` | 工作区与工具任务用工作台，轻量问答可组合聊天元件 |
| 长驻编辑工作区 | `YdWorkspaceLayout`、`YdWorkspaceTabs` | 需要活动栏、可拖拽分栏、多文件标签与状态栏的职业工作面（知识库编辑等）；外壳不滚、面板内滚，一次性内容页仍用页面容器与 `YdSettingsLayout` |

### 3.0 表格页版式（固定）

管理表格页的标题、视图切换、筛选与操作按固定三段排布，统一由 `YdListToolbar` 承载：

| 位置 | 内容 | 约束 |
| --- | --- | --- |
| 第一行 | 左：列表标题（可带条数等摘要）；右：视图切换 tab（`YdSegmented` 等） | 标题与 tab 独立成行，**不得**与筛选控件挤在同一行 |
| 第二行 | 搜索框、各下拉筛选、行尾操作（重置 / 筛选 / 表格列控件） | 同一行等宽网格 `repeat(auto-fit, minmax(200px, 1fr))`；筛选与**表格列控件同属这一行**；窄屏自动退化为单列 |
| 第三行起 | 数据表与分页 | 表格自身不再重复放筛选或标题 |

- **表格页不放刷新按钮**：查询由「筛选」触发，翻页与数据变更自然刷新；需要强制重取时由筛选/重置承担。
- 搜索框与下拉筛选等宽（默认 200px 起），**禁止**用 `flex-1`/`w-full` 让搜索框铺满整行，**禁止**把筛选另起一行。
- 下拉筛选一律用 `YdFilterSelect`，宽度以属性传入；不要写 `.x { width: 150px }` 这类页面 scoped 规则（FaSelect 的 class 落在内部 trigger 上，选择器不命中，是静默死样式）。
- 状态计数筛选用 `YdListToolbar` 的 `statusItems`，呈现为筛选行下方的胶囊行（计数即筛选、底部只读比例条）。

### 3.1 普通按钮、表单与选项

业务主操作使用 `YdButton`，同一局部区域保持明确主操作。次要操作用 outline/ghost 等已有变体；危险操作使用 destructive 并按业务决定是否需确认。只有图标的操作必须有可访问名称，禁用态必须说明原因。复制功能优先 `YdCopyButton`，而不是在每个页面重写复制与成功反馈。

文本用 `YdInput` / `YdTextarea`，数值用 `YdNumberField`，开关用 `YdSwitch`，区间调节用 `YdSlider`；密码强度是 `YdPasswordStrength` 的附加反馈，不替代密码输入。表单用 `YdForm` / `YdFormItem` 承载标签、帮助、必填、校验与错误；标签数组用 `YdInputTag`，评分用 `YdRate`。契约见 [YD 表单与交互](yd-form-components.md)。

`YdAction` 是保留原生按钮语义的基础动作容器，用于导航、卡片选择、编辑器工具等专门组合的可点击区域；它统一键盘、焦点和禁用行为，视觉由组合组件控制。普通 CRUD、表单提交与危险操作继续使用 `YdButton`，不能用 YdAction 到处自造主色按钮。

`YdSelect` 与 Fa 基础实现同契约，不等于 Arco Select。它的 `options` 使用 `{ label, value, disabled? }` 或分组对象。单选绑定标量，`multiple` 绑定数组；选择框回显全部已选标签，空值默认显示“没有选择”，不得回落到第一项。`allowSearch` 可开启本地候选筛选，默认关闭；搜索不清除已有选择，不提供远程分页能力。不得通过裸 `Option` 子节点、任意字符串替换或传递不支持的远程搜索参数完成迁移。尤其多选必须验证模型、标签回显、取消选择和空态。修复契约见 [YD 下拉选择](yd-select-components.md)；该修复已包含在验签通过的 components 1.6.0 中。

### 3.2 树、级联与弹出集合选择

树的 `data` 使用 `{ key: string, label: string, children?, disabled? }`；选中、勾选和展开分别为 `selectedKeys`、`checkedKeys`、`expandedKeys` 三个模型。ID 不转为 Number。启用 `checkable` 默认按父子级联勾选；需要同时节点选中时显式设置 `selectable`。筛选保留命中节点的祖先，不能因搜索过滤而删除已有业务授权。

`YdTree` 负责浏览、筛选与勾选；持久化重排使用独立的 `YdSortableTree`。它发出包含 drag/drop 节点、`dropPosition`、原/目标父级与深度的受控提案，拒绝禁用节点、自身子树环及 `maxDepth` 超限，支持 Alt + 方向键重排和类型化的 `loadChildren`。父级要应用并保存 `drop` 提案；组件不直接修改传入 `data`。拖拽过程给出落点提示（落在行前/行内/行后），不可接受的落点不给提示并由浏览器显示禁止状态；节点行带头部折叠箭头、层级缩进参考线与分支/叶子图标。`YdSortableTree` 当前不提供虚拟列表或 Arco `fieldNames` 兼容透传。`YdCascader` 当前也是单值；若需要多路径或多选，应先封装相应契约。

流程图与关系网络使用场景不同：`YdWorkflowCanvas` 承载工作流编辑器，节点/边由调用方受控，事件只暴露 ID 与连接端点，节点 renderer 插槽使用 `#node-{type}`，连接点用 `YdWorkflowHandle`。它的 ref facade 提供画布坐标、增删改节点边和视口适配方法；`readonly` 保留查看/选择，关闭节点拖动和连线，`disabled` 冻结画布交互。技术引擎由 YD 组件在卸载时清理，业务页面不得直接导入 VueFlow 或其类型。`YdGraphCanvas` 继续用于一般关系/知识图，不替代可编辑工作流。

`YdTablePicker` 是带查询、分页和确认的选择弹窗，使用其现有 API：

```vue
<script setup lang="ts">
import type { TableColumn, YdTablePickerQuery, YdTablePickerResult } from '@yudream/components'
import { YdTablePicker } from '@yudream/components'
import { ref } from 'vue'

interface Resource { id: string, name: string }
const ids = ref<string[]>([])
const columns: TableColumn<Resource>[] = [
  { accessorKey: 'name', header: '资源名称' },
]
async function fetchResources(query: YdTablePickerQuery): Promise<YdTablePickerResult<Resource>> {
  // 调用既有业务 API，并显式将其分页结构映射为 { list, total }。
  return resourceApi.page(query)
}
</script>

<template>
  <YdTablePicker
    v-model="ids"
    :columns="columns"
    :fetcher="fetchResources"
    row-key="id"
    label-key="name"
    title="选择资源"
  />
</template>
```

示例中的 `resourceApi` 是业务适配点，不是组件库提供的 API。查询参数为 `{ page, size, keyword }`，返回 `{ list, total }`。`modelValue` 始终是 `string[]`，即使 `multiple=false` 也不改成单字符串。组件内部缓存跨页标签；编辑场景传 `initialLabels: Record<string, string>` 以回显未加载的所选项。取消弹窗不提交临时选择，确认才触发模型更新。`#filters` 可增加业务过滤，表格使用 `cell-*` / `header-*` / `footer-*` 等插槽，不是 Arco 的任意表格插槽。

不要用 `YdModal` 加页面私有分页选择逻辑重复造 TablePicker。相反，简单枚举不应全部升级为表格弹窗；集合选型取决于数据规模、远程来源与辨别所需字段。

### 3.3 浮层层级

| 类型 | 适用内容 | 不适用内容 |
| --- | --- | --- |
| `YdTooltip` | 简短补充说明、图标含义 | 表单、按钮、长详情、必读错误 |
| `YdHoverCard` | 悬停可见的只读摘要 | 必须依赖悬停才能完成的移动操作 |
| `YdPopover` | 邻近触发器的小范围交互或轻量筛选 | 大表单、长文编辑、多阶段确认 |
| `YdDropdown` / `YdContextMenu` | 动作菜单；上下文菜单同时有可发现的普通入口 | 任意业务数据多选、正文阅读 |
| `YdModal` / `useYdModal` | 聚焦确认、短编辑、独立选择 | 默认承载整个管理页面或层层嵌套 |
| `YdDrawer` / `useYdDrawer` | 保留当前列表上下文的详情、较长编辑、审阅 | 无必要地阻断所有页面导航 |
| `YdTour` | 分步新手引导：首次上手、新功能巡览，聚光灯高亮 `data-tour` 登记元素 | 代替操作文档或帮助中心；长驻遮挡高频操作路径的引导 |
| `YdTablePicker` | 远程分页集合选择 | 内容详情和通用编辑 |

关闭、Esc、遮罩与焦点恢复沿用组件能力；业务应处理未保存内容、执行中关闭和失败后继续编辑。确认框说明操作对象与结果，异步确认展示 loading，失败时保留现场。不要仅关闭弹窗就宣称服务端取消成功。多层浮层应减少到可理解的任务层级，不能在页面手写无限增长的 z-index。

## 4. 动效选择与保持功能

功能动效表达仍在进行的工作、数值变化、当前项切换或内容揭示；装饰动效表达视觉重点。普通表单和密集列表默认使用控件自身反馈；首页欢迎、指标重点、活动内容和 AI 空会话可选择少量装饰。

| 动效类别 | 组件 | 场景约束 |
| --- | --- | --- |
| 加载与任务反馈 | `YdLoading`、`YdMultiStepLoader`、`YdChatLoading` | 运行时一直有可读状态，完成或失败由真实结果驱动 |
| 数字变化 | `YdCountTo`、`YdAnimatedCountTo`、`YdAnimatedCountToGroup`、`YdDigitalCard` | 指标更新；可复制/可访问值必须准确，不把补间当真实数据 |
| 内容导航 | `YdCarousel`、`YdSmoothSwipe` | 保留键盘和显式导航；自动播放不是完成关键任务的前提 |
| 连续文本 | `YdMarquee`、`YdScrollingText`、`YdFlipWords` | 适合非关键滚动内容；必要信息仍有静态可读入口 |
| 内容揭示 | `YdBlurReveal`、`YdTextHighlight`、`YdFlipCard`、`YdScratchOff` | 不隐藏重要操作；刮刮卡保留键盘/直接揭晓 |
| 文本重点 | `YdTextShiny`、`YdSparklesText` | 短标题或少量重点，不能覆盖长正文 |
| 卡片装饰 | `YdBorderBeam`、`YdSpotlightCard`、`YdGlowyCard`、`YdGlowyCardWrapper` | 重点卡片，不对全部表格行反复叠加 |
| 动作重点 | `YdInteractiveButton`、`YdGradientButton` | 欢迎/活动等少量重点操作，普通 CRUD 保持 YdButton |
| 连线与背景 | `YdAnimatedBeam`、`YdParticlesBg`、`YdPatternBg` | 连接关系或局部背景；不能抢占输入、滚动和点击 |

三种外观下，已启用的功能动画都必须运行。灵动保留完整表现；雅致与紧凑在系统/用户减少动态效果偏好下，仅调整速度、位移、弹性和装饰密度。禁止用 `useYdMotion().reduced` 直接关闭计数、词语轮换、加载、流光等本应运行的功能，也禁止全局 `animation: none`。

装饰数量可按令牌和 `useYdMotion()` 柔化。每个页面应有明确动效重点，避免多个大面积常驻粒子、文本闪动和高频悬停同时出现。持续运动的导航提供合理暂停入口，组件卸载后释放计时器、观察器、RAF 与事件监听。动效不能引起布局反复跳动或遮住焦点。

## 5. 数据、流程与全状态

组件选择不能替代业务状态建模。数据区至少明确初次加载、已有数据刷新、正常结果、初始为空、搜索无结果、错误和无权限。错误应提供原因与可行恢复操作；不得用空态掩盖请求失败，刷新不应无条件清空已读内容。这些状态用 `YdEmpty`（初始为空、搜索无结果）、`YdResult`（错误、无权限、结果页）与组件自身的加载态表达，不在页面手写状态容器；需要插画时只从 `YdStateIllustration` 的命名场景选择。

`YdSteps` 表示步骤位置；`YdTimeline` 表示历史；`YdMultiStepLoader` 表示当前顺序执行动作。需要非线性分支编辑的流程，必须使用专门的 YD 工作流适配器。禁止为追求统一外观把可编辑流程画布换成静态步骤条。

多步骤任务接入 `AbortSignal`，取消阻止后续步骤和迟到结果；取消不自动撤销已经发生的服务端写入。重试从哪些步骤重新执行、哪些动作可重复必须由业务明确。任务成功后有明确完成反馈，失败保留已完成步骤与失败原因。异步事件属于当前任务或会话，切换上下文后不得错误写入另一个任务。

只读、禁用、加载、错误、选中、部分选中、待确认、已取消等状态都由组件契约表达，不用页面私有 class 伪造。局部刷新使用 `YdLoadingOverlay`，保留内容尺寸与状态；默认通过 inert 锁定该容器的交互并恢复原焦点，展示型遮罩可显式关闭 `blockInteraction`。不再使用裸 `v-loading`。筛选后保留隐藏但仍有效的所选数据；所有 Long/Snowflake ID 全程为字符串。

`YdResponsiveTable` / `YdResponsiveEnhancedTable` 通过 `v-model:row-selection` 控制选择键集合，桌面与卡片共享同一表格状态和禁用判定。`#card` 使用 `selected`、`selectDisabled`、`toggleSelected`，不另外维护移动端选中数组；切换断点不销毁表格状态。函数式旧导入入口使用 `useYdFilePicker().open()`，必须在用户动作中调用；取消、再次打开和作用域销毁释放临时输入，目录选择保留 File 的相对路径。

- 关联实体触发器统一为「头像/图标 + 名称」，悬停给 `color-mix` 底色、指针为手型；沿用页面色彩体系的自定义标签也要带同样的交互语义（`yd-entity-trigger`）。跨页定位命中行用主色呼吸提亮并滚入可视区，`prefers-reduced-motion` 下静态呈现。

## 6. AI 与 Agent 界面

Coding/工具任务优先 `YdAgentWorkbench`，上下文复用 `YdAgentContextPanel`，回答复用 `YdAgentAnswer`。知识库问答与简单聊天可组合 `YdChatWindow`、`YdChatMessageList`、`YdChatSender` 等元件，不为全部场景强塞工作区侧栏。

生产 AI 使用统一后端持久运行快照和游标，正文、真实推理、工具与进度独立；切页或关闭窗口不取消后台任务。`YdAgentWorkbench` 的 `availableModes` 只开放宿主实际支持的模式，`attachments` 与移除事件保持宿主上传列表一致，业务范围控件放 `sender-actions` 插槽。全局助手要求登录与权限，路由/页面感知及导航/高亮可分别关闭；工具只定位已授权路由和显式注册操作，不收集表单数据或代用户点击。

`useYdPersistentChatStream` 与 `clearYdPersistentChatOwner` 不属于 components 1.6.0 / SDK 1.11.0 契约；使用前必须确认目标版本包含这两个入口并已完成发布验签，缺少入口时先扩充和发布契约。

需要前端真实执行工具的持久 WebSocket 场景使用 `useYdPersistentChatStream`：以登录用户和稳定业务上下文保存观察状态，由宿主提供快照、取消、工具回执端口；断线只续订已有 run，不重复提交。已保存的真实工具结果只接续后端原生交互，不重复修改页面。业务工具结果放 `message-extra`，等待与重连提示放 `composer-before`；没有真实后端端口时明确说明未连接。

引用必须有原文、来源标识与可定位链接。version 1 渲染协议使用 `markdown`、`reference`、`tool` 三类块，先校验再渲染。正文通过 `[[ref:id]]` 关联原文；不同回答实例锚点隔离。工作区文件通过事件交宿主定位，外链仅 HTTP(S)。Markdown 局部清理 HTML，工具输入、输出与错误是文本，不能作为任意执行界面。

工具卡片区分 `queued`、`running`、`awaiting-approval`、`success`、`error`、`cancelled` 六种状态。保持稳定调用 ID，并由宿主权威事件更新；一个工具成功不意味着整项任务完成。只在宿主允许且失败可重试时提供重试，批准入口只提交决策，宿主重新校验权限并恢复对应调用。

Harness、Skills、MCP、工作区、记忆、检查点与 diff 均是运行状态投影，不是前端自行实现 AI 底层。普通聊天不因 UI 出现“编码”模式自动获得文件或 shell 权限。本地演示必须显式标记，断开连接不能渲染成已连接。完整契约见 [YD Agent 工作台](yd-agent-workbench.md)。



## 7. 已公开的组件目录

以下名称已在源码根入口和 resolver 核对。目录描述职责，不承诺与 FA Pro 或 Arco 同名 API 兼容。基础 47 个 adapter 沿用其 Fa 行为契约；extended 与 AI 以自身契约为准。新增组件必须同步本表与选型规则，不能在文档先列出尚不存在的入口。

| 类别 | 公开入口 |
| --- | --- |
| 页面、布局与分区 | `YdPageHeader`、`YdPageMain`、`YdTablePageLayout`、`YdCard`、`YdDivider`、`YdFixedBar`、`YdScrollArea`、`YdTabs`、`YdCollapsible`、`YdLayoutContainer`、`YdWorkspaceLayout`、`YdWorkspaceTabs`、`YdBreadcrumb`、`YdAnchor`、`YdSettingsLayout`、`YdSettingsSection` |
| 普通动作 | `YdButton`、`YdButtonGroup`、`YdCopyButton`、`YdAction`、`YdFab`、`YdPopconfirm` |
| 表单与输入 | `YdForm`、`YdFormItem`、`YdInput`、`YdTextarea`、`YdInputOTP`、`YdNumberField`、`YdNumberRange`、`YdSwitch`、`YdSlider`、`YdPasswordStrength`、`YdMention`、`YdInputTag`、`YdRate`、`YdColorPicker`、`YdTreeSelect` |
| 集合选择 | `YdSelect`、`YdCombobox`、`YdMultiSelect`、`YdCascader`、`YdCheckbox`、`YdCheckboxGroup`、`YdRadioGroup`、`YdSegmented` |
| 层级与专用选择 | `YdTree`、`YdTreeSelect`、`YdSortableTree`、`YdTablePicker`、`YdIconPicker` |
| 日期与时间 | `YdDatePicker`、`YdRangePicker`、`YdTimePicker`、`YdDate`、`YdCalendarDate`、`YdCalendarDateRange`、`YdCalendarMonth`、`YdCalendarMonthRange`、`YdCalendarYear`、`YdCalendarYearRange`、`YdTimeAgo` |
| 查询与分页 | `YdListToolbar`、`YdFilterSelect`、`YdSearchBar`、`YdDynamicSearchForm`、`YdPagination` |
| 数据与关系展示 | `YdTable`、`YdResponsiveTable`、`YdEnhancedTable`、`YdResponsiveEnhancedTable`、`YdList`、`YdListRow`、`YdDescriptions`、`YdStats`、`YdStat`、`YdDigitalCard`、`YdTrend`、`YdSparkline`、`YdGraphCanvas`、`YdInfiniteScroll` |
| 技术工作流画布 | `YdWorkflowCanvas`、`YdWorkflowHandle` |
| 信息与辅助标识 | `YdAvatar`、`YdBadge`、`YdTag`、`YdIcon`、`YdLabel`、`YdKbd`、`YdKbdGroup` |
| 浮层、菜单与预览 | `YdModal`、`YdDrawer`、`YdPopover`、`YdTooltip`、`YdHoverCard`、`YdDropdown`、`YdContextMenu`、`YdLinkPreview`、`YdMockupBrowser`、`YdMockupWindow`、`YdTour`、`YdPopconfirm` |
| 文件 | `YdFilePicker`、`YdFileUpload`、`YdImageUpload`、`YdImagePreview`、`YdCropper` |
| 反馈 | `YdAlert`、`YdToast`、`YdEmpty`、`YdResult`、`YdStateIllustration`、`YdProgress`、`YdImageCaptcha` |
| 流程与任务 | `YdSteps`、`YdTimeline`、`YdLoading`、`YdLoadingOverlay`、`YdSkeleton`、`YdMultiStepLoader`、`YdCountdown` |
| 辅助标识 | `YdWatermark` |
| 内容工具 | `YdCode`、`YdCodePreview`、`YdCodeEditor`、`YdMarkdownEditor`、`YdPageBuilder`、`YdQrcode`、`YdStorageBox` |
| 数字与内容运动 | `YdCountTo`、`YdAnimatedCountTo`、`YdAnimatedCountToGroup`、`YdStat`、`YdStats`、`YdCarousel`、`YdSmoothSwipe`、`YdMarquee`、`YdScrollingText` |
| 文本动效 | `YdBlurReveal`、`YdFlipWords`、`YdSparklesText`、`YdTextHighlight`、`YdTextShiny` |
| 装饰与互动 | `YdAnimatedBeam`、`YdBorderBeam`、`YdFlipCard`、`YdGlowyCard`、`YdGlowyCardWrapper`、`YdGradientButton`、`YdInteractiveButton`、`YdParticlesBg`、`YdPatternBg`、`YdSpotlightCard`、`YdScratchOff` |
| Agent 组合 | `YdAgentWorkbench`、`YdAgentContextPanel`、`YdAgentAnswer` |
| 聊天与回答元件 | `YdChatGraphView`、`YdChatMessageList`、`YdChatProcess`、`YdChatSender`、`YdCitationList`、`YdAttachmentList`、`YdChatSessionList`、`YdWelcome`、`YdChatWindow`、`YdBubble`、`YdPrompts`、`YdThoughtChain`、`YdChatActions`、`YdSuggestion`、`YdChatLoading`、`YdChatReasoning` |
| 根装配（仅宿主/预览） | `YdUiProvider` |

组合式公开入口包括 `useYdDrawer`、`useYdModal`、`useYdImagePreview`、`useYdToast`、`useYdLoading`、`useYdFilePicker`、`useYdMotion`、`useYdChat`、`useYdChatStream`、`useYdPersistentChatStream`、`clearYdPersistentChatOwner`、`provideYdChatConfig`、`useYdChatConfig`。`useIsMobile` 是现有断点能力，不为改前缀另实现重复观察器。组件与模型详细边界见 [YD 通用组件库](yd-components.md)。

- `YdFab` 用 `v-model:open` 控制开合，动作配置支持图标、标签、禁用态；扇形最多四个动作，超过改用纵向布局。Escape/外点/焦点离开收起，动作由调用方处理。
- `YdStats` 提供指标组合布局，卡片间使用主题间距；`YdStat` 以具名插槽承载图表和动作，数字动画通过 `YdCountTo` / `YdAnimatedCountTo` 组合。它们与 `YdDigitalCard` 的带强调边框单卡视觉用途不同。
- `YdEmpty` 与 `YdResult` 承载空态与结果状态，插画位由 `YdStateIllustration` 提供 `empty`/`no-result`/`error`/`forbidden`/`success`/`warning` 六个命名场景。默认仍是 56px 图标盒，显式选择场景或传 `#illustration` 插槽才渲染插画；插画是纯装饰（`aria-hidden`、不可聚焦），颜色只取中性语义变量与 `--yd-status-*`，随三套外观、明暗与减少动效偏好变化。它不替代状态文案与恢复动作，业务也不得因此自建插画素材库。
- `YdColorPicker`、`YdTreeSelect` 的面板传送到 body，必须由共享弹层样式承载，不允许把面板样式留在会被裁切的容器里。
- `YdImageCaptcha` 只有 `mode="remote"` 接入服务端挑战与校验并拿到一次性票据；`demo` 模式是本地绘制，禁止用于认证、防刷或风控。
- `YdList` / `YdListRow` 只用于非表格内容行；需要字段对齐、排序、分页或批量选择时使用表格组件。
- `YdMockupBrowser` 地址输入、前后退、刷新只更新本地 `v-model:url` 和事件；`YdMockupWindow` 的最小化/最大化/关闭通过 `v-model` 状态表达，不访问外网或操作系统窗口。
- `YdMockupBrowser` 地址输入、前后退、刷新只更新本地 `v-model:url` 和事件；`YdMockupWindow` 的最小化/最大化/关闭通过 `v-model` 状态表达，不访问外网或操作系统窗口。
- `YdCode` 保持高亮、复制与 `lineNumbers`；新增 `prefixes` 可为每行提供终端提示符或结果标记。
- `YdMarkdownEditor` 的只读模式是阅读区域而不是输入控件：正文内边距与列表标记由组件补回（应用层 reset 会清掉 `list-style`），内容不贴边框；只读内容不能就地修复，此时 `error` 以提示条呈现在编辑区域之外，不给只读区域套红色校验边框。可编辑状态的校验失败仍按表单约定用无效边框 + 下方错误文案表达。
- `YdSteps` 的 `steps` 项可提供独立 `status` 与 `icon`，否则继续按 `v-model` 和全局 `status` 推导；`YdTimeline` 支持方向、紧凑布局、可选日期、图标及自定义 marker/content 插槽。
- `YdSettingsLayout` 默认整块内容居中限宽（`align="center"`，`widthClass` 可覆盖），宽屏为左侧吸顶分区导航 + 右侧内容；窄屏**不**退化为横向标签，而是渲染分区选择器切换分区。宿主设置类页面统一用 `surface` 模式：页面根节点铺白底（`min-h-full bg-card`）、不再套 `YdPageMain` 卡片，分区导航吸顶、页面自身滚动；**保存类操作统一放 `YdPageHeader`**（多分区按当前分区切换按钮），分区内不再出现「页底保存」按钮，`#footer` 只留给对话框式确认。宿主设置类页面（系统设置、安全中心、主题配置、数据备份）统一由它承载分区；页面已有 `YdPageHeader` 时不再传 `title`/`description`，避免双标题。
- `YdResponsiveEnhancedTable` 是 `YdEnhancedTable` 的响应式版本：窄屏按桌面可见列生成卡片，`#cell-{id}` 插槽与列的 `cell` 渲染函数在卡片中复用，可用 `cardHiddenColumns` 隐藏宽列；`actions` 支持函数 `label`（启用/停用）与行级 `visible`。**有行级管理操作的列表统一优先使用该组件：名称身份列固定左侧，内置唯一“操作”下拉列固定右侧；所有行级操作集中在 `actions`，危险动作仍必须进入现有确认流程。** 不再同时声明 `operation` 普通列或在移动卡片重复绘制同一组操作。`YdPagination`、筛选和加载状态仍由页面组合。只读列表可使用 `YdResponsiveTable`；必须完全自绘移动卡片时也可使用其 `#card`，但不能因此重复创建桌面/移动两套业务行为。

下表是能力缺口，不是可用组件目录。完成封装、公开导出和验证后，再更新为实际组件名称。

| 缺口 / 规划入口（未完成前不可使用） | 迁移要求 |
| --- | --- |
| 完整多选搜索与远程候选契约 | 保留数组模型、候选标签、删除、键盘与远程加载；不硬塞单值 Combobox。标签自由录入已使用 `YdInputTag` |
| 特殊上传/文件候选与颜色选择 | 缺少当前公开上传器能力时，新增受控 adapter，不回退裸 input |
| 编辑型工作流画布、可视化构建器 | 保留节点、边、拖拽、缩放、历史、导入导出等真实能力；不是 YdGraphCanvas |
| 完整图表与高级集合交互 | 根据实际业务需要封装图表引擎、远程多选、异步树、拖拽等能力，公开业务稳定模型 |

Grid/Space 等纯布局优先原生结构加布局工具类，不为所有第三方布局组件机械制造同名包装。根 ConfigProvider、主题装配和第三方运行时的必要 provider 属框架实现层；业务不直接使用它们。

### 7.2 不能机械改名的 API

Arco 表格使用的数据字段与插槽不等于 YD 的 TanStack `accessorKey` / `id` 和 `row.original`。Radio 单项需要改成 RadioGroup 的 `options`；Tabs 使用 `list` 与具名内容槽；Dropdown 使用二维分组 `items`；Descriptions 使用自身的条目契约。Arco Button 的 `type` 与 YD `variant`、Modal 的 `visible` 与 YD 默认 `v-model` 也不能简单保留原属性。

Arco Week/Month/Quarter/Year Picker 迁移到 `YdDatePicker` 时，应显式设置 `mode`，保留原来的日期格式和选择粒度。`YdCalendar*` / `YdDate` 使用 DateValue，既有 Picker 主要使用 string/number；模型必须显式转换。数字动画组件的秒/毫秒单位因组件而异，按契约核对，不把 Pro 参数原样复制。

上传器、确认 API、通知 API、受控表格排序/展开、树回调和编辑器不能仅通过批量文本替换迁移。必须核对属性、事件、插槽、模型与 exposed 方法，并验证用户任务仍完整。

## 8. 持续维护与验收

这份规范随组件和业务变更即时维护，不是一次性整理文档。新增/改变通用能力的同一工作项必须完成：

1. 明确使用场景、现有组件无法覆盖的能力与归属，优先扩展现有受控契约。
2. 在共享组件库实现 YD 入口，封装模型、事件、插槽、实例方法、属性透传与主题弹出层；保持稳定字符串 ID。
3. 同步根/领域导出、resolver、自动导入、公开类型和插件 SDK 的宿主共享映射，避免一处有入口另一处为 undefined。
4. 在实时预览（`yudream-frontend/apps/component-showcase`）提供或更新对应示例：组件库每次新增或修改组件都必须同步预览，示例应反映当前 API、外观与交互；适用时覆盖真实的空/加载/失败/禁用/选中/取消状态。演示与真实执行明确区分。
5. 更新本文件的选型与目录、对应 API 文档、根与前端/插件 AGENTS，以及受影响的 architecture skill / knowledge 规则。
6. 运行组件和受影响 app 的类型检查、定向 lint、构建及必要行为测试；在三套外观、深浅、移动和插件变量覆盖下检查视觉与交互。

在宿主 `yudream-frontend` 运行 `pnpm sync:ui-docs`，自动更新相邻插件仓的规则副本；完成前运行 `pnpm check:ui-docs`。检查同时核对公开组件目录、resolver、根导出与 SDK 桥；新增组件漏写目录或副本过期都会失败。CI 不挂载插件仓时仍核对宿主目录与契约。插件仓位置不同可向脚本传入 `--plugins=<仓库路径>`，不要在文档写死开发机路径。

全局扫描需要覆盖模板、脚本导入、别名、render/JSX、通用原生交互与业务样式。规则应报告不符合 YD-only 的业务用法；不能通过行级禁用规则、临时白名单或把组件挪成局部文件规避。组件实现和框架装配的边界例外按路径与职责集中记录，并有测试，不能任意扩张。

两仓 GitLab 的 `validate:yd-ui` 使用相同的 AST 扫描器，覆盖完整嵌套插槽、导入与类型导入、JSX/render、原生通用控件、无链接目标的动作锚点、`v-loading` 与私有品牌令牌；规则测试覆盖误判和绕过场景。正文链接事件代理、布局、标签与图表的多键数据浏览不视为动作按钮。

迁移期间 CI 使用宿主 `yudream-frontend/eslint-rules/yd-only-baseline.json`、插件 `ci/yd-ui-baseline.json` 做不回退门禁。基线记录已提交源码的 SHA；只能用 `--source=git:<已提交版本> --write-baseline` 初始化或缩减，禁止从工作区接受新增违规。每个文件/规则/消息的计数只减不增，不能用一个页面减少的数量抵消另一个页面新增的违规。全部归零后保留 findings 为空的基线和验收提交 SHA，门禁仍按零违规阻断，不得重新接受历史例外；`audit:ui` 报告的 `summary.violationCount` 是迁移验收计数，CI 保留完整报告而不倾倒源码到日志。

迁移时按独立模块收敛，每个模块验证通过后创建中文本地提交。保留用户已有修改；不批量覆盖尚未核对契约的复杂组件。生成式 CMS 内容、公开主题模板和第三方库内部生成 DOM 与业务 Vue 源码区分处理，禁止对已保存内容做无语义保证的字符串替换。

远程插件只能消费宿主实际可用、且已发布验签的 components/SDK 契约。工作区源码已有某个入口不代表生产插件已可用；不能伪升版本、复制宿主组件到插件或凭空宣称下游升级完成。发布按契约发布规范单独推进，未要求时不 push/tag/发布。

验收至少覆盖以下可观察结果：

- 同一任务在灵动、雅致、紧凑及明暗主题下都有可读文本、边框、焦点和状态；主题插件继承不破坏布局与功能。
- 通用交互全部来自 YD；组件缺口先封装，不在页面重造控件或裸用第三方控件。
- 键盘可完成选择、输入、弹层打开/关闭与任务操作；输入法组合不误发送；移动端不依赖悬停。
- 后台管理列表按 768px 断点优先使用 `YdResponsiveEnhancedTable`：名称列固定左、唯一操作菜单列固定右，所有行操作合并入菜单，桌面与移动卡片共用操作定义；只读列表或确需完全自绘卡片时可用 `YdResponsiveTable`。不重复手写双端列表或双端操作行为。
- 已启用动画在所有外观中保持功能，装饰不会抢夺交互；加载、取消、失败与重试来自真实业务状态。
- 规范、目录、组件、resolver、SDK、预览与扫描规则保持同一事实，不存在文档承诺但没有实现的组件。
