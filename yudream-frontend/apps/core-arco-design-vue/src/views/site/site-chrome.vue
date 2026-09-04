<script setup lang="ts">
import type { CmsSiteLayoutMode } from '@/utils/cms-chrome'
import { chromeRuntimeCss, extractChromeCss, readChromeCss } from '@/utils/cms-chrome'
import { useSiteNavigation } from './site-navigation'

// 公开站共享页头/页脚：/site 页面与声明了 siteNav 的公开插件页共用。
// settings 为 CMS 首页设置（navigationJson/footer*/homeCss 中的历史 chrome 样式），缺省时退化为纯插件导航。
const props = withDefaults(defineProps<{
  settings?: Record<string, string> | null
  blank?: boolean
}>(), {
  settings: null,
  blank: false,
})

const appAccountStore = useAppAccountStore()
const appSettingsStore = useAppSettingsStore()

const { navigationTree, footerNavigationItems } = useSiteNavigation(() => props.settings?.navigationJson)

const siteLayout = computed<CmsSiteLayoutMode>(() => (props.settings?.siteLayout as CmsSiteLayoutMode) || 'HEADER_FOOTER')
const showFooter = computed(() => siteLayout.value === 'HEADER_FOOTER')
const showCopyright = computed(() => siteLayout.value === 'HEADER_COPYRIGHT' || siteLayout.value === 'ADMIN')
const chromeCustomCss = computed(() => [
  readChromeCss(props.settings ?? undefined, 'header'),
  readChromeCss(props.settings ?? undefined, 'footer'),
  // 兼容 Grapes 曾把 Header/Footer 样式合并保存进 homeCss 的历史数据。
  extractChromeCss(props.settings?.homeCss || ''),
].filter(Boolean).join('\n'))
const siteRuntimeCss = computed(() => chromeRuntimeCss(siteLayout.value, chromeCustomCss.value))
const footerTitle = computed(() => props.settings?.footerTitle || appSettingsStore.siteName || '')
const footerDescription = computed(() => props.settings?.footerDescription || appSettingsStore.siteDescription || (appSettingsStore.siteName ? `由 ${appSettingsStore.siteName} 驱动的内容站点` : ''))
const footerCopyright = computed(() => props.settings?.footerCopyright || `© ${new Date().getFullYear()} ${appSettingsStore.siteName}. All rights reserved.`)
</script>

<template>
  <div class="site-chrome">
    <component :is="'style'">
      {{ siteRuntimeCss }}
    </component>
    <header v-if="!blank" data-yb-chrome="header" class="site-layout-header">
      <div class="site-layout-header__bar">
        <a data-yb-chrome-slot="logo" class="site-layout-header__brand" href="/site">
          <img v-if="appSettingsStore.logo" :src="appSettingsStore.logo" :alt="appSettingsStore.siteName">
          <span>{{ appSettingsStore.siteName }}</span>
        </a>
        <nav data-yb-chrome-slot="navigation" class="site-layout-header__nav">
          <div v-for="item in navigationTree" :key="item.id || item.url" class="site-nav-item" :class="{ 'has-children': item.children?.length }">
            <a :href="item.url">
              {{ item.label }}
              <span v-if="item.children?.length">⌄</span>
            </a>
            <div v-if="item.children?.length" class="site-nav-dropdown">
              <a v-for="child in item.children" :key="child.id || child.url" :href="child.url">{{ child.label }}</a>
            </div>
          </div>
        </nav>
        <div data-yb-chrome-slot="auth" class="site-layout-header__auth">
          <div v-if="!appAccountStore.isLogin" data-visible-when="guest">
            <a href="/login" class="ghost">登录</a>
            <a href="/register" class="primary">注册</a>
          </div>
          <details v-else data-visible-when="logged-in" class="site-layout-header__account">
            <summary class="ghost site-layout-header__action">
              <img v-if="appAccountStore.avatar" :src="appAccountStore.avatar" :alt="appAccountStore.account">
              <span>{{ appAccountStore.account }}</span>
              <i>⌄</i>
            </summary>
            <div>
              <a href="/">控制台</a>
              <a href="/profile">个人资料</a>
              <a href="/logout" class="danger">退出登录</a>
            </div>
          </details>
        </div>
      </div>
    </header>

    <div class="site-chrome__body">
      <slot />
    </div>

    <footer v-if="showFooter && !blank" data-yb-chrome="footer" class="site-layout-footer">
      <div class="site-shell">
        <div data-yb-chrome-slot="footer-brand">
          <strong>{{ footerTitle }}</strong>
          <p>{{ footerDescription }}</p>
          <small>{{ footerCopyright }}</small>
        </div>
        <nav data-yb-chrome-slot="footer-navigation">
          <a v-for="item in footerNavigationItems" :key="`foot-${item.id || item.url}`" :href="item.url">{{ item.label }}</a>
        </nav>
      </div>
    </footer>
    <footer v-else-if="showCopyright && !blank" data-yb-chrome="footer" class="site-layout-copyright">
      {{ footerCopyright }}
    </footer>
  </div>
</template>

<style scoped>
/* 与 /site 页面保持同一套站点主题变量；嵌套在 .site-page 内时取值一致，独立用于插件公开页时自带主题。 */
/* 注意：不能包 @layer —— Tailwind preflight 的 *{margin:0;padding:0} 等非分层规则会压过所有分层规则 */
.site-chrome {
  --yb-site-bg: #f8fafc;
  --yb-site-text: #111827;
  --yb-site-heading: #0f172a;
  --yb-site-muted: #64748b;
  --yb-site-caption: #94a3b8;
  --yb-site-nav-text: #475569;
  --yb-site-text-2: #334155;
  --yb-site-border: #e5e7eb;
  --yb-site-border-2: #e2e8f0;
  --yb-site-header-bg: rgba(255, 255, 255, 0.94);
  --yb-site-surface: #ffffff;
  --yb-site-hover: #f1f5f9;
  --yb-site-primary: #0f766e;
  --yb-site-primary-text: #ffffff;
  --yb-site-primary-btn-bg: #111827;
  --yb-site-primary-btn-text: #ffffff;
  --yb-site-hero-bg: linear-gradient(135deg, #0f766e, #1f2937);
  --yb-site-hero-text: #ffffff;
  --yb-site-danger: #b91c1c;

  display: flex;
  flex-direction: column;
  min-height: 100vh;
  background: var(--yb-site-bg);
  color: var(--yb-site-text);
}

.site-chrome.dark,
.dark .site-chrome {
  --yb-site-bg: #0f172a;
  --yb-site-text: #e2e8f0;
  --yb-site-heading: #f8fafc;
  --yb-site-muted: #94a3b8;
  --yb-site-caption: #64748b;
  --yb-site-nav-text: #cbd5e1;
  --yb-site-text-2: #e2e8f0;
  --yb-site-border: #1e293b;
  --yb-site-border-2: #334155;
  --yb-site-header-bg: rgba(15, 23, 42, 0.86);
  --yb-site-surface: #1e293b;
  --yb-site-hover: #334155;
  --yb-site-primary: #2dd4bf;
  --yb-site-primary-text: #0f172a;
  --yb-site-primary-btn-bg: #2dd4bf;
  --yb-site-primary-btn-text: #0f172a;
  --yb-site-hero-bg: linear-gradient(135deg, #115e59, #111827);
  --yb-site-hero-text: #f8fafc;
}

.site-shell {
  width: min(1120px, calc(100% - 32px));
  margin: 0 auto;
}

.site-chrome__body {
  display: flex;
  flex: 1 1 auto;
  flex-direction: column;
  min-width: 0;
}

.site-chrome__body > :deep(*) {
  flex: 1 1 auto;
}

.site-layout-header {
  position: sticky;
  top: 0;
  z-index: 1000;
  border-bottom: 1px solid var(--yb-site-border);
  background: var(--yb-site-header-bg);
  backdrop-filter: blur(12px);
  isolation: isolate;
}

.site-layout-header__bar {
  display: flex;
  width: min(1240px, calc(100% - 40px));
  min-height: 62px;
  margin: 0 auto;
  gap: 22px;
  align-items: center;
}

.site-layout-header__brand,
.site-layout-header__nav,
.site-layout-header__auth,
.site-layout-header__account summary {
  display: flex;
  align-items: center;
}

.site-layout-header__brand {
  min-width: 0;
  gap: 10px;
  color: var(--yb-site-heading);
  font-size: 18px;
  font-weight: 900;
  text-decoration: none;
}

.site-layout-header__brand img {
  width: 30px;
  height: 30px;
  border-radius: 8px;
  object-fit: cover;
}

.site-layout-header__nav {
  flex: 1 1 auto;
  justify-content: flex-start;
  gap: 4px;
  min-width: 0;
}

.site-nav-item {
  position: relative;
}

.site-layout-header__nav a,
.site-nav-item > a {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 8px 9px;
  border-radius: 7px;
  color: var(--yb-site-nav-text);
  font-size: 14px;
  font-weight: 650;
  text-decoration: none;
}

.site-layout-header__nav a:hover,
.site-nav-item:hover > a {
  background: var(--yb-site-hover);
  color: var(--yb-site-heading);
}

.site-nav-dropdown {
  position: absolute;
  top: 100%;
  left: -8px;
  z-index: 20;
  display: none;
  min-width: 168px;
  padding: 14px 8px 8px;
  border-radius: 10px;
  isolation: isolate;
}

.site-nav-dropdown::before {
  position: absolute;
  inset: 8px 0 0;
  z-index: -1;
  border: 1px solid var(--yb-site-border);
  border-radius: 8px;
  background: var(--yb-site-surface);
  box-shadow: 0 18px 42px rgba(15, 23, 42, 0.12);
  content: "";
}

.site-nav-item:hover .site-nav-dropdown,
.site-nav-item:focus-within .site-nav-dropdown {
  display: grid;
  gap: 2px;
}

.site-nav-dropdown a {
  position: relative;
  display: flex;
  white-space: nowrap;
}

.site-layout-header__auth {
  gap: 8px;
}

.site-layout-header__auth > div[data-visible-when="guest"] {
  display: flex;
  gap: 8px;
}

.site-layout-header__auth a,
.site-layout-header__account summary {
  min-height: 34px;
  padding: 0 12px;
  border-radius: 7px;
  font-size: 14px;
  font-weight: 750;
  line-height: 1;
  text-decoration: none;
}

.site-layout-header__auth a {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 34px;
}

.site-layout-header__auth .ghost,
.site-layout-header__account summary {
  background: var(--yb-site-surface);
  color: var(--yb-site-text-2);
}

.site-layout-header__auth .ghost {
  border: 1px solid var(--yb-site-border-2);
}

.site-layout-header__auth .primary {
  background: var(--yb-site-primary-btn-bg);
  color: var(--yb-site-primary-btn-text);
}

.site-layout-header__account {
  position: relative;
}

.site-layout-header__account summary {
  gap: 7px;
  border: 0;
  list-style: none;
  cursor: pointer;
  outline: none;
  transition: background-color 0.18s ease, box-shadow 0.18s ease;
}

.site-layout-header__account summary::-webkit-details-marker {
  display: none;
}

.site-layout-header__account summary:hover,
.site-layout-header__account[open] summary {
  background: var(--yb-site-bg);
}

.site-layout-header__account summary:focus-visible {
  box-shadow: 0 0 0 3px rgba(148, 163, 184, 0.18);
}

.site-layout-header__account img {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  object-fit: cover;
}

.site-layout-header__account i {
  color: var(--yb-site-caption);
  font-size: 12px;
  font-style: normal;
}

.site-layout-header__account > div {
  position: absolute;
  top: calc(100% + 8px);
  right: 0;
  display: grid;
  min-width: 142px;
  padding: 7px;
  border: 1px solid var(--yb-site-border);
  border-radius: 8px;
  background: var(--yb-site-surface);
  box-shadow: 0 16px 36px rgba(15, 23, 42, 0.14);
}

.site-layout-header__account a {
  padding: 9px 10px;
  border-radius: 8px;
  color: var(--yb-site-text-2);
  text-decoration: none;
}

.site-layout-header__account a:hover {
  background: var(--yb-site-hover);
}

.site-layout-header__account a.danger {
  color: var(--yb-site-danger);
}

.site-layout-footer {
  position: relative;
  z-index: 1;
  padding: 36px 0;
  border-top: 1px solid var(--yb-site-border);
  background: var(--yb-site-surface);
  color: var(--yb-site-heading);
}

.site-layout-footer .site-shell {
  display: flex;
  gap: 18px;
  align-items: flex-start;
  justify-content: space-between;
}

.site-layout-footer strong {
  font-size: 20px;
}

.site-layout-footer p {
  margin: 8px 0 0;
  color: var(--yb-site-muted);
}

.site-layout-footer small {
  display: block;
  margin-top: 12px;
  color: var(--yb-site-caption);
}

.site-layout-footer nav {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.site-layout-footer a {
  color: var(--yb-site-nav-text);
  text-decoration: none;
}

.site-layout-footer a:hover {
  color: var(--yb-site-primary);
}

.site-layout-copyright {
  padding: 18px;
  border-top: 1px solid var(--yb-site-border);
  background: var(--yb-site-surface);
  color: var(--yb-site-muted);
  text-align: center;
}

@media (max-width: 760px) {
  .site-layout-header {
    position: sticky;
    top: 0;
    z-index: 1000;
  }

  .site-layout-header__bar {
    align-items: stretch;
    flex-direction: column;
    width: calc(100% - 28px);
    min-height: 0;
    padding: 12px 0;
    gap: 10px;
  }

  .site-layout-header__nav {
    justify-content: flex-start;
    flex-wrap: wrap;
  }

  .site-layout-footer .site-shell {
    flex-direction: column;
  }

  .site-layout-footer nav {
    justify-content: flex-start;
  }

  .site-shell {
    width: calc(100% - 24px);
  }
}
</style>
