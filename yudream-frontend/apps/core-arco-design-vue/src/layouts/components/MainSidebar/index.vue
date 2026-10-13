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
      <FaScrollArea :scrollbar="false" mask gradient-color="var(--g-main-sidebar-bg)" class="menu overscroll-contain flex-1">
        <!-- 侧边栏模式（含主导航） -->
        <div
          v-if="appSettingsStore.settings.menu.mode === 'side' || (appSettingsStore.mode === 'mobile' && appSettingsStore.settings.menu.mode !== 'single')" class="py-1 flex flex-col w-full transition-all of-hidden -mt-2"
        >
          <template v-for="(item, index) in appMenuStore.allMenus" :key="index">
            <div
              class="menu-item px-2 py-1 transition-all relative" :class="{
                active: index === appMenuStore.actived,
              }"
            >
              <FaTooltip v-if="item.children && item.children.length !== 0" :disabled="!generateTitle(item.meta?.title)" :text="generateTitle(item.meta?.title)" side="right">
                <button
                  type="button"
                  :aria-label="generateTitle(item.meta?.title)"
                  :aria-pressed="index === appMenuStore.actived"
                  class="group menu-item-container text-[var(--g-main-sidebar-menu-color)] py-4 border-0 rounded-lg bg-transparent flex gap-1 h-full w-full cursor-pointer items-center justify-center relative hover-(text-[var(--g-main-sidebar-menu-hover-color)] bg-[var(--g-main-sidebar-menu-hover-bg)]) px-2!" :class="{
                    'text-[var(--g-main-sidebar-menu-active-color)]! bg-[var(--g-main-sidebar-menu-active-bg)]!': index === appMenuStore.actived,
                  }" @click="switchTo(index)"
                >
                  <FaIcon :name="item.meta?.icon || 'morph:folder'" class="menu-item-container-icon" aria-hidden="true" />
                </button>
              </FaTooltip>
            </div>
          </template>
        </div>
      </FaScrollArea>
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
    :deep(.menu-item) {
      .menu-item-container {
        aspect-ratio: 1 / 1;
        padding-block: 8px;
        color: var(--g-main-sidebar-menu-color);
        border-radius: var(--yd-control-radius);
        transition: background-color var(--yd-motion-duration-fast), color var(--yd-motion-duration-fast), scale var(--yd-motion-duration-normal) var(--yd-motion-ease-spring);

        &:active {
          scale: var(--yd-motion-press);
        }

        &:focus-visible {
          outline: 2px solid currentcolor;
          outline-offset: -3px;
        }

        &:hover {
          color: var(--g-main-sidebar-menu-hover-color);
          background-color: var(--g-main-sidebar-menu-hover-bg);
        }

        .menu-item-container-icon {
          font-size: 20px !important;
          transition: translate var(--yd-motion-duration-normal) var(--yd-motion-ease-spring);
        }
      }

      &.active .menu-item-container {
        color: var(--g-main-sidebar-menu-active-color) !important;
        background-color: var(--g-main-sidebar-menu-active-bg) !important;
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

@media (hover: hover) and (pointer: fine) {
  .menu-item-container:hover .menu-item-container-icon {
    translate: 0 var(--yd-motion-lift);
  }
}
</style>
