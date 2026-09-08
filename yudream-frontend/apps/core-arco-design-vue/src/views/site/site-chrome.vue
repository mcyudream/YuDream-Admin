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
const mobileNavOpen = ref(false)
const headerBarRef = ref<HTMLElement | null>(null)
const mobilePanelRef = ref<HTMLElement | null>(null)
let mobileQuery: MediaQueryList | undefined
let touchStartY = 0
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

function closeMobileNav() {
  mobileNavOpen.value = false
}

function syncMobileNavOffset() {
  const header = headerBarRef.value?.closest('.site-layout-header') as HTMLElement | null
  const bottom = (header || headerBarRef.value)?.getBoundingClientRect().bottom
  const top = Math.max(0, Math.round(bottom || 56))
  document.documentElement.style.setProperty('--site-mobile-nav-top', `${top}px`)
}

function isInsideMobilePanel(target: EventTarget | null) {
  const panel = mobilePanelRef.value
  return Boolean(panel && target instanceof Node && panel.contains(target))
}

function shouldBlockScroll(event: TouchEvent | WheelEvent) {
  if (!mobileNavOpen.value) return false
  const panel = mobilePanelRef.value
  if (!panel || !isInsideMobilePanel(event.target)) return true
  const atTop = panel.scrollTop <= 0
  const atBottom = panel.scrollTop + panel.clientHeight >= panel.scrollHeight - 1
  if (event instanceof WheelEvent) {
    return (event.deltaY < 0 && atTop) || (event.deltaY > 0 && atBottom)
  }
  const currentY = event.touches[0]?.clientY ?? touchStartY
  return (currentY > touchStartY && atTop) || (currentY < touchStartY && atBottom)
}

function onTouchStart(event: TouchEvent) {
  touchStartY = event.touches[0]?.clientY ?? 0
}

function onLockScroll(event: TouchEvent | WheelEvent) {
  if (shouldBlockScroll(event)) event.preventDefault()
}

function lockPageScroll(lock: boolean) {
  const { documentElement, body } = document
  if (lock) {
    syncMobileNavOffset()
    documentElement.classList.add('is-site-mobile-nav-open')
    window.addEventListener('touchstart', onTouchStart, { passive: true })
    window.addEventListener('touchmove', onLockScroll, { passive: false })
    window.addEventListener('wheel', onLockScroll, { passive: false })
  }
  else {
    documentElement.classList.remove('is-site-mobile-nav-open')
    documentElement.style.removeProperty('--site-mobile-nav-top')
    window.removeEventListener('touchstart', onTouchStart)
    window.removeEventListener('touchmove', onLockScroll)
    window.removeEventListener('wheel', onLockScroll)
    body.style.overflow = ''
    documentElement.style.overflow = ''
  }
}

function onEscape(event: KeyboardEvent) {
  if (event.key === 'Escape') closeMobileNav()
}

function onMobileQueryChange() {
  if (!mobileQuery?.matches) closeMobileNav()
}

watch(mobileNavOpen, async (open) => {
  lockPageScroll(open)
  if (open) {
    await nextTick()
    syncMobileNavOffset()
  }
})

onMounted(() => {
  mobileQuery = window.matchMedia('(max-width: 760px)')
  mobileQuery.addEventListener('change', onMobileQueryChange)
  window.addEventListener('keydown', onEscape)
  window.addEventListener('resize', syncMobileNavOffset)
  window.visualViewport?.addEventListener('resize', syncMobileNavOffset)
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', onEscape)
  window.removeEventListener('resize', syncMobileNavOffset)
  window.visualViewport?.removeEventListener('resize', syncMobileNavOffset)
  mobileQuery?.removeEventListener('change', onMobileQueryChange)
  if (mobileNavOpen.value) lockPageScroll(false)
})
</script>

<template>
  <div class="site-chrome">
    <component :is="'style'">
      {{ siteRuntimeCss }}
    </component>
    <header v-if="!blank" data-yb-chrome="header" class="site-layout-header" :class="{ 'is-open': mobileNavOpen }">
      <div ref="headerBarRef" class="site-layout-header__bar">
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
        <button
          type="button"
          class="site-layout-header__menu-toggle"
          :class="{ 'is-open': mobileNavOpen }"
          :aria-expanded="mobileNavOpen"
          :aria-label="mobileNavOpen ? '关闭导航菜单' : '打开导航菜单'"
          @click="mobileNavOpen = !mobileNavOpen"
        >
          <span class="site-layout-header__menu-toggle-line" />
          <span class="site-layout-header__menu-toggle-line" />
          <span class="site-layout-header__menu-toggle-line" />
        </button>
      </div>
    </header>
    <Teleport to="body">
      <Transition name="site-mobile">
        <div
          v-show="mobileNavOpen"
          ref="mobilePanelRef"
          class="site-layout-header__mobile"
          role="dialog"
          aria-modal="true"
          aria-label="站点导航"
        >
          <nav class="site-mobile-nav" aria-label="站点导航">
            <div v-for="item in navigationTree" :key="`m-${item.id || item.url}`" class="site-mobile-nav__group">
              <a :href="item.url" class="site-mobile-nav__link">{{ item.label }}</a>
              <a v-for="child in item.children || []" :key="`m-${child.id || child.url}`" :href="child.url" class="site-mobile-nav__link site-mobile-nav__link--child">{{ child.label }}</a>
            </div>
          </nav>
          <div class="site-mobile-auth">
            <template v-if="!appAccountStore.isLogin">
              <a href="/login" class="ghost">登录</a>
              <a href="/register" class="primary">注册</a>
            </template>
            <template v-else>
              <div class="site-mobile-auth__account">
                <img v-if="appAccountStore.avatar" :src="appAccountStore.avatar" :alt="appAccountStore.account">
                <span>{{ appAccountStore.account }}</span>
              </div>
              <a href="/">控制台</a>
              <a href="/profile">个人资料</a>
              <a href="/logout" class="danger">退出登录</a>
            </template>
          </div>
        </div>
      </Transition>
    </Teleport>

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

.site-layout-header__menu-toggle {
  display: none;
  width: 36px;
  height: 36px;
  margin-left: auto;
  padding: 0;
  border: 1px solid var(--yb-site-border-2);
  border-radius: 7px;
  background: var(--yb-site-surface);
  color: var(--yb-site-text-2);
  cursor: pointer;
  flex-direction: column;
  gap: 5px;
  align-items: center;
  justify-content: center;
  transition: background 0.2s ease, border-color 0.2s ease, transform 0.15s ease;
}

.site-layout-header__menu-toggle:hover {
  background: var(--yb-site-hover);
  border-color: var(--yb-site-text-3);
}

.site-layout-header__menu-toggle:active {
  transform: scale(0.92);
}

.site-layout-header__menu-toggle-line {
  display: block;
  width: 16px;
  height: 2px;
  border-radius: 1px;
  background: currentcolor;
  transition: transform 0.25s ease, opacity 0.2s ease;
}

.site-layout-header__menu-toggle.is-open .site-layout-header__menu-toggle-line:nth-child(1) {
  transform: translateY(7px) rotate(45deg);
}

.site-layout-header__menu-toggle.is-open .site-layout-header__menu-toggle-line:nth-child(2) {
  opacity: 0;
}

.site-layout-header__menu-toggle.is-open .site-layout-header__menu-toggle-line:nth-child(3) {
  transform: translateY(-7px) rotate(-45deg);
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
    width: calc(100% - 28px) !important;
    min-height: 56px !important;
    padding: 8px 0 !important;
    gap: 12px;
    align-items: center;
    flex-direction: row !important;
    flex-wrap: nowrap !important;
  }

  .site-layout-header__brand {
    font-size: 16px;
  }

  /* 历史 CMS chrome 自定义样式（Grapes 导出的 main.site-page 前缀规则）会强制导航铺开，移动端折叠必须压过它们 */
  .site-layout-header__nav,
  .site-layout-header__auth {
    display: none !important;
  }

  .site-layout-header__menu-toggle {
    display: inline-flex !important;
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

<style>
:root {
  --site-mobile-nav-top: 56px;
}

.site-layout-header__mobile {
  box-sizing: border-box;
  padding: 12px 16px max(20px, env(safe-area-inset-bottom));
  overflow-x: hidden;
  overflow-y: auto;
  overscroll-behavior: contain;
  -webkit-overflow-scrolling: touch;
  border-bottom: 1px solid var(--yb-site-border, #e5e7eb);
  background: var(--yb-site-surface, #ffffff);
}

.site-mobile-nav {
  display: grid;
  gap: 2px;
}

.site-mobile-nav__group {
  display: grid;
  gap: 2px;
}

.site-mobile-nav__group + .site-mobile-nav__group {
  margin-top: 6px;
  padding-top: 6px;
  border-top: 1px solid var(--yb-site-border, #e5e7eb);
}

.site-mobile-nav__link {
  display: flex;
  padding: 10px 12px;
  align-items: center;
  border-radius: 8px;
  color: var(--yb-site-text-2, #334155);
  font-size: 15px;
  font-weight: 650;
  text-decoration: none;
}

.site-mobile-nav__link:hover {
  background: var(--yb-site-hover, #f1f5f9);
  color: var(--yb-site-heading, #0f172a);
}

.site-mobile-nav__link--child {
  padding-left: 28px;
  color: var(--yb-site-muted, #64748b);
  font-size: 14px;
  font-weight: 550;
}

.site-mobile-auth {
  display: grid;
  gap: 6px;
  margin-top: 10px;
  padding-top: 12px;
  border-top: 1px solid var(--yb-site-border, #e5e7eb);
}

.site-mobile-auth a {
  display: flex;
  min-height: 40px;
  padding: 0 12px;
  align-items: center;
  border-radius: 8px;
  color: var(--yb-site-text-2, #334155);
  font-size: 14px;
  font-weight: 650;
  text-decoration: none;
}

.site-mobile-auth a.ghost {
  border: 1px solid var(--yb-site-border-2, #e2e8f0);
  background: var(--yb-site-surface, #ffffff);
  justify-content: center;
}

.site-mobile-auth a.primary {
  background: var(--yb-site-primary-btn-bg, #111827);
  color: var(--yb-site-primary-btn-text, #ffffff);
  justify-content: center;
}

.site-mobile-auth a.danger {
  color: var(--yb-site-danger, #b91c1c);
}

.site-mobile-auth__account {
  display: flex;
  gap: 8px;
  align-items: center;
  padding: 4px 12px 8px;
  color: var(--yb-site-heading, #0f172a);
  font-size: 14px;
  font-weight: 750;
}

.site-mobile-auth__account img {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  object-fit: cover;
}

.site-mobile-enter-active,
.site-mobile-leave-active {
  transition: opacity 0.22s ease, transform 0.22s ease;
}

.site-mobile-enter-from,
.site-mobile-leave-to {
  opacity: 0;
  transform: translateY(-10px);
}

@media (max-width: 760px) {
  .site-layout-header__mobile {
    display: block;
    position: fixed;
    top: var(--site-mobile-nav-top, 56px);
    right: 0;
    left: 0;
    z-index: 4000;
    width: 100%;
    height: calc(100dvh - var(--site-mobile-nav-top, 56px));
    max-height: calc(100dvh - var(--site-mobile-nav-top, 56px));
    min-height: 240px;
    touch-action: pan-y;
  }
}

html.is-site-mobile-nav-open,
html.is-site-mobile-nav-open body {
  overflow: hidden !important;
  overscroll-behavior: none;
}
</style>
