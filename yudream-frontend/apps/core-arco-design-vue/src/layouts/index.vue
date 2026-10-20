<script setup lang="ts">
import { useElementSize, useScroll } from '@vueuse/core'
import { useHotkeyBindings } from '@/hotkeys/useHotkeys'
import { useSlots } from '@/slots'
import { cn } from '@/utils'
import eventBus from '@/utils/eventBus'
import FloatingSidebarMenuButton from './components/FloatingSidebarMenuButton/index.vue'
import Header from './components/Header/index.vue'
import Hotkeys from './components/Hotkeys/index.vue'
import MainSidebar from './components/MainSidebar/index.vue'
import PluginDevtools from './components/PluginDevtools/index.vue'
import PluginGlobalWidgets from './components/PluginGlobalWidgets/index.vue'
import SubSidebar from './components/SubSidebar/index.vue'
import Topbar from './components/Topbar/index.vue'
import LinkView from './components/views/link.vue'

defineOptions({
  name: 'Layout',
})

const routeInfo = useRoute()

const appSettingsStore = useAppSettingsStore()
const appKeepAliveStore = useAppKeepAliveStore()
const appMenuStore = useAppMenuStore()
const appAccountStore = useAppAccountStore()
const mainPage = useAppPage()
const toast = useYdToast()
const verificationMailSending = ref(false)

useHotkeyBindings({
  'page.reload': () => {
    mainPage.reload()
  },
})

const layoutTopRef = useTemplateRef('layoutTopRef')
const { height: layoutTopHeight } = useElementSize(layoutTopRef)

const layoutBottomRef = useTemplateRef('layoutBottomRef')
const { height: layoutBottomHeight } = useElementSize(layoutBottomRef)

// 头部是否显示
const isHeaderEnable = computed(() => {
  return appSettingsStore.mode === 'pc'
    && appSettingsStore.settings.menu.mode === 'head'
})

// 侧边栏主导航是否显示
const isMainSidebarEnable = computed(() => {
  return appSettingsStore.settings.menu.mode === 'side'
    || (appSettingsStore.mode === 'mobile' && appSettingsStore.settings.menu.mode !== 'single')
})

// 侧边栏次导航是否显示
const isSubSidebarEnable = computed(() => {
  return appSettingsStore.mode === 'mobile'
    || (
      ['side', 'head'].includes(appSettingsStore.settings.menu.mode)
      && appMenuStore.sidebarMenus.length !== 0
      // 单菜单模块（如 AI 助手）不占二级栏，页面直接占满
      && !appMenuStore.sidebarMenusHasOnlyMenu
      && (
        (
          appSettingsStore.settings.menu.mainMenuClickMode === 'switch'
          && !appMenuStore.sidebarMenus.every(item => item.meta?.menu === false)
        )
        || (
          appSettingsStore.settings.menu.mainMenuClickMode !== 'switch'
          && appMenuStore.sidebarMenus.some(item => item.meta?.menu !== false)
        )
      )
    )
    || (
      ['single'].includes(appSettingsStore.settings.menu.mode)
      && appMenuStore.sidebarMenus.length !== 0
      && !appMenuStore.sidebarMenusHasOnlyMenu
      && !appMenuStore.sidebarMenus.every(item => item.meta?.menu === false)
    )
})

// 顶栏是否显示
const isTopbarEnable = computed(() => true)

// 标签栏是否显示
const isTabbarEnable = computed(() => {
  return appSettingsStore.settings.topbar.tabbar
})

// 工具栏是否显示
const isToolbarEnable = computed(() => {
  const { toolbar } = appSettingsStore.settings
  return appSettingsStore.settings.topbar.toolbar && [
    toolbar.breadcrumb,
    toolbar.menuSearch,
    toolbar.fullscreen,
    toolbar.colorScheme,
  ].some((item) => {
    if (typeof item === 'boolean') {
      return item
    }
    return item?.enable ?? false
  })
})

const isLink = computed(() => !!routeInfo.meta.link)

watch(() => appSettingsStore.settings.menu.subMenuCollapse, (val) => {
  if (appSettingsStore.mode === 'mobile') {
    if (!val) {
      document.body.classList.add('overflow-hidden')
    }
    else {
      document.body.classList.remove('overflow-hidden')
    }
  }
})

watch(() => routeInfo.path, () => {
  if (appSettingsStore.mode === 'mobile') {
    appSettingsStore.$patch((state) => {
      state.settings.menu.subMenuCollapse = true
    })
  }
})

const { y } = useScroll(window)
const { height: fixedContentBeforeAreaHeight } = useElementSize(useTemplateRef('fixedContentBeforeAreaRef'))
const { height: fixedContentAfterAreaHeight } = useElementSize(useTemplateRef('fixedContentAfterAreaRef'))
const topbarScrollVisibleOrHidden = ref(false)
function handleTopbarScrollVisibleOrHidden(val: boolean) {
  topbarScrollVisibleOrHidden.value = val
}
eventBus.on('topbar-scroll-visible-or-hidden', handleTopbarScrollVisibleOrHidden)

onBeforeUnmount(() => {
  eventBus.off('topbar-scroll-visible-or-hidden', handleTopbarScrollVisibleOrHidden)
})

async function resendVerificationEmail() {
  verificationMailSending.value = true
  try {
    await appAccountStore.resendVerificationEmail()
    toast.success('验证邮件已重新发送')
  }
  finally {
    verificationMailSending.value = false
  }
}
</script>

<template>
  <div
    class="layout h-full" :style="{
      '--g-slots-layout-top-height': `${layoutTopHeight}px`,
      '--g-slots-layout-bottom-height': `${layoutBottomHeight}px`,
      '--g-header-actual-height': isHeaderEnable ? 'var(--g-header-height)' : '0px',
      '--g-main-sidebar-actual-width': isMainSidebarEnable ? 'var(--g-main-sidebar-width)' : '0px',
      '--g-sub-sidebar-actual-width': isSubSidebarEnable ? (appSettingsStore.settings.menu.subMenuCollapse && appSettingsStore.mode !== 'mobile' ? 'var(--g-sub-sidebar-collapse-width)' : 'var(--g-sub-sidebar-width)') : '0px',
      '--g-topbar-height': 'calc(var(--g-tabbar-actual-height) + var(--g-toolbar-actual-height))',
      '--g-topbar-actual-height': isTopbarEnable ? 'calc(var(--g-tabbar-actual-height) + var(--g-toolbar-actual-height))' : '0px',
      '--g-tabbar-actual-height': isTabbarEnable ? 'var(--g-tabbar-height)' : '0px',
      '--g-toolbar-actual-height': isToolbarEnable ? 'var(--g-toolbar-height)' : '0px',
      '--g-main-container-padding-top': `${fixedContentBeforeAreaHeight}px`,
      '--g-main-container-padding-bottom': `${fixedContentAfterAreaHeight}px`,
    }"
  >
    <div ref="layoutTopRef" class="slots-layout-top mx-auto bg-background w-full shadow-[-1px_0_0_0_oklch(var(--border)),1px_0_0_0_oklch(var(--border)),0_-1px_0_0_oklch(var(--border)),0_1px_0_0_oklch(var(--border))] transition-width-300 inset-t-0 fixed z-1030 empty:hidden">
      <Component :is="useSlots('layout-top')" />
    </div>
    <Header :enable="isHeaderEnable" />
    <div class="wrapper">
      <div class="sidebar-container" :class="{ show: appSettingsStore.mode === 'mobile' && !appSettingsStore.settings.menu.subMenuCollapse }">
        <MainSidebar :enable="isMainSidebarEnable" />
        <SubSidebar :enable="isSubSidebarEnable" />
      </div>
      <!-- 移动端下，展开侧边栏时的遮罩层 -->
      <YdAction
        type="button"
        aria-label="关闭侧边栏" :class="cn('invisible fixed inset-0 z-1009 bg-black/50 op-0 backdrop-blur-sm transition-opacity', { 'op-100 visible': appSettingsStore.mode === 'mobile' && !appSettingsStore.settings.menu.subMenuCollapse })" @click="appSettingsStore.toggleSidebarCollapse()" />
      <FloatingSidebarMenuButton />
      <PluginDevtools />
      <PluginGlobalWidgets />
      <div class="main-container pb-[calc(var(--g-slots-layout-bottom-height)+var(--g-main-container-padding-bottom,0px))] pt-[calc(var(--g-slots-layout-top-height)+var(--g-header-actual-height)+var(--g-topbar-actual-height))]">
        <div
          class="fixed-content-around-area w-full inset-t-[calc(var(--g-slots-layout-top-height)+var(--g-header-actual-height))] inset-inline-1/2 fixed z-1005" :style="{
            ...(appSettingsStore.settings.topbar.mode === 'static' && { marginTop: `calc(min(var(--g-topbar-actual-height), ${y}px) * -1)` }),
            ...(topbarScrollVisibleOrHidden && { translate: '0 calc(var(--g-topbar-actual-height) * -1)' }),
          }"
        >
          <Topbar :enable="isTopbarEnable" :enable-tabbar="isTabbarEnable" :enable-toolbar="isToolbarEnable" />
          <div id="fixed-content-before-area" ref="fixedContentBeforeAreaRef" class="shadow-[0_1px_0_0_oklch(var(--border)),0_-1px_0_0_oklch(var(--border))] relative z-1 empty:hidden">
            <div v-if="appAccountStore.isEmailUnverified" class="email-verification-banner">
              <div class="email-verification-banner__content">
                <YdIcon name="morph:triangle-alert" />
                <span>当前账户未验证无法使用其他功能，或者点击重新发送验证邮件</span>
              </div>
              <YdButton size="sm" variant="outline" :loading="verificationMailSending" @click="resendVerificationEmail">
                <YdIcon name="morph:send" />
                重新发送验证邮件
              </YdButton>
            </div>
          </div>
        </div>
        <div id="app-content" class="main">
          <RouterView v-slot="{ Component, route }">
            <Transition :name="!appSettingsStore.isReloading ? 'admin-page' : ''" mode="out-in">
              <KeepAlive :include="appKeepAliveStore.list">
                <Component :is="Component" v-show="!isLink" :key="route.fullPath" />
              </KeepAlive>
            </Transition>
          </RouterView>
          <LinkView v-if="isLink" />
        </div>
        <div class="copyright">
          <AppCopyright />
        </div>
        <div class="fixed-content-around-area w-full inset-b-0 inset-inline-1/2 fixed z-1005">
          <div id="fixed-content-after-area" ref="fixedContentAfterAreaRef" class="shadow-[0_1px_0_0_oklch(var(--border)),0_-1px_0_0_oklch(var(--border))] relative z-1 empty:hidden" />
        </div>
      </div>
    </div>
    <div ref="layoutBottomRef" class="slots-layout-bottom mx-auto bg-background w-full shadow-[-1px_0_0_0_oklch(var(--border)),1px_0_0_0_oklch(var(--border)),0_-1px_0_0_oklch(var(--border)),0_1px_0_0_oklch(var(--border))] transition-width-300 inset-b-0 fixed z-1030 empty:hidden">
      <Component :is="useSlots('layout-bottom')" />
    </div>
    <Hotkeys />
    <Component :is="useSlots('free-position')" />
  </div>
</template>

<style scoped>
[data-mode="mobile"] {
  .sidebar-container {
    transform: translateX(calc((var(--g-main-sidebar-width) + var(--g-sub-sidebar-width)) * -1));

    &.show {
      transform: translateX(0);
    }
  }

  .main-container {
    padding-inline-start: 0 !important;
  }

  &[data-menu-mode="single"] {
    .sidebar-container {
      transform: translateX(calc(var(--g-sub-sidebar-width) * -1));

      &.show {
        transform: translateX(0);
      }
    }
  }
}

.layout {
  width: 100%;
  height: 100%;
  margin: 0 auto;
  transition: width var(--yd-motion-duration-normal) var(--yd-motion-ease);
}

/*
  整高内滚页面（YdTablePageLayout 等打了 data-yd-fullheight 标记的页面）：
  容器固定为视口高度、不再整页滚动，滚动交给页面内部（如表格卡片的滚动区），
  分页等底部元素因此能固定在卡片底部。未标记的页面行为不变。
*/
.main-container:has(> .main [data-yd-fullheight]) {
  height: 100dvh;
  overflow: hidden;

  > .main {
    flex: 1;
    min-height: 0;
    height: auto;

    /* 页面根与整高容器之间没有布局包装，贯通整高 */
    > * {
      height: 100%;
    }

    /* 远程插件页在路由根与页面之间多一层 data-yudream-plugin 包装，高度继续贯通 */
    > * > :deep([data-yudream-plugin]) {
      height: 100%;
      min-height: 0;
    }

    /* 插件页根节点（通常是页面组件根元素）继续承接整高，整高布局才能铺满 */
    > * > :deep([data-yudream-plugin]) > * {
      height: 100%;
      min-height: 0;
    }
  }
}

.email-verification-banner {
  display: flex;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
  min-height: 44px;
  padding: 8px 16px;
  color: rgb(var(--warning-7, 245 158 11));
  background: rgb(var(--warning-1, 255 251 235));
}

.email-verification-banner__content {
  display: flex;
  gap: 8px;
  align-items: center;
  min-width: 0;
  font-size: 14px;
  line-height: 1.45;
}

.email-verification-banner__content span {
  overflow-wrap: anywhere;
}

.wrapper {
  position: relative;
  width: 100%;
  height: 100%;

  .sidebar-container {
    position: fixed;
    top: calc(var(--g-slots-layout-top-height) + var(--g-header-actual-height));
    bottom: var(--g-slots-layout-bottom-height);
    z-index: 1010;
    display: flex;
    width: calc(var(--g-main-sidebar-actual-width) + var(--g-sub-sidebar-actual-width));
    transition: width var(--yd-motion-duration-slow) var(--yd-motion-ease), transform var(--yd-motion-duration-slow) var(--yd-motion-ease), top var(--yd-motion-duration-normal), box-shadow var(--yd-motion-duration-fast);

    &:has(> .main-sidebar-container.main-sidebar-enter-active),
    &:has(> .main-sidebar-container.main-sidebar-leave-active),
    &:has(> .sub-sidebar-container.sub-sidebar-enter-active),
    &:has(> .sub-sidebar-container.sub-sidebar-leave-active) {
      overflow: hidden;
    }
  }

  .main-sidebar-container:not(.main-sidebar-leave-active) + .sub-sidebar-container {
    inset-inline-start: var(--g-main-sidebar-width);
  }

  .main-container {
    display: flex;
    flex-direction: column;
    min-width: 0;
    min-height: 100%;
    padding-inline-start: calc(var(--g-main-sidebar-actual-width) + var(--g-sub-sidebar-actual-width));
    overflow: auto;
    background-color: var(--g-main-area-bg);
    box-shadow: -1px 0 0 0 oklch(var(--border)), 1px 0 0 0 oklch(var(--border));
    transition: padding var(--yd-motion-duration-slow) var(--yd-motion-ease), background-color var(--yd-motion-duration-fast), box-shadow var(--yd-motion-duration-fast);

    .fixed-content-around-area {
      width: calc(100% - var(--g-main-sidebar-actual-width) - var(--g-sub-sidebar-actual-width));
      padding-right: var(--scrollbar-width, 0);
      transform: translateX(-50%) translateX(calc(var(--g-main-sidebar-actual-width) / 2)) translateX(calc(var(--g-sub-sidebar-actual-width) / 2));
      transform-origin: left;
      transition: width var(--yd-motion-duration-slow) var(--yd-motion-ease), transform var(--yd-motion-duration-slow) var(--yd-motion-ease), translate var(--yd-motion-duration-normal) var(--yd-motion-ease), top var(--yd-motion-duration-normal);

      [data-mode="mobile"] & {
        width: 100% !important;
        transform: translateX(-50%) !important;
      }
    }

    .main {
      position: relative;
      flex: auto;
      width: 100%;
      min-width: 0;
      height: 100%;
      padding-top: var(--g-main-container-padding-top, 0);
      margin: 0 auto;
      overflow: hidden;
      background-color: var(--g-main-area-bg);
      box-shadow: -1px 0 0 0 oklch(var(--border)), 1px 0 0 0 oklch(var(--border));
      transition: padding-top var(--yd-motion-duration-normal), width var(--yd-motion-duration-slow) var(--yd-motion-ease), background-color var(--yd-motion-duration-fast), box-shadow var(--yd-motion-duration-fast);
    }

    .copyright {
      position: relative;
      width: 100%;
      margin-inline: auto;
      background-color: oklch(var(--background));
      box-shadow: -1px 0 0 0 oklch(var(--border)), 1px 0 0 0 oklch(var(--border)), 0 -1px 0 0 oklch(var(--border));
    }
  }
}

@media (width <= 640px) {
  .email-verification-banner {
    flex-direction: column;
    align-items: stretch;
  }
}

/* 主内容区动画 */
.admin-page-enter-active {
  transition: opacity var(--yd-motion-duration-normal) ease, transform var(--yd-motion-duration-slow) var(--yd-motion-ease);
}

.admin-page-leave-active {
  transition: opacity var(--yd-motion-duration-fast) ease;
}

.admin-page-enter-from {
  opacity: 0;
  transform: translateY(var(--yd-motion-enter-y));
}

.admin-page-leave-to {
  opacity: 0;
}
</style>
