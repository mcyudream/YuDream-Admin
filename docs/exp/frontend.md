# 前端经验：组件解析、样式级联与真实验收

> **适用范围**：宿主 Vue 前端、共享组件与插件远程前端。
> **目标**：记录静默降级、CSS 优先级、响应式布局和浏览器验收的已知陷阱。

## 1. Vue 组件与工具类

### 页面内容在但 YD 组件外壳消失

- **现象**：模板插槽内容还在，组件边框/触发器/props 不工作，控制台可能有 Failed to resolve component。
- **根因**：只有 Fa*/Yd* 全局注册，兄弟业务组件不会自动解析；漏 import 后 Vue 把标签当原生元素。
- **规避**：逐一显式 import 所有本地大写组件；组件规则见 `docs/dev/frontend.md`。
- **来源**：`unresolved-local-component-silent-degrade`。

### 插件工具类覆盖宿主弹窗宽度

- **现象**：插件页面的宿主 FaModal 在桌面被拉成全宽，像是宿主响应式类失效。
- **根因**：旧插件 CSS 未分层，插件 `.max-w-full` 等同名工具类优先级高于宿主工具类。
- **规避**：升级 SDK 至 1.6.0+ 并重建 `style.css`，所有插件工具类进入 `@layer yudream-plugin`；宿主层序 `yudream-base < yudream-plugin < unlayered host utilities`。
- **来源**：`plugin-remote-style-injection`；`docs/dev/plugin-system.md`。

### 宿主不显示插件原子类样式

- **现象**：插件 DOM 有 UnoCSS class，但看不到对应样式。
- **根因**：宿主构建不会扫描插件包。
- **规避**：插件构建产物自行包含所需工具类，不能依赖宿主运行时补扫。
- **来源**：`plugin-remote-style-injection`。

### 表格页分页停在“共 0 条”且没有第二页

- **现象**：某页分页首帧后再不更新（永远“共 0 条”、无页码），同页其它文本照常刷新，其它页同组件分页正常；控制台出现 `Unhandled error during execution of app errorHandler`。
- **根因**：`:ref="fn"` 的函数式 ref 在组件每次 patch 都被重调；函数内先读后写同一份响应式状态（如 `shallowReactive(Map)` 注册表），且读写发生在当前渲染 effect 内，effect 自我失效 → 同一次 flush 递归渲染 100+ 次 → 撞 Vue 递归更新上限被跳过，排在后面的组件（分页）更新成批丢失。
- **规避**：函数式 ref 里目标未变时立即 return，绝不在渲染期读写响应式状态；定位手段是包装全树实例的 `effect.run` 计数——被拖死的组件计数远超 1，正常链路各 1 次（见 `tmp/probe-update-counts.cjs`）。
- **来源**：`f19a2591`；验证脚本 `tmp/verify-pagination-fix.cjs`、`tmp/final-three-pages.cjs`。

### Markdown 预览的 mermaid 图不渲染或变成乱码方块

- **现象**：` ```mermaid ` 代码块在预览里始终是源码文本（DOM 只有 `<div class="md-editor-mermaid">`，没有 `data-processed`）；修好后首次渲图正常，重渲染后图表被替换成一块黑方块，DOM 里 svg 内出现被转义的 `&lt;marker&gt;`/`&lt;style&gt;` 文本。
- **根因**：两层。① YdMarkdownEditor 的 `:sanitize` 白名单对 `div/p` 只放行 `class`，而 md-editor 的 mermaid 渲染器按 `data-mermaid-revision`/`data-mermaid-block`/`data-closed` 筛选待渲染块——属性被消毒删掉后过滤条件永不满足，块被静默跳过。② md-editor 的 mermaid **缓存命中分支**会把 SVG 字符串内联进 markdown HTML，而逐标签白名单消毒只认识 svg/g/path，`marker/defs/style/text` 全被转义成文本，图表就烂成方块。
- **规避**：`markdown-render.ts` 里按值域放行这组 `data-*`（数字/枚举逐个校验，不放开任意属性）；同时在 `markdown-engine-config.ts` 用 `markdownItPlugins` 把 mermaid 插件的 `getCached` 置空，让图表只在运行期把 SVG 直接写进 DOM、永不经过字符串消毒——比放宽 SVG 白名单（要放 `foreignObject`/`style`）安全得多。验证看 `.md-editor-preview .md-editor-mermaid svg` 数量与 `<style>` 节点，别只看有没有 svg。
- **来源**：`yd-mermaid-preview-sanitize`；验证脚本 `tmp/wiki-mermaid-verify.mjs`（`previewSvg>0`、无 CDN mermaid 脚本）。

### 表格行尾操作菜单渲染不出来

- **现象**：`YdResponsiveEnhancedTable` 传了 `actions` 却看不到行尾「操作」列的下拉按钮；单元格是空的，`visible` 谓词手工求值明明是 true。
- **根因**：业务 `columns` 里自己声明了一列 `id: 'actions'`。`actions` 是表格内置操作列的保留 id（组件 push 时用它 + 用 `#cell-actions` 插槽渲染），撞名后业务列的插槽覆盖了内置下拉的渲染。
- **规避**：业务列 id 避开 `actions`（如 `grants`/`status`），插槽名跟着改成 `#cell-grants`；验证看 `.yd-enhanced-table__actions-trigger` 数量而不是只看 `actions` 传了没传。
- **来源**：`wiki-collaboration-panels`（成员与权限面板）。

### YdTablePicker 的模型是行键数组，不是行对象

- **现象**：选择器选完 `v-model` 里拿不到行对象字段（`selection[0].nickname` 为 undefined），提交后端报「主体 ID 不合法」。
- **根因**：`YdTablePicker` 的 `modelValue` 是 `string[]`（行键），只负责选中与回显标签；行对象只存在于 `fetcher` 的返回里。
- **规避**：模型声明成 `ref<string[]>([])`，行对象字段（昵称/名称）在 fetcher 里另存标签表供回显；提交时用键值。另：`YdCheckboxGroup` 的每个选项是 `div`（`role=group` 的子元素，点容器即切换，无内层 input/button），驱动验收脚本时要点选项容器本身。
- **来源**：`wiki-collaboration-panels`（成员与权限面板）。

### 树里的长标题把整行撑出容器

- **现象**：目录树/菜单树的标题不省略号、在面板右边缘被硬裁（半截字）；鼠标移上去时行尾的删除/操作按钮只剩半个白块露出在容器外。
- **根因**：Arco 的树按内容定宽——`.arco-tree` 的宽度等于最长节点内容，容器只负责裁剪。行内的 `width: 100%` 只在「已经过宽的节点」内部生效，标题的 `text-overflow: ellipsis` 与行尾按钮都落在可见区之外；YdTree 的样式只改了字号/颜色，没约束宽度。
- **规避**：在 YD 树样式里逐层约束 `.arco-tree`、`.arco-tree-node`、`.arco-tree-node-title`、`.arco-tree-node-title-text` 为 `width: 100%; min-width: 0`（title-text 另给 `flex: 1`）。验收看 `.yd-tree` 视口 `scrollWidth === clientWidth`，且没有节点的 `right` 超出树容器——只看标题是否带省略号会漏掉整棵树过宽的情况。
- **来源**：`yd-tree-width-constraint`。

## 2. 布局与交互

### 表格表头 sticky 失效，跟着内容一起滚走

- **现象**：表头已带 `position: sticky; top: 0`，但表格内滚动时表头仍随内容滚走；同页固定列的横向钉住也时灵时不灵。
- **根因**：`sticky` 只认最近的滚动盒。表格链路上存在多个 `overflow` 非 `visible` 的中间盒（表格根 `overflow-hidden`、内层 `table-container` 的 `overflow-auto`），纵向滚动实际发生在更外层的容器上，表头被解析到那个从不纵向滚动的中间盒，于是原地不动地随内容滚走；横向同理（当时横向恰好滚在 `table-container` 上才显得固定列"偶然有效"）。
- **规避**：横向与纵向滚动必须落在同一个滚动盒——给插槽链路逐层 `height: 100%`（表格壳内表格 `flex: 1`），让表格自身的 `table-container` 成为滚动盒；表头背景改不透明，否则行会从表头下方透出。自造外壳时中间不得出现 `overflow` 非 `visible` 的盒子。
- **来源**：`061b9df6`；验证脚本 `tmp/verify-sticky-header.cjs`（表内滚 320px 后表头 top 不变）。

### 首页插件卡片保存成功但刷新后消失

- **现象**：窄屏或非 lg 断点下保存后，刷新卡片被当成旧单列布局过滤。
- **根因**：`isLegacyAutoStackedLayout` 用 md/sm 判断历史单列；md/sm 默认竖排，不代表遗留状态。
- **规避**：只用 lg 判定旧布局；验收保存+刷新后 placements。
- **来源**：`dashboard-legacy-stack-lg-only`。

### 拖拽手柄只在按钮边缘生效

- **现象**：GridStack 只有点按钮边缘才能拖，点中间图标无反应。
- **根因**：事件命中图标后代，GridStack `_mouseDown` 把交互元素过滤为非拖拽。
- **规避**：拖拽手柄内图标根节点设 `pointer-events:none`；浏览器实测从图标中心拖并验证保存/刷新。
- **来源**：`gridstack-button-handle-icon`。

### Dialog 已关闭但仍挡点击

- **现象**：自动化认为 dialog 还在无障碍树中，或关闭后短暂拦截指针。
- **根因**：IAB/Chromium 后台标签页节流动画帧，Reka Presence 收不到 `animationend`，`data-state=closed`、`opacity=0` 节点暂留。
- **规避**：关闭验收不能只查 `getByRole(dialog).count()`；激活标签页出帧并等卸载，或根据截图/可见性确认真实状态。
- **来源**：`reka-presence-background-animation-throttle`。

### `pnpm lint:eslint` 全仓验证改写了无关文件

- **现象**：改了一条 ESLint 配置后，全仓 lint 大量修改无关源码或 pnpm catalog。
- **根因**：根脚本带 `--fix`，改 `eslint.config.js` 又会让 eslint cache 失效，导致全仓修复全部文件。
- **规避**：规则变更用 `npx eslint <paths> --no-cache` 定向验证；提交只暂存本工作项文件，禁止用全仓 `--fix` 充当只读检查。
- **来源**：`eslint-full-fix-mass-rewrite`。

### 固定高度工作台的长工具列表被裁切

- **现象**：侧栏内容超出后不可滚动或底部工具不可达。
- **根因**：Grid/Flex 子项默认 `min-height:auto`，高度约束无法传递。
- **规避**：根节点 `height:100%; min-height:0; overflow:hidden`，内部滚动区 `min-height:0; overflow:auto`。
- **来源**：`workflow-editor-scroll-container`。

### 双栏工作台用「视口高度减固定值」定高，底部翻页条被挤出可视区

- **现象**：插件管理页在 1280×800 到 1500×1180 的窗口下，两栏工作台比可视区高出 75–119px，页头/工具栏本身还要滚动才能看到底部的分页条；同一份 `calc(100dvh - 272px)` 在宽窗口“看起来没问题”，窄窗口工具栏按钮换行后就露馅。
- **根因**：减去的常量只按某一组页头 + 工具栏高度调过；工具栏行数随宽度变化（按钮换行、状态胶囊换行），`dvh` 减常量与元素真实位置脱钩。
- **规避**：挂载时实测工作台 `getBoundingClientRect().top`，用 `window.innerHeight - top - 34`（页面容器 p-4/m-4 各 16px）算可用高度写进 CSS 变量，并在 `resize` 时重算；窄屏单栏模式移除该变量。验收断言取工作台/分页条的 `bottom` 与 `innerHeight` 比较，以及 `documentElement.scrollHeight <= innerHeight`，别只看截图是否“看着居中”（见 `tmp/probe-workbench-fit.cjs`）。
- **来源**：`plugin-workbench-pane-fit`。

### 服务端分页的计数胶囊把数字拼成了字符串

- **现象**：筛选胶囊的计数与底部比例条渲染成一长串数字或宽度明显失真——`total`、各状态计数在响应里是字符串，`count + count` 变成拼接。
- **根因**：全局 Jackson `ToStringSerializer` 把 Long 序列化成字符串（`{"total":"12"}`），前端类型仍声明为 `number`；服务端分页后计数来自 `summary` 接口，不再是前端自己数的数字，问题才暴露。
- **规避**：分页/概览响应里的 `total`、`page`、`size` 与每个计数值一律 `Number()` 归一化后再进状态；行数据全量下发时前端自己计数不会碰到这个坑，改服务端分页时是必检项。
- **来源**：`plugin-directory-server-side-paging`。

### 浮动调试面板被侧栏/模态遮挡

- **现象**：面板在布局容器里被侧栏压住；或抽屉打开时遮罩阻断系统操作。
- **根因**：布局 stacking context 与 z-index 层级不匹配，FaDrawer 会锁滚动并盖遮罩。
- **规避**：宿主级面板 Teleport 到 body，z-index ≥2100；面板用非模态浮窗，不用 Drawer；内部模态 2200，嵌套 2300。
- **来源**：`host-zindex-scale-floating-overlay`。

### 嵌套的同类组件会被外层 `:deep` 规则连带改造

- **现象**：治理区顶部区域 Tab 的下划线样式套到了设置面板里的分区导航上——宽屏活动项变整行下划线（本该胶囊高亮），窄屏本该收起成下拉却仍显示整列导航。
- **根因**：外层页面按 `data-slot="tabs-list" / "tabs-trigger"` 改 Tab 外观，而这些槽位是所有 reka Tabs（YdTabs / YdSettingsLayout 内部）共用的；`:deep` 会命中全部后代，内容里再嵌一套 Tabs 就被一起改了。
- **规避**：页面级 Tab 样式限定到直接子级（`:deep(> [data-slot="tabs-list"]) [data-slot="tabs-trigger"]`）；发现某个槽位选择器“改了一处、坏了两处”时先查同款组件嵌套。
- **来源**：`managed-tab-scope-leak`。

### 灰底舞台里嵌白卡，被读成「背景里的大白块」

- **现象**：设置面板白卡四周留一圈灰边、下面空一条灰带，用户反馈背景里套了个方形白块。
- **根因**：白卡（`surface`）+ 四周留白是页面级表达，只在 `YdPageMain` 那种整块灰底页面里成立；放进自带底色的舞台后，卡片边界就显形成方块，且卡片高度由内容决定、填不满舞台。
- **规避**：二选一——整幅白底（舞台底色随分区切白 + 区域/面板 `flex: 1; min-height: 0` 铺满），或去掉 `surface` 与兄弟分区同底色；两种都要实测白底/灰底铺到舞台四边。
- **来源**：`settings-sheet-fullbleed`。

### 离开某几个页面后，点任何菜单都白屏且控制台无报错

- **现象**：点击插件发布/发布审核有概率白屏，之后插件管理、插件市场乃至系统页全部白屏，刷新才恢复；控制台零报错。DOM 特征是内容区只剩 `<!---->` 占位注释。
- **根因**：布局用 `<Transition mode="out-in">` 包路由组件。多根模板页面（如 `YdModal` 与布局并列，或模板根以注释开头）在 dev 下编译成 fragment 根；Vue 对 fragment 的卸载路径（`removeFragment`/逐子节点移除）不会触发 `afterLeave`，`isLeaving` 永久为 true，Transition 从此只渲染空占位。生产构建剥掉注释所以偏「概率」，dev 里离开 fragment 根页面必现。
- **规避**：路由级页面组件必须有单一元素根——多根内容包一层 `<div>`，模板注释一律放根元素内部而不是 `<template>` 顶层（参考 `plugin-market-source`、`plugin-publish`、`site-studio` 内的约束注释）。复现/验证：`router.push` 在页面间轮换后断言 `#app-content` 非空且不以 `<!---->` 开头；开发时见到 "Component inside <Transition> renders non-element root node" 警告即为同款前兆。
- **来源**：`route-transition-fragment-root-whiteout`。

## 3. 主题与登录

### 深色 SITE 主题下登录页文字不可读

- **现象**：无插件可读，启用 SITE 主题后登录页文字与背景对比度不足。
- **根因**：主题只覆盖站点容器变量，登录页另消费 `--color-*`/组件语义变量；或相对 URL 从宿主 CSS 解析。
- **规避**：同时验收无插件/启用 SITE 主题；在 `.login-page` 桥接 `--color-*` 与组件语义变量，回落浅色默认；相对资源 URL 在插件自己的 CSS 选择器中消费；匹配 `text-primary` 用完整类名，勿误伤 `text-primary-foreground`。
- **来源**：`site-theme-login-contrast`。

### 主题启动动画偶尔先显示宿主默认皮肤

- **现象**：首次公开页加载先显示默认 loading，稍后才切主题；已登录后台 `/` 不应套 SITE 主题。
- **根因**：主题 CSS 直到 Vue main.ts 请求 active 后才挂载，而 loading 在 Vue/router 之前注入。
- **规避**：公开路径 index.html 用 localStorage 缓存的 SITE CSS 先行注入/preload loading 图；theme-runtime 在 router ready 前按 URL 公共前缀启用 stylesheet；等 CSS 与图像就绪再 fade；后台 media=not all。主题资产只在插件 JAR，不放宿主 public。
- **来源**：`plugin-theme-splash`。

### 主题 image 配置清空后又恢复默认图

- **现象**：管理端清空图片并保存，重新打开仍显示 schema 默认图。
- **根因**：保存空值被当作“未设置”，读取又合并 schema default。
- **规避**：image 清空存空串，读取不得回落 schema default。
- **来源**：`plugin-theme-splash`。

## 4. 登录布局与浏览器表单

### 登录 Tab 切回账号密码后按钮无反应

- **现象**：切到第三方 Tab 再切回，浏览器自动填充值仍在输入框但登录无响应。
- **根因**：账号输入与「记住账号」通过 `v-if` 卸载，浏览器自动填充和 vee-validate field 注册失效。
- **规避**：账号字段常驻，用 `v-show`；Tab 切换不卸载输入。
- **来源**：`plugin-menu` 经验与 SPI 2.32.0 登录规范；`docs/dev/plugin-system.md`。

### 登录页默认 center 但几何位置偏移

- **现象**：配置为 center，却出现双栏/偏左/偏右布局，短视口内容被工具栏或版权挤压。
- **根因**：selector 值没有对应真实几何样式，center 与 right 共用了全屏双栏；登录/注册/找回/绑定容器实现不一致。
- **规避**：桌面 center 用限宽盒、自动外边距与内容高度；左右布局保留通栏方向；手机统一上下布局；短视口允许滚动并为工具栏/版权留空间。四类页面共用同一容器并实测三种对齐。
- **来源**：`login-center-layout-contract`。

### Chrome 自动填充背景覆盖输入组边框

- **现象**：登录输入框自动填充后上下边框消失；密码切换后模型值被清空。
- **根因**：原生输入高度与带边框外壳同高；切换 input type 时浏览器自动填充值尚未触发 input。
- **规避**：输入高度落在外壳内容区内，输入/自动填充同中性底色，外壳裁切圆角；切换类型前同步原生输入值；密码切换按钮始终有显示/隐藏名称并透传 disabled。
- **来源**：`login-autofill-input-group`。

## 5. 真实视觉验收

### 工作台卡片仅在默认布局看似正常

- **现象**：用户自定义宽高或小屏下卡片内容被裁切、按钮越界、滚动区域消失。
- **根因**：只验收默认卡片集合与桌面断点，忽略隐藏插件卡片、容器实际尺寸和手机宽度。
- **规避**：浏览器中加入全部当前可用卡片（含默认隐藏插件卡），逐卡检查默认/最小/宽矮/宽高尺寸及桌面/390/320px；同时核对正文 client/scroll 尺寸与关键操作边界，不能只隐藏滚动条而裁内容。
- **来源**：`dashboard-all-card-size-audit`。

### YdSelect 显示了第一项但模型为空

- **现象**：空值控件显示第一候选像已选；多选标签溢出或远程选择不可检索。
- **根因**：显示 fallback 与真实 model 脱节，或把本地过滤组件用于远程分页。
- **规避**：空值显示“没有选择”，不回落首项；multiple 全部标签在 trigger 内并可取消；`allowSearch` 仅本地过滤且保留选中；远程分页用 YdTablePicker。
- **来源**：`yd-select-selection-feedback`。

### 能力都实现了，界面上却没有任何入口开启它

- **现象**：用户报「评论不可用」。公开评论接口、作者端评论区、审核面板都在，服务端开关也生效，但空间设置里没有对应开关——开关默认关闭，整套能力在界面上恒不可达；作者端提示还把「空间没开」写成「评论接口不可用」，看不出该去哪里开。
- **根因**：只按代码路径验收（接口存在、组件存在、字段能存能读），没走「用户从哪个界面把它打开」这一层；能力开关与设置入口分属两个改动，缺一个就成了死能力。
- **规避**：带开关的能力必须在设置页有真实入口，依赖未满足时给直达动作（评论依赖公开访问 → 一键切到对应分区）；文案区分「空间未开启」与「接口不可用」并指向可操作位置；回归断言覆盖开关字段、依赖分支与提示指向。
- **来源**：`capability-without-enable-entry`。
