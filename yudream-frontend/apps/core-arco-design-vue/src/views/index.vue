<script setup lang="ts">
import type { DashboardBreakpoint, DashboardCard, DashboardLayout, DashboardLayoutItem, DashboardWorkspace } from '@/api/modules/system-dashboard'
import apiDashboard from '@/api/modules/system-dashboard'
import { useAppFeatureStore } from '@/store/modules/app/features'
import DashboardCanvas from './dashboard/DashboardCanvas.vue'
import DashboardCapabilityStatsCard from './dashboard/DashboardCapabilityStatsCard.vue'
import DashboardChartStatsCard from './dashboard/DashboardChartStatsCard.vue'
import DashboardEndpointCard from './dashboard/DashboardEndpointCard.vue'
import DashboardGuideCard from './dashboard/DashboardGuideCard.vue'
import DashboardMobilePairCard from './dashboard/DashboardMobilePairCard.vue'
import DashboardModuleCard from './dashboard/DashboardModuleCard.vue'
import DashboardMonitorCard from './dashboard/DashboardMonitorCard.vue'
import DashboardPluginStatsCard from './dashboard/DashboardPluginStatsCard.vue'
import DashboardProfileCard from './dashboard/DashboardProfileCard.vue'
import DashboardQuickActionsCard from './dashboard/DashboardQuickActionsCard.vue'
import DashboardRemotePluginCard from './dashboard/DashboardRemotePluginCard.vue'
import { addLayoutCard, arrangeLayout, cloneItems, COLUMNS, normalizeLayout } from './dashboard/layout'
import { toneIconClass } from './dashboard/tone.ts'
import '@/assets/styles/dashboard-appearance.css'

interface DashboardInsight {
  label: string
  value: string
}

interface DashboardAction {
  code: string
  title: string
  description?: string
  icon?: string
  category?: string
  actionPath?: string
  tone?: string
}

const router = useRouter()
const toast = useFaToast()
const accountStore = useAppAccountStore()
const featureStore = useAppFeatureStore()
const menuStore = useAppMenuStore()

const loading = ref(false)
const loadError = ref('')
const saving = ref(false)
const editMode = ref(false)
const drawerVisible = ref(false)
const layoutMode = ref<'personal' | 'default'>('personal')
const workspace = ref<DashboardWorkspace | null>(null)
const defaultCards = ref<DashboardCard[]>([])
const defaultLayout = ref<DashboardLayout | null>(null)
const draftItems = ref<DashboardLayoutItem[]>([])
const activeBreakpoint = ref<DashboardBreakpoint>('lg')
const cardSearch = ref('')
const history = ref<DashboardLayoutItem[][]>([])
const historyIndex = ref(0)
const cards = computed(() => {
  const source = layoutMode.value === 'default' ? defaultCards.value : (workspace.value?.cards ?? [])
  return source.filter(canViewCard)
})
const cardMap = computed(() => new Map(cards.value.map(card => [card.code, card])))
const visibleItems = computed(() => draftItems.value.filter(item => item.visible && cardMap.value.has(item.cardCode)))
const hiddenCards = computed(() => cards.value.filter(card => !draftItems.value.some(item => item.cardCode === card.code && item.visible)))
const hiddenCardGroups = computed(() => {
  const groups = new Map<string, DashboardCard[]>()
  const query = cardSearch.value.trim().toLowerCase()
  for (const card of hiddenCards.value) {
    if (query && ![card.title, card.description, card.category, card.pluginCode].join(' ').toLowerCase().includes(query)) {
      continue
    }
    const groupName = card.source === 'PLUGIN' ? `插件 · ${card.pluginCode || '扩展'}` : (card.category || '系统')
    groups.set(groupName, [...(groups.get(groupName) ?? []), card])
  }
  return Array.from(groups.entries()).map(([name, items]) => ({ name, items }))
})
const canConfigureDefault = computed(() => accountStore.permissions.includes('*') || accountStore.permissions.includes('system:dashboard:config'))
const roleSummary = computed(() => accountStore.currentRole?.name || '未选择角色')
const deptSummary = computed(() => accountStore.currentDept?.name || '未选择部门')
const quickActions = computed(() => preferredMenuActions(menuActions()).slice(0, 8))
const layoutSummary = computed(() => workspace.value?.userLayout ? '个人工作空间' : '默认工作空间')
const breakpointLabel = computed(() => ({ lg: '宽屏', md: '桌面', sm: '平板', xs: '手机' })[activeBreakpoint.value])
const dirty = computed(() => JSON.stringify(draftItems.value) !== JSON.stringify(history.value[0]))
const canUndo = computed(() => historyIndex.value > 0)
const canRedo = computed(() => historyIndex.value < history.value.length - 1)

function canViewCard(card: DashboardCard) {
  return !card.permission || accountStore.permissions.includes('*') || accountStore.permissions.includes(card.permission)
}

onMounted(() => {
  featureStore.load()
  loadWorkspace()
})

async function loadWorkspace() {
  loading.value = true
  loadError.value = ''
  try {
    const res = await apiDashboard.workspace()
    workspace.value = res.data
    draftItems.value = initialItems(res.data.effectiveLayout)
  }
  catch (error) {
    loadError.value = error instanceof Error ? error.message : '工作台加载失败'
    toast.error(loadError.value)
  }
  finally {
    loading.value = false
  }
}

function beginHistory() {
  history.value = [cloneItems(draftItems.value)]
  historyIndex.value = 0
}

function initialItems(layout?: DashboardLayout | null) {
  const items = normalizeLayout(layout?.items ?? [], cards.value)
  // Preserve explicitly saved layouts. A generated first-visit layout uses the
  // same recommendation engine as the editor, on every supported screen.
  return layout?.id ? items : arrangeLayout(items, cards.value, 'recommended')
}

function changeLayout(items: DashboardLayoutItem[]) {
  if (!editMode.value || saving.value) {
    return
  }
  const next = normalizeLayout(items, cards.value)
  if (JSON.stringify(next) === JSON.stringify(draftItems.value)) {
    return
  }
  draftItems.value = next
  history.value = [...history.value.slice(0, historyIndex.value + 1), cloneItems(next)]
  historyIndex.value = history.value.length - 1
}

function undo() {
  if (canUndo.value) {
    draftItems.value = cloneItems(history.value[--historyIndex.value])
  }
}

function redo() {
  if (canRedo.value) {
    draftItems.value = cloneItems(history.value[++historyIndex.value])
  }
}

function startEdit() {
  layoutMode.value = 'personal'
  editMode.value = true
  beginHistory()
}

function openCardPicker() {
  if (!editMode.value) {
    startEdit()
  }
  drawerVisible.value = true
}

async function startDefaultEdit() {
  loading.value = true
  try {
    const [cardRes, layoutRes] = await Promise.all([apiDashboard.cards(), apiDashboard.defaultLayout()])
    defaultCards.value = cardRes.data
    defaultLayout.value = layoutRes.data
    layoutMode.value = 'default'
    draftItems.value = initialItems(layoutRes.data)
    editMode.value = true
    beginHistory()
  }
  catch (error) {
    toast.error(error instanceof Error ? error.message : '默认工作台加载失败')
  }
  finally {
    loading.value = false
  }
}

function cancelEdit() {
  editMode.value = false
  drawerVisible.value = false
  layoutMode.value = 'personal'
  draftItems.value = initialItems(workspace.value?.effectiveLayout)
}

async function saveLayout() {
  saving.value = true
  try {
    const payload = { items: cloneItems(draftItems.value) }
    if (layoutMode.value === 'default') {
      await apiDashboard.saveDefaultLayout(payload)
      toast.success('默认工作台已保存')
    }
    else {
      await apiDashboard.saveMyLayout(payload)
      toast.success('工作台布局已保存')
    }
    editMode.value = false
    drawerVisible.value = false
    layoutMode.value = 'personal'
    await loadWorkspace()
  }
  catch (error) {
    toast.error(error instanceof Error ? error.message : '保存失败，调整已保留，可以重试')
  }
  finally {
    saving.value = false
  }
}

function resetLayout() {
  changeLayout(initialItems(layoutMode.value === 'default' ? defaultLayout.value : workspace.value?.defaultLayout))
}

function arrange(mode: 'compact' | 'recommended') {
  changeLayout(arrangeLayout(draftItems.value, cards.value, mode, mode === 'compact' ? activeBreakpoint.value : undefined))
}

function addCard(card: DashboardCard) {
  changeLayout(addLayoutCard(draftItems.value, cards.value, card))
}

function hideCard(cardCode: string) {
  changeLayout(draftItems.value.map(item => item.cardCode === cardCode ? { ...item, visible: false } : item))
}

function resizeCard(card: DashboardCard, size: 'standard' | 'wide' | 'taller' | 'shorter') {
  const next = cloneItems(draftItems.value)
  const p = next.find(item => item.cardCode === card.code)!.placements[activeBreakpoint.value]!
  if (size === 'wide') {
    p.w = COLUMNS[activeBreakpoint.value]
    p.x = 0
  }
  else if (size === 'standard') {
    p.w = Math.min(card.defaultW, COLUMNS[activeBreakpoint.value])
    p.h = card.defaultH
  }
  else {
    p.h = Math.max(card.minH, p.h + (size === 'taller' ? 1 : -1))
  }
  changeLayout(next)
}

function moveCard(card: DashboardCard, direction: number) {
  const next = cloneItems(draftItems.value)
  const ordered = next.filter(item => item.visible && cardMap.value.has(item.cardCode)).sort((a, b) => {
    const pa = a.placements[activeBreakpoint.value]!
    const pb = b.placements[activeBreakpoint.value]!
    return pa.y - pb.y || pa.x - pb.x
  })
  const index = ordered.findIndex(item => item.cardCode === card.code)
  const target = Math.max(0, Math.min(ordered.length - 1, index + direction))
  ;[ordered[index], ordered[target]] = [ordered[target], ordered[index]]
  let y = 0
  for (const item of ordered) {
    const p = item.placements[activeBreakpoint.value]!
    p.y = y
    p.x = 0
    y += p.h
  }
  changeLayout(arrangeLayout(next, cards.value, 'compact', activeBreakpoint.value))
}

function cardMenu(card: DashboardCard) {
  return [
    [
      { label: '标准尺寸', icon: 'morph:square', handle: () => resizeCard(card, 'standard') },
      { label: '铺满一行', icon: 'morph:unfold-horizontal', handle: () => resizeCard(card, 'wide') },
      { label: '增加高度', icon: 'morph:plus', handle: () => resizeCard(card, 'taller') },
      { label: '减少高度', icon: 'morph:minus', handle: () => resizeCard(card, 'shorter') },
    ],
    [
      { label: '向前移动', icon: 'morph:arrow-up', handle: () => moveCard(card, -1) },
      { label: '向后移动', icon: 'morph:arrow-down', handle: () => moveCard(card, 1) },
      { label: '移出工作台', icon: 'morph:eye-off', handle: () => hideCard(card.code) },
    ],
  ]
}

function openCard(card?: { actionPath?: string }) {
  if (editMode.value || !card?.actionPath) {
    return
  }
  router.push(card.actionPath)
}

function openPublicHome() {
  if (!featureStore.cmsEnabled) {
    return
  }
  router.push({ name: 'publicSiteHome' })
}

function endpointUrl(card?: DashboardCard) {
  const path = card?.actionPath || ''
  if (!path) {
    return ''
  }
  const normalizedPath = path.startsWith('/') ? path : `/${path}`
  const envBase = import.meta.env.VITE_APP_API_BASEURL || window.location.origin
  const base = /^https?:\/\//i.test(envBase)
    ? envBase
    : `${window.location.origin}${envBase === '/' ? '' : envBase.startsWith('/') ? envBase : `/${envBase}`}`
  return `${base.replace(/\/$/, '')}${normalizedPath}`.replace(/\/$/, '')
}

function endpointDragPayload(card?: DashboardCard) {
  const url = endpointUrl(card)
  const template = card?.dragPayloadTemplate || '{url}'
  return template
    .replaceAll('{encodedUrl}', encodeURIComponent(url))
    .replaceAll('{url}', url)
}

async function copyEndpointUrl(card?: DashboardCard) {
  await navigator.clipboard.writeText(endpointUrl(card))
  toast.success('API 地址已复制')
}

function menuActions() {
  const actions: DashboardAction[] = []
  for (const group of menuStore.allMenus) {
    collectMenuActions(group.children ?? [], String(group.meta?.title || ''), actions)
  }
  const deduped = new Map<string, DashboardAction>()
  for (const action of actions) {
    if (action.actionPath && !deduped.has(action.actionPath)) {
      deduped.set(action.actionPath, action)
    }
  }
  return Array.from(deduped.values())
}

function collectMenuActions(menus: any[], category: string, target: DashboardAction[]) {
  for (const menu of menus) {
    if (menu.meta?.menu === false) {
      continue
    }
    const children = Array.isArray(menu.children) ? menu.children.filter((child: any) => child.meta?.menu !== false) : []
    const title = String(menu.meta?.title || '')
    if (children.length) {
      collectMenuActions(children, title || category, target)
      continue
    }
    const path = String(menu.path || '')
    if (!path || path === '/' || !title) {
      continue
    }
    target.push({
      code: path,
      title,
      description: category,
      category,
      icon: menu.meta?.icon || 'morph:arrow-right',
      actionPath: path,
      tone: toneForPath(path),
    })
  }
}

function preferredMenuActions(actions: DashboardAction[]) {
  const preferredPaths = [
    '/system/user',
    '/system/online-user',
    '/system/redis-monitor',
    '/system/api-log',
    '/system/login-log',
    '/platform/capability',
    '/platform/plugin',
  ]
  const byPath = new Map(actions.map(action => [action.actionPath, action]))
  const preferred = preferredPaths
    .map(path => byPath.get(path))
    .filter(Boolean) as DashboardAction[]
  const preferredSet = new Set(preferred.map(action => action.actionPath))
  return [
    ...preferred,
    ...actions.filter(action => !preferredSet.has(action.actionPath)),
  ]
}

function toneForPath(path: string) {
  if (path.startsWith('/system/')) {
    return 'blue'
  }
  if (path.startsWith('/platform/')) {
    return 'cyan'
  }
  return 'gray'
}

function moduleInsights(card?: DashboardCard): DashboardInsight[] {
  if (!card) {
    return []
  }
  return [
    { label: '来源', value: sourceText(card) },
    { label: '分类', value: card.category || '未分类' },
    { label: '访问控制', value: card.permission ? '需要对应权限' : '当前可见' },
  ]
}

function sourceText(card?: DashboardCard) {
  if (!card) {
    return '-'
  }
  return card.source === 'PLUGIN' ? (card.pluginCode || '插件') : '系统'
}

function displayCardTitle(card?: DashboardCard) {
  if (card?.component === 'PROFILE_SUMMARY') {
    return '欢迎回来'
  }
  return card?.title || ''
}

function resolveCardComponent(card?: DashboardCard) {
  if (card?.source === 'PLUGIN') {
    return DashboardRemotePluginCard
  }
  switch (card?.component) {
    case 'PROFILE_SUMMARY':
      return DashboardProfileCard
    case 'QUICK_ACTIONS':
      return DashboardQuickActionsCard
    case 'MONITOR_STATS':
      return DashboardMonitorCard
    case 'CAPABILITY_STATS':
      return DashboardCapabilityStatsCard
    case 'MOBILE_PAIR':
      return DashboardMobilePairCard
    case 'PLUGIN_STATS':
      return DashboardPluginStatsCard
    case 'DATAVIZ_CAPABILITY_STATS':
    case 'DATAVIZ_USER_REGISTRATION':
    case 'DATAVIZ_DEPT_CREATED':
    case 'DATAVIZ_LOG_ACTIVITY':
      return DashboardChartStatsCard
    case 'ENDPOINT_CARD':
      return DashboardEndpointCard
    case 'GUIDE_CARD':
      return DashboardGuideCard
    default:
      return DashboardModuleCard
  }
}

function genericCardProps(card: DashboardCard) {
  const base = {
    card,
    onOpen: openCard,
  }
  switch (card.component) {
    case 'PROFILE_SUMMARY':
      return {
        ...base,
        account: accountStore.account,
        dept: deptSummary.value,
        role: roleSummary.value,
        impersonating: accountStore.isImpersonating,
        impersonatorAccount: accountStore.impersonatorAccount,
        avatar: accountStore.avatar,
      }
    case 'QUICK_ACTIONS':
      return {
        ...base,
        actions: quickActions.value,
      }
    case 'MONITOR_STATS':
      return {
        ...base,
        permissions: accountStore.permissions,
      }
    case 'CAPABILITY_STATS':
    case 'PLUGIN_STATS':
    case 'MOBILE_PAIR':
      return base
    case 'ENDPOINT_CARD':
      return {
        ...base,
        endpointUrl: endpointUrl(card),
        dragPayload: endpointDragPayload(card),
        onCopy: () => copyEndpointUrl(card),
      }
    default:
      return {
        ...base,
        insights: moduleInsights(card),
      }
  }
}

function cardProps(card: DashboardCard) {
  return genericCardProps(card)
}
</script>

<template>
  <div class="dashboard-page">
    <div class="dashboard-shell">
      <div class="dashboard-header">
        <div class="dashboard-greeting">
          <div class="dashboard-greeting__title">
            <h1>工作台</h1>
            <span class="dashboard-layout-label">{{ editMode ? (layoutMode === 'default' ? '编辑默认布局' : '编辑个人布局') : layoutSummary }}</span>
          </div>
          <p v-if="editMode" class="dashboard-edit-hint">
            拖动标题调整位置，拖动右下角缩放；也可用卡片菜单调整。
          </p>
          <p v-else class="dashboard-edit-hint">
            从这里开始今天的工作。
          </p>
        </div>
        <div class="dashboard-toolbar">
          <FaButton v-if="!editMode && featureStore.cmsEnabled" variant="ghost" size="sm" @click="openPublicHome">
            <FaIcon name="morph:house" />
            访问首页
          </FaButton>
          <FaButton v-if="!editMode" size="sm" :disabled="!workspace || loading" @click="startEdit">
            <FaIcon name="morph:sliders-horizontal" />
            自定义工作台
          </FaButton>
          <FaDropdown v-if="!editMode && canConfigureDefault" :items="[[{ label: '配置默认工作台', icon: 'morph:users', handle: startDefaultEdit }]]" align="end">
            <FaButton variant="ghost" size="icon-sm" aria-label="工作台设置" :disabled="loading">
              <FaIcon name="morph:ellipsis" />
            </FaButton>
          </FaDropdown>
          <template v-if="editMode">
            <span class="dashboard-save-state" role="status">{{ dirty ? '有未保存的调整' : '已与保存的布局同步' }}</span>
            <FaButton variant="outline" size="sm" :disabled="saving" @click="cancelEdit">
              取消
            </FaButton>
            <FaButton size="sm" :loading="saving" @click="saveLayout">
              <FaIcon name="morph:check" />保存并退出
            </FaButton>
          </template>
        </div>
      </div>

      <div v-if="editMode" class="dashboard-editor-bar" :inert="saving || undefined">
        <div class="dashboard-editor-bar__group">
          <FaButton size="sm" @click="drawerVisible = true">
            <FaIcon name="morph:plus" />添加卡片
          </FaButton>
          <FaButton size="icon-sm" variant="ghost" :disabled="!canUndo" aria-label="撤销调整" @click="undo">
            <FaIcon name="morph:undo-2" />
          </FaButton>
          <FaButton size="icon-sm" variant="ghost" :disabled="!canRedo" aria-label="重做调整" @click="redo">
            <FaIcon name="morph:redo-2" />
          </FaButton>
          <span class="dashboard-editor-divider" />
          <FaButton size="sm" variant="ghost" title="保持尺寸，收紧当前屏幕的卡片间隙" @click="arrange('compact')">
            <FaIcon name="morph:align-start-vertical" />整理空隙
          </FaButton>
          <FaButton size="sm" variant="ghost" title="为所有屏幕重新推荐尺寸与位置，可撤销" @click="arrange('recommended')">
            <FaIcon name="morph:panels-top-left" />推荐排布
          </FaButton>
          <FaButton size="sm" variant="ghost" @click="resetLayout">
            使用默认布局
          </FaButton>
        </div>
        <span class="dashboard-editor-caption">{{ breakpointLabel }} · {{ COLUMNS[activeBreakpoint] }} 列 · {{ visibleItems.length }} 张卡片</span>
      </div>

      <div v-if="loading && !workspace" class="dashboard-loading" role="status" aria-live="polite">
        <div class="dashboard-loading__preview" aria-hidden="true">
          <div v-for="index in 6" :key="index" class="dashboard-skeleton">
            <span />
            <strong />
            <i />
          </div>
        </div>
        <span class="dashboard-loading__label">正在准备你的工作台…</span>
      </div>

      <div v-else-if="loadError && !workspace" class="dashboard-empty" role="status">
        <FaIcon name="morph:cloud-off" />
        <strong>工作台暂时无法加载</strong>
        <p>{{ loadError }}</p>
        <FaButton variant="outline" size="sm" @click="loadWorkspace">
          重新加载
        </FaButton>
      </div>

      <DashboardCanvas v-else :items="draftItems" :cards="cards" :editing="editMode && !saving" @change="changeLayout" @breakpoint="activeBreakpoint = $event">
        <template #default="{ card, index, ready }">
          <article
            class="grid-stack-item-content dashboard-card"
            :class="{
              'dashboard-card--welcome': card.component === 'PROFILE_SUMMARY',
              'dashboard-card--quick': card.component === 'QUICK_ACTIONS',
            }"
            :style="{ '--dashboard-card-index': Math.min(index, 7) }"
            :aria-label="card.title"
          >
            <header class="dashboard-card__header">
              <div class="dashboard-card__heading">
                <FaIcon v-if="editMode" name="morph:grip-vertical" class="dashboard-card__grip" />
                <span v-else class="dashboard-card__icon" :class="toneIconClass(card.tone)">
                  <FaIcon :name="card.icon || 'morph:layout-grid'" />
                </span>
                <div class="dashboard-card__title-block">
                  <h2>{{ displayCardTitle(card) }}</h2>
                </div>
              </div>
              <FaDropdown v-if="editMode" :items="cardMenu(card)" align="end">
                <FaButton variant="ghost" size="icon-sm" :aria-label="`调整${card.title}`">
                  <FaIcon name="morph:ellipsis" />
                </FaButton>
              </FaDropdown>
            </header>
            <div class="dashboard-card__body" :inert="editMode || undefined">
              <component :is="resolveCardComponent(card)" v-if="ready" v-bind="cardProps(card)" />
            </div>
          </article>
        </template>
      </DashboardCanvas>
      <div v-if="!loading && workspace && !visibleItems.length" class="dashboard-empty">
        <FaIcon name="morph:panels-top-left" />
        <strong>从一张卡片开始</strong>
        <p>把常用入口与关心的数据放到工作台。</p>
        <FaButton size="sm" @click="openCardPicker">
          <FaIcon name="morph:plus" />
          添加卡片
        </FaButton>
      </div>
    </div>

    <FaDrawer v-model="drawerVisible" title="卡片库" :footer="false" content-class="w-full sm:max-w-[460px]">
      <div class="card-picker">
        <p class="card-picker-intro">
          系统信息与插件内容，都可以自由组合。添加后保存布局即可保留。
        </p>
        <FaInput v-model="cardSearch" clearable placeholder="搜索卡片、功能或插件" aria-label="搜索卡片" />
        <div v-for="group in hiddenCardGroups" :key="group.name" class="card-picker-group">
          <div class="card-picker-group__title">
            <strong>{{ group.name }}</strong>
            <span>{{ group.items.length }}</span>
          </div>
          <button v-for="card in group.items" :key="card.code" type="button" class="picker-card" :aria-label="`添加${card.title}`" @click="addCard(card)">
            <span class="picker-card__icon" :class="toneIconClass(card.tone)">
              <FaIcon :name="card.icon || 'morph:layout-grid'" />
            </span>
            <span class="picker-card__main">
              <strong>{{ card.title }}</strong>
              <small>{{ card.description || card.category }}</small>
            </span>
            <span class="picker-card__add">
              <FaIcon name="morph:plus" />
            </span>
          </button>
        </div>
        <div v-if="!hiddenCardGroups.length" class="picker-empty">
          <FaIcon name="morph:search" />
          <strong>{{ cardSearch ? '没有匹配的卡片' : '所有可用卡片已加入工作台' }}</strong>
        </div>
      </div>
    </FaDrawer>
  </div>
</template>

<style scoped>
.dashboard-page {
  position: absolute;
  inset: 0;
  overflow: auto;
  background: var(--g-main-area-bg, var(--color-bg-1));
}

.dashboard-shell {
  width: min(1560px, calc(100% - var(--yd-workspace-gap, 24px) * 2));
  padding: var(--yd-workspace-gap, 24px) 0 40px;
  margin: 0 auto;
}

.dashboard-header {
  display: flex;
  gap: var(--yd-workspace-gap, 24px);
  align-items: center;
  justify-content: space-between;
  min-height: 40px;
}

.dashboard-greeting {
  display: grid;
  gap: 7px;
  min-width: 0;
}

.dashboard-greeting__title {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
}

.dashboard-greeting h1 {
  margin: 0;
  font-size: 26px;
  font-weight: 700;
  line-height: 1.3;
  color: var(--color-text-1);
  letter-spacing: -0.04em;
}

.dashboard-layout-label,
.dashboard-card__source {
  padding: 4px 8px;
  font-size: 11px;
  line-height: 1.2;
  color: var(--color-text-3);
  white-space: nowrap;
  background: var(--color-fill-2);
  border: 1px solid var(--color-border-1);
  border-radius: 6px;
}

.dashboard-edit-hint {
  margin: 0;
  font-size: 13px;
  line-height: 1.6;
  color: var(--color-text-3);
}

.dashboard-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  justify-content: flex-end;
}

/* The frame is identical in view and edit; controls share a fixed header. */
.grid-stack-item-content.dashboard-card {
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
  container-type: inline-size;
  overflow: hidden;
  background: var(--color-bg-2);
  border: 1px solid var(--color-border-2);
  border-radius: var(--yd-surface-radius, 18px);
  box-shadow: var(--yd-surface-shadow);
  transition: box-shadow var(--yd-motion-duration-normal) ease;
  animation: dashboard-card-enter var(--yd-motion-duration-slow) var(--yd-motion-ease) both;
  animation-delay: calc(var(--dashboard-card-index, 0) * var(--yd-motion-stagger));
}

.dashboard-card:hover,
.dashboard-card:focus-within {
  box-shadow: var(--yd-surface-shadow-hover);
}

.grid-stack-item-content.dashboard-card--welcome {
  color: var(--yd-dashboard-feature-text);
  background: var(--yd-dashboard-feature-bg);
  border-color: transparent;
}

.dashboard-card--welcome .dashboard-card__header,
.dashboard-card--welcome .dashboard-card__title-block h2 {
  color: var(--yd-dashboard-feature-muted);
}

.dashboard-card--welcome .dashboard-card__icon {
  color: inherit;
  background: transparent;
}

.dashboard-card__header {
  display: flex;
  flex: 0 0 56px;
  gap: 10px;
  align-items: center;
  min-width: 0;
  padding: 10px 20px;
}

.dashboard-card__heading {
  display: flex;
  flex: 1;
  gap: 10px;
  align-items: center;
  min-width: 0;
}

.dashboard-card__icon,
.dashboard-card__grip {
  display: grid;
  flex: 0 0 24px;
  place-items: center;
  width: 24px;
  height: 24px;
  font-size: 17px;
}

.dashboard-card__title-block {
  min-width: 0;
}

.dashboard-card__title-block h2 {
  margin: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 13px;
  font-weight: 600;
  line-height: 1.4;
  color: var(--color-text-2);
  white-space: nowrap;
}

.dashboard-card__body {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
}

.dashboard-card__body :deep(.dashboard-card__content) {
  flex: 1;
  min-width: 0;
  min-height: 0;
  margin: 0 20px 20px;
  overflow: auto;
}

.dashboard-card__body :deep(.dashboard-remote-host > .dashboard-card__content) {
  margin: 0;
}

.dashboard-card__body :deep(.dashboard-chart-stats) {
  overflow: hidden;
}

.dashboard-editor-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  margin-top: 20px;
  background: var(--color-bg-2);
  border: 1px solid var(--color-border-2);
  border-radius: var(--yd-control-radius);
}

.dashboard-editor-bar__group {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  align-items: center;
}

.dashboard-editor-divider {
  width: 1px;
  height: 18px;
  margin: 0 6px;
  background: var(--color-border-2);
}

.dashboard-editor-caption,
.dashboard-save-state,
.card-picker-intro {
  font-size: 12px;
  line-height: 1.6;
  color: var(--color-text-3);
}

.card-picker-intro {
  margin: 0;
}

.dashboard-loading {
  position: relative;
  margin-top: 26px;
}

.dashboard-loading__preview {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 18px;
}

.dashboard-skeleton {
  display: grid;
  gap: 18px;
  align-content: start;
  height: 238px;
  padding: 26px;
  background: var(--color-bg-2);
  border: 1px solid var(--color-border-2);
  border-radius: var(--yd-surface-radius, 18px);
}

.dashboard-skeleton :is(span, strong, i) {
  display: block;
  height: 14px;
  background: var(--color-fill-2);
  border-radius: 6px;
  animation: dashboard-skeleton-pulse 1.6s ease-in-out infinite alternate;
}

.dashboard-skeleton span {
  width: 45%;
}

.dashboard-skeleton strong {
  width: 72%;
  height: 34px;
  margin-top: 16px;
}

.dashboard-skeleton i {
  width: 90%;
}

.dashboard-loading__label {
  position: absolute;
  inset: 50% auto auto 50%;
  padding: 10px 16px;
  font-size: 13px;
  color: var(--color-text-2);
  white-space: nowrap;
  background: var(--color-bg-2);
  border: 1px solid var(--color-border-2);
  border-radius: 999px;
  transform: translate(-50%, -50%);
}

.dashboard-empty {
  display: flex;
  flex-direction: column;
  gap: 12px;
  align-items: center;
  justify-content: center;
  min-height: 280px;
  padding: 32px;
  margin-top: 20px;
  color: var(--color-text-3);
  text-align: center;
  background: var(--color-bg-2);
  border: 1px dashed var(--color-border-2);
  border-radius: var(--yd-surface-radius, 18px);
}

.dashboard-empty > :deep(.fa-icon) {
  margin-bottom: 6px;
  font-size: 32px;
}

.dashboard-empty strong {
  font-size: 16px;
  color: var(--color-text-1);
}

.dashboard-empty p {
  margin: 0 0 8px;
  font-size: 13px;
}

.card-picker {
  display: grid;
  gap: 18px;
}

.card-picker-group {
  display: grid;
  gap: 10px;
  min-width: 0;
}

.card-picker-group__title {
  display: flex;
  gap: 10px;
  align-items: center;
  justify-content: space-between;
  min-width: 0;
}

.card-picker-group__title strong {
  min-width: 0;
  padding-left: 10px;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 13px;
  font-weight: 700;
  color: var(--color-text-2);
  white-space: nowrap;
  border-left: 3px solid var(--color-text-1);
}

.card-picker-group__title span {
  display: grid;
  flex: 0 0 auto;
  place-items: center;
  min-width: 22px;
  height: 22px;
  padding: 0 7px;
  font-size: 12px;
  color: var(--color-text-3);
  background: var(--color-fill-2);
  border-radius: 999px;
}

.picker-card {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  gap: 12px;
  align-items: flex-start;
  min-width: 0;
  padding: 14px;
  color: var(--color-text-1);
  text-align: left;
  cursor: pointer;
  background: var(--color-bg-2);
  border: 1px solid var(--color-border-2);
  border-radius: 10px;
  transition: border-color var(--yd-motion-duration-fast, 140ms) ease, background var(--yd-motion-duration-fast, 140ms) ease, transform var(--yd-motion-duration-normal, 240ms) var(--yd-motion-ease, ease);
}

.picker-card:hover {
  background: var(--color-fill-2);
  border-color: var(--color-border-3);
  transform: translateY(var(--yd-motion-lift, -3px));
}

.picker-card__icon {
  display: grid;
  flex: 0 0 auto;
  place-items: center;
  width: 24px;
  height: 24px;
  font-size: 16px;
}

.picker-card__main {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.picker-card__main strong {
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 13px;
  font-weight: 600;
  white-space: nowrap;
}

.picker-card__main small {
  display: -webkit-box;
  overflow: hidden;
  -webkit-line-clamp: 2;
  font-size: 12px;
  line-height: 1.45;
  color: var(--color-text-3);
  overflow-wrap: anywhere;
  -webkit-box-orient: vertical;
}

.picker-card__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
  min-width: 0;
}

.picker-card__meta em {
  max-width: 100%;
  padding: 2px 6px;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 11px;
  font-style: normal;
  line-height: 1.2;
  color: var(--color-text-3);
  white-space: nowrap;
  background: var(--color-fill-2);
  border-radius: 999px;
}

.picker-card__add {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  font-size: 15px;
  color: var(--color-text-3);
}

.picker-empty {
  display: grid;
  gap: 8px;
  justify-items: center;
  padding: 36px 12px;
  color: var(--color-text-3);
  text-align: center;
  border: 1px dashed var(--color-border-2);
  border-radius: 10px;
}

.picker-empty :deep(.fa-icon) {
  font-size: 28px;
  color: var(--color-text-2);
}

.picker-empty strong {
  font-size: 14px;
  color: var(--color-text-2);
}

@keyframes dashboard-card-enter {
  from {
    opacity: 0;
    transform: translateY(var(--yd-motion-enter-y, 14px));
  }

  to {
    opacity: 1;
    transform: none;
  }
}

@keyframes dashboard-skeleton-pulse {
  to {
    opacity: 0.45;
  }
}

:root[data-admin-motion="reduced"] .dashboard-skeleton :is(span, strong, i) {
  animation: none;
}

@media (width <= 768px) {
  .dashboard-shell {
    width: calc(100% - 24px);
    padding-top: 20px;
    padding-bottom: calc(84px + env(safe-area-inset-bottom));
  }

  .dashboard-header {
    flex-direction: column;
    align-items: stretch;
  }

  .dashboard-toolbar {
    justify-content: flex-start;
    width: 100%;
  }

  .dashboard-greeting h1 {
    font-size: 23px;
  }

  .dashboard-loading__preview {
    grid-template-columns: 1fr;
  }

  .dashboard-skeleton:nth-child(n + 3) {
    display: none;
  }
}

@media (width <= 480px) {
  .dashboard-card__title-block h2 {
    display: -webkit-box;
    -webkit-line-clamp: 2;
    white-space: normal;
    -webkit-box-orient: vertical;
  }
}

@media (prefers-reduced-motion: reduce) {
  .dashboard-card,
  .dashboard-skeleton :is(span, strong, i) {
    animation: none;
  }

  .dashboard-card,
  .picker-card,
  .card-hide-button,
  .card-drag-handle {
    transition: none;
  }

  .picker-card:hover {
    transform: none;
  }
}
</style>
