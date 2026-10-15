<script setup lang="ts">
import { useSlots } from '@/slots'
import Logo from '../Logo/index.vue'
import Menu from '../Menu/index.vue'

defineOptions({
  name: 'SubSidebar',
})

defineProps<{
  enable: boolean
}>()

const route = useRoute()

const appSettingsStore = useAppSettingsStore()
const appMenuStore = useAppMenuStore()
const { generateTitle } = useAppMenu()

const isCollapse = computed(() => appSettingsStore.settings.menu.subMenuCollapse)
const activeModuleTitle = computed(() => generateTitle(appMenuStore.allMenus[appMenuStore.actived]?.meta?.title))
// 品牌行保留站点 Logo 与名称；模块名作为独立说明行，只在多模块的一级导航下出现。
const showModuleTitle = computed(() => !!activeModuleTitle.value
  && appSettingsStore.settings.menu.mode === 'side'
  && !isCollapse.value)

const transitionName = ref('')
watch(() => appMenuStore.actived, (val, oldVal) => {
  if (appSettingsStore.mode === 'mobile' || appSettingsStore.settings.menu.mode === 'side') {
    if (val > oldVal) {
      transitionName.value = 'sub-sidebar-y-start'
    }
    else {
      transitionName.value = 'sub-sidebar-y-end'
    }
  }
  else if (appSettingsStore.settings.menu.mode === 'head') {
    if (val > oldVal) {
      transitionName.value = 'sub-sidebar-x-start'
    }
    else {
      transitionName.value = 'sub-sidebar-x-end'
    }
  }
})
</script>

<template>
  <Transition name="sub-sidebar">
    <div
      v-if="enable" class="sub-sidebar-container" :class="{
        'is-collapse': isCollapse,
      }"
    >
      <Component :is="useSlots('sub-sidebar-top')" />
      <Logo
        v-if="['side', 'single'].includes(appSettingsStore.settings.menu.mode) || appSettingsStore.mode === 'mobile'" :show-logo="appSettingsStore.settings.menu.mode === 'single'" class="sidebar-logo" :class="{
          single: appSettingsStore.settings.menu.mode === 'single',
        }"
      />
      <div v-if="showModuleTitle" class="module-title" :title="activeModuleTitle">
        {{ activeModuleTitle }}
      </div>
      <Component :is="useSlots('sub-sidebar-after-logo')" />
      <YdScrollArea :scrollbar="false" mask class="overscroll-contain flex-1">
        <TransitionGroup :name="transitionName">
          <template v-for="(mainItem, mainIndex) in appMenuStore.allMenus" :key="mainIndex">
            <div v-show="mainIndex === appMenuStore.actived">
              <Menu
                :menu="mainItem.children" :value="route.meta.activeMenu || route.path" :default-expand-paths="appMenuStore.defaultExpandPaths" :accordion="appSettingsStore.settings.menu.subMenuUniqueExpand" :collapse="isCollapse" class="menu" :class="{
                  '-mt-2': !(isCollapse || ['head', 'single'].includes(appSettingsStore.settings.menu.mode)),
                }"
              />
            </div>
          </template>
        </TransitionGroup>
      </YdScrollArea>
      <div
        v-if="appSettingsStore.mode === 'pc' && appSettingsStore.settings.menu.subMenuCollapseButton" class="px-4 py-3 flex items-center relative" :class="{
          'justify-center': isCollapse,
          'justify-end': !isCollapse,
        }"
      >
        <YdButton variant="secondary" size="icon-sm" class="yd-interactive" :aria-label="isCollapse ? '展开侧边栏' : '收起侧边栏'" @click="appSettingsStore.toggleSidebarCollapse()">
          <YdIcon name="app-toolbar-collapse" class="size-4 transition" :class="{ 'rotate-z--180': appSettingsStore.settings.menu.subMenuCollapse }" />
        </YdButton>
      </div>
      <Component :is="useSlots('sub-sidebar-after-menu')" />
      <div v-if="appSettingsStore.settings.menu.mode === 'single'" class="px-4 pb-3 flex-center">
        <AppAccountButton :only-avatar="isCollapse" dropdown-align="center" :dropdown-side="isCollapse ? 'right' : 'top'" button-variant="secondary" :class="{ 'w-full': !isCollapse }" />
      </div>
      <Component :is="useSlots('sub-sidebar-bottom')" />
    </div>
  </Transition>
</template>

<style scoped>
.sub-sidebar-container {
  position: absolute;
  inset-inline-start: 0;
  top: 0;
  bottom: 0;
  display: flex;
  flex-direction: column;
  width: var(--g-sub-sidebar-width);
  background-color: var(--g-sub-sidebar-bg);
  box-shadow: 1px 0 0 0 oklch(var(--border));
  transition: inset-inline-start var(--yd-motion-duration-slow) var(--yd-motion-ease), width var(--yd-motion-duration-slow) var(--yd-motion-ease), background-color var(--yd-motion-duration-fast), box-shadow var(--yd-motion-duration-fast);

  /* 品牌行下方的当前模块说明，比站点名轻一档 */
  .module-title {
    display: flex;
    align-items: center;
    flex-shrink: 0;
    height: 30px;
    padding: 0 16px;
    overflow: hidden;
    color: var(--g-sub-sidebar-menu-color);
    font-size: 12px;
    font-weight: 600;
    opacity: 0.72;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  &.is-collapse {
    width: var(--g-sub-sidebar-collapse-width);

    .sidebar-logo {
      &:not(.single) {
        display: none;
      }

      :deep(span) {
        display: none;
      }
    }
  }

  .menu {
    width: 100%;
  }
}

/* 次侧边栏动画 */
.sub-sidebar-x-start-enter-active,
.sub-sidebar-x-end-enter-active,
.sub-sidebar-y-start-enter-active,
.sub-sidebar-y-end-enter-active {
  transition: opacity var(--yd-motion-duration-normal), transform var(--yd-motion-duration-slow) var(--yd-motion-ease);
}

.sub-sidebar-x-start-enter-from,
.sub-sidebar-x-start-leave-active {
  opacity: 0;
  transform: translateX(var(--yd-motion-enter-y));
}

.sub-sidebar-x-end-enter-from,
.sub-sidebar-x-end-leave-active {
  opacity: 0;
  transform: translateX(calc(-1 * var(--yd-motion-enter-y)));
}

.sub-sidebar-y-start-enter-from,
.sub-sidebar-y-start-leave-active {
  opacity: 0;
  transform: translateY(var(--yd-motion-enter-y));
}

.sub-sidebar-y-end-enter-from,
.sub-sidebar-y-end-leave-active {
  opacity: 0;
  transform: translateY(calc(-1 * var(--yd-motion-enter-y)));
}

.sub-sidebar-x-start-leave-active,
.sub-sidebar-x-end-leave-active,
.sub-sidebar-y-start-leave-active,
.sub-sidebar-y-end-leave-active {
  position: absolute;
}

/* 次侧边栏动画 */
.sub-sidebar-enter-active,
.sub-sidebar-leave-active {
  transition: transform var(--yd-motion-duration-slow) var(--yd-motion-ease);
}

.sub-sidebar-enter-from,
.sub-sidebar-leave-to {
  transform: translateX(calc(var(--g-sub-sidebar-width) * -1));
}
</style>
