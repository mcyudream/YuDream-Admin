/**
 * Re.Pack ScriptManager 接管：
 * 插件 remoteEntry 一律优先解析激活域的沙盒缓存（离线可用）；无缓存回源站。
 * 解析失败会在 pluginLoader 层触发回滚重试。
 *
 * 注意：locator.url 必须是纯字符串 —— Script.getRemoteURL 不带
 * {excludeExtension: true} 时返回 webpackContext => webpackContext.u(url)，
 * 而宿主单文件 bundle 没有 __webpack_require__.u，resolve 时直接抛
 * "webpackContext.u is not a function"。
 */
import { ScriptManager } from '@callstack/repack/client';
import { getActiveDomain } from '@/core/domains/store';
import { resolveAssetUrl } from '@/core/domains/assetUrl';
import { getPlugin, getPlugins } from './registry';
import { getLoadingPluginCode, remoteNameOf } from './pluginLoader';

function findPluginByScript(scriptId: string, caller?: string) {
  // remote 名已规范化（连字符转下划线），需要双向反查。
  const byId = (id: string) =>
    getPlugin(id) ?? getPlugins().find((p) => p.code.replace(/\W/g, '_') === id);
  return byId(scriptId) ?? (caller ? byId(caller) : undefined);
}

/** 该 scriptId 是否是某插件的 remoteEntry 入口（而非其异步 chunk）。 */
function isEntryScript(scriptId: string, code: string): boolean {
  return scriptId === code || scriptId === remoteNameOf(code);
}

/** chunk 回源文件名：优先用 remote 运行时给的文件名，数字 id 追加 .js。 */
function chunkFileName(scriptId: string, referenceUrl?: string): string {
  if (referenceUrl && /\.[\w]+$/.test(referenceUrl)) {
    return referenceUrl.replace(/^\/+/, '');
  }
  return /\.(js|mjs|bundle|png|jpg|json)$/i.test(scriptId) ? scriptId : `${scriptId}.js`;
}

export function installScriptManager(): void {
  ScriptManager.shared.addResolver(async (scriptId, caller, referenceUrl) => {
    const domain = getActiveDomain();
    const plugin =
      findPluginByScript(scriptId, caller) ?? (caller ? findPluginByScript(caller) : undefined);
    // remote 内部异步 chunk 以纯数字 chunkId 加载（无插件标识），
    // 依据 loadRemote 的加载作用域归属到对应应用。
    const scopedPlugin = plugin ?? (getLoadingPluginCode() ? getPlugin(getLoadingPluginCode()!) : undefined);
    if (!scopedPlugin || !domain?.serverUrl) {
      // 非插件脚本（如 dev server 的宿主 bundle 附属块）交给默认解析。
      return undefined;
    }
    if (!isEntryScript(scriptId, scopedPlugin.code)) {
      // 异步 chunk：v1 缓存目录只存 remoteEntry 本体，chunk 回源源站 remoteEntry 同目录。
      const base = scopedPlugin.remoteEntryUrl.replace(/[^/]*$/, '');
      const file = chunkFileName(scriptId, referenceUrl);
      return { url: resolveAssetUrl(domain.serverUrl, `${base}${file}`), cache: false };
    }
    // remoteEntry 一律回源并交由 ScriptManager 原生缓存（首次下载后离线可用）。
    // 不用 file:// 指向 bundleCache：该路径在 Hermes 上 eval 稳定复现 ScriptEvalFailure。
    return { url: resolveAssetUrl(domain.serverUrl, scopedPlugin.remoteEntryUrl), cache: true };
  });

  ScriptManager.shared.on('error', (event) => {
    // 加载失败的具体处理（回滚/重试）在 pluginLoader.loadPluginModule；
    // 这里只留日志钩子，避免静默失败。
    console.warn('[plugins] script error', event);
  });
}
