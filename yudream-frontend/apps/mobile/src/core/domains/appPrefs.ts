/**
 * 每域「应用」偏好：可见性与排序（域管理页维护，首页/应用页消费）。
 * 仅存本地（AsyncStorage 按域隔离）；新装应用默认可见，排在末尾。
 */
import AsyncStorage from '@react-native-async-storage/async-storage';

export interface DomainAppPrefs {
  /** 被隐藏（不在主页/快捷区展示）的应用 code */
  hidden: string[];
  /** 展示顺序（应用 code）；未列出的按 manifest 顺序追加 */
  order: string[];
}

const EMPTY: DomainAppPrefs = { hidden: [], order: [] };

type Listener = () => void;

const listeners = new Set<Listener>();
const cache = new Map<string, DomainAppPrefs>();

function keyOf(domainId: string): string {
  return `apps.prefs.${domainId}`;
}

function emit(): void {
  for (const l of listeners) {
    l();
  }
}

export function subscribeAppPrefs(listener: Listener): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

export async function loadAppPrefs(domainId: string): Promise<DomainAppPrefs> {
  const raw = await AsyncStorage.getItem(keyOf(domainId));
  const prefs: DomainAppPrefs = raw
    ? { ...EMPTY, ...(JSON.parse(raw) as Partial<DomainAppPrefs>) }
    : { ...EMPTY };
  cache.set(domainId, prefs);
  return prefs;
}

export function getAppPrefs(domainId: string): DomainAppPrefs {
  return cache.get(domainId) ?? EMPTY;
}

async function save(domainId: string, prefs: DomainAppPrefs): Promise<void> {
  cache.set(domainId, prefs);
  await AsyncStorage.setItem(keyOf(domainId), JSON.stringify(prefs));
  emit();
}

export async function setAppHidden(domainId: string, code: string, hidden: boolean): Promise<void> {
  const prefs = await loadAppPrefs(domainId);
  const next = hidden
    ? [...new Set([...prefs.hidden, code])]
    : prefs.hidden.filter((c) => c !== code);
  await save(domainId, { ...prefs, hidden: next });
}

export async function moveApp(domainId: string, code: string, dir: -1 | 1): Promise<void> {
  const prefs = await loadAppPrefs(domainId);
  const order = [...prefs.order];
  const i = order.indexOf(code);
  if (i < 0) {
    return;
  }
  const j = i + dir;
  if (j < 0 || j >= order.length) {
    return;
  }
  const a = order[i];
  const b = order[j];
  if (a === undefined || b === undefined) {
    return;
  }
  order[i] = b;
  order[j] = a;
  await save(domainId, { ...prefs, order });
}

/** 应用 manifest 顺序与本地偏好，产出「可见且有序」的应用 code 列表。 */
export function applyAppPrefs(
  codes: string[],
  prefs: DomainAppPrefs,
): string[] {
  const ordered = [
    ...prefs.order.filter((c) => codes.includes(c)),
    ...codes.filter((c) => !prefs.order.includes(c)),
  ];
  return ordered.filter((c) => !prefs.hidden.includes(c));
}
