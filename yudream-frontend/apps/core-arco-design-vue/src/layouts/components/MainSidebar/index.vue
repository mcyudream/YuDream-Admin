<script setup lang="ts">
import { useHotkeyBindings } from '@/hotkeys/useHotkeys'
import { useSlots } from '@/slots'
import Logo from '../Logo/index.vue'

defineOptions({
  name: 'MainSidebar',
})

defineProps<{
  enable: boolean
}>()

const appSettingsStore = useAppSettingsStore()
const appMenuStore = useAppMenuStore()

const { generateTitle, switchTo } = useAppMenu()

useHotkeyBindings({
  'menu.next': () => {
    switchTo(appMenuStore.actived + 1 < appMenuStore.allMenus.length ? appMenuStore.actived + 1 : 0)
  },
  'menu.prev': () => {
    switchTo(appMenuStore.actived - 1 >= 0 ? appMenuStore.actived - 1 : appMenuStore.allMenus.length - 1)
  },
})
</script>

<template>
  <Transition name="main-sidebar">
    <div v-if="enable" class="main-sidebar-container">
      <Component :is="useSlots('main-sidebar-top')" />
      <Logo :show-title="false" class="sidebar-logo" />
      <Component :is="useSlots('main-sidebar-after-logo')" />
      <YdScrollArea :scrollbar="false" mask gradient-color="var(--g-main-sidebar-bg)" class="menu overscroll-contain flex-1">
        <!-- 侧边栏模式（含主导航） -->
        <div
          v-if="appSettingsStore.settings.menu.mode === 'side' || (appSettingsStore.mode === 'mobile' && appSettingsStore.settings.menu.mode !== 'single')" class="main-menu-list flex flex-col w-full"
          role="navigation" aria-label="一级菜单"
        >
          <template v-for="(item, index) in appMenuStore.allMenus" :key="index">
            <div
              class="menu-item" :class="{
                active: index === appMenuStore.actived,
              }"
            >
              <YdButton
                variant="ghost"
                size="icon"
                type="button"
                :aria-label="generateTitle(item.meta?.title)"
                :aria-pressed="index === appMenuStore.actived"
                class="menu-item-container" @click="switchTo(index)"
              >
                <YdIcon :name="item.meta?.icon || 'morph:folder'" class="menu-item-container-icon" aria-hidden="true" />
                <span class="menu-item-label" aria-hidden="true">{{ generateTitle(item.meta?.title) }}</span>
              </YdButton>
            </div>
          </template>
        </div>
      </YdScrollArea>
      <Component :is="useSlots('main-sidebar-after-menu')" />
      <div class="px-4 py-3 flex-center">
        <AppAccountButton only-avatar :button-variant="appSettingsStore.settings.menu.mode === 'side' ? 'secondary' : 'ghost'" class="p-2 size-12" />
      </div>
      <Component :is="useSlots('main-sidebar-bottom')" />
    </div>
  </Transition>
</template>

<style scoped>
.main-sidebar-container {
  position: relative;
  z-index: 10;
  display: flex;
  flex-direction: column;
  width: var(--g-main-sidebar-width);
  color: var(--g-main-sidebar-menu-color);
  background-color: var(--g-main-sidebar-bg);
  box-shadow: 1px 0 0 0 oklch(var(--border));
  transition: color var(--yd-motion-duration-fast), background-color var(--yd-motion-duration-fast), box-shadow var(--yd-motion-duration-fast);

  .sidebar-logo {
    background-color: var(--g-main-sidebar-bg);
    transition: background-color 0.15s;
  }

  .menu {
    min-height: 0;

    :deep(.main-menu-list) {
      gap: 4px;
      padding: 8px 0;
    }

    :deep(.menu-item) {
      position: relative;
      padding: 0 6px;

      .menu-item-container {
        position: relative;
        display: flex;
        width: 100%;
        height: 56px;
        padding: 0;
        align-items: center;
        justify-content: center;
        color: var(--g-main-sidebar-menu-color);
        background-color: transparent;
        border: 0;
        border-radius: var(--yd-control-radius);
        transition: background-color var(--yd-motion-duration-fast), color var(--yd-motion-duration-fast), scale var(--yd-motion-duration-fast) var(--yd-motion-ease);

        &:active {
          scale: var(--yd-motion-press);
        }

        &:hover {
          color: var(--g-main-sidebar-menu-hover-color);
          background-color: var(--g-main-sidebar-menu-hover-bg);
        }

        &:focus-visible {
          outline: 2px solid currentcolor;
          outline-offset: -3px;
        }

        .menu-item-container-icon {
          flex-shrink: 0;
          font-size: 20px !important;
          transition: translate var(--yd-motion-duration-normal) var(--yd-motion-ease-spring);
        }

        /* 悬停或选中时在图标下方显示模块名，取代悬浮提示 */
        .menu-item-label {
          position: absolute;
          inset-inline: 4px;
          inset-block-end: 5px;
          overflow: hidden;
          font-size: 11px;
          font-weight: 500;
          line-height: 15px;
          text-align: center;
          text-overflow: ellipsis;
          white-space: nowrap;
          opacity: 0;
          transition: opacity var(--yd-motion-duration-fast);
        }
      }

      &:hover .menu-item-container-icon,
      &.active .menu-item-container-icon {
        translate: 0 -6px;
      }

      &:hover .menu-item-label,
      &.active .menu-item-label {
        opacity: 1;
      }

      &.active .menu-item-container {
        color: var(--g-sub-sidebar-menu-active-color);
        background-color: var(--g-sub-sidebar-menu-active-bg);
      }
    }
  }
}

/* 主侧边栏动画 */
.main-sidebar-enter-active,
.main-sidebar-leave-active {
  transition: transform var(--yd-motion-duration-slow) var(--yd-motion-ease);
}

.main-sidebar-enter-from,
.main-sidebar-leave-to {
  transform: translateX(calc(var(--g-main-sidebar-width) * -1));
}
</style>
