/**
 * 插件注册表：当前生效的 manifest + 加载状态。
 * manifest 拉取失败时用 AsyncStorage 里的上次成功快照（离线启动的核心）。
 */
import AsyncStorage from '@react-native-async-storage/async-storage';
import type { ManifestPluginEntry, MobileManifest } from '@/core/manifest/types';

const KEY_CACHED_MANIFEST = 'manifest.cached';

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
  void AsyncStorage.setItem(
    KEY_CACHED_MANIFEST,
    JSON.stringify({ ...manifest, fetchedAt: new Date().toISOString() }),
  );
}

/** 启动早期调用：先用快照填充，保证离线/弱网时 UI 立即可渲染。 */
export async function restoreCachedManifest(): Promise<boolean> {
  const raw = await AsyncStorage.getItem(KEY_CACHED_MANIFEST);
  if (!raw) {
    return false;
  }
  try {
    current = (JSON.parse(raw) as MobileManifest).plugins;
    emit();
    return true;
  } catch {
    await AsyncStorage.removeItem(KEY_CACHED_MANIFEST);
    return false;
  }
}
