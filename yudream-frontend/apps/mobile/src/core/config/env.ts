/**
 * 运行时环境：服务器地址可由用户在设置页修改并持久化，
 * 默认值仅用于开发（Android 模拟器回环到宿主机）。
 */
import AsyncStorage from '@react-native-async-storage/async-storage';

const KEY_SERVER_URL = 'env.serverUrl';

// adb reverse tcp:8080 已把模拟器 localhost 映射到宿主机（run-android 亦会自动转发 8081），
// 比 10.0.2.2 更确定（MuMu/标准模拟器通吃）；真机调试在设置页改成局域网地址。
export const DEFAULT_SERVER_URL = 'http://127.0.0.1:8080';

/** App 宿主自身版本，随 package.json 同步维护；manifest 协商的 minHostVersion 依据。 */
export const HOST_VERSION = '0.1.0';

/** 宿主内嵌的原生能力集，manifest 过滤时上报。新增能力只增不改插件契约。 */
export const HOST_CAPABILITIES: readonly string[] = [
  'secure-storage',
  'sse',
  'download',
  'deeplink',
] as const;

let currentServerUrl = DEFAULT_SERVER_URL;

export async function initEnv(): Promise<void> {
  const persisted = await AsyncStorage.getItem(KEY_SERVER_URL);
  if (persisted) {
    currentServerUrl = persisted;
  }
}

export function getServerUrl(): string {
  return currentServerUrl;
}

export async function setServerUrl(url: string): Promise<void> {
  currentServerUrl = url.replace(/\/+$/, '');
  await AsyncStorage.setItem(KEY_SERVER_URL, currentServerUrl);
}

// __PLATFORM__ 由 rspack.config.mjs 注入。
declare const __PLATFORM__: string;

export type AppPlatform = 'android' | 'ios';

export const PLATFORM: AppPlatform =
  typeof __PLATFORM__ === 'string' && __PLATFORM__ === 'ios' ? 'ios' : 'android';
