<script setup lang="ts">
import { computed, inject, ref } from 'vue'
import { rebuildWikiIndex } from '@/api/modules/platform-wiki'
import { wikiManageAreaPanels, wikiManageAreas, wikiWorkbenchKey } from '../wiki-utils'
import WikiManageArea from './WikiManageArea.vue'

/**
 * 治理区：整体采用设置版式（YdSettingsLayout）——左侧是一级区域导航，
 * 右侧按选中的区域动态加载对应的列表 / 表格 / 选项面板；区域内多分区时用横向子标签切换。
 * 治理区不暴露正文编辑器，也不复用作者区目录树。
 *
 * 每个区域显式写一个插槽（组件库模板编译不支持 v-for + 动态插槽名，会直接崩）；
 * 区域内容统一由 WikiManageArea 承载，避免六份重复结构。
 */
const store = inject(wikiWorkbenchKey)!
const toast = useYdToast()
const modal = useYdModal()
const rebuilding = ref(false)

// 一级区域导航：每个区域可含多个分区，区域内的分区用横向子标签切换
const navAreas = computed(() => wikiManageAreas.map(area => ({
  ...area,
  unavailable: wikiManageAreaPanels(area.key).every(panel => Boolean(panel.unavailable)),
})))

/** 设置版式的左导航：区域名即分区名；未接入区域在名称上直说，不用角标制造歧义 */
const areaList = computed(() => navAreas.value.map(area => ({
  icon: area.icon,
  label: area.unavailable ? `${area.label}（待接入）` : area.label,
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
    <YdSettingsLayout
      v-model="activeAreaKey"
      :list="areaList"
      surface
      scroll
      :title="wikiManageAreas.find(area => area.key === activeArea.key)?.label"
      class="manage__layout"
      aria-label="知识库治理分区"
    >
      <template #header>
        <div class="wiki-manage__header">
          <div class="wiki-manage__header-left">
            <YdTooltip text="返回知识库列表">
              <YdButton variant="ghost" size="icon-sm" aria-label="返回知识库列表" @click="store.openEntry()">
                <YdIcon name="morph:arrow-left" />
              </YdButton>
            </YdTooltip>
            <div class="wiki-manage__space-selector">
              <YdSelect
                v-model="selectedSpaceId"
                :options="spaceOptions"
                :disabled="!spaceOptions.length"
                placeholder="暂无知识库"
                aria-label="当前知识库"
                class="wiki-manage__space"
              />
              <div v-if="store.loadingTree.value" class="wiki-manage__sync" role="status">
                <YdIcon name="morph:loader-circle" class="wiki-manage__spin" />
                <span>同步中</span>
              </div>
            </div>
          </div>

          <div class="wiki-manage__actions">
            <YdButton 
              variant="outline" 
              size="sm" 
              :disabled="!store.spaceId.value" 
              @click="openAuthor"
              class="wiki-manage__action-btn"
            >
              <YdIcon name="morph:pencil" />
              编辑
            </YdButton>
            <YdButton 
              variant="outline" 
              size="sm" 
              :disabled="!store.spaceId.value" 
              :loading="rebuilding" 
              @click="rebuild"
              class="wiki-manage__action-btn"
            >
              <YdIcon name="morph:refresh-cw" />
              重建索引
            </YdButton>
          </div>
        </div>
      </template>

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
    </YdSettingsLayout>
  </div>
</template>

<style scoped>
/* 治理区是定高工作区：设置版式铺满白底，页头与分区导航固定，只有内容区滚动 */
.manage {
  flex: 1;
  min-width: 0;
  min-height: 0;
  background: var(--color-bg-1);
}

.manage__layout {
  flex: 1;
  min-height: 0;
}

/* 现代化的管理页头 */
.wiki-manage__header {
  display: flex;
  gap: 16px;
  align-items: center;
  padding: 12px 16px;
  background: linear-gradient(135deg, var(--color-bg-1) 0%, var(--color-fill-1) 100%);
  border-bottom: 1px solid var(--color-border-2);
  border-radius: 8px 8px 0 0;
}

.wiki-manage__header-left {
  display: flex;
  gap: 12px;
  align-items: center;
  flex: 1;
  min-width: 0;
}

.wiki-manage__space-selector {
  display: flex;
  gap: 8px;
  align-items: center;
  flex: 1;
  min-width: 0;
  max-width: 280px;
}

.wiki-manage__space {
  flex: 1;
  min-width: 120px;
}

.wiki-manage__sync {
  display: inline-flex;
  gap: 6px;
  align-items: center;
  padding: 4px 8px;
  font-size: 12px;
  color: rgb(var(--primary-6));
  background: rgb(var(--primary-1));
  border-radius: 4px;
  white-space: nowrap;
  flex-shrink: 0;
}

.wiki-manage__spin {
  animation: wiki-manage-spin 1s linear infinite;
  flex-shrink: 0;
}

@keyframes wiki-manage-spin {
  to {
    transform: rotate(360deg);
  }
}

.wiki-manage__actions {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-shrink: 0;
  margin-left: auto;
}

.wiki-manage__action-btn {
  display: inline-flex;
  gap: 6px;
  align-items: center;
  transition: all var(--yd-motion-duration-fast) var(--yd-motion-ease);
}

.wiki-manage__action-btn:hover:not(:disabled) {
  transform: translateY(-2px);
}

@media (width <= 1024px) {
  .wiki-manage__header {
    flex-direction: column;
    align-items: stretch;
    gap: 10px;
  }

  .wiki-manage__header-left {
    width: 100%;
  }

  .wiki-manage__space-selector {
    width: 100%;
    max-width: none;
  }

  .wiki-manage__actions {
    margin-left: 0;
    width: 100%;
  }

  .wiki-manage__action-btn {
    flex: 1;
    justify-content: center;
  }
}

@media (width <= 768px) {
  .wiki-manage__space-selector {
    flex-direction: column;
    align-items: stretch;
  }

  .wiki-manage__sync {
    width: 100%;
    justify-content: center;
  }

  .wiki-manage__actions {
    flex-direction: column;
  }

  .wiki-manage__action-btn {
    width: 100%;
  }
}
</style>
