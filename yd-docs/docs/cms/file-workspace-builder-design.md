# CMS 静态站点工作区 —— 设计定稿 v2（多文件 + lib.js + 基础模板）

> 愿景（用户定调）：CMS 创作面不再是单文件 htmlContent/cssContent（不可塑），而是**类似反向代理
> 托管的纯 HTML 静态环境**：多 HTML 页面、CSS、图片、视频等资产文件；通过**宿主提供的 lib.js**
> 与后端数据互通；并提供建站基础模板（导航栏、页脚等）。

## 1. 现状可复用事实（调研确认）

- 公开渲染已是「静态 HTML + 浏览器 JS 注入 data-yb-*」：`renderDynamicHtml` +
  `cms-template-render.ts` + `GET /api/public/cms/template-context`（无服务端渲染器）。
- Harness 远程工作区：`RemoteFilesystemSpec.addSharedPrefix("cms/")` 把 `cms/**` 路由进
  KV（MongoBaseStore），USER 隔离命名空间 = `agents/cms-builder/users/<userId>` + 路由段，
  文件键 = 前缀下相对路径（`base + ["cms"]` 段，键 `home/index.html` 等）。
- 校验资产纯函数可复用：括号/禁 script/JS 生命周期/类覆盖/chrome 结构。

## 2. 工作区布局（静态站点文件树）

```
<workspace:USER 隔离，cms-builder agent>
  cms/
    pages/index.html                 ← / （首页）
    pages/about/index.html           ← /about（多页面，目录式路由）
    pages/docs/start.html            ← /docs/start
    assets/css/site.css  assets/js/site.js
    assets/img/logo.png  assets/video/intro.mp4   ← 二进制资产原样存储
  发布快照（published/ 命名空间，同构文件树） ← 公开端只读发布区，草稿不外泄
```

- **路由（反向代理语义）**：公开 `GET /site/{path}` → 发布区 `pages/{path}`（缺省 index.html、
  `.html` 后缀可选）；`GET /site/assets/**` → 发布区 assets 原样返回（Content-Type 按扩展名）。
- 与现有 CmsPage 体系关系：**工作区是创作真源**；发布时为每个页面同步一条 CmsPage
  （slug=路径，保留 SEO/分类/发布状态元数据），存量画布编辑器与 v1/v2 工具进入维护期。

## 3. lib.js —— 数据互通的唯一通道

宿主端点 `GET /api/public/cms/lib.js`（长缓存 + 版本参数），静态页一行接入：

```html
<script src="/api/public/cms/lib.js"></script>
```

职责（实现 = 现有 TS 渲染逻辑的独立 JS 化，语义一致）：
1. 预扫描 `data-yb-limit` → 拉取 `template-context`（含页面本地变量 site/system/auth）；
2. 渲染 `data-yb-for/repeat`、`data-yb-if`、`data-yb-html/markdown`、`{{dotted.path}}`；
3. 暴露 `window.YuCMS = { ready(cb), context(), refresh() }` 与
   `__YU_CMS_REGISTER_CLEANUP__` 生命周期（与现约定兼容）；
4. 资产/链接相对路径不改写（静态服务已按同基路径提供）。

构建器预览（管理端 iframe）加载同一 lib.js + 带 `platform` 权限的 context 端点 →
所见即所得，无需服务端渲染。

## 4. 基础模板（导航栏等）

- 内置模板进 **CmsBlock 区块库种子**（navbar/footer/hero/pricing/contact…，含
  data-yb-* 与 yb-ai- 约定的标准实现），agent 经现有区块读取工具/提示词取用；
- agent 建新页时提示词引导「导航栏/页脚用区块库模板 + `cms.chrome.style` 调样式」；
- 插件主题可经既有 homePreset/pages[] 机制提供整套页面模板（工作区播种时导入）。

## 5. 端点清单（新增）

| 端点 | 说明 |
|---|---|
| `GET  /api/platform/ai/cms/workspace/{path}` | 读工作区文件（文本回字符串、二进制回原样 + Content-Type） |
| `PUT  /api/platform/ai/cms/workspace/{path}` | 写文件（文本/二进制 multipart） |
| `GET  /api/platform/ai/cms/workspace` | 列文件树（size/mtime） |
| `POST /api/platform/ai/cms/workspace/publish` | 工作区 → 发布快照 + 同步 CmsPage（发布前跑校验闸门） |
| `GET  /api/public/cms/lib.js` | 框架运行时库 |
| `GET  /site/**` | 公开静态服务（读发布区，HTML 交 lib.js 注入） |
| `POST /api/platform/ai/cms/workspace/chat/stream` | builder agent SSE（Harness 文件工具直执行，无需浏览器桥） |

权限：工作区 IO/发布 = `platform:ai:generate` + cms publish 语义；公开只读 = 匿名。

## 6. 实施顺序

1. 运行时 profile：`cms-builder`（文件工具开、shell 关、addSharedPrefix("cms/")）
2. `CmsWorkspaceService`（infra）：命名空间精确 IO（文本/二进制）、列树、播种、发布快照
3. IO + lib.js + /site 公开端点
4. builder agent SSE 链（提示词：多页面/资产引用/lib.js 接入/区块模板）
5. 发布闸门：校验 + CmsPage 同步
6. 前端文件管理面板（文件树 + 编辑/上传 + 预览 iframe + 对话）
7. 存量画布工具退役（观察期）

## 7. 兼容与风险

- 存量 homeHtml/页面：播种即初始文件树，保存路径保留（旧编辑器可继续用，双轨观察）。
- 二进制资产存 Mongo（BinData ≤ 单文件上限），超大媒体建议外链对象存储（后续接 OSS 端点）。
- 发布快照隔离草稿；未发布 = 公开 404。
