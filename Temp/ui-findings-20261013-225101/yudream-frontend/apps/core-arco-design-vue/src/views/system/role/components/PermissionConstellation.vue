<script setup lang="ts">
import type { PermissionOverview } from '@/api/modules/system-permission'
import type { PermissionItem, RoleManageItem } from '@/api/modules/system-role'
import { useMediaQuery } from '@vueuse/core'
import { computed, ref, useId, watch } from 'vue'
import { buildPermissionTree, permissionCategoryOf, permissionResourceLabels, permissionResourceOf, permissionTreeKeys } from '@/utils/permission-catalog'

const props = defineProps<{
  overview: PermissionOverview
  loading: boolean
  userKeyword?: string
  userUnassignedOnly?: boolean
}>()
const emit = defineEmits<{
  editRole: [role: RoleManageItem]
  editUser: [id: string]
  editMenu: [code: string]
  grantPermission: [code: string]
  revokePermission: [roleId: string, code: string]
  assignUserRoles: [id: string]
  inspectPermission: [code: string]
  userPage: [page: number]
  userSearch: [keyword: string]
  userUnassigned: [enabled: boolean]
  refresh: []
}>()

type NodeKind = 'users' | 'roles' | 'menus' | 'permissions'
interface StarNode {
  id: string
  entityId: string
  kind: NodeKind
  label: string
  caption: string
  roleIds: string[]
  available: boolean
  resourceKey?: string
}
interface Point { x: number, y: number }
interface PositionedNode extends StarNode, Point { index: number }

const { auth } = useAppAuth()
const mobile = useMediaQuery('(max-width: 768px)')
const gradientId = `permission-sphere-${useId().replace(/:/g, '')}`
const paused = ref(false)
const replayKey = ref(0)
const keyword = ref('')
const remoteUserKeyword = ref('')
const focusedKind = ref<NodeKind | null>(null)
const selectedId = ref<string | null>(null)
const queryMode = ref<'all' | 'idle' | 'unassigned'>('all')
const resourceKey = ref('')
const rolePermissionKeyword = ref('')
const rolePermissionPage = ref(1)
const rolePermissionSize = ref(30)
const labels: Record<NodeKind, string> = { users: '用户', roles: '角色', menus: '菜单', permissions: '权限' }
const limits: Record<NodeKind, number> = { users: 16, roles: 24, menus: 20, permissions: 18 }
const roleIndex = computed(() => new Map(props.overview.roles.map(role => [role.id, role])))
const idleRoleIds = computed(() => new Set(props.overview.idleRoleIds || []))
const resourceLabels = computed(() => permissionResourceLabels(props.overview.permissions))
const activePermissions = computed(() => props.overview.permissions.filter(permission => permission.status === 'ACTIVE'))
const activePermissionIndex = computed(() => new Map(activePermissions.value.map(permission => [permission.code, permission])))
const permissionDisplayOrder = computed(() => new Map(permissionTreeKeys(buildPermissionTree(activePermissions.value), true).map((code, index) => [code, index])))
const resourceOptions = computed(() => {
  const resources = new Map<string, { value: string, label: string }>()
  activePermissions.value.forEach((permission) => {
    const resource = permissionResourceOf(permission, resourceLabels.value)
    resources.set(resource.key, { value: resource.key, label: `${resource.domainLabel} / ${resource.label}` })
  })
  return [...resources.values()]
})
const allNodes = computed<StarNode[]>(() => [
  ...props.overview.users.map(user => ({
    id: `user:${user.id}`,
    entityId: user.id,
    kind: 'users' as const,
    label: user.nickname || user.username,
    caption: user.username,
    roleIds: user.roleIds,
    available: user.status === 'ACTIVE' && user.emailVerified,
  })),
  ...props.overview.roles.map(role => ({
    id: `role:${role.id}`,
    entityId: role.id,
    kind: 'roles' as const,
    label: role.name,
    caption: role.code,
    roleIds: [role.id],
    available: role.status === 'ACTIVE',
  })),
  ...props.overview.menus.map(menu => ({
    id: `menu:${menu.code}`,
    entityId: menu.code,
    kind: 'menus' as const,
    label: menu.name,
    caption: menu.permission || menu.path || menu.code,
    roleIds: menu.roleIds,
    available: menu.runtimeAvailable && menu.status === 'ACTIVE',
  })),
  ...activePermissions.value.map(permission => ({
    id: `permission:${permission.code}`,
    entityId: permission.code,
    kind: 'permissions' as const,
    label: permission.name,
    caption: `${permission.code} · ${permissionResourceOf(permission, resourceLabels.value).label} · ${permissionCategoryOf(permission.code).label}`,
    roleIds: props.overview.roles.filter(role => role.systemType === 'SUPER_ADMIN' || role.permissions.includes('*') || role.permissions.includes(permission.code)).map(role => role.id),
    available: permission.status === 'ACTIVE',
    resourceKey: permissionResourceOf(permission, resourceLabels.value).key,
  })),
])
const selected = computed(() => allNodes.value.find(node => node.id === selectedId.value))
const activeResource = computed(() => selected.value?.kind === 'permissions'
  ? selected.value.resourceKey
  : resourceOptions.value.some(option => option.value === resourceKey.value) ? resourceKey.value : resourceOptions.value[0]?.value)
const normalizedKeyword = computed(() => keyword.value.trim().toLowerCase())
const filteredNodes = computed(() => allNodes.value.filter((node) => {
  if (focusedKind.value && node.kind !== focusedKind.value) {
    return false
  }
  if (focusedKind.value === 'permissions' && !normalizedKeyword.value && node.resourceKey !== activeResource.value) {
    return false
  }
  if (queryMode.value === 'idle' && (node.kind !== 'roles' || !idleRoleIds.value.has(node.entityId))) {
    return false
  }
  if (queryMode.value === 'unassigned' && (node.kind !== 'users' || node.roleIds.length > 0)) {
    return false
  }
  const needle = normalizedKeyword.value
  return !needle || `${node.label} ${node.caption} ${labels[node.kind]}`.toLowerCase().includes(needle)
}))
const searchResults = computed(() => filteredNodes.value.slice(0, 12))
const relatedIds = computed(() => {
  const active = selected.value
  if (!active) {
    return new Set<string>()
  }
  const roles = new Set(active.roleIds)
  return new Set(allNodes.value.filter(node => node.id === active.id || node.roleIds.some(id => roles.has(id))).map(node => node.id))
})
const relatedRoles = computed(() => (selected.value?.roleIds || []).map(id => roleIndex.value.get(id)).filter((role): role is RoleManageItem => !!role))
const selectedUser = computed(() => props.overview.users.find(user => selected.value?.kind === 'users' && user.id === selected.value.entityId))
const selectedMenu = computed(() => props.overview.menus.find(menu => selected.value?.kind === 'menus' && menu.code === selected.value.entityId))
const selectedRole = computed(() => selected.value?.kind === 'roles' ? roleIndex.value.get(selected.value.entityId) : undefined)
const selectedPermission = computed(() => selected.value?.kind === 'permissions' ? activePermissionIndex.value.get(selected.value.entityId) : undefined)
const menuDirectPermission = computed(() => {
  const menu = selectedMenu.value
  return menu && ['MENU', 'LINK', 'BUTTON'].includes(menu.type) ? activePermissionIndex.value.get(menu.permission) : undefined
})
const selectedPermissionDetails = computed(() => {
  const permission = selectedPermission.value
  if (!permission) {
    return []
  }
  const resource = permissionResourceOf(permission, resourceLabels.value)
  return [
    { key: 'name', label: '操作名称', value: permission.name },
    { key: 'code', label: '权限码', value: permission.code, valueClass: 'break-all font-mono' },
    { key: 'resource', label: '业务资源', value: `${resource.domainLabel} / ${resource.label}` },
    { key: 'module', label: '登记模块', value: permission.module || '未声明' },
    { key: 'category', label: '动作分类', value: permissionCategoryOf(permission.code).label },
    { key: 'status', label: '目录状态', value: permission.status === 'ACTIVE' ? '已登记 · 可授予' : '已失效' },
    { key: 'desc', label: '操作说明', value: permission.desc || '此权限未登记说明，查看细则可核对授权关系与入口。' },
  ]
})
const userDeptNames = computed(() => (selectedUser.value?.deptIds || []).map((deptId) => {
  const name = props.overview.roles.find(role => role.deptId === deptId && role.deptName)?.deptName
  return name || `部门 ${deptId}`
}))
const userContextDept = computed(() => {
  const deptId = selectedUser.value?.currentDeptId
  if (!deptId) {
    return '未选择'
  }
  return props.overview.roles.find(role => role.deptId === deptId && role.deptName)?.deptName || `部门 ${deptId}`
})
const canAssignSelectedUser = computed(() => !!selectedUser.value && props.overview.canViewUsers && auth('system:user:assign-role'))
const rolePermissionItems = computed(() => {
  const role = selectedRole.value
  if (!role) {
    return []
  }
  const needle = rolePermissionKeyword.value.trim().toLowerCase()
  return activePermissions.value.filter((permission) => {
    if (role.systemType !== 'SUPER_ADMIN' && !role.permissions.includes('*') && !role.permissions.includes(permission.code)) {
      return false
    }
    const resource = permissionResourceOf(permission, resourceLabels.value)
    return !needle || [permission.name, permission.code, permission.module, resource.domainLabel, resource.label, permissionCategoryOf(permission.code).label].some(value => value?.toLowerCase().includes(needle))
  }).sort((left, right) => (permissionDisplayOrder.value.get(left.code) || 0) - (permissionDisplayOrder.value.get(right.code) || 0))
})
const rolePermissionGroups = computed(() => {
  const groups = new Map<string, { key: string, label: string, permissions: PermissionItem[] }>()
  const start = (rolePermissionPage.value - 1) * rolePermissionSize.value
  rolePermissionItems.value.slice(start, start + rolePermissionSize.value).forEach((permission) => {
    const resource = permissionResourceOf(permission, resourceLabels.value)
    if (!groups.has(resource.key)) {
      groups.set(resource.key, { key: resource.key, label: `${resource.domainLabel} / ${resource.label}`, permissions: [] })
    }
    groups.get(resource.key)!.permissions.push(permission)
  })
  return [...groups.values()]
})
const uncataloguedRolePermissionCount = computed(() => new Set(selectedRole.value?.permissions.filter(code => !activePermissionIndex.value.has(code))).size)
const canEditSelected = computed(() => {
  if (selected.value?.kind === 'roles') {
    return auth('system:role:edit')
  }
  return selected.value?.kind === 'users'
    ? props.overview.canViewUsers && auth('system:user:edit')
    : selected.value?.kind === 'menus' && props.overview.canViewMenus && auth('system:menu:edit')
})

const geometry = computed(() => mobile.value
  ? { width: 680, height: 710, center: { x: 340, y: 345 }, hubs: { users: { x: 174, y: 440 }, roles: { x: 340, y: 153 }, menus: { x: 506, y: 440 }, permissions: { x: 340, y: 345 } }, radius: 53, satelliteRadius: 102 }
  : { width: 1200, height: 700, center: { x: 600, y: 355 }, hubs: { users: { x: 282, y: 410 }, roles: { x: 600, y: 157 }, menus: { x: 918, y: 410 }, permissions: { x: 600, y: 355 } }, radius: 62, satelliteRadius: 135 })
const categories = computed(() => {
  const { stats, canViewUsers, canViewMenus, users } = props.overview
  const userRatio = users.length ? users.filter(user => user.roleIds.length > 0).length / users.length : 0
  const menuRatio = props.overview.menus.length ? props.overview.menus.filter(menu => menu.runtimeAvailable && menu.status === 'ACTIVE').length / props.overview.menus.length : 0
  return [
    { kind: 'users' as const, count: stats.userCount, caption: canViewUsers ? '刻度 · 本页已分配角色占比' : '用户信息未开放', ratio: userRatio, ...geometry.value.hubs.users },
    { kind: 'roles' as const, count: stats.roleCount, caption: stats.idleRoleCount === null ? '刻度 · 成员关系未开放' : '刻度 · 已关联用户占比', ratio: stats.roleCount && stats.idleRoleCount !== null ? 1 - stats.idleRoleCount / stats.roleCount : 0, ...geometry.value.hubs.roles },
    { kind: 'menus' as const, count: stats.menuCount, caption: canViewMenus ? '刻度 · 当前运行可用占比' : '菜单信息未开放', ratio: menuRatio, ...geometry.value.hubs.menus },
  ]
})
const stars = computed<PositionedNode[]>(() => {
  const { hubs, satelliteRadius, center } = geometry.value
  const list: PositionedNode[] = []
  const kinds: NodeKind[] = ['users', 'roles', 'menus', 'permissions']
  kinds.forEach((kind) => {
    let candidates = allNodes.value.filter(node => node.kind === kind)
    if (kind === 'permissions' && !normalizedKeyword.value) {
      candidates = candidates.filter(node => node.resourceKey === activeResource.value)
    }
    if (focusedKind.value || normalizedKeyword.value || queryMode.value !== 'all') {
      candidates = candidates.filter(node => filteredNodes.value.some(match => match.id === node.id) || relatedIds.value.has(node.id))
    }
    if (selected.value) {
      candidates.sort((left, right) => Number(relatedIds.value.has(right.id)) - Number(relatedIds.value.has(left.id)))
    }
    candidates = candidates.slice(0, limits[kind])
    candidates.forEach((node, index) => {
      const fraction = candidates.length === 1 ? 0.5 : index / (candidates.length - 1)
      const angle = kind === 'roles' ? -165 + fraction * 150 : kind === 'users' ? 112 + fraction * 136 : kind === 'menus' ? -68 + fraction * 136 : 35 + fraction * 110
      const radians = angle * Math.PI / 180
      const hub = kind === 'permissions' ? center : hubs[kind]
      const radius = kind === 'permissions' ? (mobile.value ? 91 : 105) : satelliteRadius
      list.push({ ...node, x: hub.x + Math.cos(radians) * radius, y: hub.y + Math.sin(radians) * radius, index: list.length })
    })
  })
  return list
})
const relationshipPaths = computed(() => {
  if (!selected.value) {
    return []
  }
  const related = stars.value.filter(node => relatedIds.value.has(node.id))
  const links: { id: string, path: string }[] = []
  related.filter(node => node.kind === 'roles').forEach((roleNode) => {
    related.filter(node => node.kind !== 'roles' && node.roleIds.includes(roleNode.entityId)).forEach((node) => {
      links.push({ id: `${node.id}->${roleNode.id}`, path: curve(node, roleNode) })
    })
  })
  return links.slice(0, 32)
})
const userPageCount = computed(() => props.overview.userTotal === null ? 1 : Math.max(1, Math.ceil(props.overview.userTotal / props.overview.userSize)))
const shownSummary = computed(() => (['users', 'roles', 'menus', 'permissions'] as const).map((kind) => {
  const count = allNodes.value.filter(node => node.kind === kind).length
  const shown = stars.value.filter(node => node.kind === kind).length
  return count > shown ? `${labels[kind]} ${shown}/${count}` : ''
}).filter(Boolean).join(' · '))

function curve(from: Point, to: Point) {
  const center = geometry.value.center
  return `M ${from.x} ${from.y} Q ${(from.x + to.x + center.x) / 3} ${(from.y + to.y + center.y) / 3} ${to.x} ${to.y}`
}
function tickPoint(index: number, radius: number) {
  const angle = (index / 64 * 360 - 90) * Math.PI / 180
  return { x: Math.cos(angle) * radius, y: Math.sin(angle) * radius }
}
function showKind(kind: NodeKind) {
  if (queryMode.value === 'unassigned') {
    emit('userUnassigned', false)
  }
  focusedKind.value = focusedKind.value === kind ? null : kind
  queryMode.value = 'all'
  selectedId.value = null
}
function inspect(node: StarNode) {
  selectedId.value = node.id
}
function openManagement(node: StarNode) {
  if (node.kind === 'roles' && auth('system:role:edit')) {
    const role = roleIndex.value.get(node.entityId)
    if (role) {
      emit('editRole', role)
    }
  }
  else if (node.kind === 'users' && props.overview.canViewUsers && auth('system:user:edit')) {
    emit('editUser', node.entityId)
  }
  else if (node.kind === 'menus' && props.overview.canViewMenus && auth('system:menu:edit')) {
    emit('editMenu', node.entityId)
  }
}
function quickQuery(mode: 'idle' | 'unassigned') {
  const wasUnassigned = queryMode.value === 'unassigned'
  queryMode.value = queryMode.value === mode ? 'all' : mode
  if (mode === 'unassigned' || wasUnassigned) {
    emit('userUnassigned', queryMode.value === 'unassigned')
  }
  else if (remoteUserKeyword.value) {
    emit('userSearch', '')
  }
  remoteUserKeyword.value = ''
  focusedKind.value = null
  keyword.value = ''
  selectedId.value = null
}
function reset() {
  if (queryMode.value === 'unassigned' || remoteUserKeyword.value || props.userKeyword || props.userUnassignedOnly) {
    emit('userSearch', '')
  }
  remoteUserKeyword.value = ''
  keyword.value = ''
  focusedKind.value = null
  queryMode.value = 'all'
  selectedId.value = null
}
function searchUsers() {
  if (!props.overview.canViewUsers || !keyword.value.trim()) {
    return
  }
  remoteUserKeyword.value = keyword.value.trim()
  queryMode.value = 'all'
  focusedKind.value = null
  selectedId.value = null
  emit('userSearch', remoteUserKeyword.value)
}
function replay() {
  paused.value = false
  replayKey.value += 1
}
function displayCount(value: number | null) {
  return value === null ? '—' : value.toLocaleString('zh-CN')
}
function nodeKeydown(event: KeyboardEvent, node: StarNode) {
  if (event.key === 'Enter' || event.key === ' ') {
    event.preventDefault()
    inspect(node)
  }
}
function canRevoke(role: RoleManageItem, code: string) {
  return auth('system:role:edit') && role.systemType !== 'SUPER_ADMIN' && !role.permissions.includes('*') && role.permissions.includes(code) && activePermissionIndex.value.has(code)
}
watch([() => selectedRole.value?.id, rolePermissionKeyword], () => {
  rolePermissionPage.value = 1
})
watch(() => rolePermissionItems.value.length, (total) => {
  rolePermissionPage.value = Math.min(rolePermissionPage.value, Math.max(1, Math.ceil(total / rolePermissionSize.value)))
})
watch(() => props.overview, () => {
  if (selectedId.value && !allNodes.value.some(node => node.id === selectedId.value)) {
    selectedId.value = null
  }
})
watch([() => props.userKeyword, () => props.userUnassignedOnly], ([serverKeyword, unassigned]) => {
  remoteUserKeyword.value = serverKeyword || ''
  keyword.value = serverKeyword || ''
  if (unassigned) {
    queryMode.value = 'unassigned'
  }
  else if (queryMode.value === 'unassigned') {
    queryMode.value = 'all'
  }
}, { immediate: true })
</script>

<template>
  <section class="permission-constellation" :class="{ 'is-paused': paused, 'is-mobile': mobile }" :aria-busy="loading" aria-label="权限关系星图" @keydown.esc="selectedId = null">
    <div class="constellation-toolbar flex flex-wrap gap-3 items-start justify-between">
      <div>
        <span class="constellation-eyebrow"><YdIcon name="morph:orbit" /> 权限全景</span>
        <p class="constellation-subtitle">
          把分散的授权，连成一张可以探索的星图
        </p>
      </div>
      <div class="flex flex-wrap gap-2 items-center">
        <YdButton size="sm" variant="ghost" :disabled="loading" @click="replay">
          <YdIcon name="morph:rotate-ccw" />重播
        </YdButton>
        <YdButton size="sm" variant="ghost" :aria-pressed="paused" @click="paused = !paused">
          <YdIcon :name="paused ? 'morph:play' : 'morph:pause'" />{{ paused ? '继续' : '暂停' }}
        </YdButton>
        <YdButton size="sm" variant="outline" :loading="loading" @click="emit('refresh')">
          <YdIcon name="morph:refresh-cw" />刷新
        </YdButton>
      </div>
    </div>

    <div class="constellation-stage">
      <div class="constellation-summary max-md:mx-3 max-md:mb-2 max-md:relative" aria-label="权限统计">
        <div v-for="category in categories" :key="category.kind" class="summary-item">
          <span>{{ labels[category.kind] }}</span><strong>{{ displayCount(category.count) }}</strong>
        </div>
        <YdButton variant="ghost" class="summary-item summary-query p-0 h-auto items-start" :disabled="!overview.canViewUsers" @click="quickQuery('idle')">
          <span>闲置角色</span><strong>{{ displayCount(overview.stats.idleRoleCount) }}</strong>
        </YdButton>
        <div class="summary-item">
          <span>未配置授权菜单</span><strong>{{ displayCount(overview.stats.ungrantedMenuCount) }}</strong>
        </div>
        <YdButton variant="ghost" class="summary-item summary-query p-0 h-auto items-start" :disabled="!overview.canViewUsers" @click="quickQuery('unassigned')">
          <span>无角色用户</span><strong>{{ displayCount(overview.stats.unassignedUserCount) }}</strong>
        </YdButton>
      </div>

      <svg :key="replayKey" class="constellation-svg" :viewBox="`0 0 ${geometry.width} ${geometry.height}`" role="group" aria-label="用户、角色、权限和菜单的配置关系。Tab 键选择节点，Enter 键显示详情与授权操作。">
        <defs>
          <radialGradient :id="gradientId" cx="35%" cy="26%" r="78%"><stop offset="0" class="sphere-stop-light" /><stop offset="0.65" class="sphere-stop-mid" /><stop offset="1" class="sphere-stop-dark" /></radialGradient>
        </defs>
        <g class="constellation-orbits" aria-hidden="true">
          <ellipse :cx="geometry.center.x" :cy="geometry.center.y" :rx="mobile ? 265 : 408" :ry="mobile ? 261 : 278" />
          <ellipse :cx="geometry.center.x" :cy="geometry.center.y" :rx="mobile ? 202 : 333" :ry="mobile ? 224 : 231" class="orbit-inner" />
          <g class="orbit-drift" :style="{ transformOrigin: `${geometry.center.x}px ${geometry.center.y}px` }">
            <circle :cx="geometry.center.x + (mobile ? 265 : 408)" :cy="geometry.center.y" r="3" />
            <circle :cx="geometry.center.x - (mobile ? 265 : 408)" :cy="geometry.center.y" r="2" />
          </g>
          <path v-for="category in categories" :key="category.kind" :d="curve(geometry.center, category)" class="hub-link" pathLength="1" />
        </g>

        <g class="constellation-stars">
          <g v-for="star in stars" :key="star.id" :style="{ '--enter-delay': `${Math.min(star.index * 24 + 380, 1400)}ms` }" :class="{ 'star-is-muted': selected && !relatedIds.has(star.id), 'star-is-selected': selectedId === star.id, 'star-is-unavailable': !star.available }">
            <path :d="curve(geometry.hubs[star.kind], star)" class="satellite-link" pathLength="1" aria-hidden="true" />
            <g class="satellite" :transform="`translate(${star.x}, ${star.y})`" role="button" tabindex="0" :aria-label="`${labels[star.kind]}：${star.label}，${star.caption}${!star.available ? '，当前不可用' : ''}`" :aria-pressed="selectedId === star.id" @click="inspect(star)" @keydown="nodeKeydown($event, star)" @focus="inspect(star)">
              <title>{{ star.label }} · {{ star.caption }}</title>
              <circle r="15" class="satellite-hit" />
              <circle :r="star.kind === 'permissions' ? 3 : 4.5" class="satellite-dot" />
              <circle r="9" class="satellite-halo" />
              <text v-if="selectedId === star.id || (normalizedKeyword && filteredNodes.length <= 4)" y="-15" text-anchor="middle" class="satellite-label">{{ star.label.length > 11 ? `${star.label.slice(0, 10)}…` : star.label }}</text>
            </g>
          </g>
        </g>

        <g class="relation-paths" aria-hidden="true"><path v-for="relation in relationshipPaths" :key="relation.id" :d="relation.path" pathLength="1" /></g>

        <g v-for="category in categories" :key="category.kind" :transform="`translate(${category.x}, ${category.y})`" class="category-hub" :class="{ 'hub-is-focused': focusedKind === category.kind }" role="button" tabindex="0" :aria-label="`探索${labels[category.kind]}，${displayCount(category.count)}个`" :aria-pressed="focusedKind === category.kind" @click="showKind(category.kind)" @keydown.enter.prevent="showKind(category.kind)" @keydown.space.prevent="showKind(category.kind)">
          <title>点击探索{{ labels[category.kind] }}，不会修改授权</title>
          <circle :r="geometry.radius + 12" class="hub-aura" />
          <circle :r="geometry.radius" class="hub-surface" />
          <g class="hub-ticks" aria-hidden="true"><line v-for="tick in 64" :key="tick" :x1="tickPoint(tick - 1, geometry.radius - 4).x" :y1="tickPoint(tick - 1, geometry.radius - 4).y" :x2="tickPoint(tick - 1, geometry.radius - 11).x" :y2="tickPoint(tick - 1, geometry.radius - 11).y" :class="{ 'tick-is-filled': tick <= category.ratio * 64 }" /></g>
          <text y="-10" text-anchor="middle" class="hub-symbol">{{ category.kind === 'users' ? '◎' : category.kind === 'roles' ? '◇' : '≡' }}</text>
          <text y="24" text-anchor="middle" class="hub-count">{{ displayCount(category.count) }}</text>
          <text :y="geometry.radius + 32" text-anchor="middle" class="hub-label">{{ labels[category.kind] }}</text>
          <text :y="geometry.radius + 52" text-anchor="middle" class="hub-caption">{{ category.caption }}</text>
        </g>

        <g :transform="`translate(${geometry.center.x}, ${geometry.center.y})`" class="constellation-core" aria-hidden="true">
          <circle :r="mobile ? 79 : 88" class="core-aura" />
          <g class="core-ticks"><line v-for="tick in 64" :key="tick" :x1="tickPoint(tick - 1, mobile ? 76 : 85).x" :y1="tickPoint(tick - 1, mobile ? 76 : 85).y" :x2="tickPoint(tick - 1, mobile ? 72 : 80).x" :y2="tickPoint(tick - 1, mobile ? 72 : 80).y" /></g>
          <circle :r="mobile ? 62 : 70" :fill="`url(#${gradientId})`" class="core-sphere" />
          <ellipse :rx="mobile ? 48 : 56" :ry="mobile ? 59 : 67" class="core-meridian" transform="rotate(-25)" />
          <text :y="mobile ? 122 : 136" text-anchor="middle" class="core-label">权限全景</text>
          <text :y="mobile ? 143 : 159" text-anchor="middle" class="core-caption">{{ displayCount(overview.stats.assignmentCount) }} 条角色分配 · {{ activePermissions.length }} 项权限</text>
        </g>
      </svg>

      <div v-if="loading" class="constellation-loading" role="status">
        <YdIcon name="morph:loader-circle" class="animate-spin" />正在同步授权关系…
      </div>
      <div class="constellation-stage-caption">
        <YdIcon name="morph:mouse-pointer-2" /><span>{{ shownSummary ? `卫星展示 ${shownSummary} · 搜索可定位其余节点` : '点击刻度环展开类别，点击节点查看管理入口' }}</span>
      </div>
    </div>

    <div class="constellation-dock">
      <form class="flex gap-2 items-center" @submit.prevent="searchUsers">
        <YdIcon name="morph:search" class="shrink-0" />
        <YdInput v-model="keyword" class="flex-1 min-w-0" clearable aria-label="搜索用户、角色、菜单或权限" placeholder="搜索用户、角色、菜单或权限，例如「删除用户」" />
        <YdButton v-if="overview.canViewUsers" type="submit" size="sm" variant="outline" :disabled="!keyword.trim() || loading" title="在全部用户中查询">
          查用户
        </YdButton>
      </form>
      <div class="mt-2 flex flex-wrap gap-2 items-center">
        <YdButton size="sm" :variant="queryMode === 'idle' ? 'secondary' : 'ghost'" :aria-pressed="queryMode === 'idle'" :disabled="overview.idleRoleIds === null" @click="quickQuery('idle')">
          闲置角色
        </YdButton>
        <YdButton size="sm" :variant="queryMode === 'unassigned' ? 'secondary' : 'ghost'" :aria-pressed="queryMode === 'unassigned'" :disabled="!overview.canViewUsers" @click="quickQuery('unassigned')">
          无角色用户
        </YdButton>
        <YdButton size="sm" variant="ghost" @click="showKind('permissions')">
          {{ focusedKind === 'permissions' ? '查看全景' : '按权限溯源' }}
        </YdButton>
        <YdButton v-if="focusedKind || queryMode !== 'all' || keyword || selected || remoteUserKeyword" size="sm" variant="ghost" @click="reset">
          复位视图
        </YdButton>
      </div>
      <div v-if="focusedKind === 'permissions'" class="mt-3 flex flex-wrap gap-2 items-center">
        <span class="inspector-meta">按资源探索</span>
        <YdCombobox :model-value="activeResource" :options="resourceOptions" class="flex-1 min-w-0" virtual-list :disabled="loading" placeholder="选择业务资源" aria-label="选择权限业务资源" @update:model-value="resourceKey = String($event || ''); selectedId = null" />
      </div>
    </div>

    <div class="constellation-context flex flex-wrap gap-x-5 gap-y-2 items-start">
      <p><YdIcon name="morph:shield-check" />图中展示授权配置关系。实际访问按当前部门、当前角色及资源运行状态判定。</p>
      <p v-if="!overview.canViewUsers">
        <YdIcon name="morph:lock-keyhole" />缺少用户查看权限，用户及成员统计未展示。
      </p>
      <p v-else>
        用户仅展示第 {{ overview.userPage }} 页 {{ overview.users.length }} 人 / {{ displayCount(overview.userTotal) }} 人。
      </p>
      <p v-if="!overview.canViewMenus">
        <YdIcon name="morph:lock-keyhole" />缺少菜单查看权限，菜单关系未展示。
      </p>
      <p v-if="queryMode === 'idle'">
        闲置角色由服务端按全部用户的分配关系判断。
      </p>
      <p v-if="queryMode === 'unassigned'">
        正在查看服务端筛选出的无角色用户，结果按用户分页展示。
      </p>
      <p v-if="remoteUserKeyword">
        用户已按「{{ remoteUserKeyword }}」在服务端检索；复位视图可恢复全部用户。
      </p>
    </div>

    <div v-if="overview.canViewUsers && userPageCount > 1" class="mb-4 flex gap-3 items-center justify-center">
      <YdButton size="sm" variant="outline" :disabled="overview.userPage <= 1 || loading" @click="emit('userPage', overview.userPage - 1)">
        <YdIcon name="morph:chevron-left" />上一页用户
      </YdButton>
      <span class="constellation-page-count">{{ overview.userPage }} / {{ userPageCount }}</span>
      <YdButton size="sm" variant="outline" :disabled="overview.userPage >= userPageCount || loading" @click="emit('userPage', overview.userPage + 1)">
        下一页用户<YdIcon name="morph:chevron-right" />
      </YdButton>
    </div>

    <div v-if="selected || focusedKind || normalizedKeyword || queryMode !== 'all'" class="constellation-inspector gap-4 grid md:grid-cols-2">
      <div v-if="focusedKind || normalizedKeyword || queryMode !== 'all'" class="inspector-panel">
        <div class="flex gap-2 items-center justify-between">
          <strong>{{ queryMode === 'idle' ? '闲置角色' : queryMode === 'unassigned' ? '无角色用户' : focusedKind ? labels[focusedKind] : '搜索结果' }}</strong><span class="inspector-meta">{{ filteredNodes.length }} 项{{ filteredNodes.length > 12 ? ' · 显示前 12 项' : '' }}</span>
        </div>
        <div v-if="searchResults.length" class="mt-3 flex flex-wrap gap-2">
          <YdButton v-for="node in searchResults" :key="node.id" size="sm" variant="outline" :title="node.caption" :aria-pressed="selectedId === node.id" @click="inspect(node)">
            <span class="node-kind">{{ labels[node.kind] }}</span>{{ node.label }}
          </YdButton>
        </div>
        <p v-else class="inspector-empty">
          {{ focusedKind === 'users' && !overview.canViewUsers ? '用户信息未开放' : focusedKind === 'menus' && !overview.canViewMenus ? '菜单信息未开放' : '当前载入的数据中没有匹配项' }}
        </p>
      </div>
      <div v-if="selected" class="inspector-panel" :class="{ 'md:col-span-2': selectedRole || (!focusedKind && !normalizedKeyword && queryMode === 'all') }" aria-live="polite">
        <div class="flex gap-3 items-start justify-between">
          <div class="min-w-0">
            <span class="inspector-meta">{{ labels[selected.kind] }} · {{ selected.available ? '当前可用' : '当前停用或运行不可用' }}</span><h3 class="text-base font-semibold mt-1 break-words">
              {{ selected.label }}
            </h3><p class="inspector-code">
              {{ selected.caption }}
            </p>
          </div><YdButton size="sm" variant="ghost" aria-label="关闭节点详情" @click="selectedId = null">
            <YdIcon name="morph:x" />
          </YdButton>
        </div>
        <p v-if="selectedUser" class="inspector-note">
          {{ selectedUser.contextSource === 'SESSION' ? '本次会话的部门 / 角色上下文' : '默认部门 / 角色上下文（实际会话可切换）' }}：{{ userContextDept }} / {{ roleIndex.get(selectedUser.currentRoleId || '')?.name || '未选择' }}。已分配 {{ selectedUser.roleIds.length }} 个角色，实际访问不会合并所有角色权限。{{ selectedUser.status !== 'ACTIVE' || !selectedUser.emailVerified ? '此用户停用或尚未验证邮箱，当前不可访问。' : '' }}
        </p>
        <p v-if="selectedUser" class="inspector-note">
          所属部门：{{ userDeptNames.join('、') || '尚未分配' }}。修改角色分配只调整可选择的身份，不代表本次会话已切换。
        </p>
        <div v-if="selectedPermission" class="mt-3">
          <YdDescriptions :items="selectedPermissionDetails" :column="1" size="sm" :label-width="80" value-class="break-words" />
          <div class="mt-3 flex flex-wrap gap-2">
            <YdButton size="sm" variant="outline" :disabled="loading" @click="emit('inspectPermission', selectedPermission.code)">
              <YdIcon name="morph:book-open" />查看细则
            </YdButton>
            <YdButton v-if="auth('system:role:edit')" size="sm" :disabled="loading" @click="emit('grantPermission', selectedPermission.code)">
              <YdIcon name="morph:shield-plus" />授予角色
            </YdButton>
          </div>
        </div>
        <p v-if="selectedRole" class="inspector-note">
          {{ selectedRole.status === 'ACTIVE' ? '角色已启用' : '角色已停用' }} · 配置 {{ selectedRole.permissions.length }} 项权限。{{ selectedRole.systemType === 'SUPER_ADMIN' ? '超级管理员的实际授权由运行时规则判定。' : '' }}
        </p>
        <div v-if="selectedRole" class="mt-4">
          <div class="flex flex-wrap gap-2 items-center justify-between">
            <strong>已有权限 · {{ (selectedRole.systemType === 'SUPER_ADMIN' || selectedRole.permissions.includes('*')) ? '全目录通配' : '显式配置' }}</strong><YdButton v-if="auth('system:role:edit')" size="sm" variant="outline" :disabled="loading" @click="emit('editRole', selectedRole)">
              <YdIcon name="morph:settings-2" />调整权限
            </YdButton>
          </div>
          <p v-if="selectedRole.systemType === 'SUPER_ADMIN' || selectedRole.permissions.includes('*')" class="inspector-note">
            此身份在启用并被会话选中时通过 * 获得全部权限。单项撤销不能限制通配身份，使用现有角色管理调整身份与状态。
          </p>
          <p v-if="uncataloguedRolePermissionCount" class="inspector-note">
            另有 {{ uncataloguedRolePermissionCount }} 项未登记或暂失效权限码保留在配置中；此处不会展示或清除这些码。
          </p>
          <YdInput v-model="rolePermissionKeyword" class="mt-3 w-full" clearable aria-label="筛选角色已有权限" placeholder="搜索已有权限、资源或动作分类" />
          <div v-if="rolePermissionGroups.length" class="role-permission-catalog">
            <section v-for="group in rolePermissionGroups" :key="group.key" class="role-permission-group" :aria-label="group.label">
              <h4 class="permission-resource-label">
                {{ group.label }}
              </h4>
              <ol class="permission-action-list">
                <li v-for="permission in group.permissions" :key="permission.code" class="permission-action-row">
                  <YdButton size="sm" variant="ghost" class="permission-inspect-action" :disabled="loading" :title="`查看 ${permission.name} 的权限细则`" @click="emit('inspectPermission', permission.code)">
                    <span class="permission-action-name">{{ permission.name }}<span class="permission-action-code">{{ permission.code }}</span></span>
                  </YdButton>
                  <YdTag :variant="permissionCategoryOf(permission.code).variant">
                    {{ permissionCategoryOf(permission.code).label }}
                  </YdTag>
                  <YdButton v-if="canRevoke(selectedRole, permission.code)" size="sm" variant="destructive" :disabled="loading" :aria-label="`从 ${selectedRole.name} 撤销 ${permission.name}`" @click="emit('revokePermission', selectedRole.id, permission.code)">
                    撤销
                  </YdButton>
                </li>
              </ol>
            </section>
          </div>
          <p v-else class="inspector-empty">
            {{ rolePermissionKeyword ? '没有匹配的已登记权限' : '没有显式配置的 ACTIVE 权限' }}
          </p>
          <YdPagination v-if="rolePermissionItems.length > rolePermissionSize" v-model:page="rolePermissionPage" v-model:size="rolePermissionSize" :total="rolePermissionItems.length" :sizes="[30]" layout="total, ->, pager" class="mt-3" />
        </div>
        <div v-if="selectedMenu" class="mt-3">
          <YdDescriptions
            :items="[
              { key: 'code', label: '菜单编码', value: selectedMenu.code, valueClass: 'break-all font-mono' },
              { key: 'type', label: '节点类型', value: selectedMenu.type },
              { key: 'permission', label: '直接入口码', value: selectedMenu.permission || '无独立入口权限码', valueClass: 'break-all font-mono' },
              { key: 'status', label: '配置状态', value: selectedMenu.status === 'ACTIVE' ? '已启用' : '已停用' },
              { key: 'runtime', label: '运行状态', value: selectedMenu.runtimeAvailable ? '当前可用' : '插件或平台能力当前不可用' },
            ]" :column="1" size="sm" :label-width="80" value-class="break-words"
          />
          <p class="inspector-note">
            可见角色身份由服务端按入口、按钮与结构祖先规则计算。配置权限不会开启菜单、插件或平台能力，实际访问仍取决于会话身份和运行状态。
          </p>
          <div v-if="menuDirectPermission" class="mt-3 flex flex-wrap gap-2">
            <YdButton size="sm" variant="outline" :disabled="loading" @click="emit('inspectPermission', menuDirectPermission.code)">
              <YdIcon name="morph:book-open" />入口权限细则
            </YdButton>
            <YdButton v-if="auth('system:role:edit')" size="sm" :disabled="loading" @click="emit('grantPermission', menuDirectPermission.code)">
              <YdIcon name="morph:shield-plus" />授予此入口权限
            </YdButton>
          </div>
          <p v-else class="inspector-note">
            此节点没有对应的 ACTIVE 直接入口权限，无法从结构祖先或按钮关联推导可授予的权限码。
          </p>
        </div>
        <div v-if="selected.kind !== 'roles'" class="mt-3">
          <span class="inspector-meta">{{ selected.kind === 'permissions' ? '权限来源角色' : selected.kind === 'menus' ? '可见角色身份' : '已分配角色' }}</span>
          <div v-if="relatedRoles.length" class="related-role-actions">
            <div v-for="role in relatedRoles" :key="role.id" class="related-role-row">
              <YdButton v-if="auth('system:role:edit')" size="sm" variant="outline" :disabled="loading" @click="emit('editRole', role)">
                <YdIcon name="morph:shield" />{{ role.name }}
              </YdButton>
              <YdTag v-else variant="outline">
                {{ role.name }}
              </YdTag>
              <YdTag v-if="role.status !== 'ACTIVE'" variant="secondary">
                停用 · 配置未生效
              </YdTag>
              <YdTag v-if="role.systemType === 'SUPER_ADMIN' || role.permissions.includes('*')" variant="secondary">
                全权限通配
              </YdTag>
              <YdButton v-if="selectedPermission && canRevoke(role, selectedPermission.code)" size="sm" variant="destructive" :disabled="loading" @click="emit('revokePermission', role.id, selectedPermission.code)">
                撤销此权限
              </YdButton>
            </div>
          </div><p v-else class="inspector-empty">
            没有关联的角色配置。
          </p>
          <p v-if="selected.kind === 'permissions'" class="inspector-note">
            包含超级管理员和历史配置的通配授权；停用角色仅展示配置关系，不会生效。
          </p>
        </div>
        <div class="mt-3 flex flex-wrap gap-2">
          <YdButton v-if="canAssignSelectedUser && selectedUser" size="sm" :disabled="loading" @click="emit('assignUserRoles', selectedUser.id)">
            <YdIcon name="morph:user-round-cog" />分配角色
          </YdButton>
          <YdButton v-if="canEditSelected && selected.kind !== 'roles'" size="sm" variant="outline" :disabled="loading" @click="openManagement(selected)">
            <YdIcon name="morph:arrow-up-right" />{{ selected.kind === 'users' ? '前往用户管理' : '前往菜单管理' }}
          </YdButton>
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.permission-constellation {
  position: relative;
  padding: 20px;
  overflow: hidden;
  color: var(--color-text-1);
  background: var(--color-bg-1);
  border: 1px solid var(--color-border-2);
  border-radius: var(--yd-card-radius, 20px);
}

.constellation-toolbar {
  position: relative;
  z-index: 2;
}

.constellation-eyebrow {
  display: inline-flex;
  gap: 8px;
  align-items: center;
  padding: 8px 12px;
  font-size: 13px;
  font-weight: 600;
  background: var(--color-bg-2);
  border: 1px solid var(--color-border-2);
  border-radius: 999px;
}

.constellation-subtitle {
  margin-top: 10px;
  font-size: 12px;
  color: var(--color-text-3);
}

.constellation-stage {
  position: relative;
  margin: 0 -20px;
  background-image: radial-gradient(var(--color-fill-3) 0.7px, transparent 0.7px);
  background-size: 18px 18px;
}

.constellation-svg {
  display: block;
  width: 100%;
  max-height: 740px;
  overflow: visible;
}

.constellation-summary {
  position: absolute;
  top: 10px;
  right: 22px;
  z-index: 2;
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 13px 20px;
  padding: 16px 20px;
  background: color-mix(in srgb, var(--color-bg-2) 86%, transparent);
  border: 1px solid var(--color-border-2);
  border-radius: 20px;
  box-shadow: 0 8px 25px color-mix(in srgb, var(--color-text-1) 4%, transparent);
  backdrop-filter: blur(12px);
}

.summary-item {
  display: flex;
  flex-direction: column;
  gap: 3px;
  text-align: left;
}

.summary-item span {
  font-size: 11px;
  color: var(--color-text-3);
}

.summary-item strong {
  font-size: 22px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.summary-query {
  cursor: pointer;
}

.summary-query:hover strong {
  text-decoration: underline;
  text-underline-offset: 4px;
}

.summary-query:disabled {
  cursor: default;
  opacity: 0.5;
}

.summary-query:focus-visible {
  outline: 2px solid var(--color-text-2);
  outline-offset: 4px;
  border-radius: 4px;
}

.constellation-orbits ellipse {
  fill: none;
  stroke: var(--color-border-2);
  stroke-width: 1;
}

.constellation-orbits .orbit-inner {
  opacity: 0.6;
  stroke-dasharray: 3 11;
}

.orbit-drift {
  fill: var(--color-text-3);
  animation: orbit-revolve 65s linear infinite;
}

.hub-link {
  fill: none;
  stroke: var(--color-border-3);
  stroke-width: 1.7;
  stroke-dasharray: 1;
  animation: relation-grow 1.1s ease both;
}

.category-hub {
  cursor: pointer;
  outline: none;
}

.hub-aura {
  opacity: 0.4;
  fill: var(--color-bg-1);
}

.hub-surface {
  filter: drop-shadow(0 5px 8px color-mix(in srgb, var(--color-text-1) 9%, transparent));
  fill: var(--color-bg-2);
  stroke: var(--color-border-2);
  stroke-width: 1;
  transition: stroke-width var(--yd-motion-duration-fast, 140ms);
}

.category-hub:hover .hub-surface,
.category-hub:focus-visible .hub-surface,
.hub-is-focused .hub-surface {
  stroke: var(--color-text-3);
  stroke-width: 2;
}

.hub-ticks line {
  stroke: var(--color-fill-3);
  stroke-width: 2;
}

.hub-ticks .tick-is-filled {
  stroke: var(--color-text-3);
}

.hub-symbol {
  font-size: 19px;
  fill: var(--color-text-3);
}

.hub-count {
  font-size: 29px;
  font-weight: 650;
  font-variant-numeric: tabular-nums;
  fill: var(--color-text-1);
}

.hub-label {
  font-size: 17px;
  font-weight: 600;
  fill: var(--color-text-1);
}

.hub-caption {
  font-size: 11px;
  fill: var(--color-text-3);
}

.sphere-stop-light {
  stop-color: var(--color-bg-1);
}

.sphere-stop-mid {
  stop-color: var(--color-fill-2);
}

.sphere-stop-dark {
  stop-color: var(--color-fill-4);
}

.core-aura {
  opacity: 0.5;
  fill: var(--color-fill-1);
}

.core-sphere {
  filter: drop-shadow(0 9px 13px color-mix(in srgb, var(--color-text-1) 13%, transparent));
  stroke: var(--color-border-2);
  stroke-width: 1;
  animation: sphere-breathe 6s ease-in-out infinite;
}

.core-meridian {
  opacity: 0.3;
  fill: none;
  stroke: var(--color-bg-1);
  stroke-width: 0.8;
}

.core-ticks {
  transform-origin: center;
  animation: orbit-revolve 110s linear infinite;
}

.core-ticks line {
  stroke: var(--color-border-3);
  stroke-width: 1.4;
}

.core-label {
  font-size: 18px;
  font-weight: 600;
  fill: var(--color-text-1);
}

.core-caption {
  font-size: 12px;
  fill: var(--color-text-3);
}

.constellation-stars > g {
  transition: opacity var(--yd-motion-duration-normal, 240ms);
}

.satellite-link {
  opacity: 0.65;
  fill: none;
  stroke: var(--color-border-2);
  stroke-width: 0.8;
  stroke-dasharray: 1;
  animation: relation-grow 850ms ease var(--enter-delay) both;
}

.satellite {
  cursor: pointer;
  outline: none;
}

.satellite-hit {
  fill: transparent;
}

.satellite-dot {
  fill: var(--color-text-3);
  stroke: var(--color-bg-1);
  stroke-width: 2;
  animation: star-appear 700ms ease var(--enter-delay) both;
}

.satellite-halo {
  opacity: 0;
  fill: none;
  stroke: var(--color-text-2);
  stroke-width: 1;
  transition: opacity var(--yd-motion-duration-fast, 140ms);
}

.satellite:hover .satellite-halo,
.satellite:focus-visible .satellite-halo,
.star-is-selected .satellite-halo {
  opacity: 0.8;
}

.star-is-muted {
  opacity: 0.22;
}

.star-is-unavailable .satellite-dot {
  fill: var(--color-bg-2);
  stroke: var(--color-text-4);
  stroke-dasharray: 2 1;
}

.satellite-label {
  font-size: 12px;
  fill: var(--color-text-2);
  stroke: var(--color-bg-1);
  stroke-width: 4px;
  paint-order: stroke;
}

.relation-paths {
  pointer-events: none;
}

.relation-paths path {
  opacity: 0.55;
  fill: none;
  stroke: var(--color-text-3);
  stroke-width: 1.2;
  stroke-dasharray: 0.03 0.018;
  animation: path-flow 16s linear infinite;
}

.constellation-loading {
  position: absolute;
  inset: 0;
  z-index: 4;
  display: flex;
  gap: 8px;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  background: color-mix(in srgb, var(--color-bg-1) 75%, transparent);
  backdrop-filter: blur(2px);
}

.constellation-stage-caption {
  position: absolute;
  right: 20px;
  bottom: 16px;
  left: 20px;
  display: flex;
  gap: 6px;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  color: var(--color-text-4);
  text-align: center;
}

.constellation-dock {
  position: relative;
  z-index: 3;
  width: min(100%, 680px);
  padding: 12px 14px;
  margin: -14px auto 0;
  background: color-mix(in srgb, var(--color-bg-2) 92%, transparent);
  border: 1px solid var(--color-border-2);
  border-radius: 18px;
  box-shadow: 0 6px 25px color-mix(in srgb, var(--color-text-1) 5%, transparent);
  backdrop-filter: blur(12px);
}

.constellation-context {
  margin: 16px 0;
  font-size: 11px;
  line-height: 1.8;
  color: var(--color-text-3);
}

.constellation-context p {
  display: flex;
  gap: 5px;
  align-items: center;
  margin: 0;
}

.constellation-context p:first-child {
  width: 100%;
}

.constellation-page-count {
  font-size: 12px;
  color: var(--color-text-3);
}

.inspector-panel {
  min-width: 0;
  padding: 16px;
  background: var(--color-bg-2);
  border: 1px solid var(--color-border-2);
  border-radius: 16px;
}

.inspector-panel strong {
  font-size: 13px;
}

.inspector-meta,
.node-kind {
  font-size: 11px;
  color: var(--color-text-3);
}

.inspector-code {
  margin-top: 4px;
  font-family: monospace;
  font-size: 11px;
  color: var(--color-text-3);
  overflow-wrap: anywhere;
}

.inspector-note,
.inspector-empty {
  margin-top: 10px;
  font-size: 12px;
  line-height: 1.8;
  color: var(--color-text-3);
}

.role-permission-catalog {
  display: flex;
  flex-direction: column;
  gap: 16px;
  max-height: 520px;
  padding: 4px;
  margin-top: 12px;
  overflow: auto;
}

.permission-resource-label {
  padding-bottom: 8px;
  font-size: 12px;
  font-weight: 600;
  color: var(--color-text-2);
  border-bottom: 1px solid var(--color-border-2);
}

.permission-action-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 0;
  margin: 8px 0 0;
  list-style: none;
}

.permission-action-row {
  display: flex;
  gap: 8px;
  align-items: center;
  min-width: 0;
}

.permission-inspect-action {
  flex: 1;
  justify-content: flex-start;
  min-width: 0;
  height: auto;
  white-space: normal;
}

.permission-action-name {
  display: block;
  min-width: 0;
  padding: 2px 0;
  font-size: 12px;
  text-align: left;
  overflow-wrap: anywhere;
}

.permission-action-code {
  display: block;
  margin-top: 2px;
  font-family: monospace;
  font-size: 10px;
  color: var(--color-text-3);
}

.related-role-actions {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 8px;
}

.related-role-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

.is-paused :is(.orbit-drift, .hub-link, .core-sphere, .core-ticks, .satellite-link, .satellite-dot, .relation-paths path) {
  animation-play-state: paused;
}

.is-mobile {
  padding: 14px;
}

.is-mobile .constellation-stage {
  margin: 18px -14px 0;
}

.is-mobile .constellation-summary {
  position: relative;
  inset: auto;
  gap: 8px 16px;
  padding: 12px 16px;
  margin: 0 14px;
}

.is-mobile .summary-item strong {
  font-size: 19px;
}

.is-mobile .constellation-svg {
  margin-top: -10px;
}

.is-mobile .constellation-dock {
  margin-top: 0;
}

.is-mobile .constellation-stage-caption {
  bottom: 5px;
  font-size: 10px;
}

@keyframes relation-grow {
  from {
    opacity: 0;
    stroke-dashoffset: 1;
  }

  to {
    stroke-dashoffset: 0;
  }
}

@keyframes star-appear {
  from {
    opacity: 0;
  }

  to {
    opacity: 1;
  }
}

@keyframes orbit-revolve {
  to {
    transform: rotate(360deg);
  }
}

@keyframes sphere-breathe {
  0%,
  100% {
    opacity: 1;
  }

  50% {
    opacity: 0.82;
  }
}

@keyframes path-flow {
  to {
    stroke-dashoffset: -1;
  }
}

@media (prefers-reduced-motion: reduce) {
  .orbit-drift {
    animation-duration: 160s;
  }

  .core-ticks {
    animation-duration: 220s;
  }

  .core-sphere {
    animation-duration: 12s;
  }

  .relation-paths path {
    animation-duration: 40s;
  }
}
</style>
