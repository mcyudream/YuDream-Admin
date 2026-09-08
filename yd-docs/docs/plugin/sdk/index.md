# @yudream/plugin-sdk

宿主注入给插件前端页面的 SDK。插件页面组件通过 props 接收 `{ sdk, route }`，**不要**在插件内捆绑私有 axios 实例。

- npm 包版本：**1.3.0**（发布于 `nexus.yudream.online/repository/npm-public/`）；peerDependencies 仅 `vue`、`vue-router`——一切运行时能力由宿主注入。
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
  messaging: YuDreamPluginMessagingClient
  users: YuDreamPluginUsersClient
  ai: YuDreamPluginAiClient
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

## messaging —— 宿主消息连接目录

列出当前已启用的 QQ 消息连接及其群/频道，供插件管理页做下拉选择。**不要**再包一层插件 HTTP 去转发 `framework.messaging()`，也不要直接打宿主管理接口 `/api/platform/milky/**`。

```ts
interface YuDreamPluginMessagingConnection {
  id: string                 // string！Long/Snowflake ID
  name: string
  platform?: string          // 平台，当前为 qq
  userId?: string | null
  protocol?: string | null   // milky | official
}

interface YuDreamPluginMessagingGroup {
  id: string
  name: string
}

interface YuDreamPluginMessagingClient {
  connections(): Promise<YuDreamPluginMessagingConnection[]>
  groups(connectionId: string): Promise<YuDreamPluginMessagingGroup[]>
}
```

```ts
const connections = await sdk.messaging.connections()
const official = connections.filter(item => item.protocol === 'official')
const groups = await sdk.messaging.groups(official[0]?.id || '')
```

- 登录即可读取；能力未启用时返回空列表，不抛错。
- 官方机器人没有历史群拉取接口，`groups()` 只返回本进程事件流已见到的群。重启后列表为空，直到机器人再次收到该群消息。打开选择器时，宿主会对还只有 openid 的群补一次 `GET /v2/groups/{openid}/info`，有权限时把群名写进缓存。
- 连接 `id` 全程字符串，禁止 `Number(id)`。
- 对应宿主 HTTP（登录即可，不要求 `platform:milky:view`）：
  - `GET /api/platform/plugins/messaging/connections`
  - `GET /api/platform/plugins/messaging/groups?connectionId=`

## users —— 宿主用户 / 部门 / 角色目录

列出当前系统用户、部门树与全站角色，供插件管理页做选择器。**不要**再包一层插件 HTTP 去转发 `framework.users()`。

```ts
interface YuDreamPluginUserOption {
  id: string                 // string！Long/Snowflake ID
  username: string
  nickname?: string
  email?: string
  avatar?: string
  status?: string
  deptIds?: string[]
  deptNames?: string[]
}

interface YuDreamPluginDeptOption {
  id: string
  name: string
  label?: string             // flatten=true 时带父级路径，如「技术部 / 平台组」
  parentId?: string | null
  status?: string
  children?: YuDreamPluginDeptOption[]
}

interface YuDreamPluginRoleOption {
  id: string
  code?: string
  name: string
  deptId?: string | null
  deptName?: string | null
}

interface YuDreamPluginUsersClient {
  search(query?: { keyword?: string; deptId?: string; page?: number; size?: number }): Promise<YuDreamPluginUserOption[]>
  resolve(ids: string[]): Promise<YuDreamPluginUserOption[]>
  departments(query?: { keyword?: string; flatten?: boolean }): Promise<YuDreamPluginDeptOption[]>
  roles(): Promise<YuDreamPluginRoleOption[]>
}
```

```ts
const users = await sdk.users.search({ keyword: '张', page: 1, size: 20 })
const depts = await sdk.users.departments({ flatten: true })
const roles = await sdk.users.roles()
```

- 登录即可读取；用户/部门/角色属于系统能力，不依赖平台闸门。
- `search()` 只返回当前页列表，SPI 没有总数；需要「还有下一页」时按 `size + 1` 探测。
- `departments({ flatten: true })` 拍平为带路径 `label` 的列表；默认返回树。
- `roles()` 是全站角色选项，不是某个用户的 `listRoles(userId)`。
- 全部 ID 全程字符串，禁止 `Number(id)`。
- 对应宿主 HTTP（登录即可）：
  - `GET /api/platform/plugins/users?keyword=&deptId=&page=&size=`
  - `GET /api/platform/plugins/users/resolve?ids=`
  - `GET /api/platform/plugins/users/departments?keyword=&flatten=`
  - `GET /api/platform/plugins/users/roles`

## ai —— 宿主 Agent / 模型供应商目录

列出当前已启用的 AI Agent 与供应商/模型，供插件管理页做下拉选择。**不要**再包一层插件 HTTP 去转发 `framework.ai().agents()/providers()`。

```ts
interface YuDreamPluginAiAgentOption {
  code: string
  name: string
  description?: string
}

interface YuDreamPluginAiProviderOption {
  code: string
  name: string
  models?: { code: string; name: string }[]
}

interface YuDreamPluginAiClient {
  agents(): Promise<YuDreamPluginAiAgentOption[]>
  providers(): Promise<YuDreamPluginAiProviderOption[]>
}
```

```ts
const agents = await sdk.ai.agents()
const providers = await sdk.ai.providers()
```

- 登录即可读取；AI 能力未启用时返回空列表，不抛错。
- 真正对话/跑 Agent 仍走插件后端 `framework.ai()`，本目录只给前端选择器。
- 对应宿主 HTTP（登录即可）：
  - `GET /api/platform/plugins/ai/agents`
  - `GET /api/platform/plugins/ai/providers`

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
当前 npm 包与宿主注入行为版本均为 **1.3.0**（`sdk.messaging` / `sdk.users` / `sdk.ai`）。以 `yudream-frontend/packages/plugin-sdk/package.json` 为准；后端 `@PluginFrontend.sdkVersion` 填写宿主实际注入的 SDK 行为版本。源码升版不等于已发布到 Nexus。
:::
