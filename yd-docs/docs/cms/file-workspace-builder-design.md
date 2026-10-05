# CMS 生成适配文件工作区 —— 设计方案（已调研，待实施）

> 结论先行：**公开渲染本来就是「静态 HTML + 浏览器 JS 注入 data-yb-*」架构**，存储层已是纯
> html/css/js 字符串。因此「纯 HTML/CSS/JS 文件工作区」方案与现有架构天然对齐；
> **数据注入维持 JS 方式**（现有 `renderDynamicHtml` + `cms-template-render.ts` + 
> `/api/public/cms/template-context`），不需要也不应该引入服务端模板渲染。

## 1. 现状关键事实（调研结论）

- 存储即静态字符串：首页 `HomePageLayout.settings.homeHtml/homeCss/homeJs`、页面
  `CmsPage.htmlContent/cssContent/jsContent`；发布=保存+状态翻转，无构建步骤。
- data-yb-* 注入全部发生在浏览器（`views/site/index.vue` 444-510 行）：`data-yb-for/repeat`、
  `data-yb-limit`（驱动 template-context 预取）、`data-yb-if`（受限表达式，无 eval）、
  `{{dotted.path}}` 变量、`data-yb-html/markdown`；CSS 作用域化、JS 走
  `__YU_CMS_READY__/__YU_CMS_REGISTER_CLEANUP__` 生命周期。
- v2 工具闭环骨架（`AguiWebSocketHandler` + `CmsCanvasClientBridge` + TOOL_CALL_REQUEST/
  TOOL_RESULT 帧 + 60s ticket + PING/PONG）与"工具在哪执行"无关，可整体复用。
- 校验资产全是纯函数：`CmsCanvasValidateAiTool`（括号/禁 script/JS 生命周期/交互标记）、
  `CmsCanvasStyleCoverage`（类覆盖）、`CmsChromeAiTool.validate`（chrome 结构）——可直接对
  工作区三文件内容调用。
- GrapesJS projectJson 仅页面画布还原用；html/css 始终是公开渲染唯一真源。

## 2. 目标架构

```
builder agent（Harness，agentCode "cms-builder"，workflow profile 同款文件工具）
  工作区（IsolationScope.USER，Mongo）：
    cms/<targetKey>/index.html  styles.css  scripts.js
      · targetKey = home 或 page-<slug 或 id>
  模型用 read_file/write_file/edit_file/list_files 直接编辑三文件
  写入遵循现有全部约束：data-yb-* 指令、{{变量}}、yb-ai- 前缀 CSS、JS 生命周期约定
发布：
  服务端读回三文件 → CmsCanvasValidateAiTool 校验（含 chrome 结构/类覆盖）
  → 首页 CmsAppService.saveHomeLayout(settings.homeHtml/homeCss/homeJs)
    页面 savePage(html/css/js) + publish —— 存储与公开渲染零改动
```

## 3. 数据注入（用户问点）：维持 JS，不换服务端

- **公开端**：不变——`/site` SPA 运行时注入（现有实现），SEO 由 robots/预渲染另行考虑
  （与本方案解耦）。
- **构建器预览**：工作区文件通过新的只读端点（如
  `GET /api/platform/ai/cms/workspace/{targetKey}/index.html`）供 iframe/预览面板加载，
  页面内引入现成的注入运行时（抽取 `cms-template-render.ts` + renderDynamicHtml 为
  可复用 ESM，或注入同款脚本 + `window.__YU_CMS_CONTEXT__` 预置数据），画布/预览里
  data-yb-* 即时展开。
- **给模型的数据上下文**：现有 `cmsVariableContextJson`（前端组）继续随请求传入提示词；
  后续可将 renderContext 组装下沉服务端（`CmsTemplateContextAppService` 已有数据源），
  供提示词与校验共享——非必需，第二阶段做。

## 4. 实施分解（建议顺序）

1. **运行时**：HarnessAgentRuntime 增加 `cms-builder` 专用 profile（等同 workflow：文件工具
   开、shell 关）；新增按 targetKey 的工作区会话寻址（sessionId = `cms-<targetKey>`，
   userId = 编辑者）。
2. **后端工作区 IO 端点**：`GET /api/platform/ai/cms/workspace/{targetKey}`（列文件+读三文件，
   编辑器打开时初始化：目标已有 homeHtml/htmlContent 则播种为初始文件）、
   `POST .../publish`（服务端读回+校验+走现有保存/发布路径，权限 platform:ai:generate +
   cms publish 对应权限）。
3. **builder 工具与提示词**：沿用 Harness 内置文件工具（无需自定义工具 bean）；提示词改写自
   v2 `agentSystemPrompt` 的先读后写/小步修改/yb-ai-/data-yb-* 段落，增加"三文件职责"
   （index.html 仅结构+指令、styles.css 内容样式、scripts.js 生命周期约定）。
4. **校验闸门**：publish 前服务端强制跑 `CmsCanvasValidateAiTool` 同款校验 + 
   `ensureCanvasValidationPassed` 语义（校验不过拒绝发布）。
5. **前端**：编辑器新增「文件工作区模式」：预览 iframe（经注入运行时渲染）+ 对话面板
   （沿用 useYdChatStream/YdAgentChatPanel，ai 工具执行不再走画布补丁）；GrapesJS 画布
   与 v1/v2 canvas 工具保留为旧模式，不做破坏性下线。
6. **退役路径**（观察期后）：`CmsCanvasAiTool`/`CmsCanvasAtomicAiTools`/前端 `applyAiTool`
   v1 盲应用、projectJson 写入（首页已置空）。

## 5. 风险与兼容

- 首页固定壳：工作区 index.html 只写内容区（发布时嵌入现有 chromeFrame），公开端 SiteChrome
  自会包壳——避免 data-yb-chrome 拆分校验负担；若 agent 需调 chrome 样式，保留
  `cms.chrome.style` 工具（服务端工具与文件工具可并存）。
- `blankHomeHtml` 预设保护：发布回读后 settings.homeHtml 非空即受保护，语义不变。
- 首页 CSS 历史混入 chrome 规则：文件方案 styles.css 只含内容样式，chrome 兼容层
  （extractChromeCss）在读取播种时剥离。
- 并发编辑：工作区文件按 (user, targetKey) 隔离 + 发布时整体覆盖，天然规避多人画布冲突；
  如需协作再谈。
