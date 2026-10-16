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
/* 管理页：玻璃态 + 微交互 + 现代配色 */
.wiki-manage__header {
  display: flex;
  gap: 14px;
  align-items: center;
  padding: 10px 16px;
  background: linear-gradient(135deg, rgba(255,255,255,0.95) 0%, rgba(249,250,251,0.9) 100%);
  backdrop-filter: blur(20px);
  border-bottom: 1px solid rgba(0,0,0,0.08);
  border-radius: 0 0 12px 12px;
  box-shadow: 0 4px 16px rgba(0,0,0,0.08);
  position: relative;
  overflow: hidden;
}

.wiki-manage__header::before {
  content: '';
  position: absolute;
  inset: 0;
  background: 
    radial-gradient(circle at 0% 0%, rgba(99,102,241,0.08) 0%, transparent 40%),
    radial-gradient(circle at 100% 100%, rgba(139,92,246,0.08) 0%, transparent 50%);
  pointer-events: none;
}

.wiki-manage__header-left {
  display: flex;
  gap: 10px;
  align-items: center;
  flex: 1;
  min-width: 0;
  position: relative;
  z-index: 1;
}

.wiki-manage__space-selector {
  display: flex;
  gap: 10px;
  align-items: center;
  flex: 1;
  min-width: 0;
  max-width: 320px;
  background: linear-gradient(135deg, rgba(243,244,246,0.8), rgba(249,250,251,0.8));
  padding: 6px 12px;
  border-radius: 10px;
  border: 1px solid rgba(0,0,0,0.08);
  backdrop-filter: blur(10px);
  transition: all 300ms cubic-bezier(0.34, 1.56, 0.64, 1);
}

.wiki-manage__space-selector:hover {
  background: linear-gradient(135deg, rgba(249,250,251,0.9), rgba(255,255,255,0.9));
  box-shadow: 0 4px 12px rgba(0,0,0,0.08);
}

.wiki-manage__space {
  flex: 1;
  min-width: 140px;
  border: 1px solid rgba(0,0,0,0.08) !important;
  padding: 6px 10px !important;
  font-size: 13px;
  font-weight: 500;
  border-radius: 6px;
  background: linear-gradient(135deg, #ffffff, rgba(249,250,251,0.95)) !important;
  color: #1f2937;
  transition: all 200ms;
}

.wiki-manage__space:hover {
  border-color: #4f46e5 !important;
  box-shadow: 0 2px 8px rgba(79,70,229,0.1) !important;
}

.wiki-manage__space:focus {
  border-color: #4f46e5 !important;
  box-shadow: 0 0 0 3px rgba(79,70,229,0.1) !important;
}

.wiki-manage__sync {
  display: inline-flex;
  gap: 6px;
  align-items: center;
  padding: 5px 10px;
  font-size: 12px;
  font-weight: 500;
  color: #0891b2;
  background: linear-gradient(135deg, rgba(6,182,212,0.1), rgba(8,145,178,0.1));
  border: 1px solid rgba(8,145,178,0.2);
  border-radius: 6px;
  white-space: nowrap;
  flex-shrink: 0;
  backdrop-filter: blur(10px);
  transition: all 200ms;
}

.wiki-manage__sync:hover {
  background: linear-gradient(135deg, rgba(6,182,212,0.15), rgba(8,145,178,0.15));
  box-shadow: 0 2px 8px rgba(8,145,178,0.1);
}

.wiki-manage__spin {
  animation: spin 1.2s linear infinite;
  flex-shrink: 0;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.wiki-manage__actions {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-shrink: 0;
  margin-left: auto;
  position: relative;
  z-index: 1;
}

.wiki-manage__action-btn {
  display: inline-flex;
  gap: 6px;
  align-items: center;
  padding: 7px 14px;
  background: linear-gradient(135deg, #ffffff, rgba(249,250,251,0.95));
  border: 1px solid rgba(0,0,0,0.08);
  border-radius: 8px;
  cursor: pointer;
  font-size: 13px;
  font-weight: 500;
  color: #1f2937;
  transition: all 300ms cubic-bezier(0.34, 1.56, 0.64, 1);
  box-shadow: 0 2px 6px rgba(0,0,0,0.06);
  backdrop-filter: blur(10px);
  white-space: nowrap;
}

.wiki-manage__action-btn:hover {
  transform: translateY(-3px);
  box-shadow: 0 8px 16px rgba(0,0,0,0.12);
  background: linear-gradient(135deg, #ffffff, #fafbfc);
  border-color: #4f46e5;
}

.wiki-manage__action-btn:active {
  transform: translateY(-1px);
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
