<script setup lang="ts">
import { useMediaQuery } from '@vueuse/core'
import AgentTracesPanel from './panels/AgentTracesPanel.vue'
import AuditPanel from './panels/AuditPanel.vue'
import OverviewPanel from './panels/OverviewPanel.vue'
import PluginAssetsPanel from './panels/PluginAssetsPanel.vue'
import SettingsPanel from './panels/SettingsPanel.vue'
import { useDevtoolsFab } from './useDevtoolsFab'

defineOptions({
  name: 'PluginDevtools',
})

const { auth } = useAppAuth()
const store = usePluginDevtoolsStore()

const permitted = computed(() => auth('platform:plugin-devtools:view'))
// 后端开发者工具不可用（旧版本/未部署）时，vite dev 环境仍可打开抽屉查看降级提示
const visible = computed(() => permitted.value && (!!store.status || import.meta.env.DEV))

const drawerVisible = computed({
  get: () => store.drawerOpen,
  set: value => (value ? store.openDrawer() : store.closeDrawer()),
})

// ---------- 悬浮按钮（常驻、可拖拽、贴边收起） ----------
const fabRef = ref<HTMLElement | null>(null)
const {
  fabState,
  fabDragging,
  fabStyle,
  handlePointerDown,
  handlePointerMove,
  handlePointerUp,
  handlePointerCancel,
  handleLostPointerCapture,
  handleActivate,
  undock,
  resetFab,
} = useDevtoolsFab(fabRef)

provide('pluginDevtoolsFabReset', resetFab)

// ---------- 抽屉页面导航 ----------
const isNarrow = useMediaQuery('(max-width: 640px)')

const navItems = [
  { label: '概览', value: 'overview', icon: 'i-ri:dashboard-line' },
  { label: '插件', value: 'plugins', icon: 'i-ri:puzzle-2-line' },
  { label: '追踪', value: 'traces', icon: 'i-ri:node-tree' },
  { label: '审查', value: 'audit', icon: 'i-ri:search-eye-line' },
  { label: '设置', value: 'settings', icon: 'i-ri:settings-3-line' },
] as const

onMounted(async () => {
  if (!permitted.value) {
    return
  }
  await store.loadStatus()
  if (store.status) {
    store.connect()
  }
})
</script>

<template>
  <template v-if="visible">
    <div
      ref="fabRef"
      class="devtools-fab"
      :style="fabStyle"
    >
      <div
        v-if="fabState.docked"
        class="devtools-fab__strip"
        :class="`devtools-fab__strip--${fabState.side}`"
        title="展开插件开发者工具"
        @click="undock"
      >
        <FaIcon name="i-ri:bug-line" class="size-3.5" />
        <span v-if="store.unreadCount" class="devtools-fab__dot" />
      </div>
      <FaBadge v-else :value="store.unreadCount" variant="destructive">
        <FaTooltip text="插件开发者工具（可拖拽，拖到屏幕边缘收起）" :side="fabState.side === 'right' ? 'left' : 'right'">
          <FaButton
            size="icon-lg"
            class="rounded-full select-none shadow-md touch-none"
            :class="fabDragging ? 'cursor-grabbing' : 'cursor-grab'"
            @pointerdown="handlePointerDown"
            @pointermove="handlePointerMove"
            @pointerup="handlePointerUp"
            @pointercancel="handlePointerCancel"
            @lostpointercapture="handleLostPointerCapture"
            @click="handleActivate(store.openDrawer)"
          >
            <FaIcon name="i-ri:bug-line" class="size-5" />
          </FaButton>
        </FaTooltip>
      </FaBadge>
    </div>

    <FaDrawer
      v-model="drawerVisible"
      title="插件开发者工具"
      :footer="false"
      content-class="w-full sm:max-w-2xl"
    >
      <template #header>
        <div class="flex gap-2 items-center">
          <FaIcon name="i-ri:bug-line" />
          <span class="text-base font-semibold">插件开发者工具</span>
          <FaTooltip :text="`生命周期流${store.lifecycleConnected ? '已连接' : '未连接'}`" side="bottom">
            <span class="sse-dot" :class="store.lifecycleConnected ? 'sse-dot--on' : ''" />
          </FaTooltip>
          <FaTooltip :text="`追踪流${store.traceConnected ? '已连接' : '未连接'}`" side="bottom">
            <span class="sse-dot" :class="store.traceConnected ? 'sse-dot--on' : ''" />
          </FaTooltip>
        </div>
      </template>

      <div v-if="!store.status" class="devtools-unavailable">
        <FaIcon name="i-ri:cloud-off-line" class="size-8" />
        <p>后端开发者工具接口不可用{{ store.statusError ? `：${store.statusError}` : '。' }}</p>
        <p class="text-xs">
          请确认后端已部署包含插件开发者工具的版本并以管理员身份登录；vite dev 环境下此面板仅作降级展示。
        </p>
      </div>

      <div v-else class="devtools-body">
        <nav class="devtools-nav">
          <FaTooltip
            v-for="item in navItems"
            :key="item.value"
            :text="item.label"
            side="right"
            :disabled="!isNarrow"
          >
            <button
              type="button"
              class="devtools-nav__item"
              :class="{ 'devtools-nav__item--active': store.activePage === item.value }"
              @click="store.setActivePage(item.value)"
            >
              <FaIcon :name="item.icon" class="shrink-0 size-4" />
              <span class="devtools-nav__label">{{ item.label }}</span>
            </button>
          </FaTooltip>
        </nav>

        <div class="devtools-content">
          <div v-show="store.activePage === 'overview'" class="devtools-page">
            <OverviewPanel />
          </div>
          <div v-show="store.activePage === 'plugins'" class="devtools-page">
            <PluginAssetsPanel />
          </div>
          <div v-show="store.activePage === 'traces'" class="devtools-page">
            <AgentTracesPanel />
          </div>
          <div v-show="store.activePage === 'audit'" class="devtools-page">
            <AuditPanel />
          </div>
          <div v-show="store.activePage === 'settings'" class="devtools-page">
            <SettingsPanel />
          </div>
        </div>
      </div>
    </FaDrawer>
  </template>
</template>

<style scoped>
.devtools-fab {
  position: fixed;
  z-index: 1008;
}

.devtools-fab__strip {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 56px;
  color: var(--color-text-3);
  background: var(--color-bg-2);
  border: 1px solid var(--color-border-2);
  opacity: 0.65;
  cursor: pointer;
  position: relative;
  transition: opacity 120ms ease;
}

.devtools-fab__strip:hover {
  opacity: 1;
}

.devtools-fab__strip--right {
  border-right: 0;
  border-radius: 8px 0 0 8px;
}

.devtools-fab__strip--left {
  border-left: 0;
  border-radius: 0 8px 8px 0;
}

.devtools-fab__dot {
  position: absolute;
  top: 4px;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--color-danger, #f53f3f);
}

.devtools-fab__strip--right .devtools-fab__dot {
  left: 3px;
}

.devtools-fab__strip--left .devtools-fab__dot {
  right: 3px;
}

.sse-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--color-fill-4, var(--color-fill-3));
  cursor: default;
}

.sse-dot--on {
  background: var(--color-success, #00b42a);
}

.devtools-unavailable {
  display: flex;
  flex-direction: column;
  gap: 8px;
  align-items: center;
  padding: 48px 24px;
  color: var(--color-text-3);
  font-size: 14px;
  text-align: center;
}

.devtools-body {
  display: flex;
  gap: 0;
  height: 100%;
  min-height: 0;
}

.devtools-nav {
  display: flex;
  flex-direction: column;
  gap: 2px;
  flex-shrink: 0;
  width: 9rem;
  padding: 12px 8px 12px 0;
  border-right: 1px solid var(--color-border-2);
}

.devtools-nav__item {
  display: flex;
  gap: 8px;
  align-items: center;
  width: 100%;
  padding: 8px 10px;
  border-radius: 6px;
  color: var(--color-text-3);
  font-size: 13px;
  cursor: pointer;
  user-select: none;
}

.devtools-nav__item:hover {
  background: var(--color-fill-1, var(--color-bg-3));
  color: var(--color-text-2);
}

.devtools-nav__item--active {
  background: var(--color-fill-2, var(--color-bg-3));
  color: var(--color-text-1);
  font-weight: 600;
}

.devtools-content {
  flex: 1;
  min-width: 0;
  overflow-y: auto;
}

.devtools-page {
  min-height: 100%;
}

@media (max-width: 640px) {
  .devtools-nav {
    width: 3rem;
  }

  .devtools-nav__label {
    display: none;
  }

  .devtools-nav__item {
    justify-content: center;
    padding: 8px 0;
  }
}
</style>
