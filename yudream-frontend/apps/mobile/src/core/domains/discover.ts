/**
 * 域发现：输入端点地址后探测这是一个 YuDream 站点，取回站点名与移动能力状态。
 * GET /api/mobile/public/site-info 为匿名端点，不受能力双闸门约束。
 */
import { ResultEnvelope } from '@/core/api/envelope';
import { hostOf } from '@/core/domains/store';

export interface SiteInfo {
  siteName: string;
  logo: string;
  favicon: string;
  version: string;
  mobileEnabled: boolean;
  /** mobile-app 能力配置：登录页 hero 背景图（空=未定制） */
  loginHeroImage: string;
  /** mobile-app 能力配置：登录页 hero 底色（空=未定制） */
  loginHeroBackground: string;
}

interface SiteInfoRes {
  siteName?: string;
  logo?: string;
  favicon?: string;
  version?: string;
  mobileEnabled?: boolean;
  loginHeroImage?: string;
  loginHeroBackground?: string;
}

export type DiscoverOutcome =
  | { kind: 'ok'; info: SiteInfo }
  /** 站点可达但不是 YuDream（或极老版本）：允许以受限模式添加 */
  | { kind: 'foreign' }
  | { kind: 'unreachable'; message: string };

export async function discoverDomain(rawUrl: string): Promise<DiscoverOutcome> {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), 8000);
  try {
    const res = await fetch(`${rawUrl}/api/mobile/public/site-info`, {
      headers: { Accept: 'application/json' },
      signal: controller.signal,
    });
    if (res.ok) {
      const body = (await res.json()) as ResultEnvelope<SiteInfoRes>;
      if (body.code === 200 && body.data) {
        return {
          kind: 'ok',
          info: {
            siteName: body.data.siteName || hostOf(rawUrl),
            logo: body.data.logo ?? '',
            favicon: body.data.favicon ?? '',
            version: body.data.version ?? '',
            mobileEnabled: body.data.mobileEnabled === true,
            loginHeroImage: body.data.loginHeroImage ?? '',
            loginHeroBackground: body.data.loginHeroBackground ?? '',
          },
        };
      }
    }
    // 404/其他：可达但端点不存在
    await fetch(rawUrl, { method: 'HEAD', signal: controller.signal }).catch(() => undefined);
    return { kind: 'foreign' };
  } catch (e) {
    const message =
      e instanceof Error && e.name === 'AbortError'
        ? '连接超时，请检查地址与网络'
        : '无法连接到该地址';
    return { kind: 'unreachable', message };
  } finally {
    clearTimeout(timer);
  }
}
