/**
 * 运行时环境。服务器地址不再是全局单值——多域模型下见 core/domains。
 * 此处仅保留宿主自身常量。
 */

/** App 宿主自身版本，随 package.json 同步维护；manifest 协商的 minHostVersion 依据。
 *  与 android/app/build.gradle 的 versionName / versionCode 三处手工同步。 */
export const HOST_VERSION = '0.2.8';

/** 宿主版本线（客户端更新检查的比较依据），与 build.gradle versionCode 同步。 */
export const HOST_VERSION_CODE = 10;

/** 宿主内嵌的原生能力集，manifest 过滤时上报。新增能力只增不改插件契约。 */
export const HOST_CAPABILITIES: readonly string[] = [
  'secure-storage',
  'sse',
  'download',
  'deeplink',
] as const;

// __PLATFORM__ / __YDAM_OFFICIAL_UPDATE_BASE__ 由 rspack.config.mjs 注入（后者可经 .env 覆盖）。
declare const __PLATFORM__: string;
declare const __YDAM_OFFICIAL_UPDATE_BASE__: string | undefined;

/** 官方更新源站点：App 更新检查/下载/更新日志固定走这里，不随激活域变。 */
export const OFFICIAL_UPDATE_BASE: string =
  typeof __YDAM_OFFICIAL_UPDATE_BASE__ === 'string' && __YDAM_OFFICIAL_UPDATE_BASE__
    ? __YDAM_OFFICIAL_UPDATE_BASE__
    : 'https://www.swustmc.cn';

export type AppPlatform = 'android' | 'ios';

export const PLATFORM: AppPlatform =
  typeof __PLATFORM__ === 'string' && __PLATFORM__ === 'ios' ? 'ios' : 'android';
