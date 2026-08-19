<script setup lang="ts">
import AgentTracesPanel from './panels/AgentTracesPanel.vue'
import AuditPanel from './panels/AuditPanel.vue'
import EventsPanel from './panels/EventsPanel.vue'
import PluginAssetsPanel from './panels/PluginAssetsPanel.vue'

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

const activeTab = ref<string | number>('assets')
const tabs = [
  { label: '插件资产', value: 'assets', icon: 'i-ri:puzzle-2-line' },
  { label: 'Agent 追踪', value: 'traces', icon: 'i-ri:node-tree' },
  { label: '事件流', value: 'events', icon: 'i-ri:radar-line' },
  { label: '前端审查', value: 'audit', icon: 'i-ri:search-eye-line' },
]

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
    <FaBadge :value="store.unreadCount" variant="destructive" class="bottom-6 right-6 fixed z-1008">
      <FaTooltip text="插件开发者工具" side="left">
        <FaButton size="icon-lg" class="rounded-full shadow-md" @click="store.openDrawer()">
          <FaIcon name="i-ri:bug-line" class="size-5" />
        </FaButton>
      </FaTooltip>
    </FaBadge>

    <FaDrawer
      v-model="drawerVisible"
      title="插件开发者工具"
      :footer="false"
      content-class="w-full sm:max-w-2xl"
    >
      <template #header>
        <div class="flex flex-col gap-1">
          <div class="text-base font-semibold flex gap-2 items-center">
            <FaIcon name="i-ri:bug-line" />
            插件开发者工具
          </div>
          <div v-if="store.status" class="flex flex-wrap gap-1.5 items-center">
            <FaTag :variant="store.status.devModeEnabled ? 'default' : 'secondary'" class="text-xs">
              开发模式{{ store.status.devModeEnabled ? '已启用' : '未启用' }}
            </FaTag>
            <FaTag :variant="store.status.traceEnabled ? 'default' : 'secondary'" class="text-xs">
              Agent 追踪{{ store.status.traceEnabled ? '已启用' : '未启用' }}
            </FaTag>
            <FaTag variant="outline" class="text-xs">
              插件 {{ store.status.enabledCount }}/{{ store.status.installedCount }} 启用
            </FaTag>
          </div>
        </div>
      </template>

      <div v-if="!store.status" class="devtools-unavailable">
        <FaIcon name="i-ri:cloud-off-line" class="size-8" />
        <p>后端开发者工具接口不可用{{ store.statusError ? `：${store.statusError}` : '。' }}</p>
        <p class="text-xs">
          请确认后端已部署包含插件开发者工具的版本并以管理员身份登录；vite dev 环境下此面板仅作降级展示。
        </p>
      </div>

      <FaTabs v-else v-model="activeTab" :list="tabs" class="devtools-tabs">
        <template #assets>
          <PluginAssetsPanel />
        </template>
        <template #traces>
          <AgentTracesPanel />
        </template>
        <template #events>
          <EventsPanel />
        </template>
        <template #audit>
          <AuditPanel />
        </template>
      </FaTabs>
    </FaDrawer>
  </template>
</template>

<style scoped>
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

.devtools-tabs {
  height: 100%;
}
</style>
