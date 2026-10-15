# AffectUI 内容库对照与预览验收

> 适用范围：YuDream Admin `component-showcase` 的 AffectUI 参考演示。
> 目标：追踪参考目录的 103 个条目在本地 YD 风格预览中的落点与验证状态；目录覆盖不等于源实现、视觉或全部边界行为逐像素等价。

## 1. 总览

| 内容域 | 参考数 | 本地预览入口 | 当前状态 |
| --- | ---: | --- | --- |
| Components | 49 | `?group=Components` | 17 + 14 + 18 个独立本地交互演示，另保留原有 29 个 YD 分类页 |
| Screens | 12 | `?category=scene-gallery` | 12 个独立本地场景，默认筛选整页 |
| Blocks | 11 | `?category=blocks-gallery` | 11 个独立本地区块，默认筛选区块 |
| Illustrations | 23 | `?category=illustrations-gallery` | 23 个命名的原创 CSS 交互磁贴 |
| Motion | 8 | `?category=motion-gallery` | 8 个命名的可重播/可调节动效 |

全部示例使用本地数据，不连接 AffectUI、插件后端或真实 Agent 服务。视觉采用 YD 设计令牌与公开组件入口；没有复制参考站源码或品牌图稿。

## 2. Components（49）

### Chat（10）与 Agents（7）

入口：`?category=affect-chat-agents`。每项在独立 `DemoCard` 中展示。

| Chat | Agents |
| --- | --- |
| AI Answer、Chat Composer、Thinking State、Writing Assistant、Thinking Loader | Agent Inbox、Context Window、Memory Panel、Tool Call Card |
| Cited Answer、Message Actions、Model Picker、Comments Panel、Comment Thread | Approval Card、Agent Plan、Inline Diff |

已实现本地回答回放、发送与停止、推理阶段、来源展开、操作反馈、模型切换、评论回复/解决、任务筛选、记忆编辑/暂停/撤销、工具重试、审批决策、计划重排/重试、差异接受/拒绝/撤销。审批仅改变演示状态，不代表真实宿主授权。

### Cards（5）、Data display（7）、Buttons（2）

入口：`?category=affect-data-cards`。

| 分类 | 条目 |
| --- | --- |
| Cards | Cover Flow、Project Card、Tips Panel、Smart Recommendation、Email Draft |
| Data display | Usage Meter、Compact Breakdown、Experiment Card、KPI Chart、Breakdown Chart、Data Table、Change Summary |
| Buttons | Button、Reaction Bar |

封面流支持拖动/方向键与按钮；项目卡任务推进；提示、推荐和草稿提供撤销；数据类支持切换周期、悬停/键盘聚焦、编辑、复制粘贴、排序及撤销。`Data Table` 是可横向滚动的交互演示，不替代业务管理列表的 `YdResponsiveTable` 移动卡片模式。

### Navigation（4）、Inputs（9）、Feedback（5）

入口：`?category=affect-controls`。

| 分类 | 条目 |
| --- | --- |
| Navigation | Command Menu、Segmented Control、Integration Picker、Profile Menu |
| Inputs | Combobox、Date Range Picker、Dropzone、OTP Input、Stepper Form、Integration Settings、Prompt Library、Filter Menu、Billing Schedule |
| Feedback | Toaster、Progress Tracker、Setup Guide、Decision Inbox、Notification Stack |

提供命令搜索/键盘操作、集成筛选、文件本地校验与失败重试、OTP 输入/粘贴、逐步表单校验、提示词变量组装、账期计算、异步提示及决定/通知状态流转。上传和连接测试均为本地模拟。

## 3. Screens（12）与 Blocks（11）

入口为 `?category=scene-gallery` 与 `?category=blocks-gallery`，两者使用同一场景库但默认过滤各自内容域。

| Screens（12） | 本地演示 |
| --- | --- |
| App Shell | 应用工作台 |
| Assistant Home | 助手首页与对话（与 Agent Chat 共享部分基础元件） |
| Folders | 文件空间 |
| Folder | 文件夹详情：面包屑、子文件夹、文件、搜索与双空态 |
| Integrations | 集成中心 |
| Agent Chat | Agent 对话台 |
| CRM | CRM 客户表格 |
| Analytics | 数据分析看板 |
| Agent Run | Agent 运行与审批 |
| Settings & Billing | 设置与账单 |
| Onboarding | 新手引导（当前页仍附带一组评估任务） |
| Evals | 独立模型评估台 |

| Blocks（11） | 本地演示 |
| --- | --- |
| Agent Gallery | Agent 画廊：分类、搜索、启用与运行反馈 |
| Pricing Plans | 定价切换 |
| Invoice & Credit Note | 发票预览 + 贷项通知单 |
| Metrics Dashboard | 指标仪表盘 |
| KPI Board | 团队 KPI 看板 |
| App Directory | 应用目录 |
| Client Context | 客户上下文 |
| Task Board | 任务看板 |
| AI Workspace | AI 工作台：项目侧栏、项目隔离会话与本地聊天 |
| Logo Marquee | 客户 Logo 墙 |
| Testimonial Wall | 用户评价墙 |

**差异**：各场景均有独立演示和本地状态，但布局、文案、视觉资源与部分细节并非参考站的逐像素或逐行为复制。演示的本地模拟不能替代真实数据/宿主 Harness 集成。

## 4. Illustrations（23）与 Motion（8）

入口：`?category=illustrations-gallery`、`?category=motion-gallery`。插画是按参考条目特征设计的原创 CSS/组件视觉场景，不是参考图稿的像素复刻。

- 插画 23 项：Agent Team、DACH Region、Empty Calendar、Enrichment Orbit、Frost Cloud、Ghosted Chat、Glass Folder、Glow Cloud、Glow Keyboard、Integration Beam、Integration Flow、Integration Grid、Layer Stack、Lead Sourcing、Logo Cluster、Model Wheel、Outreach Branches、Project Flow、Side Folder、Tab Overload、Team Invite、Voice Orb、World Map Continental。
- 动效 8 项：Blur Crossfade、Gradient Floor、Gradient Text、Grid Background、Number Ticker、Shimmer Loader、Text Reveal、Tilt Card。
- 插画支持按卡片/产品/地图分组，磁贴逐项有悬停、点击或状态切换；动效可重播、调节或暂停。减少动态效果偏好柔化装饰运动，保留功能反馈。

## 5. 验证与截图

已通过：

- `pnpm --dir yudream-frontend --filter @fantastic-admin/component-showcase run test:typecheck`。
- `pnpm --dir yudream-frontend --filter @fantastic-admin/component-showcase run build`。
- `pnpm --dir yudream-frontend --filter @yudream/components run typecheck`。
- `pnpm --dir yudream-frontend --filter @fantastic-admin/core-arco-design-vue run build`（宿主前端目标 app）。
- 本次展示文件定向 `eslint --no-cache`（App.vue、DemoCard、SceneGallery、VisualGallery、三个 Affect 专题页）。

浏览器验收（`http://localhost:5190/`）：

- 五类入口与计数：组件 32 个分类页（含三个专题页 17/14/18）、整页场景 12、功能区块 11、插画 23、动效 8。
- 交互抽验：场景筛选与分组切换、组件专题内分组切换（导航/输入/反馈）、任务看板推进、Agent 画廊启用/运行、定价周期切换、发票⇄贷项通知单切换、文件夹详情面包屑进入子目录。
- 窄屏 390px：目录按钮可展开/收起，选择条目后自动关闭，正文无遮挡。
- 暗色：通过「主题定制」面板切换暗色后 `documentElement` 进入 dark，页面与抽屉均按暗色渲染并已截图。

未通过 / 阻塞（均非本工作项文件）：

- `audit:ui`：并行未提交的 `apps/component-showcase/src/fa-fix/main.ts` 直接导入 Arco CSS，属基线外新增违规。
- `check:ui-docs`：插件仓 `yd-ui-guidelines.md` 镜像落后，需按 `docs/dev/repo-sync.md` 决定同步时机。
- 两个门禁未从脏工作区重建基线，也未新增例外。

截图从运行页面采集，覆盖 Screens、Blocks、Illustrations、Motion、三个 Components 专题、暗色主题与 390px 窄屏；随交付消息附索引。
