/**
 * 与后端 GET /api/mobile/manifest 的响应契约（镜像）。
 * 协议从 v1 起携带 platform / minHostVersion / capabilities，
 * 后端过滤逻辑对 iOS 用例已有测试覆盖——客户端不得省略这些字段。
 */

export type MobilePlatform = 'android' | 'ios';

export interface ManifestPluginEntry {
  code: string;
  version: string;
  assetRevision: string;
  remoteEntryUrl: string;
  remoteEntrySha256: string;
  styleUrl: string | null;
  minHostVersion: string;
  platforms: MobilePlatform[];
  requiredNativeCapabilities: string[];
}

export interface MobileManifest {
  generatedAt: string;
  hostVersionMin: string;
  plugins: ManifestPluginEntry[];
}

/** manifest 不可用（能力关闭/网络失败）时的降级来源 */
export interface CachedManifest extends MobileManifest {
  fetchedAt: string;
}
