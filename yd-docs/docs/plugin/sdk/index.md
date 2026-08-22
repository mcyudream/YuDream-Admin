# @yudream/plugin-sdk

宿主注入给插件前端页面的 SDK。插件页面组件通过 props 接收 `{ sdk, route }`，**不要**在插件内捆绑私有 axios 实例。

- npm 包版本：**1.1.0**（发布于 `nexus.yudream.online/repository/npm-public/`）；peerDependencies 仅 `vue`、`vue-router`——一切运行时能力由宿主注入。
- 宿主启动时挂载两个全局对象：
  - `window.__YUDREAM_PLUGIN_SHARED__ = { vue, vueRouter, components }`（宿主的 Vue/VueRouter/@yudream/components 单例）；
  - `window.__YUDREAM_PLUGIN_SDK__ = { version, create(pluginCode) => YuDreamPluginSdk }`。

## YuDreamPluginSdk（总入口）

```ts
interface YuDreamPluginPageProps {
  sdk: YuDreamPluginSdk
  route?: RouteLocationNormalizedLoaded
}

interface YuDreamPluginSdk {
  version: string            // SDK 版本
  pluginCode: string         // 当前插件 code
  account: YuDreamPluginAccount
  http: YuDreamPluginHttpClient
  files: YuDreamPluginFilesClient
  assets: YuDreamPluginAssetsClient
}
```

## account —— 当前账号快照

```ts
interface YuDreamPluginAccount {
  userId: string             // 注意：string！Long/Snowflake ID 全链路 string
  username: string
  avatar?: string
  currentDept?: string
  currentRole?: string
  permissions: string[]      // 前端按钮级控制自行 includes 判断
}
```

SDK 不提供独立 `hasPermission()`；用 `sdk.account.permissions.includes('plugin:demo:manage')` 自行判断，接口级控制仍以后端 `permission` 声明为准。

## http —— 插件 HTTP 客户端

自动带鉴权并指向 `/api/plugins/{pluginCode}/**`：

| 方法 | 签名 | 说明 |
|---|---|---|
| `request` | `<T>(path: string, options?: { method?: string; data?: unknown }) => Promise<T>` | 通用请求 |
| `get` | `<T>(path: string) => Promise<T>` | GET |
| `post` | `<T>(path: string, data?: unknown) => Promise<T>` | POST |
| `blob` | `(path: string, options?) => Promise<YuDreamPluginBlobResponse>` | blob 响应：`{ data: Blob; headers }`（Excel 导出等） |
| `url` | `(path: string) => string` | 生成插件端点绝对 URL（不发请求） |

- `path` 为插件内相对路径（如 `/status`），宿主拼到 `/api/plugins/{pluginCode}` 后。
- 所有方法直接返回 `data`（已剥掉系统统一响应包装）；非 2xx 由宿主拦截器统一报错。

```ts
// src/api/index.ts
import type { YuDreamPluginSdk } from '@yudream/plugin-sdk'

export function createApi(sdk: YuDreamPluginSdk) {
  return {
    listResources: (page: number) =>
      sdk.http.get<{ items: ResourceItem[]; total: number }>(
        `/admin/resources?page=${page}`),
    upload: (file: File) =>
      sdk.files.uploadImage(file, { module: 'demo' }),
  }
}
```

## files —— 文件能力

```ts
interface YuDreamPluginFileUploadOptions {
  module?: string          // 业务模块标记
  publicAccess?: boolean   // 是否公开可访问
}

interface YuDreamPluginFilesClient {
  uploadImage(file: File, options?: YuDreamPluginFileUploadOptions): Promise<YuDreamPluginFileObject>
  assetUrl(url?: string): string   // 站内文件地址 → 可访问 URL
}

interface YuDreamPluginFileObject {
  id: string               // string！禁止 Number(id)
  originalName?: string
  contentType?: string
  size?: number
  module?: string
  url?: string
  assetUrl?: string
  createTime?: string
}
```

## assets —— JAR 内静态资源

```ts
interface YuDreamPluginAssetsClient {
  url(path: string): string   // 如 sdk.assets.url("assets/logo.svg")
}
```

路径必须是相对路径，不得包含 `..` 或反斜杠。

## 共享运行时代理（避免重复打包 Vue）

从以下模块导入 Vue API / 路由 / 组件库，构建时经 `yuDreamPluginSharedAliases()` 指向宿主共享单例：

| 模块 | 内容 |
|---|---|
| `@yudream/plugin-sdk/host-vue`（经 alias 后即 `vue`） | 重导出宿主 Vue 全部 API（ref/computed/watch/onMounted…约 170 项） |
| host-vue-router（alias `vue-router`） | `useRoute/useRouter/RouterLink/RouterView/createRouter…` |
| host-components（alias `@yudream/components`） | Fantastic-admin 自带的 `Fa*` 组件（`FaResponsiveTable` 为 YuDream 原创例外）与 `useFaModal/useFaDrawer/useFaToast/useFaImagePreview/useIsMobile`，以及 YuDream 原创的 `Yd*` 组件与 composable |

共享单例缺失时报错：`YuDream plugin shared runtime is missing: {name}`。

## vite-shared 构建助手

```ts
import { yuDreamPluginSharedAliases } from '@yudream/plugin-sdk/vite-shared'
```

返回 `{ vue, vue-router, @yudream/components }` 三条 alias，使产物不打包这些依赖而引用宿主实例。详见 [插件前端工程化](/plugin/frontend-remote)。

::: tip 版本说明
宿主运行时常量 `YUDREAM_PLUGIN_SDK_VERSION` 与 npm 包版本可能不同步，以 **npm 包版本为准**；后端 `@PluginFrontend.sdkVersion` 填写宿主实际注入的 SDK 行为版本。
:::
