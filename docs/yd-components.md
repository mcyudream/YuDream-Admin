# YD 通用组件库

YD 在既有 FA 组件、主题变量和交互基础上提供自己的外观层，并按 [FA Pro 公开组件目录](https://fantastic-admin.hurui.me/components/pro/FaAnimatedBeam.html) 的 47 个参考项独立实现通用交互、日历和动效能力。参考目录用于确定功能范围；YD 没有复制闭源 Pro 源码，也不承诺 FA Pro 的属性、插槽或事件兼容。下方矩阵记录当前实现的能力与边界。

新增入口与既有 `YdDatePicker`、`YdRangePicker`、`YdTimePicker`、`YdIconPicker`、AI 和图形组件共用 `@yudream/components`。组件源码、resolver 与插件 SDK 的宿主共享映射已接入本仓工作区；本次未变更契约版本，也未发布到 Nexus。下游生产插件需等契约正式发布并验签后再升级依赖。

## 接入与基础二次封装

宿主页面统一从 YD 入口使用组件；已有 Fa 基础能力由 47 个 YD adapter 复用。adapter 保留原 Fa 的 props、默认值、布尔转换、model、事件、具名插槽、attrs 和 ref 暴露方法，并增加 YD 外观类。显式传入 `size` 的按钮沿用原尺寸；未传 `size` 的按钮随预设控件高度变化。卡片的头部、内容与底部随统一留白变量变化。

```vue
<script setup lang="ts">
import { YdButton, YdCard, YdInput } from '@yudream/components'
import { ref } from 'vue'

const name = ref('')
</script>

<template>
  <YdCard title="项目名称">
    <YdInput v-model="name" placeholder="输入名称" />
    <YdButton :disabled="!name">保存</YdButton>
  </YdCard>
</template>
```

| 基础类别 | YD 入口 |
| --- | --- |
| 输入与选择 | `YdInput`、`YdTextarea`、`YdInputOTP`、`YdNumberField`、`YdSelect`、`YdCheckbox`、`YdCheckboxGroup`、`YdRadioGroup`、`YdSlider`、`YdSwitch`、`YdPasswordStrength` |
| 按钮与信息 | `YdButton`、`YdButtonGroup`、`YdAlert`、`YdAvatar`、`YdBadge`、`YdTag`、`YdIcon`、`YdLabel`、`YdKbd`、`YdKbdGroup`、`YdProgress`、`YdTrend`、`YdToast` |
| 页面与数据 | `YdCard`、`YdPageHeader`、`YdPageMain`、`YdDescriptions`、`YdDivider`、`YdFixedBar`、`YdScrollArea`、`YdSearchBar`、`YdPagination`、`YdTable`、`YdResponsiveTable`、`YdTabs`、`YdCollapsible` |
| 浮层与文件 | `YdPopover`、`YdTooltip`、`YdHoverCard`、`YdDropdown`、`YdContextMenu`、`YdDrawer`、`YdModal`、`YdImagePreview`、`YdImageUpload`、`YdFileUpload` |

对应组合式入口为 `useYdDrawer`、`useYdModal`、`useYdImagePreview`、`useYdToast`。基础组件的具体参数仍以对应 Fa 源码契约为准；列表手机端使用 `YdResponsiveTable` 的 `#card` 插槽。

`YdCombobox` 与 `YdMention` 使用 Reka 交互基础并由 YD 绘制控件；`YdTree` 和多列模式的 `YdCascader` 仍在组件内部复用 Arco 的树及级联交互，控件和弹出层消费 YD 主题样式。业务调用方使用 YD 的模型、数据结构和插槽，无需直接引入 Arco 原生控件。

## 主题、预设与动效

主色与明暗模式继续由 FA 主题引擎管理。默认主色读取宿主设置 `#18181b`，组件不预设蓝色品牌。灵动、雅致、紧凑是三种外观和交互节奏，可以与当前主色、明暗模式及 ADMIN 插件主题组合。

宿主样式入口和独立预览共同加载：

```ts
import '@yudream/components/appearance.css'
```

默认表面与动效变量位于 `packages/components/src/theme/appearance.css` 的 `yudream-base` 层。主色转换和 FA/内部基础控件色阶生成共用 `@fantastic-admin/themes/runtime` 的 `applyPrimaryColor`；它由宿主主题 store 和独立预览的主题管理器调用，业务页面不自行设置主色。

| 预设 | 根属性 | 卡片 / 控件圆角 | 未显式指定尺寸的按钮高度 | 默认表现 |
| --- | --- | --- | --- | --- |
| 灵动 | `data-admin-style="vibrant"` | `18px` / `12px` | `40px` | 较丰富的弹性、悬停与装饰动效 |
| 雅致 | `data-admin-style="calm"` | `12px` / `8px` | `38px` | 温和过渡、较少粒子与更慢的装饰节奏 |
| 紧凑 | `data-admin-style="compact"` | `8px` / `6px` | `32px` | 更小留白、较少粒子、轻量反馈 |

根属性 `data-admin-motion` 可为 `system` 或 `reduced`。按本项目产品约定，**已启用的功能动画在三种预设中始终运行；灵动保持完整表现，雅致和紧凑按系统 `prefers-reduced-motion: reduce` 与用户 `reduced` 偏好柔化动作**。柔化调整位移幅度、速度与装饰密度，不关闭动画：计数保留补间，数字保持滚动，词语继续轮换，跑马灯和装饰流光继续循环。启用 autoplay 的轮播同样继续播放，只因鼠标悬停、聚焦或页面隐藏而暂停。`useYdMotion()` 的 `reduced` 表示柔化偏好，供新增组件调整表现，不应作为关闭功能动画的条件。

`--yd-emphasis` 从 FA 主色派生文字和装饰强调色；深色模式结合前景色提高可读性，ADMIN 插件可覆盖该根变量。雅致与紧凑在减少动效偏好下减小装饰密度并降低循环速度；跑马灯仍保留连续滚动与悬停暂停能力。

ADMIN 主题插件经既有 `@PluginTheme(scopes = ADMIN)` 和声明式 `style.css` 覆盖变量；无需为每个组件注册主题，也无需新增 SPI 字段。层级为 `yudream-base < yudream-plugin < 宿主未分层工具类`。插件在自己的 `:root` / `.dark` 声明根变量：

```css
:root {
  --yd-surface-radius: 14px;
  --yd-control-radius: 10px;
  --yd-surface-padding: 22px;
  --yd-content-gap: 14px;
  --yd-control-height: 38px;
  --yd-decoration-duration: 6s;
}

.dark {
  --yd-surface-shadow: none;
}
```

还可覆盖 `--yd-surface-shadow`、`--yd-surface-shadow-hover`、`--yd-control-font-size`、`--yd-workspace-gap` 与 `--yd-motion-*`。这些默认值不应写入 DOM inline style，也不应在子布局重设，否则会阻断插件根变量继承。组件局部参数如光束曲率、轮播间距使用自己的局部变量，不覆盖整套根预设。业务页面只消费中性语义变量；品牌强调态由组件体系处理。完整主题规则见 [后台外观预设与动效](frontend-appearance.md)。

生产插件沿用 SDK 的共享构建配置：`@yudream/components` 入口由 `@yudream/plugin-sdk/vite-shared` 映射到宿主提供的组件实例，Vue 同样由宿主共享。插件无需另打包一套 Vue 或组件主题引擎；样式继续走 `@yudream/plugin-sdk/uno-config` 的声明式 `style.css` 流程。

## FA Pro 参考项与当前实现矩阵

以下共 47 项，Group / Wrapper 在其所属参考项中列出。表中“边界”描述当前 YD 实现，并非 Pro API 兼容承诺。

| FA 参考项 | YD 入口 | 核心能力与当前边界 |
| --- | --- | --- |
| `FaAnimatedBeam` | `YdAnimatedBeam` | 元素之间的 SVG 二次贝塞尔光束；曲率、方向、偏移、颜色、时长与尺寸变化重测。当前为单条虚线流光路径。 |
| `FaAnimatedCountTo` | `YdAnimatedCountTo`、`YdAnimatedCountToGroup` | 按位数字滚动、Intl 格式化、前后缀；三预设都保留滚动，减少动效偏好柔化节奏。Group 提供成组排版，不另提供跨实例动画时间线。 |
| `FaBlurReveal` | `YdBlurReveal` | 首次进入视口时模糊、位移显现，直接子元素错峰；可调秒数、延迟、模糊和位移。 |
| `FaBorderBeam` | `YdBorderBeam` | 贴合父元素的渐变边框流光，可调边宽、颜色和毫秒时长；父元素需提供定位上下文。 |
| `FaCalendarDate` | `YdCalendarDate` | 日历单选 / 多选、月分页、星期起点、边界、禁用与不可用日期、快捷项、picker。模型使用 DateValue。 |
| `FaCalendarDateRange` | `YdCalendarDateRange` | 日期范围、双月默认视图、`maximumDays`、非连续范围政策；快捷项经过同一校验。 |
| `FaCalendarMonth` | `YdCalendarMonth` | 月份单选 / 多选、年份分页、月匹配器与 picker；输出周期首日。 |
| `FaCalendarMonthRange` | `YdCalendarMonthRange` | 月份范围、`maximumMonths`、不可用月与非连续范围；输出各端点月份首日。 |
| `FaCalendarYear` | `YdCalendarYear` | 年份单选 / 多选、`yearsPerPage`、年份匹配器与 picker；输出年份首日。 |
| `FaCalendarYearRange` | `YdCalendarYearRange` | 年份范围、`maximumYears`、年份匹配器与 picker。 |
| `FaDate` | `YdDate` | 年 → 月 → 日下钻，单值时可附秒级时间；非法默认时间安全回落。没有另外实现时区选择器。 |
| `FaCarousel` | `YdCarousel` | 泛型项目、水平 / 竖直、索引模型、箭头、指示点、循环及自动播放。鼠标悬停、聚焦和页面隐藏时暂停自动播放。 |
| `FaCascader` | `YdCascader` | 单值级联、叶子限制、清除、多列 / 单面板逐层选择。多列支持 click / hover；当前无多选或远程懒加载接口。 |
| `FaCode` | `YdCode` | Highlight.js 常用语言高亮、行号、复制；未识别语言回落纯文本。显示源码，不执行源码。 |
| `FaCodePreview` | `YdCodePreview` | 默认插槽显示示例，按钮展开 / 收起源码；不编译传入代码或动态生成沙箱。 |
| `FaCombobox` | `YdCombobox` | 单选搜索、分组、禁用项、清除、加载态与虚拟列表。当前模型为单个字符串 / 数字。 |
| `FaCountTo` | `YdCountTo` | RAF 数字补间、小数位、分隔符、前后缀、延迟；各预设保持补间，减少动效偏好调整节奏；提供开始 / 完成事件和 start/reset/stop 方法。 |
| `FaDigitalCard` | `YdDigitalCard` | 标题、值、提示、图标、描述、趋势及自定义值插槽；计数和趋势图通过组合其他 YD 组件提供。 |
| `FaEmpty` | `YdEmpty` | 图标、标题、描述与操作插槽；默认中性空态，不包含插画素材库。 |
| `FaFlipCard` | `YdFlipCard` | X/Y 轴卡片翻转，鼠标、触摸按钮与键盘入口；隐藏面不可交互。无受控 flipped 模型。 |
| `FaFlipWords` | `YdFlipWords` | 词语定时轮换；可调毫秒间隔，页面隐藏时暂停。减少动效偏好柔化切换，保留轮换。 |
| `FaGlowyCard` | `YdGlowyCard`、`YdGlowyCardWrapper` | 独立卡片指针光晕；Wrapper 向后代共享颜色、半径、光晕尺寸和边宽，不实现全局跨卡片指针轨迹。 |
| `FaGradientButton` | `YdGradientButton` | FA 按钮行为与流光边框，支持 disabled/loading；`colors` 当前使用前两个颜色。 |
| `FaIconPicker` | 既有 `YdIconPicker` | Morphicons 优先、Remix 回退，关键词搜索、常用项、分页、清除；沿用系统图标源。 |
| `FaInteractiveButton` | `YdInteractiveButton` | 文案与箭头悬停反馈、disabled/loading；默认内容或 text，不提供额外按钮变体模型。 |
| `FaLayoutContainer` | `YdLayoutContainer` | 水平 / 竖直分割面板、键盘及拖动调整、折叠、尺寸限制、可选本机布局持久化。面板通过具名插槽提供内容。 |
| `FaLinkPreview` | `YdLinkPreview` | 悬停预览卡、标题 / 描述 / 图片、协议检查；元数据由调用方提供，不自动爬取 URL 或生成截图。 |
| `FaLoading` | `YdLoading`、`useYdLoading` | 12 种 CSS 指示器、尺寸、文案、全屏遮罩；组合式入口返回 close，作用域结束自动清理。 |
| `FaMarquee` | `YdMarquee` | 插槽内容连续横 / 竖滚动、反向、间距、秒数与悬停暂停；减少动效偏好降低循环速度，保留连续滚动；复制内容隐藏于无障碍树。 |
| `FaMention` | `YdMention` | 输入框 / 多行提及、光标处候选、筛选、键盘导航、IME 处理；文本与选中 ID 双模型。当前为文本提及，不是富文本原子标签编辑器。 |
| `FaMultiStepLoader` | `YdMultiStepLoader` | 顺序异步动作、命名结果、取消信号、错误索引、完整重试。动作自行响应 AbortSignal；不自动回滚业务副作用。 |
| `FaParticlesBg` | `YdParticlesBg` | CSS 粒子背景，数量、尺寸、速度、颜色；三预设调整粒子密度。没有物理碰撞或指针吸附引擎。 |
| `FaPatternBg` | `YdPatternBg` | grid/dots/lines、三种间距、径向遮罩与八方向背景平移；当前图案集固定为三种。 |
| `FaQrcode` | `YdQrcode` | 本机生成 PNG data URL，纠错级别、尺寸、留白、颜色，ready/error 与下载方法。默认黑白，保持可扫描对比。 |
| `FaScratchOff` | `YdScratchOff` | 本机 Canvas 刮层、笔刷、采样进度、阈值揭晓、重置及键盘 / 直接揭晓；覆盖层不载入跨域图片。 |
| `FaScrollingText` | `YdScrollingText` | 文本或插槽连续横向滚动，反向与秒数；复用 YdMarquee。 |
| `FaSmoothSwipe` | `YdSmoothSwipe` | 原生滚动吸附、水平 / 竖直、鼠标拖拽、键盘导航、索引及 prev/next/scrollTo；items 或直接子项插槽。 |
| `FaSparklesText` | `YdSparklesText` | 文字周围 CSS 星光、数量和双色配置；最多 24 个固定分布星光。 |
| `FaSparkline` | `YdSparkline` | 单序列 SVG line/area/bar、尺寸、线宽、语义颜色及无障碍数据标签；没有坐标轴、交互 tooltip 或多序列图例。 |
| `FaSpotlightCard` | `YdSpotlightCard` | 指针位置光斑、尺寸 / 颜色 / 透明度；减少动效偏好柔化光斑强度，保留指针跟随；触摸不追踪光斑。 |
| `FaSteps` | `YdSteps` | 横 / 竖步骤、从 0 开始的索引模型、四种状态、可点击步骤和以 ID 命名的插槽；不自行执行任务。 |
| `FaStorageBox` | `YdStorageBox` | 当前账号和 boxId 分区的本机 JSON 预设、命名保存 / 取出 / 删除和数量上限；没有服务端同步或加密。 |
| `FaTextHighlight` | `YdTextHighlight` | 可见时的文字背景显现，四方向、毫秒时长与延迟。 |
| `FaTextShiny` | `YdTextShiny` | 主题语义色扫光文字、秒数和延迟；内容由默认插槽提供。 |
| `FaTimeAgo` | `YdTimeAgo` | Intl 相对时间、更新间隔、秒级选项及原时间 title；月 / 年按近似秒数分段。 |
| `FaTimeline` | `YdTimeline` | 泛型时间项、日期显示、相邻同日合并标签、项目插槽；保持输入顺序，不自动排序或滚动激活节点。 |
| `FaTree` | `YdTree` | 字符串 key 树、选中 / 勾选 / 展开模型、筛选保留祖先、虚拟列表及批量方法；当前无拖动重排或远程懒加载接口。 |

额外提供 `YdCopyButton(text, label?, disabled?)`，发出 `copied` / `error`，供代码、链接或普通文字复用复制反馈。

## 日期模型、限制与事件

日历系列使用 `@internationalized/date` 的不可变 `DateValue`，相关构造器和解析函数可直接从组件包导入。Vue 状态使用 `shallowRef`，避免深层代理破坏日期类类型与实例语义。

```vue
<script setup lang="ts">
import type { DateValue, YdDateRange } from '@yudream/components'
import { parseDate, YdCalendarDate, YdCalendarDateRange, YdDate } from '@yudream/components'
import { shallowRef } from 'vue'

const selected = shallowRef<DateValue | undefined>(parseDate('2026-10-14'))
const interval = shallowRef<YdDateRange>({ start: undefined, end: undefined })
const meeting = shallowRef<DateValue>()
const min = parseDate('2026-10-01')
const max = parseDate('2026-12-31')
</script>

<template>
  <YdCalendarDate v-model="selected" :min-value="min" :max-value="max" picker />
  <YdCalendarDateRange
    v-model="interval"
    :maximum-days="7"
    :is-date-unavailable="date => date.day === 20"
    :allow-non-contiguous-ranges="false"
  />
  <YdDate v-model="meeting" with-time default-time="09:30:00" picker />
</template>
```

模型形状如下：

| 场景 | `modelValue` |
| --- | --- |
| 单选 | `DateValue | undefined` |
| 多选 | `DateValue[] | undefined`，通过 `multiple` 启用；未显式指定时也可由数组模型推断 |
| 范围 | `{ start: DateValue | undefined, end: DateValue | undefined }`；选择过程中可只有 start |
| 清空 | `undefined`；范围也可传入 start/end 均为空的对象 |

未传 `modelValue` 时组件维护内部值；显式绑定 `v-model` 时跟随外部值与外部清空。不要将单值、数组和范围对象混用为同一模式的模型。初始视图依次取 `placeholder`、模型中的首个选值、`defaultPlaceholder` 和本地今天；`placeholder` 可通过 `v-model:placeholder` 控制导航视图。只读模式允许浏览日历，禁止修改值。

| 参数组 | 参数与含义 |
| --- | --- |
| 所有日历 | `minValue`、`maxValue`、`disabled`、`readonly`、`locale`、`dir`、`preventDeselect`、`shortcuts`、`formatValue` |
| 日期网格 | `weekStartsOn` 默认 1；`fixedWeeks` 默认 true；`numberOfMonths` 单选默认 1、日期范围默认 2；`pagedNavigation` |
| 日期匹配器 | `isDateDisabled` / `isDateUnavailable` |
| 月份匹配器 | `isMonthDisabled` / `isMonthUnavailable` |
| 年份匹配器 | `isYearDisabled` / `isYearUnavailable`；`yearsPerPage` 交给 Reka 默认 12 |
| 范围限制 | 日期 `maximumDays`、月份 `maximumMonths`、年份 `maximumYears`；值需为有限正数，按整数上限计算且包含两个端点 |
| 非连续范围 | `allowNonContiguousRanges` 允许范围内部经过 unavailable 项；端点 unavailable 和所有 disabled 项仍被拒绝 |
| picker | `picker`、`open` / `v-model:open`、`pickerPlaceholder`、`closeOnSelect`；默认单值或完整范围选中后关闭，多选和带时间时保持展开 |
| YdDate 时间 | `withTime`、`defaultTime`；单值时生效，输出 `CalendarDateTime`。`HH:mm` / `HH:mm:ss` 可解析，非法值回落到合法默认时间，再回落 `00:00:00` |

日选择按日比较边界；月和年选择只要该周期与 min/max 区间相交即可选择，输出该周期首日。带时间的 `YdDate` 还会检查包含时间的 min/max。快捷项使用 `{ text, value, disabled? }`，value 可为同模式的模型或返回模型的函数；快捷项不能绕过周期匹配器、内部禁用 / 不可用项或最大范围。

事件为 `update:modelValue`、`change`、`update:placeholder`、`update:open`、`complete`。`change` 包括有效的范围中间状态；`complete` 只在非空单值 / 多值或两个端点完整的有效范围上发出。YdDate 的年 / 月下钻只调整视图，最后选日才改变 model。传入外部不合法 model 会保留原值并由底层展示无效状态；限制阻止的是新的用户选择，不擅自改写业务状态。

这些日历与既有 `YdDatePicker` / `YdRangePicker` 的模型契约不同，迁移时显式转换。存入 API 或 StorageBox 时使用 `date.toString()`；取回后使用 `parseDate` / `parseDateTime` 恢复日期实例。

## 核心 API 与组合示例

### 选择、树与提及

| 入口 | 核心模型 / 参数 | 插槽、事件和方法 |
| --- | --- | --- |
| `YdCombobox` | `v-model: string | number | undefined`；`options` 为 `{ label, value, disabled? }` 或 `{ label, options }` 分组；`virtualList`、`filter(search, option)`、`clearable`、`loading` | `#option={ option }`、`#empty`、`#loading`；`change`、`clear`、`search` |
| `YdCascader` | 相同单值模型；递归 `options`；`leafOnly=true`、`panelMode='multi'|'single'`、`triggerMode='click'|'hover'` | `#option={ option }`、`#empty`；`change`、`clear`。triggerMode 作用于多列模式 |
| `YdTree` | `data: YdTreeNode[]`；`selectedKeys`、`checkedKeys`、`expandedKeys` 三个字符串数组模型；`checkable`、`filterable`、`virtualList`、`defaultExpandAll`、`filterMethod` | 默认槽 `{ node, expanded }`、`#empty`；`selectionChange(node|null)`、`checkChange(nodes)`；ref 的 `expand(keys?)`、`collapse(keys?)`、`checkAll(on)`、`setSelection(key)`、`clearSelection()` |
| `YdMention` | `v-model` 文本、`v-model:value` 去重后的提及 ID 数组；`items: YdMentionOption[]`；trigger 默认 `@`，inputAs 默认 textarea | `#option={ option }`、`#empty`；`change(text)`、`search(query)`、`select(option)`；ref 的 `focus()` / `blur()` |

ID 与树 key 使用字符串，包含 Java Long / Snowflake ID；不要为匹配组件而转为 Number。树筛选保留命中节点的祖先，默认全展开只用于首次有数据的初始化，之后 `collapse()` 可正常折叠。禁用父节点的后代同样不允许选中或勾选。

```vue
<script setup lang="ts">
import type { YdMentionOption, YdTreeNode } from '@yudream/components'
import { YdMention, YdTree } from '@yudream/components'
import { ref } from 'vue'

const selectedKeys = ref<string[]>([])
const checkedKeys = ref<string[]>([])
const nodes: YdTreeNode[] = [
  { key: 'platform', label: '平台', children: [{ key: 'theme', label: '主题中心' }] },
]
const message = ref('')
const mentionedIds = ref<string[]>([])
const people: YdMentionOption[] = [
  { value: '1900000000000000001', label: '小余' },
  { value: '1900000000000000002', label: '小梦' },
]
</script>

<template>
  <YdTree v-model:selected-keys="selectedKeys" v-model:checked-keys="checkedKeys" :data="nodes" checkable filterable>
    <template #default="{ node, expanded }">
      <span>{{ node?.label }}{{ expanded ? ' · 已展开' : '' }}</span>
    </template>
  </YdTree>
  <YdMention v-model="message" v-model:value="mentionedIds" :items="people" label="协作消息" />
</template>
```

Mention 在光标位置识别触发词，方向键 / Home / End 选择候选，Enter 插入、Esc 关闭；输入法组合期间不抢键。文本修改后重新按完整词边界计算提及 ID；同名 label 会自动附加 value 区分。它不会把普通文本变成不可拆分的富文本 token。

### 内容滑动、分割布局与趋势

| 入口 | 核心参数 | 组合方式 |
| --- | --- | --- |
| `YdCarousel<T>` | `items: T[]`、索引 `v-model`、direction、autoplay、interval(ms)、loop、arrows、indicators、label | `#item={ item, index, active }`，默认槽也接收同样对象；ref 的 prev/next/scrollTo |
| `YdSmoothSwipe<T>` | 可选 items、索引模型、direction、itemSize(CSS 字符串)、gap(px)、snapAlign、draggable | 有 items 用 `#item`；无 items 用默认槽提供直接子项，接收 activeIndex/prev/next/scrollTo。竖直模式应由页面给定可滚动高度 |
| `YdLayoutContainer` | panels 中 key、defaultSize/minSize/maxSize、collapsible/collapsedSize、sizeUnit('%'|'px')；direction、storageKey、keyboardResizeBy、disabled | 按 panel.key 提供具名槽，或 `#panel` 通用槽；槽含 panel/index/isCollapsed/isExpanded/collapse/expand/resize；事件 layout/resize/collapse/expand/dragging/error |
| `YdSparkline` | data:number[]、type=line/area/bar、width/height、strokeWidth、color、label | 自动忽略非有限数值；适合嵌入指标卡，复杂数据图继续使用已有图形能力 |
| `YdTimeline<T>` | T 至少含 datetime，可含 id/title/description；data、merge、locale | 默认槽 `{ item, index }`，按传入顺序渲染 |

```vue
<script setup lang="ts">
import { YdCarousel, YdDigitalCard, YdSparkline } from '@yudream/components'

const cards = [
  { id: 'visits', title: '访问量', value: 1286, series: [12, 18, 15, 24, 30] },
  { id: 'tasks', title: '完成任务', value: 86, series: [3, 5, 4, 9, 12] },
]
</script>

<template>
  <YdCarousel :items="cards" :autoplay="false">
    <template #item="{ item, active }">
      <YdDigitalCard :title="item.title" :digital="item.value">
        <YdSparkline :data="item.series" :label="item.title" />
        <span>{{ active ? '当前项目' : '' }}</span>
      </YdDigitalCard>
    </template>
  </YdCarousel>
</template>
```

### 异步进度与取消

`YdMultiStepLoader` 的 `steps` 是 `YdLoaderStep[]`。每个动作收到相同的 `results` 和 `signal`，按顺序执行；有 `name` 的动作结果写入对应键。`stateChange(index)`、`error(reason, index)`、`complete(results)` 帮助业务展示进度与处理结果。通过 `v-model:loading` 控制模态框；任务成功后保留完成视图，点击“完成”再关闭。

```vue
<script setup lang="ts">
import type { YdLoaderStep } from '@yudream/components'
import { YdButton, YdMultiStepLoader } from '@yudream/components'
import { ref } from 'vue'

const loading = ref(false)
const loader = ref<InstanceType<typeof YdMultiStepLoader>>()
const steps: YdLoaderStep[] = [
  {
    text: '读取当前配置',
    afterText: '配置已读取',
    name: 'settings',
    action: async ({ signal }) => {
      const response = await fetch('/api/settings/public', { signal })
      if (!response.ok) { throw new Error('读取配置失败') }
      return response.json()
    },
  },
  {
    text: '准备预览',
    name: 'preview',
    action: ({ results, signal }) => {
      signal.throwIfAborted()
      return { settings: results.settings }
    },
  },
]
function cancel() {
  loader.value?.cancel()
  loading.value = false
}
</script>

<template>
  <YdButton @click="loading = true">开始</YdButton>
  <YdButton variant="outline" :disabled="!loading" @click="cancel">取消</YdButton>
  <YdMultiStepLoader ref="loader" v-model:loading="loading" :steps="steps" />
</template>
```

ref 的 `cancel()` 会发出取消信号并阻止后续步骤，但自身不关闭模态框；调用方可同时把 loading 设为 false。`retry()` 从首步重新执行，已完成业务动作需要自行保证可重复执行。取消不等于撤销已发生的服务端写入；未响应 signal 的动作可能继续运行，但迟到结果不会被组件当作本次成功结果提交。`preventClose` 只在执行中阻止界面关闭。

普通加载指示使用 `YdLoading`；需要临时全屏指示时：

```ts
import { useYdLoading } from '@yudream/components'

const loading = useYdLoading({ type: 'wave', text: '正在处理' })
// 工作完成后调用；重复 close 安全，组件作用域结束也会清理。
loading.close()
```

type 可选 plane/chase/bounce/wave/pulse/flow/swing/circle/circle-fade/grid/fold/wander。服务端调用组合式入口返回空操作 close，不创建 DOM。

### 代码、链接、二维码与本机预设

| 入口 | 参数与输出 |
| --- | --- |
| `YdCode` | code、language、title、copyable 默认 true、lineNumbers；高亮器转义输入代码，组件不执行代码 |
| `YdCodePreview` | code、language 默认 xml、title；默认插槽直接承载已编写的 Vue 示例 |
| `YdLinkPreview` | url、title、description、image、target；默认槽是链接内容，`#preview` 可完全替换预览卡 |
| `YdQrcode` | value、size 默认180且限制64..2048、margin 默认4、errorCorrectionLevel 默认M、foreground/background、label；ready(dataUrl)、error(Error)；ref.download(filename?) / refresh() / dataUrl |
| `YdStorageBox<T>` | uniqueKey、boxId、data:T、title、limit 默认20；takeOut(data)、error(error)；默认槽可替换打开按钮 |

二维码在本机生成，不上传 value，也不调用第三方二维码服务。默认前景黑色、背景白色是扫码功能的对比保障，独立于品牌主色；调整颜色或留白后应检查实际扫码结果。

LinkPreview 的标题、描述与图片由调用方提供，不自动请求链接网页、解析远程元数据或调用截图服务。HTTP/HTTPS 及可解析为这两类协议的相对链接可导航，其他协议不产生 href。image 是普通图片 src：传入远程 URL 时浏览器仍会请求图片；需要本地预览时使用本地资产或 data URL。点击链接按 target 正常导航，默认新窗口。

StorageBox 的存储键是 `yd-storage:<编码后的 uniqueKey>:<编码后的 boxId>`。uniqueKey 使用当前账号字符串 ID，boxId 使用稳定业务场景名；切换账号或 boxId 会重新载入对应本机清单。data 必须是可 JSON 序列化对象，保存和取出均生成副本；DateValue、函数、循环引用等需在业务层先转换。它不提供服务端同步、跨浏览器共享或加密存储。

```vue
<script setup lang="ts">
import { YdLinkPreview, YdQrcode, YdStorageBox } from '@yudream/components'
import { ref } from 'vue'

const userId = '1900000000000000001'
const filters = ref({ keyword: '', status: 'enabled' })
</script>

<template>
  <YdStorageBox :unique-key="userId" box-id="user-search" :data="filters" @take-out="filters = $event" />
  <YdLinkPreview url="/platform/theme-center" title="主题中心" description="切换与配置当前主题" target="_self" />
  <YdQrcode value="https://example.com/project" label="项目地址二维码" />
</template>
```

### 数字、文本与装饰动效参数

| 入口 | 核心参数 / 内容 |
| --- | --- |
| `YdAnimatedCountTo` | value、Intl 的 format/locales、prefix/suffix；`YdAnimatedCountToGroup` 用默认槽组合数字 |
| `YdCountTo` | startVal/endVal、autoplay、duration(ms)、delay(ms)、decimals、separator、prefix/suffix；started/finished 与 ref.start/reset/stop |
| `YdAnimatedBeam` | containerRef/fromRef/toRef 是 DOM 元素，建议 shallowRef；curvature、reverse、duration(s)、delay(s)、pathWidth/pathColor/beamColor、端点偏移；ref.refresh |
| `YdBlurReveal` | duration(s)、delay(s)、blur(CSS 字符串)、yOffset(px)；默认槽 |
| `YdBorderBeam` | duration(ms)、delay(ms)、borderWidth(px)、colorFrom/colorTo；父容器需要 position:relative |
| `YdSpotlightCard` | gradientSize(px)、gradientColor、gradientOpacity；默认槽 |
| `YdGlowyCardWrapper` | color、size(px)、border(px)、radius(px)，后代 YdGlowyCard 用默认槽提供内容 |
| `YdFlipCard` | rotate=x/y；默认槽与 `#back` |
| `YdFlipWords` | words:string[]、duration(ms)；空数组显示空文本 |
| `YdGradientButton` | disabled/loading、colors:string[]、duration(ms)；默认槽 |
| `YdInteractiveButton` | text、disabled/loading；默认槽优先于 text |
| `YdMarquee` | vertical、reverse、duration(s)、gap(CSS 字符串)、pauseOnHover；默认槽 |
| `YdScrollingText` | text、duration(s)、reverse；默认槽优先于 text |
| `YdParticlesBg` | quantity、color、speed、size(px)；装饰层需放在有定位上下文的容器内 |
| `YdPatternBg` | variant=grid/dots/lines、size=sm/md/lg、mask=ellipse/circle/none、animate、direction、speed(ms)；默认槽承载内容 |
| `YdSparklesText` | text、sparklesCount、colors={first,second}；默认槽优先于 text |
| `YdTextHighlight` | from=left/right/top/bottom、duration(ms)、delay(ms)；默认槽 |
| `YdTextShiny` | duration(s)、delay(s)；默认槽 |
| `YdScratchOff` | width/height 为 px 数字或 CSS 字符串、brushSize(px)、threshold(1..100)、coverText/coverColor、disabled、revealLabel；progress/complete/reset/error；ref.reveal/reset |
| `YdTimeAgo` | date:string/number/Date、locale、showSecond、updateInterval(ms，默认30000) |

调用方可给装饰组件显式颜色；通用页面通常保留默认主题语义色，以便明暗和插件主题一致变化。避免在多个大型区域同时放置大量常驻装饰；动态组件在卸载时清理计时器、观察器与 RAF。ScratchOff 有键盘 Enter/Space 和直接揭晓入口，不依赖拖动才能访问结果。

## 实时预览与验证

从仓库根启动：

```powershell
pnpm --dir yudream-frontend --filter @fantastic-admin/component-showcase run dev -- --host 127.0.0.1
```

内置浏览器打开 [实时组件预览](http://127.0.0.1:5188/)。预览读取同一组 FA 默认主题和共享主色转换，切换外观、主色与明暗后可以检查组件及导航表现。预览的本机设置不会代替后台保存的账号主题配置。

组件与宿主检查：

```powershell
pnpm --dir yudream-frontend --filter @yudream/components run typecheck
pnpm --dir yudream-frontend --filter @fantastic-admin/core-arco-design-vue run test:typecheck
pnpm --dir yudream-frontend exec tsx --test packages/components/src/extended/calendar.test.ts packages/components/src/extended/loader-runner.test.ts
```

日期测试覆盖包含端点的范围上限、禁用 / 不可用项、边界粒度、模型规范化与时间回落；异步任务测试覆盖顺序结果、取消、异常与迟到结果。主题与交互仍需在浏览器中检查三预设、明暗、减少动效及 ADMIN 主题变量覆盖。
