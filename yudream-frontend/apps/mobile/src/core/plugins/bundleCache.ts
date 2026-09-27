/**
 * 插件 bundle 的沙盒缓存（按域隔离）：
 *
 *   {DocumentDir}/plugins/{domainId}/
 *     index.json                        // code -> 当前指针 + 上一可用版本（回滚位）
 *     {code}/{version}/remoteEntry.js
 *
 * 写入纪律：下载到临时文件 -> SHA-256 校验 -> 原子翻转指针（先写 index.tmp 再改名）。
 * 每个插件保留最近两个版本；翻转后旧目录保留为 last-known-good，再旧者清理。
 *
 * v1 假设每个插件产物为单文件 remoteEntry（构建侧关 chunk 拆分）；
 * 若后续开启代码分割，此处需要扩展为整目录校验与翻转。
 */
import RNFS from 'react-native-fs';
import CryptoJS from 'crypto-js';
import { bridges } from '@/bridges';
import type { ManifestPluginEntry } from '@/core/manifest/types';
import { getDomains } from '@/core/domains/store';
import { resolveAssetUrl } from '@/core/domains/assetUrl';

const rootOf = (domainId: string) => `${RNFS.DocumentDirectoryPath}/plugins/${domainId}`;

export interface InstalledBundle {
  version: string;
  assetRevision: string;
  sha256: string;
  /** 相对域缓存根的路径 */
  relPath: string;
}

interface CacheIndex {
  plugins: Record<
    string,
    {
      current: InstalledBundle;
      previous?: InstalledBundle;
    }
  >;
}

async function readIndex(domainId: string): Promise<CacheIndex> {
  try {
    const raw = await RNFS.readFile(`${rootOf(domainId)}/index.json`, 'utf8');
    return JSON.parse(raw) as CacheIndex;
  } catch {
    return { plugins: {} };
  }
}

async function writeIndex(domainId: string, index: CacheIndex): Promise<void> {
  const indexFile = `${rootOf(domainId)}/index.json`;
  const tmp = `${indexFile}.tmp`;
  await RNFS.writeFile(tmp, JSON.stringify(index), 'utf8');
  if (await RNFS.exists(indexFile)) {
    await RNFS.unlink(indexFile);
  }
  await RNFS.moveFile(tmp, indexFile);
}

async function sha256OfFile(path: string): Promise<string> {
  const base64 = await RNFS.readFile(path, 'base64');
  return CryptoJS.SHA256(CryptoJS.enc.Base64.parse(base64)).toString(CryptoJS.enc.Hex);
}

export async function getInstalled(domainId: string, code: string): Promise<InstalledBundle | null> {
  const index = await readIndex(domainId);
  const entry = index.plugins[code];
  if (!entry) {
    return null;
  }
  if (await RNFS.exists(`${rootOf(domainId)}/${entry.current.relPath}`)) {
    return entry.current;
  }
  // 当前指针指向的产物已损坏/丢失：自动回落上一可用版本。
  if (entry.previous && (await RNFS.exists(`${rootOf(domainId)}/${entry.previous.relPath}`))) {
    index.plugins[code] = { current: entry.previous };
    await writeIndex(domainId, index);
    return entry.previous;
  }
  delete index.plugins[code];
  await writeIndex(domainId, index);
  return null;
}

/**
 * 下载并安装一个插件版本。SHA 校验失败或不完整写入都不会污染当前指针。
 * 同版本同 SHA 直接跳过（幂等）。
 */
export async function install(domainId: string, entry: ManifestPluginEntry): Promise<InstalledBundle> {
  const index = await readIndex(domainId);
  const root = rootOf(domainId);
  const existing = index.plugins[entry.code]?.current;
  if (existing && existing.version === entry.version && existing.sha256 === entry.remoteEntrySha256) {
    return existing;
  }

  const relPath = `${entry.code}/${entry.version}/remoteEntry.js`;
  const dest = `${root}/${relPath}`;
  const tmp = `${dest}.download`;
  await RNFS.mkdir(`${root}/${entry.code}/${entry.version}`);
  if (await RNFS.exists(tmp)) {
    await RNFS.unlink(tmp);
  }

  // manifest 的 remoteEntryUrl 是站内相对路径，须经激活域 origin 拼接。
  const domain = getDomains().find((d) => d.id === domainId);
  const sourceUrl = resolveAssetUrl(domain?.serverUrl ?? '', entry.remoteEntryUrl);
  if (!/^https?:\/\//i.test(sourceUrl)) {
    throw new Error(`插件 ${entry.code} 下载地址无效（缺域 origin）`);
  }
  await bridges.download.download(sourceUrl, tmp).promise;
  const actual = await sha256OfFile(tmp);
  if (actual.toLowerCase() !== entry.remoteEntrySha256.toLowerCase()) {
    await RNFS.unlink(tmp);
    throw new Error(`插件 ${entry.code}@${entry.version} 完整性校验失败（SHA-256 不匹配）`);
  }
  if (await RNFS.exists(dest)) {
    await RNFS.unlink(dest);
  }
  await RNFS.moveFile(tmp, dest);

  const installed: InstalledBundle = {
    version: entry.version,
    assetRevision: entry.assetRevision,
    sha256: entry.remoteEntrySha256,
    relPath,
  };
  index.plugins[entry.code] = {
    current: installed,
    previous:
      existing && existing.relPath !== relPath ? existing : index.plugins[entry.code]?.previous,
  };
  await writeIndex(domainId, index);
  await pruneStaleVersions(domainId, entry.code, index.plugins[entry.code]!);
  return installed;
}

/** 回滚到上一可用版本；无回滚位返回 false。 */
export async function rollback(domainId: string, code: string): Promise<boolean> {
  const index = await readIndex(domainId);
  const entry = index.plugins[code];
  if (!entry?.previous || !(await RNFS.exists(`${rootOf(domainId)}/${entry.previous.relPath}`))) {
    return false;
  }
  index.plugins[code] = { current: entry.previous };
  await writeIndex(domainId, index);
  return true;
}

/** 删除整个域时清空其插件缓存。 */
export async function removeAllForDomain(domainId: string): Promise<void> {
  const dir = rootOf(domainId);
  if (await RNFS.exists(dir)) {
    await RNFS.unlink(dir);
  }
}

/** manifest 中已消失的插件（被禁用/卸载）应清出缓存。 */
export async function pruneToManifest(domainId: string, codes: string[]): Promise<void> {
  const index = await readIndex(domainId);
  const keep = new Set(codes);
  for (const code of Object.keys(index.plugins)) {
    if (!keep.has(code)) {
      delete index.plugins[code];
      const dir = `${rootOf(domainId)}/${code}`;
      if (await RNFS.exists(dir)) {
        await RNFS.unlink(dir);
      }
    }
  }
  await writeIndex(domainId, index);
}

async function pruneStaleVersions(
  domainId: string,
  code: string,
  entry: { current: InstalledBundle; previous?: InstalledBundle },
): Promise<void> {
  const keep = new Set([entry.current.relPath, entry.previous?.relPath]);
  const dir = `${rootOf(domainId)}/${code}`;
  if (!(await RNFS.exists(dir))) {
    return;
  }
  for (const item of await RNFS.readDir(dir)) {
    if (!item.isDirectory()) {
      continue;
    }
    if (!keep.has(`${code}/${item.name}/remoteEntry.js`)) {
      await RNFS.unlink(item.path);
    }
  }
}

/** 供 ScriptManager 解析的 file:// URL；返回 null 表示无本地缓存。 */
export async function resolveLocalUrl(domainId: string, code: string): Promise<string | null> {
  const installed = await getInstalled(domainId, code);
  if (!installed) {
    return null;
  }
  const path = `${rootOf(domainId)}/${installed.relPath}`;
  return path.startsWith('file://') ? path : `file://${path}`;
}
