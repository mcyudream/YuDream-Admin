# YuDream Admin 移动端 UI 设计稿（Pixso）

设计基准：宿主 FaTheme0（`yudream-frontend/packages/themes/index.ts`，shadcn neutral
中性单色系，OKLCH 已换算为 sRGB）。画板 360×800，Android 优先。

## 当前文件内容（在 Pixso 中）

- 页面「01 宿主 App」：封面 / 启动 / 欢迎 / 登录 / 首页 / 应用 / 我的 / 域管理 / 添加域 / 插件容器（论坛示例）
- 页面「02 论坛插件 · 设想」：帖子列表 / 帖子详情（对应 forum-entry.tsx 设想）
- 页面「03 深色模式」：首页 Dark / 我的 Dark
- 页面「04 设计规范」：Token 色板与字阶 / 组件样例
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
