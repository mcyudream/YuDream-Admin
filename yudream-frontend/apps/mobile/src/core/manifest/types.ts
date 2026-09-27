/**
 * 与后端 GET /api/mobile/manifest 的响应契约（镜像）。
 * 协议从 v1 起携带 platform / minHostVersion / capabilities，
 * 后端过滤逻辑对 iOS 用例已有测试覆盖——客户端不得省略这些字段。
 */

export type MobilePlatform = 'android' | 'ios';

/** 应用向主页注册的卡片（plugin.yml mobile.home.cards，后端透传）。 */
export interface MobileHomeCard {
  id: string;
  title: string;
  description: string;
  /** Ionicons 图标名 */
  icon: string;
  /** 应用内路由，打开应用时透传给插件模块 */
  route: string;
}

/** 应用向首页注册的内容源端点（plugin.yml mobile.home.feed）。 */
export interface MobileHomeFeed {
  /** 插件 API 根下的相对端点，如 /public/mobile-feed */
  endpoint: string;
  /** 可选分节标题 */
  title?: string;
}

/** 内容源返回的标准条目：一条真实内容（帖子等），由宿主统一渲染。 */
export interface MobileFeedItem {
  id: string;
  /** 应用内路由，点击条目打开 */
  route: string;
  title: string;
  summary: string;
  /** 缩略图（宿主最多展示 3 张） */
  images: string[];
  /** 实际图片总数（多于展示数时显示「共 N 张」） */
  imageCount?: number;
  author: { name: string; avatar: string };
  /** 分类/标签名 */
  tagName?: string;
  commentCount?: number;
  likeCount?: number;
  viewCount?: number;
  /** epoch 毫秒 */
  createTime?: number;
}

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
  /** 移动端展示名（mobile.name，缺省回落 code） */
  name?: string;
  description?: string;
  /** Ionicons 图标名（mobile.icon） */
  icon?: string;
  /** 应用注册的主页卡片 */
  homeCards?: MobileHomeCard[];
  /** 应用注册的首页内容源（真实内容条目） */
  homeFeed?: MobileHomeFeed;
}

/** 应用展示名：mobile.name 优先，回落 code。 */
export function appDisplayName(entry: ManifestPluginEntry): string {
  return entry.name?.trim() || entry.code;
}

/** 应用首个卡片路由：无声明时 undefined，宿主用默认路由打开。 */
export function appHomeRoute(entry: ManifestPluginEntry): string | undefined {
  return entry.homeCards?.[0]?.route;
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
