<script setup lang="ts">
import type { GridStack, GridStackNode, GridStackOptions, GridStackWidget } from 'gridstack'
import type { DashboardBreakpoint, DashboardCard, DashboardGridPlacement, DashboardLayout, DashboardLayoutItem, DashboardWorkspace } from '@/api/modules/system-dashboard'
import { nextTick } from 'vue'
import apiDashboard from '@/api/modules/system-dashboard'
import DashboardCapabilityStatsCard from './dashboard/DashboardCapabilityStatsCard.vue'
import DashboardChartStatsCard from './dashboard/DashboardChartStatsCard.vue'
import DashboardEndpointCard from './dashboard/DashboardEndpointCard.vue'
import DashboardModuleCard from './dashboard/DashboardModuleCard.vue'
import DashboardMonitorCard from './dashboard/DashboardMonitorCard.vue'
import DashboardPluginStatsCard from './dashboard/DashboardPluginStatsCard.vue'
import DashboardProfileCard from './dashboard/DashboardProfileCard.vue'
import DashboardQuickActionsCard from './dashboard/DashboardQuickActionsCard.vue'
import DashboardRemotePluginCard from './dashboard/DashboardRemotePluginCard.vue'
import { toneIconClass } from './dashboard/tone.ts'
import 'gridstack/dist/gridstack.min.css'

type DashboardKey = 'administrator' | 'people' | 'content' | 'platform' | 'monitor' | 'personal'

const BREAKPOINTS: DashboardBreakpoint[] = ['lg', 'md', 'sm', 'xs']
const BREAKPOINT_COLUMNS: Record<DashboardBreakpoint, number> = {
  lg: 12,
  md: 8,
  sm: 4,
  xs: 1,
}

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
const menuStore = useAppMenuStore()

const loading = ref(false)
const saving = ref(false)
const editMode = ref(false)
const drawerVisible = ref(false)
const layoutMode = ref<'personal' | 'default'>('personal')
const workspace = ref<DashboardWorkspace | null>(null)
const defaultCards = ref<DashboardCard[]>([])
const defaultLayout = ref<DashboardLayout | null>(null)
const draftItems = ref<DashboardLayoutItem[]>([])
const gridRef = ref<HTMLElement | null>(null)
const viewportWidth = ref(typeof window === 'undefined' ? 1440 : window.innerWidth)

let grid: GridStack | null = null

const cards = computed(() => {
  const source = layoutMode.value === 'default' ? defaultCards.value : (workspace.value?.cards ?? [])
  return source.filter(canViewCard)
})
const cardMap = computed(() => new Map(cards.value.map(card => [card.code, card])))
const activeBreakpoint = computed<DashboardBreakpoint>(() => {
  if (viewportWidth.value < 640) {
    return 'xs'
  }
  if (viewportWidth.value < 960) {
    return 'sm'
  }
  if (viewportWidth.value < 1280) {
    return 'md'
  }
  return 'lg'
})
const visibleItems = computed(() => draftItems.value.filter(item => item.visible && cardMap.value.has(item.cardCode)))
const hiddenCards = computed(() => cards.value.filter((card) => {
  const item = draftItems.value.find(row => row.cardCode === card.code)
  return !item || !item.visible
}))
const hiddenCardGroups = computed(() => {
  const groups = new Map<string, DashboardCard[]>()
  for (const card of hiddenCards.value) {
    const groupName = card.source === 'PLUGIN' ? `插件：${card.pluginCode || '未命名插件'}` : (card.category || '系统')
    groups.set(groupName, [...(groups.get(groupName) ?? []), card])
  }
  return Array.from(groups.entries()).map(([name, items]) => ({ name, items }))
})
const canConfigureDefault = computed(() => accountStore.permissions.includes('*') || accountStore.permissions.includes('system:dashboard:config'))
const roleSummary = computed(() => accountStore.currentRole ? `${accountStore.currentRole.name} / ${accountStore.currentRole.code}` : '未选择角色')
const deptSummary = computed(() => accountStore.currentDept?.name || '未选择部门')
const drawerWidth = computed(() => viewportWidth.value < 640 ? '100%' : 460)
const quickActions = computed(() => preferredMenuActions(menuActions()).slice(0, 8))

function canViewCard(card: DashboardCard) {
  return !card.permission || accountStore.permissions.includes('*') || accountStore.permissions.includes(card.permission)
}

onMounted(async () => {
  window.addEventListener('resize', handleResize)
  await loadWorkspace()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  destroyGrid()
})

watch([activeBreakpoint, editMode, () => visibleItems.value.map(item => item.cardCode).join('|')], async () => {
  await renderGrid()
})

async function loadWorkspace() {
  loading.value = true
  try {
    const res = await apiDashboard.workspace()
    workspace.value = res.data
    draftItems.value = cloneItems(res.data.effectiveLayout.items)
    await renderGrid()
  }
  catch (error) {
    toast.error(error instanceof Error ? error.message : '首页加载失败')
  }
  finally {
    loading.value = false
  }
}

function handleResize() {
  viewportWidth.value = window.innerWidth
}

async function renderGrid() {
  await nextTick()
  destroyGrid()
  if (!gridRef.value) {
    return
  }
  const options: GridStackOptions = {
    column: BREAKPOINT_COLUMNS[activeBreakpoint.value],
    cellHeight: activeBreakpoint.value === 'xs' ? 100 : 108,
    margin: activeBreakpoint.value === 'xs' ? 10 : 14,
    float: false,
    disableDrag: !editMode.value,
    disableResize: !editMode.value || activeBreakpoint.value === 'xs',
    resizable: { handles: 'e, se, s, sw, w' },
    draggable: { handle: '.card-drag-handle' },
  }
  const mod = await import('gridstack')
  grid = mod.GridStack.init(options, gridRef.value)
  grid.on('change', (_event, nodes) => syncGridNodes(nodes))
}

function destroyGrid() {
  if (grid) {
    grid.destroy(false)
    grid = null
  }
}

function widgetFor(item: DashboardLayoutItem): GridStackWidget {
  const card = cardMap.value.get(item.cardCode)
  const placement = placementFor(item)
  return {
    x: placement.x,
    y: placement.y,
    w: placement.w,
    h: placement.h,
    minW: activeBreakpoint.value === 'xs' ? 1 : Math.min(card?.minW || 1, BREAKPOINT_COLUMNS[activeBreakpoint.value]),
    minH: card?.minH || 2,
  }
}

function placementFor(item: DashboardLayoutItem): DashboardGridPlacement {
  const fallback = item.placements.lg || { x: 0, y: 0, w: 4, h: 3 }
  const next = item.placements[activeBreakpoint.value] || fallback
  if (activeBreakpoint.value === 'xs') {
    return { ...next, x: 0, w: 1 }
  }
  return next
}

function syncGridNodes(nodes?: GridStackNode[]) {
  if (!editMode.value || !nodes?.length) {
    return
  }
  const breakpoint = activeBreakpoint.value
  const next = cloneItems(draftItems.value)
  for (const node of nodes) {
    const cardCode = node.el?.getAttribute('data-card-code')
    if (!cardCode) {
      continue
    }
    const item = next.find(row => row.cardCode === cardCode)
    if (!item) {
      continue
    }
    item.placements[breakpoint] = {
      x: node.x || 0,
      y: node.y || 0,
      w: breakpoint === 'xs' ? 1 : (node.w || 1),
      h: node.h || 1,
    }
  }
  draftItems.value = next
}

function startEdit() {
  layoutMode.value = 'personal'
  editMode.value = true
  draftItems.value = cloneItems(workspace.value?.effectiveLayout.items ?? [])
}

async function startDefaultEdit() {
  loading.value = true
  try {
    const [cardRes, layoutRes] = await Promise.all([
      apiDashboard.cards(),
      apiDashboard.defaultLayout(),
    ])
    defaultCards.value = cardRes.data
    defaultLayout.value = layoutRes.data
    layoutMode.value = 'default'
    editMode.value = true
    draftItems.value = cloneItems(layoutRes.data.items ?? [])
    await renderGrid()
  }
  catch (error) {
    toast.error(error instanceof Error ? error.message : '默认首页加载失败')
  }
  finally {
    loading.value = false
  }
}

function cancelEdit() {
  editMode.value = false
  drawerVisible.value = false
  if (layoutMode.value === 'default') {
    layoutMode.value = 'personal'
  }
  draftItems.value = cloneItems(workspace.value?.effectiveLayout.items ?? [])
}

async function saveLayout() {
  saving.value = true
  try {
    await nextTick()
    syncAllGridItems()
    if (layoutMode.value === 'default') {
      await apiDashboard.saveDefaultLayout({ items: cloneItems(draftItems.value) })
      toast.success('默认首页已保存')
      layoutMode.value = 'personal'
    }
    else {
      await apiDashboard.saveMyLayout({ items: cloneItems(draftItems.value) })
      toast.success('首页布局已保存')
    }
    editMode.value = false
    drawerVisible.value = false
    await loadWorkspace()
  }
  catch (error) {
    toast.error(error instanceof Error ? error.message : '保存失败')
  }
  finally {
    saving.value = false
  }
}

async function resetLayout() {
  saving.value = true
  try {
    if (layoutMode.value === 'default') {
      draftItems.value = cloneItems(defaultLayout.value?.items ?? [])
      toast.success('已恢复为当前默认布局')
    }
    else {
      await apiDashboard.resetMyLayout()
      toast.success('已恢复默认布局')
      editMode.value = false
      drawerVisible.value = false
      await loadWorkspace()
    }
  }
  catch (error) {
    toast.error(error instanceof Error ? error.message : '重置失败')
  }
  finally {
    saving.value = false
  }
}

function syncAllGridItems() {
  if (!grid) {
    return
  }
  syncGridNodes(grid.engine.nodes)
}

async function addCard(card: DashboardCard) {
  syncAllGridItems()
  const existing = draftItems.value.find(item => item.cardCode === card.code)
  if (existing) {
    existing.visible = true
    draftItems.value = cloneItems(draftItems.value)
    await renderGrid()
    return
  }
  draftItems.value = [
    ...draftItems.value,
    {
      cardCode: card.code,
      visible: true,
      placements: defaultPlacements(card, draftItems.value),
    },
  ]
  await renderGrid()
}

async function hideCard(cardCode: string) {
  syncAllGridItems()
  draftItems.value = draftItems.value.map(item => item.cardCode === cardCode ? { ...item, visible: false } : item)
  await renderGrid()
}

function defaultPlacements(card: DashboardCard, existingItems: DashboardLayoutItem[]) {
  const placements: Partial<Record<DashboardBreakpoint, DashboardGridPlacement>> = {}
  for (const breakpoint of BREAKPOINTS) {
    const columns = BREAKPOINT_COLUMNS[breakpoint]
    const width = breakpoint === 'xs' ? 1 : Math.min(card.defaultW || 4, columns)
    const height = Math.max(card.defaultH || 3, card.minH || 2)
    const heights = Array.from({ length: columns }).fill(0) as number[]
    for (const item of existingItems) {
      if (!item.visible) {
        continue
      }
      const placement = item.placements[breakpoint] || item.placements.lg
      if (!placement) {
        continue
      }
      const itemWidth = breakpoint === 'xs' ? 1 : Math.min(Math.max(placement.w || 1, 1), columns)
      const itemX = breakpoint === 'xs' ? 0 : Math.min(Math.max(placement.x || 0, 0), Math.max(columns - itemWidth, 0))
      const itemBottom = Math.max(placement.y || 0, 0) + Math.max(placement.h || 1, 1)
      for (let col = itemX; col < itemX + itemWidth; col++) {
        heights[col] = Math.max(heights[col], itemBottom)
      }
    }
    const slot = lowestSlot(heights, width)
    placements[breakpoint] = {
      x: slot.x,
      y: slot.y,
      w: width,
      h: height,
    }
  }
  return placements
}

function lowestSlot(heights: number[], width: number) {
  let x = 0
  let y = Number.MAX_SAFE_INTEGER
  for (let index = 0; index <= heights.length - width; index++) {
    const candidateY = Math.max(...heights.slice(index, index + width))
    if (candidateY < y) {
      x = index
      y = candidateY
    }
  }
  return { x, y: y === Number.MAX_SAFE_INTEGER ? 0 : y }
}

function cloneItems(items: DashboardLayoutItem[]) {
  return items.map(item => ({
    cardCode: item.cardCode,
    visible: item.visible,
    placements: Object.fromEntries(Object.entries(item.placements || {}).map(([key, placement]) => [key, { ...placement }])),
  })) as DashboardLayoutItem[]
}

function openCard(card?: { actionPath?: string }) {
  if (editMode.value || !card?.actionPath) {
    return
  }
  router.push(card.actionPath)
}

function openPublicHome() {
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
      icon: menu.meta?.icon || 'i-ri:arrow-right-line',
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
    case 'PLUGIN_STATS':
      return DashboardPluginStatsCard
    case 'DATAVIZ_CAPABILITY_STATS':
    case 'DATAVIZ_USER_REGISTRATION':
    case 'DATAVIZ_DEPT_CREATED':
    case 'DATAVIZ_LOG_ACTIVITY':
      return DashboardChartStatsCard
    case 'ENDPOINT_CARD':
      return DashboardEndpointCard
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
          <strong>首页</strong>
          <span v-if="editMode" class="dashboard-edit-hint">
            {{ layoutMode === 'default' ? '正在配置所有用户的默认首页' : '正在编辑你的个人首页，可拖拽卡片并调整大小' }}
          </span>
        </div>
        <div class="dashboard-toolbar">
          <FaButton v-if="!editMode" variant="outline" size="sm" @click="openPublicHome">
            <FaIcon name="i-ri:home-4-line" />
            访问首页
          </FaButton>
          <FaButton v-if="!editMode" variant="outline" size="sm" :loading="loading" @click="loadWorkspace">
            <FaIcon name="i-ri:refresh-line" />
            刷新
          </FaButton>
          <FaButton v-if="!editMode && canConfigureDefault" variant="outline" size="sm" @click="startDefaultEdit">
            <FaIcon name="i-ri:layout-2-line" />
            配置默认首页
          </FaButton>
          <FaButton v-if="!editMode" size="sm" @click="startEdit">
            <FaIcon name="i-ri:edit-line" />
            编辑首页
          </FaButton>
          <template v-else>
            <FaButton variant="outline" size="sm" @click="drawerVisible = true">
              <FaIcon name="i-ri:add-line" />
              添加卡片
            </FaButton>
            <FaButton variant="ghost" size="sm" @click="cancelEdit">
              取消
            </FaButton>
            <FaButton variant="outline" size="sm" :loading="saving" @click="resetLayout">
              {{ layoutMode === 'default' ? '撤销调整' : '恢复默认' }}
            </FaButton>
            <FaButton size="sm" :loading="saving" @click="saveLayout">
              <FaIcon name="i-ri:save-3-line" />
              {{ layoutMode === 'default' ? '保存默认布局' : '保存布局' }}
            </FaButton>
          </template>
        </div>
      </div>

      <div v-if="loading && !workspace" class="dashboard-loading">
        <FaIcon name="i-ri:loader-4-line" class="animate-spin" />
        正在加载首页
      </div>

      <div v-else ref="gridRef" class="grid-stack dashboard-grid" :class="{ editing: editMode }">
        <div
          v-for="item in visibleItems"
          :key="item.cardCode"
          class="grid-stack-item"
          :data-card-code="item.cardCode"
          v-bind="widgetFor(item)"
          :gs-x="widgetFor(item).x"
          :gs-y="widgetFor(item).y"
          :gs-w="widgetFor(item).w"
          :gs-h="widgetFor(item).h"
          :gs-min-w="widgetFor(item).minW"
          :gs-min-h="widgetFor(item).minH"
        >
          <article class="grid-stack-item-content dashboard-card">
            <header class="dashboard-card__header">
              <button v-if="editMode" type="button" class="card-drag-handle" title="拖拽卡片">
                <FaIcon name="i-ri:drag-move-2-line" />
              </button>
              <div class="dashboard-card__heading">
                <span class="dashboard-card__icon" :class="toneIconClass(cardMap.get(item.cardCode)?.tone)">
                  <FaIcon :name="cardMap.get(item.cardCode)?.icon || 'i-ri:layout-grid-line'" />
                </span>
                <div class="dashboard-card__title-block">
                  <h2>{{ displayCardTitle(cardMap.get(item.cardCode)) }}</h2>
                </div>
              </div>
              <button
                v-if="editMode"
                type="button"
                class="card-hide-button"
                title="隐藏卡片"
                @click="hideCard(item.cardCode)"
              >
                <FaIcon name="i-ri:eye-off-line" />
              </button>
            </header>

            <component
              v-if="cardMap.get(item.cardCode)"
              :is="resolveCardComponent(cardMap.get(item.cardCode))"
              :class="{ 'dashboard-card__content--chart': resolveCardComponent(cardMap.get(item.cardCode)) === DashboardChartStatsCard }"
              v-bind="cardProps(cardMap.get(item.cardCode)!)"
            />
          </article>
        </div>
      </div>
    </div>

    <a-drawer v-model:visible="drawerVisible" :width="drawerWidth" title="添加首页卡片" unmount-on-close>
      <div class="card-picker">
        <div v-for="group in hiddenCardGroups" :key="group.name" class="card-picker-group">
          <div class="card-picker-group__title">
            <strong>{{ group.name }}</strong>
            <span>{{ group.items.length }}</span>
          </div>
          <button v-for="card in group.items" :key="card.code" type="button" class="picker-card" @click="addCard(card)">
            <span class="picker-card__icon" :class="toneIconClass(card.tone)">
              <FaIcon :name="card.icon || 'i-ri:layout-grid-line'" />
            </span>
            <span class="picker-card__main">
              <strong>{{ card.title }}</strong>
              <small>{{ card.description || card.category }}</small>
              <span class="picker-card__meta">
                <em>{{ sourceText(card) }}</em>
                <em>{{ card.defaultW }} x {{ card.defaultH }}</em>
              </span>
            </span>
            <span class="picker-card__add">
              <FaIcon name="i-ri:add-line" />
            </span>
          </button>
        </div>
        <div v-if="!hiddenCards.length" class="picker-empty">
          <FaIcon name="i-ri:checkbox-circle-line" />
          <strong>所有卡片已显示</strong>
        </div>
      </div>
    </a-drawer>
  </div>
</template>

<style scoped>
.dashboard-page {
  position: absolute;
  inset: 0;
  overflow: auto;
  background: var(--g-main-area-bg, #f7f8fa);
}

.dashboard-shell {
  width: min(1480px, calc(100% - 24px));
  padding: 16px 0 32px;
  margin: 0 auto;
}

.dashboard-header {
  display: flex;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
  min-height: 40px;
}

.dashboard-greeting {
  display: flex;
  gap: 10px;
  align-items: center;
  min-width: 0;
}

.dashboard-greeting strong {
  font-size: 18px;
  font-weight: 700;
  color: var(--color-text-1);
}

.dashboard-edit-hint {
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 13px;
  color: var(--color-text-3);
  white-space: nowrap;
}

.dashboard-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  justify-content: flex-end;
}

.dashboard-grid {
  min-height: 320px;
  margin-top: 16px;
}

.grid-stack-item-content.dashboard-card {
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
  container-type: inline-size;
  overflow: hidden;
  background: var(--color-bg-2);
  border: 1px solid var(--color-border-2);
  border-radius: 8px;
  box-shadow: 0 1px 2px rgb(0 0 0 / 3%);
  transition: border-color 0.16s ease, box-shadow 0.16s ease;
}

.dashboard-card:hover {
  border-color: var(--color-border-3);
  box-shadow: 0 3px 10px rgb(0 0 0 / 5%);
}

.editing .dashboard-card {
  cursor: default;
  outline: 1px dashed var(--color-border-3);
  outline-offset: -4px;
}

.dashboard-card__header {
  display: flex;
  gap: 10px;
  align-items: center;
  padding: 16px 16px 10px;
}

.dashboard-card__heading {
  display: flex;
  flex: 1;
  gap: 10px;
  align-items: center;
  min-width: 0;
}

.dashboard-card__icon {
  display: grid;
  flex: 0 0 auto;
  place-items: center;
  width: 24px;
  height: 24px;
  font-size: 16px;
}

.dashboard-card__title-block {
  display: grid;
  gap: 2px;
  min-width: 0;
}

.dashboard-card__title-block h2 {
  margin: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 14px;
  font-weight: 700;
  line-height: 1.3;
  color: var(--color-text-1);
  white-space: nowrap;
}

.card-drag-handle,
.card-hide-button {
  display: grid;
  flex: 0 0 auto;
  place-items: center;
  width: 28px;
  height: 28px;
  padding: 0;
  color: var(--color-text-3);
  cursor: pointer;
  background: transparent;
  border: 1px solid transparent;
  border-radius: 6px;
  transition: border-color 0.16s ease, background 0.16s ease, color 0.16s ease;
}

.editing .card-drag-handle {
  color: var(--color-text-2);
  cursor: grab;
  background: var(--color-fill-2);
  border-color: var(--color-border-2);
}

.card-drag-handle:disabled {
  cursor: default;
  opacity: 0.45;
}

.card-hide-button:hover,
.editing .card-drag-handle:hover {
  color: var(--color-text-1);
  background: var(--color-fill-2);
  border-color: var(--color-border-3);
}

.dashboard-card__content {
  flex: 1;
  min-height: 0;
  margin: 4px 16px 16px;
  overflow: auto;
}

.dashboard-card__content--chart {
  overflow: hidden;
}

.dashboard-loading {
  display: flex;
  gap: 8px;
  align-items: center;
  justify-content: center;
  min-height: 280px;
  margin-top: 16px;
  color: var(--color-text-3);
  background: var(--color-bg-2);
  border: 1px dashed var(--color-border-2);
  border-radius: 8px;
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
  transition: border-color 0.16s ease, background 0.16s ease;
}

.picker-card:hover {
  background: var(--color-fill-2);
  border-color: var(--color-border-3);
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
  color: var(--primary);
}

.picker-empty strong {
  font-size: 14px;
  color: var(--color-text-2);
}

@media (width <= 780px) {
  .dashboard-shell {
    width: min(100% - 16px, 1480px);
    padding-top: 12px;
  }

  .dashboard-header {
    flex-direction: column;
    align-items: stretch;
  }

  .dashboard-toolbar {
    justify-content: flex-start;
    width: 100%;
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
</style>
