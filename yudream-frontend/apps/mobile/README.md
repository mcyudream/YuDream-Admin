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

默认服务器地址 `http://127.0.0.1:8080`（配合 `adb reverse tcp:8080` 转发到宿主机后端，
`run-android` 会自动转发 8081）；真机调试在 App 内「设置」改成局域网/正式地址。

### Windows 构建环境（已按此配置并验证）

- JDK 21、Android SDK（platforms;android-35、build-tools;35.0.0、**NDK 26.1.10909125**、cmake;3.22.1），
  `android/local.properties` 写 `sdk.dir`。
- **长路径**：CMake/ninja 构建路径在本 monorepo 深度下必超 260，需两步：
  1. `HKLM\SYSTEM\CurrentControlSet\Control\FileSystem\LongPathsEnabled = 1`（管理员一次性）；
  2. SDK 自带 ninja 无 longPathAware 清单，把 `Sdk/cmake/3.22.1/bin/ninja.exe`
     换为 ninja ≥1.12（longPathAware；原版备份为 ninja.exe.bak）。
  不要用 junction/subst 绕短路径：pnpm 相对软链会在跨重解析点回溯 `..` 时断裂。
- Gradle 8.10.2 wrapper 已在 `android/` 生成（`gradle wrapper --gradle-version 8.10.2`）。
- `android/build.gradle` 的版本组（AGP 8.6.0 / Kotlin 1.9.24 / target 34）对齐
  `@react-native/gradle-plugin@0.76.6` 的 catalog；`react-native-screens` 钉 4.5.0
  （4.28+ 的 codegen 输出与本版 RN 不兼容）。
- `react-native.config.js` 接管 Re.Pack 命令并解析 monorepo reactNativePath。

模拟器（MuMu 12）：`adb connect 127.0.0.1:16384`、`adb reverse tcp:8081 tcp:8081`、
`adb reverse tcp:8080 tcp:8080`。构建安装：`gradlew.bat app:installDebug`。

## iOS 预留

协议与数据模型（manifest 的 platform / 能力集 / minHostVersion、device 注册、
push 通道路由）从 v1 起即双端；开发期仅实现 Android 端，iOS 客户端接入时
仅需实现 `src/bridges` 四件套 + 生成 `ios/` 工程，服务端与插件零改动。

## 发布

1. 移除 `AndroidManifest.xml` 的 `usesCleartextTraffic` 并确认全链路 HTTPS；
2. 为 release 配置正式签名（替换 `signingConfigs.debug`）；
3. `pnpm --filter @yudream/mobile bundle:android` 复用 Re.Pack 出包，
   或在 Android Studio / `./gradlew assembleRelease`。
