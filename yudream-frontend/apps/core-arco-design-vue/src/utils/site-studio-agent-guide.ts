import { STUDIO_CONTENT_EFFECTS, STUDIO_CONTENT_FIELD_TYPES, STUDIO_PARTICLE_PRESETS, STUDIO_STYLE_STATES } from '../types/site-studio'
import { STUDIO_DATA_SOURCES } from './site-studio-sources'

/**
 * 设计助手按需指南（技能式渐进披露）：系统提示只给工作循环与硬规则，领域细则由模型调 cms.studio.guide 按主题加载。
 * 文本与运行时/校验器实现同源维护：改 data-yd-* 语义、命令或内容模型时同步这里。
 */

const sources = STUDIO_DATA_SOURCES.map(item => `- ${item.value}：${item.label}；字段 ${item.fields.join(', ')}`).join('\n')

export const STUDIO_AGENT_GUIDE: Record<string, { title: string, body: string }> = {
  commands: {
    title: '修改命令',
    body: `apply 的 command 为单个命令或 {type:"batch",commands:[...]}（禁止嵌套，1–200 条，一轮一次原子应用、一次撤销）。
- set-text{nodeId,text}：仅限无子节点的节点
- update-node{nodeId,patch:{type?,tag?,text?,attributes?,styles?,responsive?,children?}}：attributes/styles 为整体替换，先 inspect 拿到原值再改
- set-style{nodeId,styles:{属性:值|null},breakpoint?:desktop|tablet|mobile}：增量合并，null 删除
- insert{target:{pageId}|{region:"header"|"footer"}|{parentId},node,index?}；remove{nodeId}；move{nodeId,target,index?}；duplicate{nodeId}
- set-class{name,styles?,responsive?:{tablet?,mobile?},states?:{${STUDIO_STYLE_STATES.join('|')}},remove?}：增改/删除单个主题样式类（优先于 set-theme）
- set-content{values:{字段键:值},remove?:[字段键]}：只改站点内容值，与「站点内容」页同一份数据
- set-theme{tokens?,header?,footer?,classes?,fonts?,keyframes?}：整体替换对应部分，慎用
- set-features{features}：整体替换站点功能（导航、数据源、公开页适配、内容模型），慎用；改内容值用 set-content
- add-page{page}；update-page{pageId,patch:{name?,path?,root?,settings?}}；remove-page{pageId}；set-assets{assets}
Node={id,type,tag,text?,attributes,styles,responsive?,children}；新节点 id 用新的 UUID 且全文档唯一；不允许 script/style/iframe/form 等标签与 on* 属性。`,
  },
  styles: {
    title: '样式与主题样式类',
    body: `优先复用主题样式类（theme 查看已有类与使用次数），节点 class 只写类名；同类元素多处出现时新建类（set-class），不要逐个节点写内联样式。
- 列表条目（data-yd-item 模板被克隆）只能用类控制样式：克隆不保留节点级样式。
- 响应式：类与节点都可写 responsive.tablet（≤992px）/ responsive.mobile（≤768px）；set-style 的 breakpoint 写入对应断点。
- 状态：类的 states 支持 ${STUDIO_STYLE_STATES.join('、')}；current = aria-current="page"（导航当前页），parent-hover/parent-expanded 用于二级菜单展开。
- 主题变量 tokens 以 var(--名称) 引用；图片背景变量形如 url("...")。
- 字体须引用 fonts 里的字体族；动画名须是 keyframes 中已定义的名称。`,
  },
  bindings: {
    title: '数据绑定（data-yd-*）',
    body: `页面不含脚本，行为由平台运行时按属性实现。
- 列表：容器 data-yd-source="数据源" + data-yd-limit；容器内第一个 data-yd-item 为条目模板，其余同标记项是示例；字段经 data-yd-text / -href / -src / -title 绑定（$index 为序号），data-yd-link="/路径?id={id}" 由字段拼链接；条目内 data-yd-show / data-yd-hide="字段" 按真假显隐；data-yd-each="children" 为嵌套列表。
- 空状态：容器内放 data-yd-empty 元素，无数据时显示；公开站不显示示例条目（data-yd-sample 仅草稿预览可见，data-yd-live 仅真实数据时可见，容器外用 data-yd-for="数据源" 关联）。
- 数据源：
${sources}
- 站点内容列表：content:{列表字段键}；插件适配器：adapter:{id}（theme 的 features.dataSources）。
- 站点信息：data-yd-site="name|description|logo|copyright|year|date|time|pageTitle"（logo 用于 img）。
- 账号：data-yd-auth="guest|user" 按登录显隐；data-yd-account="name|avatar|initial"。
- 计数：data-yd-count="数据源"。动作按钮：data-yd-action="toggle-music|toggle-sound|copy"（copy 复制自身 title）。
- 菜单：data-yd-menu 容器内 data-yd-menu-trigger 切换 aria-expanded，二级菜单 data-yd-submenu。
- 宿主页面承载：data-yd-outlet 放插件/宿主页面的插槽；data-yd-host-hide 在承载时隐藏；链接指向宿主路由（/wiki、/timeline）时加 data-yd-host-link。`,
  },
  content: {
    title: '站点内容模型',
    body: `features.content = {sections:[{code,title,description?,fields:[...]}], values:{}}；字段类型 ${STUDIO_CONTENT_FIELD_TYPES.join('/')}，list 用 itemFields 声明条目字段（不嵌套）。
- 运营在后台「站点内容」页填写 values；用户说“改横幅/换图/加一张卡片/改介绍”时，先 theme(section=content) 看是否已有字段，有就用 set-content 改值，不要改页面结构。
- 页面绑定：data-yd-content="键" 写文本（img/video/audio 写 src，video 字段绑定到 <video controls>）；data-yd-content-href 写链接；data-yd-content-title 写 title；data-yd-content-bg 设背景图；data-yd-content-show="开关键" 为 false 时隐藏；列表字段作为 data-yd-source="content:键"。
- 新增可编辑内容：set-features 往内容模型加字段（保留原有 sections 与 values），再在节点上绑定；未填写时用 defaultValue，再缺省保留作者内容。
- 值必须符合字段类型：image/link/audio/video 为站内路径或 https 地址；color 为 #rrggbb；select 必须是选项值。`,
  },
  effects: {
    title: '站点功能（粒子、音乐、音效、进入提示）',
    body: `平台实现的受控效果由内容字段声明 effect 驱动，每种效果最多一个字段：${STUDIO_CONTENT_EFFECTS.join('、')}。
- particles（particles 字段，预设 ${STUDIO_PARTICLE_PRESETS.join('/')}）；music（list，条目 src:audio、title:text）；musicVolume/effectVolume（number 0–100）；effectsDefaultOn（switch）；clickSound/buttonSound/toastSound（audio）。
- pageEffects（list，条目 path 必填，可选 particles、toastTitle、toastBody、toastIcon）：path 写文档页面路径（/servers）或承载的宿主路由（/timeline）。
- 访客开关：按钮加 data-yd-action="toggle-music" / "toggle-sound"，开启状态写 aria-pressed，用样式类 pressed 状态高亮。
- 主题没声明的效果不会出现；不要用脚本或动画模拟这些功能。`,
  },
  pages: {
    title: '页面、导航与外壳页',
    body: `- 页面路径以 / 开头，可含 :参数（动态页，参数传给数据绑定）；公开地址为 /site + 路径。
- settings.requires：["plugin:插件编码" | "capability:wiki" | "capability:plugin-market"]，未满足时页面不注册、导航隐藏。
- settings.hostRoutes + shell:true：外壳页，只为插件/宿主页面（如 /timeline、/market）提供主题外观，自身地址不注册；业务数据页由插件提供，不要在主题里复制一份数据页。
- 导航：features.navigation.items（最多两级）；页面中用 data-yd-source="site:navigation" 的列表渲染。改导航用 set-features 保留其他字段。`,
  },
  checklist: {
    title: '完成前检查',
    body: `1. 修改集中在一个 batch，复用主题样式类，列表条目样式写在类上。
2. apply 后调用 validate（文档结构）与 check（渲染检查：手机/桌面横向溢出、图片失效、低对比度、过小点击区、文本溢出）。
3. check 有问题时修正一次（新一轮 apply 需用户再次确认）或在总结中如实说明。
4. 总结列出改了哪些元素/样式类/内容字段，提醒用户保存草稿与发布由用户手动完成。`,
  },
}

export function studioAgentGuide(topic?: string) {
  if (!topic || !(topic in STUDIO_AGENT_GUIDE)) {
    return { topics: Object.entries(STUDIO_AGENT_GUIDE).map(([key, item]) => ({ topic: key, title: item.title })), hint: '传 topic 读取对应指南' }
  }
  return { topic, ...STUDIO_AGENT_GUIDE[topic]! }
}
