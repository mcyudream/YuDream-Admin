/**
 * 插件注册表：当前激活域生效的 manifest + 加载状态。
 * manifest 按域缓存（AsyncStorage 键含域 id），切域/离线启动用快照。
 */
import AsyncStorage from '@react-native-async-storage/async-storage';
import type { ManifestPluginEntry, MobileManifest } from '@/core/manifest/types';

const cacheKey = (domainId: string) => `manifest.cached.${domainId}`;

type Listener = (plugins: ManifestPluginEntry[]) => void;

let current: ManifestPluginEntry[] = [];
const listeners = new Set<Listener>();

function emit(): void {
  for (const l of listeners) {
    l(current);
  }
}

export function onPluginsChanged(listener: Listener): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

export function getPlugins(): ManifestPluginEntry[] {
  return current;
}

export function getPlugin(code: string): ManifestPluginEntry | undefined {
  return current.find((p) => p.code === code);
}

export function setManifest(manifest: MobileManifest): void {
  current = manifest.plugins;
  emit();
}

export async function cacheManifest(domainId: string, manifest: MobileManifest): Promise<void> {
  await AsyncStorage.setItem(
    cacheKey(domainId),
    JSON.stringify({ ...manifest, fetchedAt: new Date().toISOString() }),
  );
}

/** 切域/启动早期调用：先用该域快照填充，保证离线/弱网时 UI 立即可渲染。 */
export async function restoreCachedManifest(domainId: string): Promise<boolean> {
  const raw = await AsyncStorage.getItem(cacheKey(domainId));
  if (!raw) {
    current = [];
    emit();
    return false;
  }
  try {
    current = (JSON.parse(raw) as MobileManifest).plugins;
    emit();
    return true;
  } catch {
    await AsyncStorage.removeItem(cacheKey(domainId));
    current = [];
    emit();
    return false;
  }
}
