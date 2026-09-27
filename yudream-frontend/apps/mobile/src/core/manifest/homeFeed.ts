/**
 * 首页内容源：按应用声明的 mobile.home.feed 端点拉取真实内容条目。
 * 多应用各自分页（page/size），宿主聚合拼接；hasMore=false 的源不再翻页。
 */
import { request } from '@/core/api/httpClient';
import { resolveAssetUrl } from '@/core/domains/assetUrl';
import { getActiveDomain } from '@/core/domains/store';
import type { ManifestPluginEntry, MobileFeedItem } from '@/core/manifest/types';

export interface FeedPage {
  items: MobileFeedItem[];
  hasMore: boolean;
}

interface RawFeedItem {
  id?: string;
  route?: string;
  title?: string;
  summary?: string;
  images?: string[] | null;
  imageCount?: number;
  author?: { name?: string; avatar?: string } | null;
  tagName?: string;
  commentCount?: number;
  likeCount?: number;
  viewCount?: number;
  createTime?: number | string | null;
}

/** 相对资产路径拼域 origin（作者头像/图片多为站内路径）。 */
function absolute(path: string | null | undefined): string {
  if (!path) {
    return '';
  }
  return resolveAssetUrl(getActiveDomain()?.serverUrl ?? '', path);
}

function normalize(raw: RawFeedItem): MobileFeedItem | null {
  if (!raw?.id || !raw.title) {
    return null;
  }
  const images = (raw.images ?? []).map(absolute).filter(Boolean).slice(0, 3);
  return {
    id: String(raw.id),
    route: raw.route ?? '',
    title: raw.title,
    summary: raw.summary ?? '',
    images,
    imageCount: raw.imageCount ?? images.length,
    author: {
      name: raw.author?.name ?? '',
      avatar: absolute(raw.author?.avatar),
    },
    tagName: raw.tagName ?? '',
    commentCount: raw.commentCount ?? 0,
    likeCount: raw.likeCount ?? 0,
    viewCount: raw.viewCount ?? 0,
    createTime: typeof raw.createTime === 'string' ? Number(raw.createTime) : (raw.createTime ?? 0),
  };
}

export async function fetchAppFeed(
  app: ManifestPluginEntry,
  page: number,
  size = 20,
): Promise<FeedPage> {
  const feed = app.homeFeed;
  if (!feed?.endpoint) {
    return { items: [], hasMore: false };
  }
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  const res = await request<{ items?: RawFeedItem[] | null; hasMore?: boolean }>(
    `/api/plugins/${app.code}${feed.endpoint}?${params.toString()}`,
  );
  const items = (res.items ?? [])
    .map(normalize)
    .filter((item): item is MobileFeedItem => item !== null);
  return { items, hasMore: res.hasMore === true && items.length > 0 };
}
