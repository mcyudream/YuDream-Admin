/**
 * Re.Pack ScriptManager 接管：
 * 插件 remoteEntry 一律优先解析沙盒缓存（离线可用）；无缓存回源站。
 * 解析失败会在 pluginLoader 层触发回滚重试。
 */
import { Script, ScriptManager } from '@callstack/repack/client';
import { resolveLocalUrl } from './bundleCache';
import { getPlugin } from './registry';

function findPluginByScript(scriptId: string, caller?: string) {
  // MF2 下 scriptId 通常为 remote 名（= 插件 code）；caller 兜底
  // 处理 remoteEntry 二次拉取自身 chunk 的场景。
  return getPlugin(scriptId) ?? (caller ? getPlugin(caller) : undefined);
}

export function installScriptManager(): void {
  ScriptManager.shared.addResolver(async (scriptId, caller) => {
    const plugin = findPluginByScript(scriptId, caller);
    if (!plugin) {
      // 非插件脚本（如 dev server 的宿主 bundle 附属块）交给默认解析。
      return undefined;
    }
    const local = await resolveLocalUrl(plugin.code);
    if (local) {
      return { url: local, cache: false };
    }
    return { url: Script.getRemoteURL(plugin.remoteEntryUrl), cache: true };
  });

  ScriptManager.shared.on('error', (event) => {
    // 加载失败的具体处理（回滚/重试）在 pluginLoader.loadPluginModule；
    // 这里只留日志钩子，避免静默失败。
    console.warn('[plugins] script error', event);
  });
}
