# 组件库 @yudream/components

宿主共享组件库，版本 **1.2.0**，是所有页面（含插件前端）的统一 UI 基础。

## 安装与使用

```bash
pnpm add @yudream/components
```

后端由 YuDream 原创，前端使用 [Fantastic-admin](https://fantastic-admin.hurui.me/)。组件按来源与职责分工如下，业务代码请优先使用项目组件，不要直接绕过组件层使用底层 UI 控件。

## 推荐导入入口

- `Fa*` 推荐从 `@yudream/components` 导入；也支持 unplugin 自动导入，resolver 导出前缀为 `Fa`：

  ```ts
  import { FaResolver } from '@yudream/components/resolver'
  // Components({ resolvers: [FaResolver()] })
  ```

- `Yd*` 为 YuDream 原创组件：AI 对话元件推荐从 `@yudream/components/ai` 导入（该入口名称沿用历史命名）；数据与表单元件 `YdGraphCanvas`、`YdTablePicker`、`YdDatePicker`、`YdRangePicker`、`YdTimePicker` 与 `Fa*` 一样从根入口 `@yudream/components` 导入，且已注册进 resolver（`Fa`/`Yd` 前缀均支持自动导入）。

显式导入示例：

```vue
<script setup>
import { FaButton, YdDatePicker, YdGraphCanvas, YdTablePicker } from '@yudream/components'
import { YdBubble } from '@yudream/components/ai'
</script>
```

## 组件来源与分工

| 系列 | 数量 | 定位与来源 |
|---|---|---|
| `Fa*` | 47 个 | Fantastic-admin 框架自带的通用后台 UI 基础组件（reka-ui 封装 + Tailwind 样式，shadcn 风格）；`FaResponsiveTable` 是 YuDream 原创的例外，统一从 `@yudream/components` 导入 |
| `Yd*` | 21 个组件 + 3 个 composable | YuDream 原创组件与 composable；AI 对话元件从 `@yudream/components/ai` 导入，数据与表单元件（`YdGraphCanvas`、`YdTablePicker`、`YdDatePicker`、`YdRangePicker`、`YdTimePicker`）从根入口导入 |

业务代码**优先使用 Fa\*/Yd\***；ESLint 规则 `yudream/prefer-fa-component` 会检测 Arco `<a-*>` 直接使用并给出替代建议。

## 样式约定

- 业务样式只用中性语义变量：`--color-bg-*`、`--color-text-*`、`--color-border-*`、`--color-fill-*`。
- 禁止手写品牌色与 `--primary-N` 色阶 token（规则 `yudream/no-brand-color-token`）；主题、主色、深浅模式由后台主题配置统一控制。
- 运行 `pnpm audit:ui` 生成审计报告，DevTools 浮窗「审查」页可视化展示违规项。

::: warning 关于本目录的在线演示
以下组件页中的交互演示为**效果示意**（用等价原生控件模拟外观与行为），源码区展示的是组件在项目中的**真实用法**；API 表从组件源码提取，以实际类型定义为准。完整可运行的示例还可在主前端仓 `apps/component-showcase` 与各组件 `_examples/` 目录中找到。
:::

## 组件总览

### 基础控件

- [FaButton 按钮](/components/fa-button) · [FaInput 输入框](/components/fa-input) · [FaTextarea 文本域](/components/fa-textarea) · [FaInputOTP 验证码输入](/components/input-otp)
- [FaSelect 选择器](/components/fa-select) · [FaCheckbox 复选框](/components/fa-checkbox) · [FaSwitch 开关](/components/fa-switch) · [FaSlider 滑块](/components/slider)
- [FaLabel 标签](/components/label) · [FaPasswordStrength 密码强度](/components/password-strength) · [FaKbd 键盘按键](/components/kbd) · [FaKbdGroup 按键组](/components/kbd-group)

### 数据展示

- [FaTag 标签](/components/tag) · [FaBadge 徽标](/components/badge) · [FaAvatar 头像](/components/avatar) · [FaAlert 警告提示](/components/alert)
- [FaTrend 趋势](/components/trend) · [FaDescriptions 描述列表](/components/descriptions) · [FaDivider 分割线](/components/divider)
- [FaTable 表格](/components/fa-table) · [FaTabs 标签页](/components/fa-tabs) · [FaProgress 进度条](/components/fa-progress) · [FaPagination 分页](/components/pagination)
- [FaResponsiveTable 响应式表格](/components/responsive-table) · [FaSearchBar 搜索栏](/components/search-bar)

### 浮层与反馈

- [FaModal 对话框](/components/fa-modal) · [FaDrawer 抽屉](/components/fa-drawer) · [FaToast 轻提示](/components/toast)
- [FaTooltip 工具提示](/components/tooltip) · [FaPopover 弹出面板](/components/popover) · [FaHoverCard 悬停卡片](/components/hover-card)
- [FaDropdown 下拉菜单](/components/dropdown) · [FaContextMenu 右键菜单](/components/context-menu) · [FaCollapsible 折叠面板](/components/collapsible)

### 布局与其他

- [FaPageHeader 页头](/components/page-header) · [FaPageMain 页面主体](/components/page-main) · [FaFixedBar 固定栏](/components/fixed-bar)
- [FaScrollArea 滚动区域](/components/scroll-area) · [FaButtonGroup 按钮组](/components/button-group) · [FaIcon 图标](/components/fa-icon)
- [FaImagePreview 图片预览](/components/fa-image-preview) · [FaFileUpload 文件上传](/components/fa-file-upload) · [FaImageUpload 图片上传](/components/fa-image-upload)

### 功能函数

| 函数 | 文档 |
|---|---|
| `useFaToast()` | [FaToast 轻提示](/components/toast)（vue-sonner 封装） |
| `useFaModal()` | [命令式弹窗](/components/use-fa-modal) |
| `useFaDrawer()` | [命令式抽屉](/components/use-fa-drawer) |
| `useFaImagePreview()` | [命令式图片预览](/components/fa-image-preview) |
| `useIsMobile()` | 移动端断点判断（见包内 README） |

### Yd* YuDream 原创组件

- [YdBubble 气泡](/components/yd-bubble) · [YdChatSender 输入发送器](/components/yd-chat-sender) · [YdChatMessageList 消息列表](/components/yd-chat-message-list) · [YdChatWindow 对话窗口](/components/yd-chat-window)
- [YdChatSessionList 会话列表](/components/yd-chat-session-list) · [YdChatProcess 执行过程](/components/yd-chat-process) · [YdChatGraph 关系图谱](/components/yd-chat-graph) · [YdChatActions 操作栏](/components/yd-chat-actions)
- [YdChatReasoning 思维链](/components/yd-chat-reasoning) · [YdChatLoading 加载态](/components/yd-chat-loading) · [YdThoughtChain 步骤链](/components/yd-thought-chain)
- [YdSuggestion 建议](/components/yd-suggestion) · [YdPrompts 提示词](/components/yd-prompts) · [YdCitationList 引用列表](/components/yd-citation-list)
- [YdAttachmentList 附件列表](/components/yd-attachment-list) · [YdWelcome 欢迎](/components/yd-welcome)
- [YdGraphCanvas 关系图谱画布](/components/yd-graph-canvas) · [YdTablePicker 弹出式选择输入框](/components/yd-table-picker)
- [YdDatePicker 日期选择器](/components/yd-date-picker) · [YdRangePicker 日期范围选择器](/components/yd-range-picker) · [YdTimePicker 时间选择器](/components/yd-time-picker)
