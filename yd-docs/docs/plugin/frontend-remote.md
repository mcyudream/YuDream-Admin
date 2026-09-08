# 插件前端工程化（remoteEntry）

生产插件的形态：前端构建为 **ESM remote entry**，连同 assets 打进插件 JAR，由宿主动态加载并注入 SDK。

## 目录结构

```text
yudream-frontend/packages/plugin-demo/
  package.json              # @yudream/plugin-demo
  vite.config.ts
  src/
    index.ts                # remote entry 导出
    pages/Home.vue          # 路由页面
    components/             # 可复用组件
    composables/            # 有状态工作流
    api/index.ts            # 基于 sdk.http 的请求封装
    types.ts                # 视图模型类型
    styles.css              # 可选样式
```

工程要求：一个路由对应一个真实页面组件；多管理面拆多个路由（配合后端 `@PluginRoute` 的 `parentTitle` 分组），不要做一个巨型 tab 页面。

## remote entry 模块契约

```ts
export interface YuDreamPluginFrontendModule {
  routes?: Record<string, Component>       // 组件名 -> 页面组件，键对应 @PluginRoute.component
  default?: Component | YuDreamPluginFrontendModule
  install?: () => void | Promise<void>     // 首次加载执行一次（初始化逻辑；样式请走声明式 style.css）
}

export function defineYuDreamPlugin(module: YuDreamPluginFrontendModule)
```

典型 `src/index.ts`：

```ts
import Home from './pages/Home.vue'
import 'virtual:uno.css'   // 与宿主同预设的 UnoCSS 产物，汇入 dist/style.css
import './styles.css'      // 插件自有全局样式（可选），同样汇入 dist/style.css

export default defineYuDreamPlugin({
  routes: { Home },
})
```

样式产物 `dist/style.css` 通过后端 `@PluginFrontend(styles = {"style.css"})` 声明，宿主在导入 `remoteEntry.js` 前加载，并在页面切出后按引用计数回收，无需插件手动注入。

宿主加载流程：动态 import `entry`（默认 `/api/platform/plugins/{code}/assets/remoteEntry.js`）→ 调用模块 `install()` → 按 `@PluginRoute.component` 名解析组件（先 `module.routes[name]` 再 `module[name]`）→ 以 `{ sdk, route }` props 渲染。

## 样式与静态资源

三种方式（可组合）：

| 方式 | 做法 |
|---|---|
| 声明式样式（推荐） | `yuDreamPluginUnoCss()` + 入口 `import 'virtual:uno.css'`，`@PluginFrontend(styles = {"style.css"})` 声明，宿主加载/回收 |
| 独立资源声明 | 在后端 manifest 声明 `styles: List.of("assets/plugin.css")`、`scripts: List.of("assets/bootstrap.js")`，宿主在加载 remoteEntry 前按序注入 |
| 其他静态资源 | 图片/字体/JSON 随 JAR 放入同一前端目录，代码里用 `sdk.assets.url("assets/logo.svg")` 取地址 |
| 内联样式（遗留兼容） | `import styles from './styles.css?inline'` + `install()` 注入 `<style>`；已被声明式取代，新插件不得使用 |

Vite 产物要求：保留相对引用与 hash 文件名，保证 CSS、JS chunk、图片、字体都能从 `/assets/**` 地址加载；动态 import 的 chunk 由浏览器自动加载，无须写进 `scripts`。

## Vite 配置要点

```ts
import { yuDreamPluginUnoCss } from '@yudream/plugin-sdk/uno-config'
import { yuDreamPluginSharedAliases } from '@yudream/plugin-sdk/vite-shared'

export default defineConfig({
  plugins: [vue(), yuDreamPluginUnoCss()], // 与宿主一致的 UnoCSS 预设（关闭 reset，仅引用宿主主题变量）
  resolve: { alias: yuDreamPluginSharedAliases() }, // vue/vue-router/@yudream/components 指向宿主共享 shim
  build: {
    lib: {
      entry: 'src/index.ts',
      formats: ['es'],
      fileName: () => 'remoteEntry.js',
      cssFileName: 'style', // 固定输出 dist/style.css，供 @PluginFrontend(styles = {"style.css"}) 声明
    },
    // 输出 ESM remoteEntry.js + hash assets，保留相对引用
  },
})
```

产物零捆绑 Vue/VueRouter/@yudream/components——它们经 `window.__YUDREAM_PLUGIN_SHARED__` 使用宿主实例，避免双份 Vue。

## 打包进插件 JAR

```text
META-INF/yudream-plugin/frontend/demo-plugin/remoteEntry.js
META-INF/yudream-plugin/frontend/demo-plugin/assets/*
```

通常在插件模块 `pom.xml` 中把 `packages/plugin-demo/dist` 复制进去。生产 manifest **禁止依赖 workspace alias**。

## 本地联调：dev-mode 热重载

宿主 dev 模式（门控 `yudream.platform.plugin.dev-mode.enabled` 三态：缺省时源码运行自动开启、JAR 运行自动关闭）：

1. 登记开发项目：yml 的 `dev-mode.projects`（CONFIG），或调试浮窗「设置」页注册（FILE，持久化到 `plugins/dev-projects.json`——文件形式方便编码代理读取）。`code` 缺省时从 `target/classes/plugin.yml` 推断。
2. 监听管线：
   - `.java` 变更 → 自动编译 → `target/classes` 变化 → 禁用→卸载→目录加载→恢复启用；
   - 前端 `dist` 变化（配 `vite build --watch`）→ SSE 推送 → 重挂载远程模块 + 重建路由（非 HMR，状态会重置）。
3. 调试浮窗快捷键 `Ctrl/Cmd+Shift+D`；`frontend-dist` 默认推导为 `{插件模块}/../../yudream-frontend/packages/plugin-{code}/dist`。

## 验证命令

```bash
cd yudream-frontend
pnpm --filter @yudream/plugin-demo build
pnpm --filter @fantastic-admin/core-arco-design-vue run test:typecheck
```
