/**
 * App 更新检查：对接 app-release 插件的匿名公开端点。
 * 版本线（versionCode）与 android/app/build.gradle 的 versionCode 手工同步。
 */
import { HOST_VERSION_CODE } from '@/core/config/env';

export interface AppUpdateLatest {
  id: string;
  versionName: string;
  versionCode: number;
  changelog: string;
  fileName: string;
  fileSize: number;
  publishedAt: number;
}

export interface AppUpdateCheck {
  updateAvailable: boolean;
  forced: boolean;
  minVersionCode: number;
  latest: AppUpdateLatest | null;
}

export interface AppUpdateInfo extends AppUpdateCheck {
  /** 浏览器侧直接下载的 APK 地址（匿名）。 */
  downloadUrl: string;
}

/**
 * 检查更新。插件未装/未启用/网络失败一律返回 null（视作无需更新），
 * 保证更新通道故障不会阻塞 App 正常使用。
 */
export async function checkAppUpdate(serverUrl: string): Promise<AppUpdateInfo | null> {
  try {
    const res = await fetch(
      `${serverUrl}/api/plugins/app-release/public/check?platform=android&versionCode=${HOST_VERSION_CODE}`,
    );
    if (!res.ok) {
      return null;
    }
    const envelope = (await res.json()) as { code?: number; data?: AppUpdateCheck };
    if (!envelope || envelope.code !== 200 || !envelope.data) {
      return null;
    }
    const data = envelope.data;
    return {
      ...data,
      downloadUrl: data.latest
        ? `${serverUrl}/api/plugins/app-release/public/download/${encodeURIComponent(data.latest.id)}`
        : '',
    };
  } catch {
    return null;
  }
}

export interface ChangelogEntry {
  id: string;
  versionName: string;
  versionCode: number;
  changelog: string;
  fileSize: number;
  publishedAt: number;
}

/** 完整更新日志（已发布版本，新到旧）。 */
export async function fetchChangelogs(serverUrl: string): Promise<ChangelogEntry[]> {
  try {
    const res = await fetch(`${serverUrl}/api/plugins/app-release/public/changelogs?platform=android`);
    if (!res.ok) {
      return [];
    }
    const envelope = (await res.json()) as { code?: number; data?: { records?: ChangelogEntry[] } };
    return envelope?.data?.records ?? [];
  } catch {
    return [];
  }
}

export function formatUpdateSize(size: number): string {
  if (!size) {
    return '';
  }
  if (size >= 1024 * 1024) {
    return `${(size / 1024 / 1024).toFixed(1)} MB`;
  }
  return `${Math.max(1, Math.round(size / 1024))} KB`;
}
