/**
 * 应用模块 SDK 桥：为插件远程模块构造宿主注入的 PluginMobileSdk。
 * 主题 token 只读透传；api/storage 按 App code 命名空间隔离。
 */
import { Linking } from 'react-native';
import AsyncStorage from '@react-native-async-storage/async-storage';
import type { PluginMobileSdk } from '@yudream/plugin-sdk-mobile';
import { request } from '@/core/api/httpClient';
import { getActiveDomain } from '@/core/domains/store';
import { PLATFORM, HOST_VERSION } from '@/core/config/env';
import type { ThemeTokens } from '@/core/theme/tokens';

export function buildAppSdk(appCode: string, theme: ThemeTokens): PluginMobileSdk {
  const ns = `app.${appCode}.`;
  return {
    theme: theme as unknown as PluginMobileSdk['theme'],
    platform: PLATFORM,
    hostVersion: HOST_VERSION,
    baseUrl: getActiveDomain()?.serverUrl ?? '',
    api: {
      request: <T,>(path: string, options?: { method?: string; body?: unknown }) =>
        request<T>(path, {
          method: (options?.method?.toUpperCase() ?? 'GET') as 'GET' | 'POST' | 'PUT' | 'DELETE',
          body: options?.body,
        }),
    },
    storage: {
      get: (key) => AsyncStorage.getItem(ns + key),
      set: (key, value) => AsyncStorage.setItem(ns + key, value),
      remove: (key) => AsyncStorage.removeItem(ns + key),
    },
    sse: {
      // v1 不做 SSE：能力位预留，后续经 fetch-SSE 桥实现
      subscribe: () => ({
        close: () => undefined,
      }),
    },
    deeplink: {
      open: (url) => Linking.openURL(url),
    },
  };
}
