import { request } from '@/core/api/httpClient';
import { HOST_CAPABILITIES, HOST_VERSION, PLATFORM } from '@/core/config/env';
import type { MobileManifest } from './types';

/**
 * 拉取激活域过滤后的插件清单。过滤语义在服务端（platform/能力集/host 版本），
 * 客户端原样上报，不在本地二次过滤。
 */
export async function fetchManifest(): Promise<MobileManifest> {
  const params = new URLSearchParams({
    platform: PLATFORM,
    hostVersion: HOST_VERSION,
    capabilitySet: HOST_CAPABILITIES.join(','),
  });
  return request<MobileManifest>(`/api/mobile/manifest?${params.toString()}`);
}
