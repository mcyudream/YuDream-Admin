/**
 * 域（Domain）= 一个 YuDream 站点端点。与 YMCL-美西螈一致的模型：
 * 一个 App 可接入多个域，任一时刻有一个激活域；token、插件缓存、主题
 * 缓存全部按域隔离。
 */
import AsyncStorage from '@react-native-async-storage/async-storage';

export interface DomainAccount {
  userId: string;
  username: string;
  nickname: string;
  avatar: string | null;
  /** 管理员（/api/user/me 提供；决定「域管理」入口） */
  admin?: boolean;
}

/** 首页轮播图条目（mobile-app 能力配置 homeBanners）。 */
export interface DomainBanner {
  imageUrl: string;
  title: string;
  route: string;
}

/** 域品牌信息：来自站点设置（logo/favicon）与 mobile-app 能力配置（登录页/首页定制）。 */
export interface DomainBranding {
  /** 站点 logo（站点设置；相对路径或完整 URL） */
  logo?: string;
  /** 登录页 hero 背景图（能力配置） */
  loginHeroImage?: string;
  /** 登录页 hero 底色（能力配置，CSS 颜色值） */
  loginHeroBackground?: string;
  /** 首页轮播图（能力配置） */
  homeBanners?: DomainBanner[];
}

export interface Domain {
  id: string;
  /** 站点名（发现接口取回；发现失败时用主机名兜底），可在管理页改名 */
  name: string;
  /** 归一化 origin，如 https://mc.example.com */
  serverUrl: string;
  addedAt: number;
  lastActiveAt: number;
  /** 发现接口回填 */
  mobileEnabled: boolean;
  /** 最近一次登录的账户摘要（显示用；凭据在安全存储） */
  account: DomainAccount | null;
  /** 站点品牌信息（发现时与启动期后台刷新回填） */
  branding: DomainBranding;
}

const KEY_LIST = 'domains.list';
const KEY_ACTIVE = 'domains.activeId';

type Listener = () => void;

let domains: Domain[] = [];
let activeId: string | null = null;
const listeners = new Set<Listener>();

function emit(): void {
  for (const l of listeners) {
    l();
  }
}

export function subscribeDomains(listener: Listener): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

export function getDomains(): Domain[] {
  return domains;
}

export function getActiveDomain(): Domain | null {
  return domains.find((d) => d.id === activeId) ?? null;
}

export async function loadDomains(): Promise<void> {
  const [rawList, rawActive] = await Promise.all([
    AsyncStorage.getItem(KEY_LIST),
    AsyncStorage.getItem(KEY_ACTIVE),
  ]);
  domains = rawList ? (JSON.parse(rawList) as Domain[]) : [];
  activeId = rawActive;
  if (activeId && !domains.some((d) => d.id === activeId)) {
    activeId = null;
  }
  emit();
}

function persist(): Promise<void> {
  return AsyncStorage.setItem(KEY_LIST, JSON.stringify(domains));
}

export function normalizeServerUrl(input: string): string {
  let url = input.trim();
  if (!/^https?:\/\//i.test(url)) {
    url = `https://${url}`;
  }
  return url.replace(/\/+$/, '');
}

export function hostOf(serverUrl: string): string {
  try {
    return new URL(serverUrl).host;
  } catch {
    return serverUrl;
  }
}

function newId(): string {
  return `dm_${Date.now().toString(36)}_${Math.random().toString(36).slice(2, 8)}`;
}

export interface AddDomainInput {
  serverUrl: string;
  name: string;
  mobileEnabled: boolean;
  branding?: DomainBranding;
}

/** 幂等添加：同 serverUrl 视为同一个域，仅更新元数据。 */
export async function addDomain(input: AddDomainInput): Promise<Domain> {
  const existing = domains.find((d) => d.serverUrl === input.serverUrl);
  if (existing) {
    existing.name = input.name || existing.name;
    existing.mobileEnabled = input.mobileEnabled;
    existing.branding = { ...existing.branding, ...input.branding };
    await persist();
    emit();
    return existing;
  }
  const domain: Domain = {
    id: newId(),
    name: input.name || hostOf(input.serverUrl),
    serverUrl: input.serverUrl,
    addedAt: Date.now(),
    lastActiveAt: Date.now(),
    mobileEnabled: input.mobileEnabled,
    account: null,
    branding: input.branding ?? {},
  };
  domains = [...domains, domain];
  await persist();
  emit();
  return domain;
}

export async function removeDomain(id: string): Promise<void> {
  domains = domains.filter((d) => d.id !== id);
  if (activeId === id) {
    activeId = domains[0]?.id ?? null;
    if (activeId) {
      await AsyncStorage.setItem(KEY_ACTIVE, activeId);
    } else {
      await AsyncStorage.removeItem(KEY_ACTIVE);
    }
  }
  await persist();
  emit();
}

export async function setActiveDomain(id: string): Promise<void> {
  const domain = domains.find((d) => d.id === id);
  if (!domain) {
    return;
  }
  activeId = id;
  domain.lastActiveAt = Date.now();
  await AsyncStorage.setItem(KEY_ACTIVE, id);
  await persist();
  emit();
}

export async function updateDomainAccount(
  id: string,
  account: DomainAccount | null,
): Promise<void> {
  const domain = domains.find((d) => d.id === id);
  if (!domain) {
    return;
  }
  domain.account = account;
  await persist();
  emit();
}

export async function renameDomain(id: string, name: string): Promise<void> {
  const domain = domains.find((d) => d.id === id);
  if (!domain || !name.trim()) {
    return;
  }
  domain.name = name.trim();
  await persist();
  emit();
}

/** 启动期后台刷新品牌信息（站点 logo/登录页定制可能被管理员更新）。 */
export async function updateDomainBranding(id: string, branding: DomainBranding): Promise<void> {
  const domain = domains.find((d) => d.id === id);
  if (!domain) {
    return;
  }
  domain.branding = { ...domain.branding, ...branding };
  await persist();
  emit();
}
