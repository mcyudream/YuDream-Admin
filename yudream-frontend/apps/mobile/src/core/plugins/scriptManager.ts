/**
 * Re.Pack ScriptManager 接管：
 * 插件 remoteEntry 一律优先解析激活域的沙盒缓存（离线可用）；无缓存回源站。
 * 解析失败会在 pluginLoader 层触发回滚重试。
 */
import { Script, ScriptManager } from '@callstack/repack/client';
import { getActiveDomain } from '@/core/domains/store';
import { resolveLocalUrl } from './bundleCache';
import { getPlugin, getPlugins } from './registry';

function findPluginByScript(scriptId: string, caller?: string) {
  // remote 名已规范化（连字符转下划线），需要双向反查。
  const byId = (id: string) =>
    getPlugin(id) ?? getPlugins().find((p) => p.code.replace(/\W/g, '_') === id);
  return byId(scriptId) ?? (caller ? byId(caller) : undefined);
}

export function installScriptManager(): void {
  ScriptManager.shared.addResolver(async (scriptId, caller) => {
    const domain = getActiveDomain();
    const plugin = findPluginByScript(scriptId, caller) ?? (caller ? findPluginByScript(caller) : undefined);
    if (!plugin || !domain) {
      // 非插件脚本（如 dev server 的宿主 bundle 附属块）交给默认解析。
      return undefined;
    }
    // MF shared 异步 chunk：scriptId 为 chunk 文件名（如 845），caller 为 remote 名。
    // v1 缓存目录只存 remoteEntry 本体，chunk 回源源站同目录。
    if (scriptId !== plugin.code && /^[\w.-]+?(\.js)?$/.test(scriptId)) {
      const base = plugin.remoteEntryUrl.replace(/[^/]*$/, '');
      return { url: Script.getRemoteURL(`${base}${scriptId}.js`), cache: false };
    }
    const local = await resolveLocalUrl(domain.id, plugin.code);
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
