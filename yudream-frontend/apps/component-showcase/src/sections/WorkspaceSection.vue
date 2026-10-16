<script setup lang="ts">
import type { YdWorkspaceRailItem, YdWorkspaceStatusItem, YdWorkspaceTab } from '@yudream/components'
import { nextWorkspaceTabKey, useYdToast, YdWorkspaceLayout, YdWorkspaceTabs } from '@yudream/components'
import { computed, ref } from 'vue'
import DemoCard from '@/components/DemoCard.vue'

const toast = useYdToast()

const railItems: YdWorkspaceRailItem[] = [
  { value: 'files', label: '文件', icon: 'morph:files' },
  { value: 'outline', label: '大纲', icon: 'morph:list' },
  { value: 'history', label: '历史', icon: 'morph:history' },
]
const rail = ref('files')
const sidebarOpen = ref(true)
const asideOpen = ref(true)
const pane = ref<'sidebar' | 'main' | 'aside'>('main')

interface DemoDoc { key: string, label: string, body: string, dirty: boolean }
const documents = ref<DemoDoc[]>([
  { key: 'intro', label: '快速开始', body: '正文内容：介绍知识库的写作方式。', dirty: false },
  { key: 'deploy', label: '部署指南', body: '正文内容：部署步骤与常见问题。', dirty: true },
  { key: 'faq', label: '常见问题', body: '正文内容：检索、发布与评论的说明。', dirty: false },
])
const activeKey = ref('intro')
const activeDoc = computed(() => documents.value.find(doc => doc.key === activeKey.value))

/** 关闭标签：由调用方用 nextWorkspaceTabKey 决定关闭后激活谁，组件不替调用方做主 */
function closeTab(key: string) {
  const nextActive = nextWorkspaceTabKey(documents.value, activeKey.value, key)
  documents.value = documents.value.filter(doc => doc.key !== key)
  activeKey.value = nextActive
}

function toggleDirty() {
  if (activeDoc.value) {
    activeDoc.value.dirty = !activeDoc.value.dirty
  }
}

const tabs = computed<YdWorkspaceTab[]>(() => documents.value.map(doc => ({
  key: doc.key,
  label: doc.label,
  icon: 'morph:file-text',
  dirty: doc.dirty,
})))

const statusItems = computed<YdWorkspaceStatusItem[]>(() => [
  { text: activeDoc.value ? activeDoc.value.label : '未打开文件', icon: 'morph:file-text' },
  { text: activeDoc.value?.dirty ? '未保存' : '已保存', icon: activeDoc.value?.dirty ? 'morph:circle-dot' : 'morph:circle-check', tone: activeDoc.value?.dirty ? 'warning' : 'success' },
  { text: `${documents.value.length} 个标签`, align: 'end', tone: 'muted' },
])

const layoutCode = `<YdWorkspaceLayout
  v-model:rail="rail"
  v-model:sidebar-open="sidebarOpen"
  v-model:aside-open="asideOpen"
  v-model:pane="pane"
  storage-key="demo-workspace"
  :rail-items="railItems"
  :status-items="statusItems"
>
  <template #header><!-- 工具条：保存、发布、面板开关 --></template>
  <template #sidebar><!-- 文件树 --></template>
  <template #main>
    <YdWorkspaceTabs v-model="activeKey" :tabs="tabs" addable @close="closeTab" />
    <!-- 编辑器 -->
  </template>
  <template #aside><!-- 属性面板 --></template>
</YdWorkspaceLayout>`
</script>

<template>
  <div class="flex flex-col gap-4">
    <DemoCard
      title="工作区外壳与多文件标签"
      description="活动栏切换侧栏视图，侧栏与属性栏之间可拖拽分栏（宽度按 storage-key 记忆）；标签支持中键、关闭按钮与 Delete 关闭，脏点表示未保存。窄屏自动退化为单面板 + 下拉切换。"
      :code="layoutCode"
    >
      <div class="border rounded-lg h-[520px] min-h-0 overflow-hidden">
        <YdWorkspaceLayout
          v-model:rail="rail"
          v-model:sidebar-open="sidebarOpen"
          v-model:aside-open="asideOpen"
          v-model:pane="pane"
          storage-key="showcase-workspace"
          :rail-items="railItems"
          :status-items="statusItems"
          aria-label="工作区演示"
        >
          <template #header>
            <YdButton size="sm" variant="outline" @click="toggleDirty">
              <YdIcon name="morph:circle-dot" /> 切换未保存状态
            </YdButton>
            <YdButton size="sm" variant="ghost" @click="sidebarOpen = !sidebarOpen">
              <YdIcon name="morph:panel-left-close" /> 侧栏
            </YdButton>
            <YdButton size="sm" variant="ghost" @click="asideOpen = !asideOpen">
              <YdIcon name="morph:panel-right-close" /> 属性栏
            </YdButton>
            <span class="text-xs text-muted-foreground ml-auto">拖拽分栏手柄可调整侧栏与属性栏宽度</span>
          </template>

          <template #sidebar>
            <div class="p-3 flex flex-col gap-1">
              <span class="text-xs text-muted-foreground px-1 pb-1">当前视图：{{ rail }}</span>
              <YdButton
                v-for="doc in documents"
                :key="doc.key"
                size="sm"
                :variant="doc.key === activeKey ? 'secondary' : 'ghost'"
                class="justify-start"
                @click="activeKey = doc.key"
              >
                <YdIcon name="morph:file-text" />
                <span class="truncate">{{ doc.label }}</span>
              </YdButton>
            </div>
          </template>

          <template #main>
            <div class="flex flex-col h-full min-h-0">
              <YdWorkspaceTabs
                v-model="activeKey"
                :tabs="tabs"
                addable
                aria-label="演示文件标签"
                @close="closeTab"
                @add="toast.info('新建标签：由调用方决定打开哪个目标')"
              />
              <div class="text-sm p-4 flex-1 min-h-0 overflow-auto">
                <template v-if="activeDoc">
                  <h3 class="text-base text-foreground font-semibold m-0">
                    {{ activeDoc.label }}
                  </h3>
                  <p class="text-muted-foreground mt-2">
                    {{ activeDoc.body }}
                  </p>
                </template>
                <YdEmpty v-else title="没有打开的标签" description="关闭全部标签后由页面展示空态。" />
              </div>
            </div>
          </template>

          <template #aside>
            <div class="text-sm p-3 flex flex-col gap-2">
              <span class="text-xs text-muted-foreground">属性</span>
              <div class="flex gap-2 items-center justify-between">
                <span class="text-muted-foreground">标签数</span>
                <span>{{ documents.length }}</span>
              </div>
              <div class="flex gap-2 items-center justify-between">
                <span class="text-muted-foreground">当前</span>
                <span class="truncate">{{ activeDoc?.label ?? '—' }}</span>
              </div>
            </div>
          </template>
        </YdWorkspaceLayout>
      </div>
    </DemoCard>
  </div>
</template>
