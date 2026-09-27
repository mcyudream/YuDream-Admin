/**
 * 插件生命周期编排（作用于激活域）：启动检查更新并刷新缓存。
 *
 * 顺序：恢复快照（离线可渲染） -> 注册 remotes -> 后台拉 manifest
 * -> 下载变动插件（SHA 校验/原子翻转） -> 清理下架插件 -> 重新注册 remotes。
 *
 * 加载失败自动回滚到 last-known-good 并重试一次。
 */
import { init, loadRemote, registerRemotes } from '@module-federation/runtime';
import type { ComponentType } from 'react';
import type { MobilePluginModule } from '@yudream/plugin-sdk-mobile';
import { fetchManifest } from '@/core/manifest/manifestClient';
import type { ManifestPluginEntry } from '@/core/manifest/types';
import { getActiveDomain } from '@/core/domains/store';
import { cacheManifest, restoreCachedManifest } from './registry';
import { getInstalled, install, pruneToManifest, rollback } from './bundleCache';
import {
  getPlugins,
  setManifest,
} from './registry';

let federationReady = false;

export function initFederation(): void {
  if (federationReady) {
    return;
  }
  init({ name: 'host', remotes: [] });
  federationReady = true;
}

/** MF 容器名必须是合法 JS 标识符：code 中的连字符等转下划线（yudream-skin -> yudream_skin）。 */
export function remoteNameOf(code: string): string {
  return code.replace(/\W/g, '_');
}

function toRemotes(plugins: ManifestPluginEntry[]) {
  return plugins.map((p) => ({
    name: remoteNameOf(p.code),
    // 指向 manifest 给的远端地址；ScriptManager resolver 会优先改投本地缓存。
    entry: p.remoteEntryUrl,
  }));
}

async function reregisterRemotes(): Promise<void> {
  registerRemotes(toRemotes(getPlugins()), { force: true });
}

/** 启动/切域序列第一步：快照先行，保证离线启动立即可渲染。 */
export async function warmupFromCache(): Promise<void> {
  initFederation();
  const domain = getActiveDomain();
  if (!domain) {
    return;
  }
  await restoreCachedManifest(domain.id);
  await reregisterRemotes();
}

export interface SyncResult {
  updated: string[];
  failed: Record<string, string>;
}

/** 启动/下拉刷新（需登录态）：检查更新并刷新缓存。 */
export async function syncPlugins(): Promise<SyncResult> {
  const domain = getActiveDomain();
  if (!domain) {
    return { updated: [], failed: {} };
  }
  const manifest = await fetchManifest();
  setManifest(manifest);
  void cacheManifest(domain.id, manifest);

  const result: SyncResult = { updated: [], failed: {} };
  for (const entry of manifest.plugins) {
    const installed = await getInstalled(domain.id, entry.code);
    const upToDate =
      installed &&
      installed.version === entry.version &&
      installed.sha256 === entry.remoteEntrySha256;
    if (upToDate) {
      continue;
    }
    try {
      await install(domain.id, entry);
      result.updated.push(entry.code);
    } catch (e) {
      result.failed[entry.code] = e instanceof Error ? e.message : String(e);
    }
  }
  await pruneToManifest(domain.id, manifest.plugins.map((p) => p.code));
  await reregisterRemotes();
  return result;
}

/**
 * 渲染入口：加载插件导出的 React 组件。
 * 约定插件 exposes { './module': ... }，默认导出即页面组件。
 */
export async function loadPluginModule(
  code: string,
): Promise<ComponentType<Record<string, unknown>>> {
  initFederation();
  const domain = getActiveDomain();
  try {
    const mod = await loadRemote<MobilePluginModule>(`${remoteNameOf(code)}/module`);
    if (!mod?.default) {
      throw new Error('插件未导出默认组件');
    }
    return mod.default;
  } catch (first) {
    const rolledBack = domain ? await rollback(domain.id, code) : false;
    if (!rolledBack) {
      throw first;
    }
    console.warn(`[plugins] ${code} 加载失败，已回滚到上一可用版本`, first);
    const mod = await loadRemote<MobilePluginModule>(`${remoteNameOf(code)}/module`);
    if (!mod?.default) {
      throw first;
    }
    return mod.default;
  }
}
