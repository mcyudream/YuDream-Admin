import { request } from '@/core/api/httpClient';
import { HOST_CAPABILITIES, HOST_VERSION, PLATFORM } from '@/core/config/env';
import type { MobileManifest } from './types';

interface ManifestRes {
  platform?: string;
  entries?: Array<{
    code?: string;
    version?: string;
    assetRevision?: string;
    remoteEntrySha256?: string;
    remoteEntryUrl?: string;
    styleUrl?: string | null;
    minHostVersion?: string;
    platforms?: string[];
    requiredNativeCapabilities?: string[];
    name?: string | null;
    description?: string | null;
    icon?: string | null;
    homeCards?: Array<{
      id?: string;
      title?: string;
      description?: string;
      icon?: string;
      route?: string;
    }> | null;
  }>;
}

/**
 * 拉取激活域过滤后的插件清单并归一化（后端字段为 entries）。
 * 过滤语义在服务端（platform/能力集/host 版本），客户端原样上报，不做二次过滤。
 */
export async function fetchManifest(): Promise<MobileManifest> {
  const params = new URLSearchParams({
    platform: PLATFORM,
    hostVersion: HOST_VERSION,
    capabilitySet: HOST_CAPABILITIES.join(','),
  });
  const res = await request<ManifestRes>(`/api/mobile/manifest?${params.toString()}`);
  return {
    generatedAt: new Date().toISOString(),
    hostVersionMin: HOST_VERSION,
    plugins: (res.entries ?? []).map((entry) => ({
      code: entry.code ?? '',
      version: entry.version ?? '',
      assetRevision: entry.assetRevision ?? '',
      remoteEntryUrl: entry.remoteEntryUrl ?? '',
      remoteEntrySha256: entry.remoteEntrySha256 ?? '',
      styleUrl: entry.styleUrl ?? null,
      minHostVersion: entry.minHostVersion ?? '0.0.0',
      platforms: (entry.platforms ?? []) as MobileManifest['plugins'][number]['platforms'],
      requiredNativeCapabilities: entry.requiredNativeCapabilities ?? [],
      name: entry.name ?? undefined,
      description: entry.description ?? undefined,
      icon: entry.icon ?? undefined,
      homeCards: (entry.homeCards ?? [])
        .filter((c) => c.id && c.title && c.route)
        .map((c) => ({
          id: c.id ?? '',
          title: c.title ?? '',
          description: c.description ?? '',
          icon: c.icon ?? 'cube-outline',
          route: c.route ?? '',
        })),
    })),
  };
}
