<script setup lang="ts">
import type { Component } from 'vue'
import { computed, inject, ref } from 'vue'
import { wikiManageAreaPanels, wikiManagePanelMeta, wikiManagePanels, wikiWorkbenchKey } from '../wiki-utils'
import WikiGeneratedPanel from './WikiGeneratedPanel.vue'
import WikiGraphPanel from './WikiGraphPanel.vue'
import WikiIngestPanel from './WikiIngestPanel.vue'
import WikiLintPanel from './WikiLintPanel.vue'
import WikiOverviewPanel from './WikiOverviewPanel.vue'
import WikiResearchPanel from './WikiResearchPanel.vue'
import WikiReviewPanel from './WikiReviewPanel.vue'
import WikiSearchPanel from './WikiSearchPanel.vue'
import WikiSettingsPanel from './WikiSettingsPanel.vue'
import WikiSourcesPanel from './WikiSourcesPanel.vue'
import WikiUnavailablePanel from './WikiUnavailablePanel.vue'

/**
 * 治理区单个区域的内容：区域内多分区时用横向子标签切换，右侧面板按选中分区动态加载。
 * 单独成组件是为了让 WikiManageView 能给设置版式的每个区域传显式插槽
 * （组件库模板编译不支持 v-for + 动态插槽名的组合，会直接崩）。
 */
const props = defineProps<{ areaKey: string }>()

const store = inject(wikiWorkbenchKey)!

const panelComponents: Record<string, Component> = {
  overview: WikiOverviewPanel,
  sources: WikiSourcesPanel,
  ingest: WikiIngestPanel,
  generated: WikiGeneratedPanel,
  review: WikiReviewPanel,
  lint: WikiLintPanel,
  research: WikiResearchPanel,
  search: WikiSearchPanel,
  graph: WikiGraphPanel,
  settings: WikiSettingsPanel,
  members: WikiUnavailablePanel,
  comments: WikiUnavailablePanel,
}

const areaPanels = computed(() => wikiManageAreaPanels(props.areaKey))
/** 区域内多个分区时提供横向子标签；单分区区域不显示多余的切换器 */
const areaTabs = computed(() => areaPanels.value.length > 1
  ? areaPanels.value.map(panel => ({ label: panel.label, value: panel.key }))
  : [])
const activePanelKey = computed({
  get: () => store.activePanel.value,
  set: (key: string) => store.openManage(key),
})
const activeMeta = computed(() => wikiManagePanelMeta(store.activePanel.value) || areaPanels.value[0] || wikiManagePanels[0])
const activeComponent = computed(() => panelComponents[store.activePanel.value] || WikiOverviewPanel)

/** 设置面板持有未保存表单：父级切换区域前用它确认，不能静默丢改动 */
const settingsRef = ref<{ dirty?: boolean } | null>(null)
defineExpose({
  settingsDirty: () => Boolean(settingsRef.value?.dirty),
})
</script>

<template>
  <div class="manage__area">
    <div v-if="areaTabs.length" class="manage__subhead">
      <YdSegmented
        v-model="activePanelKey"
        :options="areaTabs"
        size="sm"
        aria-label="治理分区切换"
      />
    </div>
    <WikiUnavailablePanel
      v-if="activeMeta.unavailable"
      :title="activeMeta.label"
      :icon="activeMeta.icon"
      :capability="activeMeta.unavailable"
    />
    <component
      :is="activeComponent"
      v-else
      ref="settingsRef"
    />
  </div>
</template>

<style scoped>
.manage__area {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-width: 0;
  min-height: 0;
}

/* 子标签行允许换行，长标题不再被头部 overflow 硬裁 */
.manage__subhead {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  min-width: 0;
}
</style>
