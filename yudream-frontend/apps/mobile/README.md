# @yudream/mobile — YuDream 移动宿主

React Native 宿主 App：插件以 Module Federation remote（Re.Pack）热加载，
本地沙盒缓存 + 启动检查更新 + last-known-good 回滚。

## 架构速览

| 层 | 位置 | 职责 |
|---|---|---|
| 原生桥 | `src/bridges` | 安全存储 / SSE / 下载 / 深链。接口平台中立（iOS 席位预留） |
| 鉴权 | `src/core/auth` + `src/core/api` | 双 token（后端 `/api/user/login` 体系），401 先刷新再重试 |
| 插件管线 | `src/core/plugins` | manifest 快照先行 -> 后台 diff 下载 -> SHA-256 校验 -> 原子翻转 -> 回滚 |
| MF 宿主 | `rspack.config.mjs` + `scriptManager.ts` | shared singleton: react / react-native / @yudream/plugin-sdk-mobile |
| 主题 | `src/core/theme` | T0 token + 缓存先绘 + 远程覆盖层（mergeTokens 丢弃未知键） |
| 签名组件 | `src/components` | YdScreen/YdText/YdButton/YdCard/YdListItem，只消费 token |

## 插件约定

- `plugin.yml` 加 `mobile:` 块；产物打在 JAR 内 `META-INF/yudream-plugin/frontend-mobile/{code}/remoteEntry.js`（v1 单文件，关 chunk 拆分）。
- 远程模块 `exposes: { './module': ... }`，默认导出 React 组件。
- 契约类型：`@yudream/plugin-sdk-mobile`（shared singleton，插件严禁自打包）。
- 可见颜色/字号一律消费 SDK 注入的主题 token，禁止写死。

## 开发

```bash
# 在 yudream-frontend 根安装依赖（pnpm monorepo，提升后根 node_modules 生效）
pnpm install

# 启动 Re.Pack dev server（8081）
pnpm --filter @yudream/mobile start

# 另开终端装到设备/模拟器（首次需先生成 debug.keystore，见下）
pnpm --filter @yudream/mobile android
```

首次构建前在 `apps/mobile/android/app` 下生成调试签名（模板仓库无法携带二进制 keystore）：

```bash
keytool -genkeypair -v -keystore debug.keystore -alias androiddebugkey \
  -keyalg RSA -keysize 2048 -validity 10000 -storepass android -keypass android
```

默认服务器地址 `http://10.0.2.2:8080`（模拟器回环），真机/生产在 App 内「设置」修改。
Gradle 版本随 RN 0.76 模板（AGP 8.6.x / Gradle 8.10.2 / NDK 27.1 / Kotlin 2.0.21）；
如与本地 react-native 实际版本不符，同步 `android/build.gradle` 与 `gradle-wrapper.properties`。

## iOS 预留

协议与数据模型（manifest 的 platform / 能力集 / minHostVersion、device 注册、
push 通道路由）从 v1 起即双端；开发期仅实现 Android 端，iOS 客户端接入时
仅需实现 `src/bridges` 四件套 + 生成 `ios/` 工程，服务端与插件零改动。

## 发布

1. 移除 `AndroidManifest.xml` 的 `usesCleartextTraffic` 并确认全链路 HTTPS；
2. 为 release 配置正式签名（替换 `signingConfigs.debug`）；
3. `pnpm --filter @yudream/mobile bundle:android` 复用 Re.Pack 出包，
   或在 Android Studio / `./gradlew assembleRelease`。
