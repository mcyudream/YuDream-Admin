# YuDream Admin 移动端 UI 设计稿（Pixso）

设计基准：宿主 FaTheme0（`yudream-frontend/packages/themes/index.ts`，shadcn neutral
中性单色系，OKLCH 已换算为 sRGB）。画板 360×800，Android 优先。

## 当前文件内容（在 Pixso 中）

- 页面「01 宿主 App」：封面 / 启动 / 欢迎 / 登录 / 首页 / 应用 / 我的 / 域管理 / 添加域 / 插件容器（论坛示例）
- 页面「02 插件页面」（25 屏，逐屏对照插件真实端点设计，依据 [PLAN.md](PLAN.md)）：
  - forum 帖子列表/详情
  - mc-wiki 物品图鉴 / 物品详情（含 3×3 合成配方）
  - yudream-skin 我的角色 / 衣柜与材质
  - mcpanel 运维总览 / 实例运维（电源+控制台+日志）/ 备份与任务
  - minecraft-server 服务器列表 / 详情（在线玩家+TPS 历史）
  - wallet 钱包 / 充值（支付宝订单）
  - shop 积分商城广场 / 商品详情 / 我的订单（核销码）
  - questionbank 练习首页 / 随机作答（答题卡）
  - minecraft-activity-proof 活动广场 / 活动详情（报名+答题+证明）
  - project-progress 我的任务（打卡/验收）/ 验收审批
  - edu-verify 学历认证审批
  - mc-news 新闻列表 / 详情
- 页面「03 深色模式」：首页 Dark / 我的 Dark
- 页面「04 设计规范」：Token 色板与字阶 / 组件样例
- 页面「05 主题色」：框架内置预设色（主题中心 colorPresets）各出登录 + 首页两屏，
  蓝 #2563EB / 绿 #16A34A / 红 #DC2626 / 紫 #9333EA / 橙 #EA580C（近黑 #18181B 即
  FaTheme0 默认，不重复出图）；工具链经 `themed()` 在 FaTheme0 亮色 token 上替换
  primary/ring 生成，屏幕名 `theme<色><屏>`。
- 变量集「FaTheme0」：17 个语义色变量，Light / Dark 双模式（--primary、--border、--g-page 等）

## 重建 / 改稿

工具链经 Pixso MCP（本地 127.0.0.1:3667）直驱桌面端，逐屏生成自动布局图层：

```bash
cd yudream-frontend/apps/mobile/design/tools
node pixso.mjs dump                 # 列 MCP 工具（写 .pixso-out/tools.json）
node screens.mjs setup              # 初始化 4 个页面
node screens.mjs home               # 重建单屏（构建 + 截图到 .pixso-out/）
node screens.mjs all                # 重建全部 16 屏
```

改样式只动 `lib.mjs`（FaTheme0 token、box/txt/icon helper、lucide 图标表），
改版式动 `screens.mjs`（每屏一个 build() 函数体）。截图逐屏目检后再提交。

## 已知坑（重要）

- Pixso eval 解析器不接受**裸顶层 await 语句**（`await fn();` 报 expecting ';'），
  写成声明形式 `const x = await fn();`。
- `HarmonyOS Sans SC` 的 **Regular 槽位量测为 0×0**（Light/Medium/Bold 正常），
  正文用 Light 兜底。
- `resize()` 会把自动布局 AUTO 轴翻转为 FIXED，**sizing 模式必须在 resize 之后重申**；
  且 primary/counter 轴含义随 layoutMode 方向变化（HORIZONTAL 的 primary 是宽度）。
- 主轴为 hug（AUTO）时 `SPACE_BETWEEN` 会被归一化为 MIN，两端对齐用 grow spacer 实现。
- 脚本执行超时 60s；超时请求不会终止脚本，会堵塞队列——失败后先探活再重试。
- `pixso.mjs` 的会话失效判定只看 HTTP 404/400 与**错误体**里的 session 字样；
  不能对成功响应正文做 `/session/i` 匹配——屏幕名叫 `quizSession` 时成功结果会被
  误判为会话失效而反复重建（已修复，勿回退）。
- `box()` 的 padding 只认 `pt/pb/pl/pr`，简写 `p:12` 曾被静默忽略（内边距为 0、
  文字顶边）——已在 box() 内支持 `p` 展开，但旧屏若见文字贴边先查这里。
