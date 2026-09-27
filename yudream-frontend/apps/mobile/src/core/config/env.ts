/**
 * 运行时环境。服务器地址不再是全局单值——多域模型下见 core/domains。
 * 此处仅保留宿主自身常量。
 */

/** App 宿主自身版本，随 package.json 同步维护；manifest 协商的 minHostVersion 依据。 */
export const HOST_VERSION = '0.1.0';

/** 宿主内嵌的原生能力集，manifest 过滤时上报。新增能力只增不改插件契约。 */
export const HOST_CAPABILITIES: readonly string[] = [
  'secure-storage',
  'sse',
  'download',
  'deeplink',
] as const;

// __PLATFORM__ 由 rspack.config.mjs 注入。
declare const __PLATFORM__: string;

export type AppPlatform = 'android' | 'ios';

export const PLATFORM: AppPlatform =
  typeof __PLATFORM__ === 'string' && __PLATFORM__ === 'ios' ? 'ios' : 'android';
