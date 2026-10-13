<script setup lang="ts">
import type { MatrixDrafts } from './permission-matrix-draft'
import type { PermissionRoleChange } from '@/api/modules/system-permission'
import type { PermissionItem, RoleManageItem, RoleStatus } from '@/api/modules/system-role'
import { permissionCategoryMeta, permissionCategoryOf, permissionResourceLabels, permissionResourceOf } from '@/utils/permission-catalog'
import {
  MATRIX_PERMISSION_PAGE_SIZE,
  MATRIX_ROLE_PAGE_SIZE,
  MATRIX_UNASSIGNED_DEPT,
  matrixGrantLabel,
  matrixGrantState,
  matrixPage,
  matrixPermissions,
  matrixRoles,
} from './permission-matrix'
import { matrixDraftTarget, matrixSaveChanges, reconcileMatrixDrafts, setMatrixGrants, toggleMatrixGrant } from './permission-matrix-draft'

const props = defineProps<{
  roles: RoleManageItem[]
  permissions: PermissionItem[]
  loading: boolean
  saving?: boolean
}>()

const emit = defineEmits<{
  editRole: [role: RoleManageItem]
  saveChanges: [changes: PermissionRoleChange[]]
  dirtyChange: [dirty: boolean]
  inspectPermission: [code: string]
}>()

const { auth } = useAppAuth()
const modal = useYdModal()
const canEdit = computed(() => auth('system:role:edit'))
const editMode = ref(false)
const drafts = ref<MatrixDrafts>({})
const bulkRoleId = ref<string | number>()
const previewVisible = ref(false)
const previewChanges = ref<PermissionRoleChange[]>([])
const activeCodeSet = computed(() => new Set(props.permissions.filter(item => item.status === 'ACTIVE').map(item => item.code)))
const dirtyRoles = computed(() => Object.values(drafts.value))
const conflicts = computed(() => dirtyRoles.value.filter(item => item.conflict))
const saveableChanges = computed(() => matrixSaveChanges(drafts.value))
const dirty = computed(() => dirtyRoles.value.length > 0)
const busy = computed(() => props.loading || props.saving)
const previewCurrent = computed(() => JSON.stringify(previewChanges.value) === JSON.stringify(saveableChanges.value))
const permissionIndex = computed(() => new Map(props.permissions.map(item => [item.code, item])))
const resourceLabels = computed(() => permissionResourceLabels(props.permissions))
const bulkRole = computed(() => props.roles.find(role => role.id === bulkRoleId.value))
const bulkRoleOptions = computed(() => props.roles.map(role => ({
  label: `${role.name} · ${role.code}${role.deptName ? ` · ${role.deptName}` : ''}${role.status === 'DEPRECATED' ? '（停用）' : ''}`,
  value: role.id,
  disabled: role.systemType === 'SUPER_ADMIN' || role.permissions.includes('*') || !!drafts.value[role.id]?.conflict,
})))
const permissionKeyword = ref('')
const roleKeyword = ref('')
const selectedDomain = ref('')
const selectedResource = ref('')
const selectedCategory = ref('')
const selectedDept = ref('')
const selectedStatus = ref<RoleStatus | ''>('')
const rolePage = ref(1)
const permissionPage = ref(1)
const rolePageSize = ref(MATRIX_ROLE_PAGE_SIZE)
const permissionPageSize = ref(MATRIX_PERMISSION_PAGE_SIZE)

const domainOptions = computed(() => {
  const domains = new Map<string, string>()
  matrixPermissions(props.permissions).forEach((item) => {
    const resource = permissionResourceOf(item, resourceLabels.value)
    domains.set(resource.domainKey, resource.domainLabel)
  })
  return [{ label: '全部业务域', value: '' }, ...Array.from(domains, ([value, label]) => ({ label, value }))]
})
const resourceOptions = computed(() => {
  const resources = new Map<string, string>()
  matrixPermissions(props.permissions, { domain: selectedDomain.value }).forEach((item) => {
    const resource = permissionResourceOf(item, resourceLabels.value)
    resources.set(resource.key, resource.label)
  })
  return [{ label: '全部资源', value: '' }, ...Array.from(resources, ([value, label]) => ({ label, value }))]
})
const categoryOptions = [{ label: '全部动作分类', value: '' }, ...permissionCategoryMeta.map(item => ({ label: item.label, value: item.key }))]

const deptOptions = computed(() => {
  const depts = new Map<string, string>()
  props.roles.forEach((role) => {
    if (role.deptId) {
      depts.set(role.deptId, role.deptName || '未命名部门')
    }
  })
  return [
    { label: '全部部门', value: '' },
    { label: '未关联部门', value: MATRIX_UNASSIGNED_DEPT },
    ...Array.from(depts, ([value, label]) => ({ label, value })),
  ]
})

const statusOptions = [
  { label: '全部状态', value: '' },
  { label: '启用', value: 'ACTIVE' },
  { label: '停用', value: 'DEPRECATED' },
]

const filteredRoles = computed(() => matrixRoles(props.roles, {
  keyword: roleKeyword.value,
  deptId: selectedDept.value,
  status: selectedStatus.value,
}))
const filteredPermissions = computed(() => matrixPermissions(props.permissions, {
  domain: selectedDomain.value,
  resource: selectedResource.value,
  category: selectedCategory.value,
  keyword: permissionKeyword.value,
}))
const visibleRoles = computed(() => matrixPage(filteredRoles.value, rolePage.value, MATRIX_ROLE_PAGE_SIZE))
const visiblePermissions = computed(() => matrixPage(filteredPermissions.value, permissionPage.value, MATRIX_PERMISSION_PAGE_SIZE))
const groups = computed(() => {
  const grouped = new Map<string, { key: string, label: string, domainLabel: string, permissions: PermissionItem[] }>()
  visiblePermissions.value.forEach((permission) => {
    const resource = permissionResourceOf(permission, resourceLabels.value)
    const group = grouped.get(resource.key) || { key: resource.key, label: resource.label, domainLabel: resource.domainLabel, permissions: [] }
    group.permissions.push(permission)
    grouped.set(resource.key, group)
  })
  return Array.from(grouped.values())
})
const grantSets = computed(() => new Map(visibleRoles.value.map(role => [role.id, new Set(drafts.value[role.id] ? matrixDraftTarget(drafts.value[role.id]) : role.permissions)])))
const emptyMessage = computed(() => {
  if (!props.roles.length) {
    return '暂无角色，可通过新增角色建立权限配置。'
  }
  if (!props.permissions.some(item => item.status === 'ACTIVE')) {
    return '暂无可用权限。'
  }
  return '没有符合筛选条件的角色或权限，请调整筛选条件。'
})

watch([roleKeyword, selectedDept, selectedStatus], () => rolePage.value = 1)
watch([permissionKeyword, selectedDomain, selectedResource, selectedCategory], () => permissionPage.value = 1)
watch(selectedDomain, () => selectedResource.value = '')
watch(() => filteredRoles.value.length, (total) => {
  rolePage.value = Math.min(rolePage.value, Math.max(1, Math.ceil(total / MATRIX_ROLE_PAGE_SIZE)))
})
watch(() => filteredPermissions.value.length, (total) => {
  permissionPage.value = Math.min(permissionPage.value, Math.max(1, Math.ceil(total / MATRIX_PERMISSION_PAGE_SIZE)))
})
watch([() => props.roles, () => props.permissions], ([roles]) => {
  drafts.value = reconcileMatrixDrafts(drafts.value, roles, activeCodeSet.value)
}, { deep: true })
watch(dirty, value => emit('dirtyChange', value), { immediate: true })

function stateOf(role: RoleManageItem, permission: PermissionItem) {
  return matrixGrantState(role, permission.code, grantSets.value.get(role.id))
}

function editRole(role: RoleManageItem) {
  if (canEdit.value && !props.saving) {
    emit('editRole', role)
  }
}

function cellChange(role: RoleManageItem, code: string) {
  return drafts.value[role.id]?.grant.includes(code) ? '待授予' : drafts.value[role.id]?.revoke.includes(code) ? '待撤销' : ''
}

function cellLabel(role: RoleManageItem, permission: PermissionItem) {
  const change = cellChange(role, permission.code)
  const state = change ? `${role.name} · ${permission.name}：草稿${change}，尚未保存${role.status === 'DEPRECATED' ? '；角色停用，配置未生效' : ''}` : matrixGrantLabel(role, permission, stateOf(role, permission))
  return `${state}${role.systemType === 'SUPER_ADMIN' ? '；超级管理员通配授权不可编辑' : role.permissions.includes('*') ? '；配置含通配，单项改动无法限制授权，需原权限树处理' : drafts.value[role.id]?.conflict ? '；授权冲突，请先复位此角色' : editMode.value ? '；点击切换待保存授权' : ''}`
}

function cellDisabled(role: RoleManageItem) {
  return !canEdit.value || busy.value || role.systemType === 'SUPER_ADMIN' || role.permissions.includes('*') || !!drafts.value[role.id]?.conflict
}

function grantIcon(role: RoleManageItem, permission: PermissionItem) {
  const state = stateOf(role, permission)
  return state === 'super-admin' || state === 'configured-wildcard' ? 'morph:shield-check' : state === 'assigned' ? 'morph:check' : 'morph:minus'
}

function toggleCell(role: RoleManageItem, permission: PermissionItem) {
  if (editMode.value && !cellDisabled(role)) {
    drafts.value = toggleMatrixGrant(drafts.value, role, permission.code, activeCodeSet.value)
  }
}

function applyBulk(assigned: boolean) {
  if (!editMode.value || !bulkRole.value || cellDisabled(bulkRole.value)) {
    return
  }
  drafts.value = setMatrixGrants(drafts.value, bulkRole.value, filteredPermissions.value.map(item => item.code), assigned, activeCodeSet.value)
}

function discardChanges() {
  drafts.value = {}
  previewVisible.value = false
}

function confirmDiscard(leaveMode = false) {
  if (props.saving) {
    return
  }
  if (!dirty.value) {
    if (leaveMode) {
      editMode.value = false
    }
    return
  }
  modal.confirm({
    title: leaveMode ? '返回只读矩阵' : '丢弃授权草稿',
    content: `将丢弃 ${dirtyRoles.value.length} 个角色尚未保存的授权变更，服务器授权保持原样。`,
    confirmButtonText: '丢弃草稿',
    onConfirm: () => {
      if (props.saving) {
        return
      }
      discardChanges()
      if (leaveMode) {
        editMode.value = false
      }
    },
  })
}

function resetRoleDraft(roleId: string) {
  const draft = drafts.value[roleId]
  if (!draft || props.saving) {
    return
  }
  modal.confirm({
    title: '复位此角色草稿',
    content: `只丢弃「${draft.roleName}（${draft.roleCode}）」的未保存变更，其他角色草稿保留。`,
    confirmButtonText: '复位此角色',
    onConfirm: () => {
      if (props.saving) {
        return
      }
      const kept = { ...drafts.value }
      delete kept[roleId]
      drafts.value = kept
    },
  })
}

function openSavePreview() {
  drafts.value = reconcileMatrixDrafts(drafts.value, props.roles, activeCodeSet.value)
  if (!canEdit.value || busy.value || !saveableChanges.value.length) {
    return
  }
  previewChanges.value = matrixSaveChanges(drafts.value)
  previewVisible.value = true
}

function submitChanges() {
  if (canEdit.value && !busy.value && previewCurrent.value && previewChanges.value.length) {
    emit('saveChanges', previewChanges.value.map(change => ({ ...change, expectedPermissions: [...change.expectedPermissions], grant: [...change.grant], revoke: [...change.revoke] })))
  }
}

function conflictMessage(kind: string | undefined) {
  return kind === 'missing' ? '角色已不可用' : kind === 'protected' ? '角色已变为受保护的超级管理员' : kind === 'wildcard' ? '配置含通配，需原权限树处理' : kind === 'catalog' ? '变更涉及已失效权限，复位后保留这些原授权' : '服务器授权已变化，原基线与当前配置不一致'
}

function previewRole(roleId: string) {
  const draft = drafts.value[roleId]
  return draft ? `${draft.roleName} · ${draft.roleCode}` : props.roles.find(role => role.id === roleId)?.name || roleId
}

defineExpose({ discardChanges, hasUnsavedChanges: () => dirty.value })

function resetFilters() {
  permissionKeyword.value = ''
  roleKeyword.value = ''
  selectedDomain.value = ''
  selectedResource.value = ''
  selectedCategory.value = ''
  selectedDept.value = ''
  selectedStatus.value = ''
}
</script>

<template>
  <section class="permission-matrix" :aria-label="editMode ? '编辑权限矩阵' : '只读权限矩阵'" :aria-busy="busy">
    <div class="matrix-intro">
      <div>
        <h2 class="text-base font-semibold">
          权限配置矩阵
        </h2>
        <p class="matrix-description">
          对照角色保存的权限配置。用户实际权限按当前所选部门和角色判定；停用角色的配置未生效。
        </p>
      </div>
      <div class="flex flex-wrap gap-2 items-center justify-end">
        <YdTag variant="outline">
          {{ editMode ? '编辑授权' : '只读' }}
        </YdTag>
        <YdButton v-if="!editMode && canEdit" variant="outline" :disabled="busy" @click="editMode = true">
          <YdIcon name="morph:square-pen" />编辑授权
        </YdButton>
        <YdButton v-if="editMode" variant="outline" :disabled="saving" @click="confirmDiscard(true)">
          返回只读
        </YdButton>
      </div>
    </div>

    <YdAlert v-if="editMode" title="修改先保存在草稿中" description="点击单元格切换授权；筛选与分页保留草稿。保存前会逐角色预览，停用角色允许修改配置但不会生效。超级管理员与配置含通配的角色不能在矩阵编辑，需通过原权限树处理。" />
    <YdAlert v-if="editMode && !canEdit" variant="destructive" title="当前账号没有角色编辑权限" description="草稿已保留，恢复权限后可继续，或丢弃草稿返回只读。" />

    <div v-if="editMode" class="matrix-edit-toolbar">
      <div class="matrix-bulk-scope">
        <span class="matrix-description">单角色批量操作</span>
        <YdCombobox v-model="bulkRoleId" :options="bulkRoleOptions" clearable virtual-list :disabled="busy || !canEdit" placeholder="选择一个角色（名称 / 编码）" aria-label="选择批量授权目标角色" />
      </div>
      <div class="flex flex-wrap gap-2 items-center">
        <YdButton variant="outline" size="sm" :disabled="!bulkRole || cellDisabled(bulkRole) || !filteredPermissions.length" @click="applyBulk(true)">
          授予筛选权限
        </YdButton>
        <YdButton variant="outline" size="sm" :disabled="!bulkRole || cellDisabled(bulkRole) || !filteredPermissions.length" @click="applyBulk(false)">
          撤销筛选权限
        </YdButton>
      </div>
      <p class="matrix-description w-full">
        仅作用于所选角色的 {{ filteredPermissions.length }} 项筛选匹配权限，包含全部权限分页；其他角色、未匹配权限和暂失效授权均保留。{{ bulkRole?.status === 'DEPRECATED' ? '目标角色停用，修改配置不会使其生效。' : '' }}
      </p>
    </div>

    <div v-if="dirty" class="matrix-draft-summary">
      <div class="flex flex-wrap gap-3 items-center justify-between">
        <span>{{ dirtyRoles.length }} 个角色待保存 · 新增 {{ dirtyRoles.reduce((total, draft) => total + draft.grant.length, 0) }} 项 · 撤销 {{ dirtyRoles.reduce((total, draft) => total + draft.revoke.length, 0) }} 项</span>
        <div class="flex flex-wrap gap-2">
          <YdButton variant="outline" size="sm" :disabled="saving" @click="confirmDiscard()">
            丢弃全部草稿
          </YdButton>
          <YdButton size="sm" :loading="saving" :disabled="!canEdit || busy || !saveableChanges.length" @click="openSavePreview">
            预览并保存{{ conflicts.length ? '无冲突角色' : '' }}
          </YdButton>
        </div>
      </div>
      <p v-if="conflicts.length" class="matrix-description">
        {{ conflicts.length }} 个角色发生冲突，保留原草稿并停止保存这些角色；复位后可依据最新授权重新编辑。
      </p>
      <div class="mt-3 flex flex-col gap-2">
        <div v-for="draft in dirtyRoles" :key="draft.roleId" class="flex flex-wrap gap-2 items-center">
          <YdTag :variant="draft.conflict ? 'destructive' : 'outline'">
            {{ draft.roleName }} · {{ draft.roleCode }}
          </YdTag>
          <span class="matrix-description">+{{ draft.grant.length }} / −{{ draft.revoke.length }}{{ draft.conflict ? ` · ${conflictMessage(draft.conflict)}` : '' }}</span>
          <YdButton variant="ghost" size="sm" :disabled="saving" @click="resetRoleDraft(draft.roleId)">
            复位此角色
          </YdButton>
        </div>
      </div>
    </div>

    <div class="matrix-filters">
      <YdLabel class="matrix-filter">
        <span>角色</span>
        <YdInput v-model="roleKeyword" clearable placeholder="角色名称 / 编码" aria-label="搜索角色名称或编码" class="w-full" />
      </YdLabel>
      <div class="matrix-filter">
        <span>部门</span>
        <YdSelect v-model="selectedDept" :options="deptOptions" aria-label="筛选角色所属部门" class="w-full" />
      </div>
      <div class="matrix-filter">
        <span>角色状态</span>
        <YdSelect v-model="selectedStatus" :options="statusOptions" aria-label="筛选角色状态" class="w-full" />
      </div>
      <div class="matrix-filter">
        <span>业务域</span>
        <YdSelect v-model="selectedDomain" :options="domainOptions" aria-label="筛选权限业务域" class="w-full" />
      </div>
      <div class="matrix-filter">
        <span>资源</span>
        <YdSelect v-model="selectedResource" :options="resourceOptions" aria-label="筛选权限资源" class="w-full" />
      </div>
      <div class="matrix-filter">
        <span>动作分类</span>
        <YdSelect v-model="selectedCategory" :options="categoryOptions" aria-label="筛选权限动作分类" class="w-full" />
      </div>
      <YdLabel class="matrix-filter">
        <span>权限</span>
        <YdInput v-model="permissionKeyword" clearable placeholder="权限名称 / 编码" aria-label="搜索权限名称或编码" class="w-full" />
      </YdLabel>
      <YdButton variant="outline" class="self-end" @click="resetFilters">
        重置
      </YdButton>
    </div>

    <div class="matrix-legend" aria-label="矩阵图例">
      <span><YdIcon name="morph:check" aria-hidden="true" /> 已授权</span>
      <span><YdIcon name="morph:minus" aria-hidden="true" /> 未授权</span>
      <span><YdIcon name="morph:shield-check" aria-hidden="true" /> 超级管理员 · 全部权限</span>
      <span><YdIcon name="morph:shield-check" aria-hidden="true" /> 配置含通配 · 需原权限树处理</span>
      <span class="matrix-description">点击权限名称查看细则；点击角色标题进入原有编辑与权限树</span>
    </div>

    <div v-if="loading" class="matrix-empty" role="status">
      <YdLoading :size="24" text="正在加载权限矩阵…" />
    </div>
    <div v-else-if="!filteredRoles.length || !filteredPermissions.length" class="matrix-empty" role="status">
      <YdEmpty icon="morph:grid-2x2" title="暂无匹配配置" :description="emptyMessage" />
    </div>
    <template v-else>
      <!-- 权限矩阵有双向表头与逐列角色操作，使用语义 table 保持固定标题和屏幕阅读器关联。 -->
      <div class="matrix-scroll" tabindex="0" role="region" aria-label="角色与权限对照表，可横向和纵向滚动">
        <table class="matrix-table">
          <caption class="sr-only">
            角色权限配置。行是权限，列是角色；停用角色的配置未生效。{{ editMode ? '编辑模式，单元格可以切换待保存授权。' : '只读模式。' }}
          </caption>
          <thead>
            <tr>
              <th scope="col" class="matrix-permission-heading">
                <span>权限 / 角色</span>
                <small>{{ filteredPermissions.length }} 项权限 · {{ filteredRoles.length }} 个角色</small>
              </th>
              <th v-for="role in visibleRoles" :key="role.id" scope="col" :class="{ 'matrix-disabled': role.status === 'DEPRECATED' }">
                <YdButton
                  variant="ghost"
                  size="sm"
                  class="matrix-role-button"
                  :disabled="!canEdit || saving"
                  :aria-label="canEdit ? `编辑角色 ${role.name}，打开原有权限树` : `角色 ${role.name}，无编辑权限`"
                  :title="canEdit ? `编辑 ${role.name}` : '当前账号没有角色编辑权限'"
                  @click="editRole(role)"
                >
                  <span class="truncate">{{ role.name }}</span>
                  <YdIcon v-if="canEdit" name="morph:pencil" class="shrink-0 size-3" aria-hidden="true" />
                </YdButton>
                <small class="matrix-role-code" :title="role.code">{{ role.code }}</small>
                <small>{{ role.deptName || '未关联部门' }}</small>
                <YdTag v-if="role.status === 'DEPRECATED'" variant="secondary" class="mt-1">
                  停用 · 配置未生效
                </YdTag>
                <YdTag v-else-if="role.systemType === 'SUPER_ADMIN'" variant="outline" class="mt-1">
                  超级管理员
                </YdTag>
                <YdTag v-if="role.systemType !== 'SUPER_ADMIN' && role.permissions.includes('*')" variant="outline" class="mt-1">
                  配置含通配 · 需原权限树处理
                </YdTag>
                <YdTag v-if="drafts[role.id]?.conflict" variant="destructive" class="mt-1">
                  授权冲突
                </YdTag>
              </th>
            </tr>
          </thead>
          <tbody v-for="group in groups" :key="group.key">
            <tr class="matrix-module-row">
              <th scope="rowgroup" :colspan="visibleRoles.length + 1">
                <span>{{ group.domainLabel }} / {{ group.label }}</span>
              </th>
            </tr>
            <tr v-for="permission in group.permissions" :key="permission.code">
              <th scope="row" class="matrix-permission-cell" :title="permission.desc || permission.name">
                <div class="flex flex-wrap gap-2 items-center">
                  <YdButton variant="ghost" size="sm" class="matrix-permission-button" :aria-label="`查看权限细则：${permission.name}`" @click="emit('inspectPermission', permission.code)">
                    {{ permission.name }}
                  </YdButton>
                  <YdTag :variant="permissionCategoryOf(permission.code).variant">
                    {{ permissionCategoryOf(permission.code).label }}
                  </YdTag>
                </div>
                <small>{{ permission.code }}</small>
              </th>
              <td
                v-for="role in visibleRoles"
                :key="role.id"
                :class="{ 'matrix-disabled': role.status === 'DEPRECATED' }"
              >
                <YdButton
                  v-if="editMode"
                  size="icon-sm"
                  :variant="stateOf(role, permission) === 'unassigned' ? 'ghost' : 'secondary'"
                  :disabled="cellDisabled(role)"
                  :aria-pressed="stateOf(role, permission) !== 'unassigned'"
                  :aria-label="cellLabel(role, permission)"
                  :title="cellLabel(role, permission)"
                  @click="toggleCell(role, permission)"
                >
                  <YdIcon :name="grantIcon(role, permission)" aria-hidden="true" />
                </YdButton>
                <span
                  v-else
                  class="matrix-grant"
                  :class="{ 'matrix-granted': stateOf(role, permission) !== 'unassigned' }"
                  role="img"
                  :aria-label="matrixGrantLabel(role, permission, stateOf(role, permission))"
                  :title="matrixGrantLabel(role, permission, stateOf(role, permission))"
                >
                  <YdIcon
                    :name="grantIcon(role, permission)"
                    aria-hidden="true"
                  />
                </span>
                <small v-if="cellChange(role, permission.code)">{{ cellChange(role, permission.code) }}</small>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <div class="matrix-pagination">
        <div class="matrix-page-group">
          <span>角色列</span>
          <YdPagination v-model:page="rolePage" v-model:size="rolePageSize" :total="filteredRoles.length" layout="total, pager" :text-templates="{ total: total => `共 ${total} 个角色` }" />
        </div>
        <div class="matrix-page-group">
          <span>权限行</span>
          <YdPagination v-model:page="permissionPage" v-model:size="permissionPageSize" :total="filteredPermissions.length" layout="total, pager" :text-templates="{ total: total => `共 ${total} 项权限` }" />
        </div>
      </div>
    </template>

    <YdModal
      v-model="previewVisible"
      title="确认授权变更"
      description="以下是逐角色的操作意图；未列出的权限保持原样。"
      show-cancel-button
      confirm-button-text="确认保存"
      :confirm-button-disabled="!canEdit || busy || !previewCurrent || !previewChanges.length"
      :confirm-button-loading="saving"
      class="sm:max-w-3xl"
      @confirm="submitChanges"
    >
      <YdAlert v-if="!previewCurrent" variant="destructive" title="授权草稿已变化" description="请关闭此预览，处理冲突后重新预览保存。" />
      <YdAlert v-if="conflicts.length" title="冲突角色不会提交" :description="`${conflicts.length} 个角色保留草稿，仅保存下面无冲突的角色。`" class="mb-4" />
      <div class="flex flex-col gap-5">
        <section v-for="change in previewChanges" :key="change.roleId" class="matrix-preview-role">
          <h3 class="font-semibold">
            {{ previewRole(change.roleId) }}
          </h3>
          <p class="matrix-description">
            角色 ID：{{ change.roleId }}{{ roles.find(role => role.id === change.roleId)?.status === 'DEPRECATED' ? ' · 停用角色，配置暂不生效' : '' }}
          </p>
          <div v-for="operation in [{ key: 'grant', label: '新增授权', codes: change.grant }, { key: 'revoke', label: '撤销授权', codes: change.revoke }]" :key="operation.key" class="mt-3">
            <p class="text-sm font-medium">
              {{ operation.label }} · {{ operation.codes.length }} 项
            </p>
            <ul v-if="operation.codes.length" class="matrix-preview-list">
              <li v-for="code in operation.codes" :key="code" class="flex flex-wrap gap-2 items-center">
                <YdTag :variant="permissionCategoryOf(code).variant">
                  {{ permissionCategoryOf(code).label }}{{ permissionCategoryOf(code).key === 'danger' ? ' · 危险权限' : '' }}
                </YdTag>
                <span>{{ permissionIndex.get(code)?.name || code }}</span>
                <span class="matrix-description break-all">{{ code }}</span>
              </li>
            </ul>
            <p v-else class="matrix-description">
              无
            </p>
          </div>
        </section>
      </div>
    </YdModal>
  </section>
</template>

<style scoped>
.permission-matrix {
  display: flex;
  flex-direction: column;
  gap: 20px;
  min-width: 0;
}

.matrix-intro {
  display: flex;
  gap: 16px;
  align-items: flex-start;
  justify-content: space-between;
}

.matrix-description {
  margin-top: 6px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--color-text-3);
}

.matrix-edit-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
  padding: 16px;
  background: var(--color-fill-1);
  border: 1px solid var(--color-border-2);
  border-radius: 12px;
}

.matrix-bulk-scope {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 6px;
  min-width: min(100%, 250px);
}

.matrix-draft-summary,
.matrix-preview-role {
  padding: 16px;
  background: var(--color-bg-2);
  border: 1px solid var(--color-border-2);
  border-radius: 12px;
}

.matrix-preview-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 8px;
}

.matrix-filters {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(100%, 160px), 1fr));
  gap: 12px;
}

.matrix-filter {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
  font-size: 12px;
  color: var(--color-text-2);
}

.matrix-legend {
  display: flex;
  flex-wrap: wrap;
  gap: 12px 20px;
  align-items: center;
  font-size: 12px;
  color: var(--color-text-2);
}

.matrix-legend > span {
  display: inline-flex;
  gap: 6px;
  align-items: center;
}

.matrix-legend .matrix-description {
  margin: 0;
}

.matrix-empty {
  display: flex;
  gap: 10px;
  align-items: center;
  justify-content: center;
  min-height: 280px;
  padding: 24px;
  color: var(--color-text-3);
  text-align: center;
  background: var(--color-fill-1);
  border: 1px solid var(--color-border-2);
  border-radius: 12px;
}

.matrix-scroll {
  max-height: 620px;
  overflow: auto;
  overscroll-behavior: contain;
  border: 1px solid var(--color-border-2);
  border-radius: 12px;
}

.matrix-table {
  width: 100%;
  font-size: 13px;
  border-spacing: 0;
  border-collapse: separate;
}

.matrix-table th,
.matrix-table td {
  padding: 10px 12px;
  vertical-align: middle;
  border-right: 1px solid var(--color-border-1);
  border-bottom: 1px solid var(--color-border-1);
}

.matrix-table tr > :last-child {
  border-right: 0;
}

.matrix-table tbody:last-child tr:last-child > * {
  border-bottom: 0;
}

.matrix-table thead th {
  position: sticky;
  top: 0;
  z-index: 2;
  min-width: 176px;
  max-width: 208px;
  font-weight: 500;
  text-align: center;
  background: var(--color-bg-2);
}

.matrix-table .matrix-permission-heading {
  left: 0;
  z-index: 3;
  min-width: 250px;
  text-align: left;
}

.matrix-table small {
  display: block;
  margin-top: 4px;
  font-size: 11px;
  font-weight: 400;
  color: var(--color-text-3);
}

.matrix-role-button {
  width: 100%;
  max-width: 184px;
}

.matrix-permission-button {
  height: auto;
  padding: 4px;
  margin-left: -4px;
  text-align: left;
  white-space: normal;
}

.matrix-role-code {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.matrix-module-row th {
  padding-top: 8px;
  padding-bottom: 8px;
  font-size: 12px;
  color: var(--color-text-2);
  text-align: left;
  background: var(--color-fill-2);
}

.matrix-module-row span {
  position: sticky;
  left: 12px;
}

.matrix-table .matrix-permission-cell {
  position: sticky;
  left: 0;
  z-index: 1;
  min-width: 250px;
  max-width: 300px;
  font-weight: 500;
  text-align: left;
  overflow-wrap: anywhere;
  background: var(--color-bg-2);
}

.matrix-table td {
  text-align: center;
  background: var(--color-bg-2);
}

.matrix-table tbody tr:hover td,
.matrix-table tbody tr:hover .matrix-permission-cell {
  background: var(--color-fill-1);
}

.matrix-grant {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  color: var(--color-text-4);
  border: 1px solid transparent;
  border-radius: 8px;
}

.matrix-granted {
  color: var(--color-text-1);
  background: var(--color-fill-2);
  border-color: var(--color-border-2);
}

.matrix-disabled .matrix-granted {
  color: var(--color-text-3);
  background: var(--color-fill-1);
  border-style: dashed;
}

.matrix-pagination {
  display: flex;
  flex-wrap: wrap;
  gap: 20px;
  justify-content: space-between;
}

.matrix-page-group {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 12px;
  align-items: center;
  font-size: 12px;
  color: var(--color-text-3);
}
</style>
