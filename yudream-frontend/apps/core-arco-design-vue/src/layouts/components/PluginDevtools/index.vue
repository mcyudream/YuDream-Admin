<script setup lang="ts">
import { useEventListener } from '@vueuse/core'
import AgentTracesPanel from './panels/AgentTracesPanel.vue'
import AuditPanel from './panels/AuditPanel.vue'
import OverviewPanel from './panels/OverviewPanel.vue'
import PluginAssetsPanel from './panels/PluginAssetsPanel.vue'
import PluginLogsPanel from './panels/PluginLogsPanel.vue'
import QqSandboxPanel from './panels/QqSandboxPanel.vue'
import SettingsPanel from './panels/SettingsPanel.vue'
import { useDevtoolsFab } from './useDevtoolsFab'
import { useDevtoolsPanel } from './useDevtoolsPanel'

defineOptions({
  name: 'PluginDevtools',
})

const { auth } = useAppAuth()
const store = usePluginDevtoolsStore()

const permitted = computed(() => auth('platform:plugin-devtools:view'))
// 后端开发者工具不可用（旧版本/未部署）时，vite dev 环境仍可打开浮窗查看降级提示
const visible = computed(() => permitted.value && (!!store.status || import.meta.env.DEV))

function togglePanel() {
  if (store.drawerOpen) {
    store.closeDrawer()
  }
  else {
    store.openDrawer()
  }
}

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

/** 边缘条点击：展开回完整按钮并直接打开浮窗 */
function handleStripActivate() {
  undock().then(() => {
    if (!store.drawerOpen) {
      store.openDrawer()
    }
  })
}

// ---------- 浮窗（非模态、可拖拽、可缩放、层级置顶） ----------
const {
  panelStyle,
  panelDragging,
  panelResizing,
  handleHeaderPointerDown,
  handleHeaderPointerMove,
  handleHeaderPointerUp,
  handleResizePointerDown,
  handleResizePointerMove,
  handleResizePointerUp,
  resetPanel,
} = useDevtoolsPanel()

provide('pluginDevtoolsPanelReset', resetPanel)

// ---------- 全局快捷键：Ctrl/Cmd+Shift+D 开关浮窗，Esc 关闭 ----------
useEventListener('keydown', (event: KeyboardEvent) => {
  if (!visible.value) {
    return
  }
  if ((event.ctrlKey || event.metaKey) && event.shiftKey && event.key.toLowerCase() === 'd') {
    event.preventDefault()
    togglePanel()
    return
  }
  if (event.key === 'Escape' && store.drawerOpen) {
    const target = event.target as HTMLElement | null
    if (target && (target.tagName === 'INPUT' || target.tagName === 'TEXTAREA' || target.isContentEditable)) {
      return
    }
    // 面板内打开的模态框优先消费 Esc，不连带关浮窗
    if (document.querySelector('[data-slot="dialog-content"], [data-slot="sheet-content"]')) {
      return
    }
    store.closeDrawer()
  }
})

// ---------- 页面导航 ----------
const navItems = [
  { label: '概览', value: 'overview', icon: 'i-ri:dashboard-line' },
  { label: '插件', value: 'plugins', icon: 'i-ri:puzzle-2-line' },
  { label: 'QQ沙盒', value: 'qq-sandbox', icon: 'i-ri:qq-line' },
  { label: '追踪', value: 'traces', icon: 'i-ri:node-tree' },
  { label: '日志', value: 'logs', icon: 'i-ri:file-list-3-line' },
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
  <Teleport to="body">
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
          @click="handleStripActivate"
        >
          <FaIcon name="i-ri:bug-line" class="size-3.5" />
          <span v-if="store.unreadCount" class="devtools-fab__dot" />
        </div>
        <FaBadge v-else :value="store.unreadCount" variant="destructive">
          <FaTooltip
            text="插件开发者工具（可拖拽，拖到屏幕边缘收起）"
            :side="fabState.side === 'right' ? 'left' : 'right'"
          >
            <FaButton
              size="icon-lg"
              class="rounded-full select-none shadow-md touch-none"
              :class="fabDragging ? 'cursor-grabbing' : 'cursor-grab'"
              @pointerdown="handlePointerDown"
              @pointermove="handlePointerMove"
              @pointerup="handlePointerUp"
              @pointercancel="handlePointerCancel"
              @lostpointercapture="handleLostPointerCapture"
              @click="handleActivate(togglePanel)"
            >
              <FaIcon name="i-ri:bug-line" class="size-5" />
            </FaButton>
          </FaTooltip>
        </FaBadge>
      </div>

      <Transition name="devtools-panel">
        <div
          v-if="store.drawerOpen"
          class="devtools-panel"
          :style="panelStyle"
          role="dialog"
          aria-label="插件开发者工具"
        >
          <header
            class="devtools-panel__header"
            :class="panelDragging ? 'cursor-grabbing' : 'cursor-grab'"
            @pointerdown="handleHeaderPointerDown"
            @pointermove="handleHeaderPointerMove"
            @pointerup="handleHeaderPointerUp"
            @pointercancel="handleHeaderPointerUp"
            @lostpointercapture="handleHeaderPointerUp"
          >
            <FaIcon name="i-ri:bug-line" class="shrink-0 size-4" />
            <span class="devtools-panel__title">插件开发者工具</span>
            <FaTooltip :text="`生命周期流${store.lifecycleConnected ? '已连接' : '未连接'}`" side="bottom">
              <span class="sse-dot" :class="store.lifecycleConnected ? 'sse-dot--on' : ''" />
            </FaTooltip>
            <FaTooltip :text="`追踪流${store.traceConnected ? '已连接' : '未连接'}`" side="bottom">
              <span class="sse-dot" :class="store.traceConnected ? 'sse-dot--on' : ''" />
            </FaTooltip>
            <div class="flex-1" />
            <span class="devtools-panel__shortcut">Ctrl+Shift+D</span>
            <FaButton variant="ghost" size="icon-sm" aria-label="关闭" @click="store.closeDrawer()">
              <FaIcon name="i-ri:close-line" class="size-4" />
            </FaButton>
          </header>

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
              >
                <button
                  type="button"
                  class="devtools-nav__item"
                  :class="{ 'devtools-nav__item--active': store.activePage === item.value }"
                  :aria-label="item.label"
                  @click="store.setActivePage(item.value)"
                >
                  <FaIcon :name="item.icon" class="shrink-0 size-4" />
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
              <div v-show="store.activePage === 'qq-sandbox'" class="devtools-page devtools-page--fill">
                <QqSandboxPanel />
              </div>
              <div v-show="store.activePage === 'traces'" class="devtools-page">
                <AgentTracesPanel />
              </div>
              <div v-show="store.activePage === 'logs'" class="devtools-page devtools-page--fill">
                <PluginLogsPanel />
              </div>
              <div v-show="store.activePage === 'audit'" class="devtools-page">
                <AuditPanel />
              </div>
              <div v-show="store.activePage === 'settings'" class="devtools-page">
                <SettingsPanel />
              </div>
            </div>
          </div>

          <div
            class="devtools-panel__resize"
            :class="panelResizing ? 'devtools-panel__resize--active' : ''"
            aria-hidden="true"
            @pointerdown="handleResizePointerDown"
            @pointermove="handleResizePointerMove"
            @pointerup="handleResizePointerUp"
            @pointercancel="handleResizePointerUp"
            @lostpointercapture="handleResizePointerUp"
          />
        </div>
      </Transition>
    </template>
  </Teleport>
</template>

<style scoped>
.devtools-fab {
  position: fixed;
  z-index: 2100;
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
  opacity: 0.6;
  cursor: pointer;
  position: relative;
  transition: opacity 120ms ease, width 150ms ease;
}

.devtools-fab__strip:hover {
  opacity: 1;
  width: 28px;
  color: var(--color-text-2);
  box-shadow: 0 4px 16px rgb(0 0 0 / 0.12);
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

/* 浮窗：非模态置顶，不遮挡系统其余部分的交互 */
.devtools-panel {
  position: fixed;
  z-index: 2100;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: var(--color-bg-2);
  border: 1px solid var(--color-border-2);
  border-radius: 10px;
  box-shadow: 0 16px 48px rgb(0 0 0 / 0.2), 0 4px 12px rgb(0 0 0 / 0.1);
}

.devtools-panel-enter-active,
.devtools-panel-leave-active {
  transition: opacity 160ms ease, transform 160ms ease;
}

.devtools-panel-enter-from,
.devtools-panel-leave-to {
  opacity: 0;
  transform: translateY(10px) scale(0.98);
}

.devtools-panel__header {
  display: flex;
  gap: 8px;
  align-items: center;
  height: 38px;
  padding: 0 6px 0 12px;
  color: var(--color-text-2);
  background: var(--color-bg-3);
  border-bottom: 1px solid var(--color-border-2);
  user-select: none;
  touch-action: none;
  flex-shrink: 0;
}

.devtools-panel__title {
  font-size: 13px;
  font-weight: 600;
}

.devtools-panel__shortcut {
  padding: 1px 6px;
  color: var(--color-text-3);
  font-size: 11px;
  border: 1px solid var(--color-border-2);
  border-radius: 4px;
  background: var(--color-fill-1, var(--color-bg-2));
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
  flex: 1;
  min-height: 0;
}

.devtools-nav {
  display: flex;
  flex-direction: column;
  gap: 4px;
  flex-shrink: 0;
  width: 3rem;
  padding: 10px 0;
  align-items: center;
  border-right: 1px solid var(--color-border-2);
}

.devtools-nav__item {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: 7px;
  color: var(--color-text-3);
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
}

.devtools-content {
  flex: 1;
  min-width: 0;
  padding: 12px 14px;
  overflow-y: auto;
  background: var(--color-bg-1);
}

.devtools-page {
  min-height: 100%;
}

/* 沙盒等工作台页填满浮窗可用高度，由页内区域自行滚动 */
.devtools-page--fill {
  height: 100%;
}

.devtools-panel__resize {
  position: absolute;
  right: 0;
  bottom: 0;
  width: 18px;
  height: 18px;
  cursor: nwse-resize;
  touch-action: none;
  background: linear-gradient(135deg, transparent 55%, var(--color-fill-4, var(--color-border-3)) 55%, var(--color-fill-4, var(--color-border-3)) 64%, transparent 64%, transparent 72%, var(--color-fill-4, var(--color-border-3)) 72%, var(--color-fill-4, var(--color-border-3)) 81%, transparent 81%);
  border-bottom-right-radius: 10px;
}

.devtools-panel__resize--active {
  background: linear-gradient(135deg, transparent 55%, var(--color-text-3) 55%, var(--color-text-3) 64%, transparent 64%, transparent 72%, var(--color-text-3) 72%, var(--color-text-3) 81%, transparent 81%);
}

@media (max-width: 640px) {
  .devtools-panel__shortcut {
    display: none;
  }
}
</style>

<style>
/* 浮窗 z-index 2100 高于 FaSelect 下拉默认的 z-2000，下拉 teleport 到 body 后会被浮窗挡住导致"点击无反应"，这里显式抬升 */
body [data-slot="select-content"] {
  z-index: 2200;
}
</style>
