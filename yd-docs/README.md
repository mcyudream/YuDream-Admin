# yd-docs — YuDream Admin 文档站

基于 **VitePress**（Vue 3 生态，Element Plus 官网同款技术路线），支持 Mermaid 图渲染与 Vue 演示组件。

## 命令

```bash
pnpm install
pnpm dev      # 本地开发 http://localhost:5174
pnpm build    # 构建到 docs/.vitepress/dist
pnpm preview  # 预览构建产物
```

## 目录结构

```text
docs/
  index.md                    首页
  guide/                      框架主体文档（两种使用方式/架构/平台能力/工具注解/开发/部署）
  plugin/
    overview|getting-started|specification|frontend-remote
    spi/index.md              SPI 版本清单 + 升级指引 + Coding Agent 增量更新规范
    spi/v1/                   SPI v1（当前源码 2.24.0）全量教程：core/annotations/http/frontend/framework-services
    sdk/index.md              @yudream/plugin-sdk 全量 API
  components/                 组件库文档（element-plus 风格：演示 + 源码 + API 表）
  .vitepress/
    config.ts                 站点配置与侧边栏
    theme/                    主题：Mermaid、Demo（演示+源码折叠）、ApiTable
```

## 维护规范

- **SPI 新版本发布时**：按 `docs/plugin/spi/index.md` 中"面向 Coding Agent 的增量更新规范"复制上一版目录并增量修订，同步更新版本清单与侧边栏。
- 组件 API 变化时同步更新 `docs/components/` 对应页面；组件演示用 `Demo` 组件（props: `title`/`description`/`source`），API 表用 `ApiTable` 组件。
- Mermaid 图直接在 markdown 中使用 ` ```mermaid ` 围栏代码块即可渲染。
