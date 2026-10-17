<script setup lang="ts">
import { computed, inject, ref } from 'vue'
import { rebuildWikiIndex } from '@/api/modules/platform-wiki'
import { wikiManageAreas, wikiWorkbenchKey } from '../wiki-utils'
import WikiManageArea from './WikiManageArea.vue'

/**
 * 治理区：顶部横向区域 Tab（概览 / 内容 / 质量 / 洞察 / 协作 / 设置）+ 全宽内容区。
 *
 * 区域导航放顶部而不是左侧竖排：治理面板以表格与统计为主，横向 Tab 释放整行宽度，
 * 在宽屏上不再把内容挤进中间窄列；区域内多分区时仍用横向子标签切换。
 * 设置面板（WikiSettingsPanel）内部保持 YdSettingsLayout 的设置版式，两者互不干扰。
 *
 * 每个区域显式写一个插槽（组件库模板编译不支持 v-for + 动态插槽名，会直接崩）；
 * 区域内容统一由 WikiManageArea 承载，避免六份重复结构。
 */
const store = inject(wikiWorkbenchKey)!
const toast = useYdToast()
const modal = useYdModal()
const rebuilding = ref(false)

// 一级区域导航：每个区域可含多个分区，区域内的分区用横向子标签切换
const areaList = computed(() => wikiManageAreas.map(area => ({
  icon: area.icon,
  label: area.label,
  value: area.key,
})))

const activeArea = computed(() => wikiManageAreas.find(area => area.panels.includes(store.activePanel.value)) || wikiManageAreas[0])

/** 当前挂载的区域内容：切换区域前用它的设置脏状态确认，避免静默丢改动 */
const areaInstances = new Map<string, InstanceType<typeof WikiManageArea>>()

function registerArea(areaKey: string, instance: unknown) {
  if (instance) {
    areaInstances.set(areaKey, instance as InstanceType<typeof WikiManageArea>)
  }
  else {
    areaInstances.delete(areaKey)
  }
}

const activeAreaKey = computed({
  get: () => activeArea.value.key,
  set: async (key: string) => {
    const area = wikiManageAreas.find(item => item.key === key)
    if (!area || area.key === activeArea.value.key) {
      return
    }
    if (activeArea.value.key === 'settings' && areaInstances.get('settings')?.settingsDirty()) {
      const confirmed = await new Promise<boolean>((resolve) => {
        modal.confirm({
          title: '离开未保存的设置？',
          content: '知识库设置已修改但尚未保存，继续将丢失这些修改。',
          confirmButtonText: '放弃修改',
          onConfirm: () => resolve(true),
          onCancel: () => resolve(false),
        })
      })
      if (!confirmed) {
        return
      }
    }
    store.openManage(area.panels[0])
  },
})

const spaceOptions = computed(() => store.spaces.value.filter(item => item.id).map(item => ({
  label: item.name,
  value: item.id!,
})))
const selectedSpaceId = computed({
  get: () => store.spaceId.value,
  set: (id: string) => {
    const target = store.spaces.value.find(item => item.id === id)
    if (target) {
      store.openManage(store.activePanel.value, target)
    }
  },
})

async function rebuild() {
  if (!store.spaceId.value || rebuilding.value) {
    return
  }
  rebuilding.value = true
  try {
    await rebuildWikiIndex(store.spaceId.value)
    toast.success('已加入重建索引队列')
    store.openManage('ingest')
  }
  catch {
    // 失败原因由接口层统一提示，按钮只负责恢复可用
  }
  finally {
    rebuilding.value = false
  }
}

function openAuthor() {
  store.openAuthor()
}
</script>

<template>
  <div class="manage">
    <!-- 上下文条：当前知识库 + 全库级动作，保持一行，不抢 Tab 的视觉主位 -->
    <div class="manage__bar">
      <div class="manage__bar-left">
        <YdTooltip text="返回知识库列表">
          <YdButton variant="ghost" size="icon-sm" aria-label="返回知识库列表" @click="store.openEntry()">
            <YdIcon name="morph:arrow-left" />
          </YdButton>
        </YdTooltip>
        <span class="manage__bar-label">治理</span>
        <YdSelect
          v-model="selectedSpaceId"
          :options="spaceOptions"
          :disabled="!spaceOptions.length"
          placeholder="暂无知识库"
          aria-label="当前知识库"
          class="manage__space"
        />
        <span v-if="store.loadingTree.value" class="manage__sync" role="status">
          <YdIcon name="morph:loader-circle" class="manage__spin" />
          同步中
        </span>
      </div>

      <div class="manage__bar-actions">
        <YdButton variant="outline" size="sm" :disabled="!store.spaceId.value" @click="openAuthor">
          <YdIcon name="morph:pencil" />
          编辑
        </YdButton>
        <YdButton variant="outline" size="sm" :disabled="!store.spaceId.value" :loading="rebuilding" @click="rebuild">
          <YdIcon name="morph:refresh-cw" />
          重建索引
        </YdButton>
      </div>
    </div>

    <YdTabs
      v-model="activeAreaKey"
      :list="areaList"
      class="manage__tabs"
      aria-label="知识库治理分区"
      content-class="manage__content"
    >
      <template #overview>
        <WikiManageArea :ref="(instance) => registerArea('overview', instance)" area-key="overview" />
      </template>
      <template #content>
        <WikiManageArea :ref="(instance) => registerArea('content', instance)" area-key="content" />
      </template>
      <template #quality>
        <WikiManageArea :ref="(instance) => registerArea('quality', instance)" area-key="quality" />
      </template>
      <template #insights>
        <WikiManageArea :ref="(instance) => registerArea('insights', instance)" area-key="insights" />
      </template>
      <template #collaboration>
        <WikiManageArea :ref="(instance) => registerArea('collaboration', instance)" area-key="collaboration" />
      </template>
      <template #settings>
        <WikiManageArea :ref="(instance) => registerArea('settings', instance)" area-key="settings" />
      </template>
    </YdTabs>
  </div>
</template>

<style scoped>
.manage {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
  background: var(--color-bg-1);
}

/* 上下文条：单行、可换行，去掉玻璃态与阴影，靠色阶与边框分隔 */
.manage__bar {
  display: flex;
  flex-shrink: 0;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  padding: 10px 16px;
  border-bottom: 1px solid var(--color-border-2);
}

.manage__bar-left {
  display: flex;
  flex: 1;
  gap: 8px;
  align-items: center;
  min-width: 0;
}

.manage__bar-label {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-1);
  white-space: nowrap;
}

.manage__space {
  width: 100%;
  max-width: 260px;
}

.manage__sync {
  display: inline-flex;
  flex-shrink: 0;
  gap: 5px;
  align-items: center;
  font-size: 12px;
  color: var(--color-text-3);
  white-space: nowrap;
}

.manage__spin {
  animation: manage-spin 1.2s linear infinite;
}

@keyframes manage-spin {
  to {
    transform: rotate(360deg);
  }
}

.manage__bar-actions {
  display: flex;
  flex-shrink: 0;
  gap: 8px;
  align-items: center;
}

/*
 * 顶部区域 Tab：压掉组件库的分段胶囊外观，改为页面级横向标签——透明底、
 * 左对齐、活动项用品牌色下划线标记。用 :deep 触达 Tabs 内部元素，
 * 重叠一层 .manage__tabs 以外层类垫高优先级，压过未分层工具类。
 */
.manage__tabs.manage__tabs {
  flex: 1;
  min-height: 0;
}

.manage__tabs :deep([data-slot="tabs-list"]) {
  display: flex;
  flex-wrap: wrap;
  gap: 2px;
  justify-content: flex-start;
  width: 100%;
  height: auto;
  padding: 0 12px;
  background: transparent;
  border-bottom: 1px solid var(--color-border-2);
  border-radius: 0;
}

.manage__tabs :deep([data-slot="tabs-trigger"]) {
  flex: 0 0 auto;
  width: auto;
  height: 36px;
  padding: 0 10px;
  font-size: 13px;
  color: var(--color-text-2);
  background: transparent;
  border: 0;
  border-radius: 0;
  box-shadow: inset 0 -2px 0 0 transparent;
  transition: color 0.15s ease, box-shadow 0.15s ease;
}

.manage__tabs :deep([data-slot="tabs-trigger"]:hover) {
  color: var(--color-text-1);
}

.manage__tabs :deep([data-slot="tabs-trigger"][data-state="active"]) {
  font-weight: 600;
  color: var(--color-text-1);
  background: transparent;
  box-shadow: inset 0 -2px 0 0 var(--color-text-1);
}

/* 内容舞台与面板自带底色同为 fill-1，内边距不会露白；面板各自内部滚动 */
.manage__tabs :deep(.manage__content) {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
  padding: 14px 16px 16px;
  overflow: hidden;
  background: var(--color-fill-1);
}

@media (width <= 768px) {
  .manage__bar {
    align-items: stretch;
  }

  .manage__space {
    max-width: none;
  }

  .manage__bar-actions {
    width: 100%;
  }

  .manage__bar-actions :deep([data-slot="button"]) {
    flex: 1;
    justify-content: center;
  }

  .manage__tabs :deep(.manage__content) {
    padding: 12px;
  }
}
</style>
