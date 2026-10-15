<script setup lang="ts">
import type { TableColumn, YdTablePickerQuery, YdTablePickerResult } from '@yudream/components'
import type { PermissionOverview } from '@/api/modules/system-permission'
import type { PermissionItem, RoleManageItem } from '@/api/modules/system-role'
import type { UserManageItem } from '@/api/modules/system-user'
import { useYdToast } from '@yudream/components'
import apiPermission from '@/api/modules/system-permission'
import apiUser from '@/api/modules/system-user'
import { toBackendAssetUrl } from '@/utils/backend-url'
import { permissionCategoryOf } from '@/utils/permission-catalog'
import { permissionOperationError, rolePermissionChange, userRoleDelta } from './permission-operations'
import PermissionDetails from './PermissionDetails.vue'

const props = defineProps<{ overview?: PermissionOverview, blocked: boolean, busy: boolean }>()
const emit = defineEmits<{
  roleSaved: [role: RoleManageItem]
  userSaved: [user: UserManageItem]
  editRole: [role: RoleManageItem]
  busyChange: [busy: boolean]
}>()
const { auth } = useAppAuth()
const toast = useYdToast()
const detailsVisible = ref(false)
const detailsCode = ref('')
const operationVisible = ref(false)
const operationMode = ref<'grant' | 'revoke'>('grant')
const operationCode = ref('')
const selectedRoleIds = ref<string[]>([])
const roleSnapshot = ref<RoleManageItem>()
const saving = ref(false)
const operationError = ref('')
const userVisible = ref(false)
const userLoading = ref(false)
const userError = ref('')
const userSnapshot = ref<UserManageItem>()
const selectedUserRoles = ref<string[]>([])
let userRequest = 0

const roleColumns: TableColumn<RoleManageItem>[] = [
  { accessorKey: 'name', header: '角色', minWidth: 150 },
  { accessorKey: 'deptName', header: '部门', minWidth: 130 },
  { accessorKey: 'code', header: '编码', minWidth: 150 },
  { accessorKey: 'status', header: '状态', width: 110 },
]
const permission = computed<PermissionItem>(() => props.overview?.permissions.find(item => item.code === operationCode.value) || {
  code: operationCode.value,
  name: operationCode.value,
  module: '',
  status: 'DEPRECATED',
})
const eligibleUserRoles = computed(() => props.overview?.roles.filter(role => role.status === 'ACTIVE'
  && !!role.deptId && !!userSnapshot.value?.deptIds.includes(role.deptId)) || [])
const userLabels = computed(() => Object.fromEntries(selectedUserRoles.value.map(id => [id, props.overview?.roles.find(role => role.id === id)?.name || `保留的历史角色 ${id}`])))
const userChange = computed(() => userRoleDelta(userSnapshot.value?.roleIds || [], selectedUserRoles.value))
const userHasChanges = computed(() => userChange.value.grant.length + userChange.value.revoke.length > 0)
const operationHasChange = computed(() => !!roleSnapshot.value && (operationMode.value === 'grant'
  ? !roleSnapshot.value.permissions.includes(operationCode.value)
  : roleSnapshot.value.permissions.includes(operationCode.value)))

watch(saving, value => emit('busyChange', value))
watch(selectedRoleIds, (ids) => {
  const role = props.overview?.roles.find(item => item.id === ids[0])
  roleSnapshot.value = role ? { ...role, permissions: [...role.permissions] } : undefined
})

function canWrite(permissionCode: string) {
  if (!auth(permissionCode) || saving.value || props.busy) {
    return false
  }
  if (props.blocked) {
    toast.warning('请先保存或丢弃矩阵中的授权草稿，再进行快捷操作。')
    return false
  }
  return true
}

function inspectPermission(code: string) {
  detailsCode.value = code
  detailsVisible.value = true
}

function grantPermission(code: string) {
  if (!canWrite('system:role:edit')) {
    return
  }
  const item = props.overview?.permissions.find(item => item.code === code)
  if (item?.status !== 'ACTIVE') {
    toast.warning('此权限已停用或未登记，不能新增授权。')
    return
  }
  detailsVisible.value = false
  operationMode.value = 'grant'
  operationCode.value = code
  selectedRoleIds.value = []
  roleSnapshot.value = undefined
  operationError.value = ''
  operationVisible.value = true
}

function revokePermission(roleId: string, code: string) {
  if (!canWrite('system:role:edit')) {
    return
  }
  const role = props.overview?.roles.find(item => item.id === roleId)
  if (props.overview?.permissions.find(item => item.code === code)?.status !== 'ACTIVE') {
    toast.warning('此权限暂不可用，原授权保留；如需清理，请使用原权限树明确处理。')
    return
  }
  if (!role || role.systemType === 'SUPER_ADMIN' || role.permissions.includes('*') || !role.permissions.includes(code)) {
    toast.warning('该角色没有可撤销的显式授权。')
    return
  }
  detailsVisible.value = false
  operationMode.value = 'revoke'
  operationCode.value = code
  selectedRoleIds.value = [roleId]
  roleSnapshot.value = { ...role, permissions: [...role.permissions] }
  operationError.value = ''
  operationVisible.value = true
}

function rolePage(roles: RoleManageItem[], query: YdTablePickerQuery): YdTablePickerResult<RoleManageItem> {
  const keyword = query.keyword?.trim().toLocaleLowerCase() || ''
  const matches = roles.filter(role => !keyword || `${role.name} ${role.code} ${role.deptName || ''}`.toLocaleLowerCase().includes(keyword))
  return { list: matches.slice((query.page - 1) * query.size, query.page * query.size), total: matches.length }
}

async function fetchGrantRoles(query: YdTablePickerQuery) {
  return rolePage(props.overview?.roles.filter(role => role.systemType !== 'SUPER_ADMIN' && !role.permissions.includes('*')
    && !role.permissions.includes(operationCode.value)) || [], query)
}

async function fetchUserRoles(query: YdTablePickerQuery) {
  return rolePage(eligibleUserRoles.value, query)
}

async function savePermission() {
  if (!roleSnapshot.value || !operationHasChange.value || !auth('system:role:edit') || saving.value || props.blocked || props.busy) {
    return
  }
  saving.value = true
  operationError.value = ''
  try {
    if (props.overview?.permissions.find(item => item.code === operationCode.value)?.status !== 'ACTIVE') {
      throw new Error('权限目录已变化，此权限暂不可用，请刷新复核。')
    }
    const change = rolePermissionChange(roleSnapshot.value, permission.value, operationMode.value === 'grant')
    const response = await apiPermission.changeRolePermissions(change)
    emit('roleSaved', response.data)
    operationVisible.value = false
    toast.success(operationMode.value === 'grant' ? '授权已保存' : '权限已撤销')
  }
  catch (error) {
    operationError.value = permissionOperationError(error)
  }
  finally {
    saving.value = false
  }
}

async function assignUserRoles(id: string) {
  if (!auth('system:user:view') || !canWrite('system:user:assign-role')) {
    return
  }
  const request = ++userRequest
  detailsVisible.value = false
  userSnapshot.value = undefined
  selectedUserRoles.value = []
  userError.value = ''
  userVisible.value = true
  userLoading.value = true
  try {
    const response = await apiUser.detail(id)
    if (request === userRequest && userVisible.value) {
      userSnapshot.value = {
        ...response.data,
        avatar: response.data.avatar ? toBackendAssetUrl(response.data.avatar) : response.data.avatar,
      }
      selectedUserRoles.value = [...response.data.roleIds]
    }
  }
  catch (error) {
    if (request === userRequest) {
      userError.value = permissionOperationError(error)
    }
  }
  finally {
    if (request === userRequest) {
      userLoading.value = false
    }
  }
}

async function saveUserRoles() {
  const user = userSnapshot.value
  if (!user || !userHasChanges.value || !auth('system:user:view') || !auth('system:user:assign-role') || saving.value || props.blocked || props.busy) {
    return
  }
  saving.value = true
  userError.value = ''
  try {
    const response = await apiPermission.changeUserRoles(user.id, userChange.value)
    emit('userSaved', response.data)
    userVisible.value = false
    toast.success('用户角色分配已保存')
  }
  catch (error) {
    userError.value = permissionOperationError(error)
  }
  finally {
    saving.value = false
  }
}

function editRole(id: string) {
  if (!canWrite('system:role:edit')) {
    return
  }
  const role = props.overview?.roles.find(role => role.id === id)
  if (role) {
    detailsVisible.value = false
    emit('editRole', role)
  }
}

function closeDialogs() {
  if (!saving.value) {
    ++userRequest
    detailsVisible.value = false
    operationVisible.value = false
    userVisible.value = false
  }
}

defineExpose({ inspectPermission, grantPermission, revokePermission, assignUserRoles, closeDialogs, isSaving: () => saving.value })
</script>

<template>
  <YdModal v-model="detailsVisible" title="权限细则与授权来源" :show-confirm-button="false" class="sm:max-w-3xl">
    <PermissionDetails v-if="overview" :overview="overview" :code="detailsCode" @grant="grantPermission" @revoke="revokePermission" @edit-role="editRole" />
  </YdModal>

  <YdModal
    v-model="operationVisible"
    :title="operationMode === 'grant' ? '授予角色权限' : '撤销角色权限'"
    :show-confirm-button="false"
    :closable="!saving"
    :close-on-click-overlay="!saving"
    :close-on-press-escape="!saving"
    class="sm:max-w-xl"
  >
    <div class="flex flex-col gap-4">
      <div>
        <p class="font-semibold">
          {{ permission.name }}
        </p>
        <p class="text-xs text-secondary-foreground/70 mt-1 break-all">
          {{ permission.code }}
        </p>
        <YdTag :variant="permissionCategoryOf(permission.code).variant" class="mt-2">
          {{ permissionCategoryOf(permission.code).label }}
        </YdTag>
      </div>
      <div v-if="operationMode === 'grant'" class="flex flex-col gap-2">
        <YdLabel>目标角色</YdLabel>
        <YdTablePicker v-model="selectedRoleIds" :columns="roleColumns" :fetcher="fetchGrantRoles" :multiple="false" row-key="id" label-key="name" title="选择一个尚未持有此权限的角色" :disabled="saving" />
      </div>
      <div v-if="roleSnapshot" class="text-sm p-3 border rounded-lg">
        <p>{{ operationMode === 'grant' ? '新增授权' : '撤销授权' }}：{{ roleSnapshot.name }} · {{ roleSnapshot.deptName || '未关联部门' }}</p>
        <p v-if="roleSnapshot.status !== 'ACTIVE'" class="text-secondary-foreground/70 mt-2">
          角色已停用，此次只修改配置，不会使角色自动生效。
        </p>
      </div>
      <p class="text-sm text-secondary-foreground/70 leading-relaxed">
        只修改这一个权限，其余权限和历史插件授权保留。若角色授权已被其他操作修改，本次保存会拒绝覆盖并提示刷新。
      </p>
      <p v-if="operationError" class="text-sm p-3 border rounded-lg" role="alert">
        {{ operationError }}
      </p>
    </div>
    <template #footer>
      <YdButton variant="outline" :disabled="saving" @click="operationVisible = false">
        取消
      </YdButton>
      <YdButton :variant="operationMode === 'revoke' ? 'destructive' : 'default'" :loading="saving" :disabled="!operationHasChange || blocked || busy" @click="savePermission">
        {{ operationMode === 'grant' ? '确认授予' : '确认撤销' }}
      </YdButton>
    </template>
  </YdModal>

  <YdModal
    v-model="userVisible"
    title="分配用户角色"
    :show-confirm-button="false"
    :closable="!saving"
    :close-on-click-overlay="!saving"
    :close-on-press-escape="!saving"
    class="sm:max-w-2xl"
  >
    <p v-if="userLoading" role="status">
      正在读取用户角色与部门…
    </p>
    <div v-else-if="userSnapshot" class="flex flex-col gap-4">
      <div class="flex gap-2 items-center">
        <YdAvatar :src="userSnapshot.avatar || ''" :fallback="userSnapshot.nickname || userSnapshot.username" class="shrink-0" />
        <p class="font-semibold">
          {{ userSnapshot.nickname || userSnapshot.username }} <span class="text-xs text-secondary-foreground/70">{{ userSnapshot.username }}</span>
        </p>
      </div>
      <p class="text-sm text-secondary-foreground/70">
        只可新增用户已有部门内的启用角色。原有历史或暂不可用角色保留，只有明确取消选择才会撤销。
      </p>
      <YdTablePicker v-model="selectedUserRoles" :columns="roleColumns" :fetcher="fetchUserRoles" :initial-labels="userLabels" row-key="id" label-key="name" title="选择用户所属部门内的角色" :disabled="saving" />
      <div class="text-sm leading-relaxed p-3 border rounded-lg">
        <p>新增 {{ userChange.grant.length }} 个 · 撤销 {{ userChange.revoke.length }} 个</p>
        <p class="text-secondary-foreground/70 mt-2">
          用户可以切换角色身份；授权不会合并成全部角色权限，也不会自动切换其当前登录会话。
        </p>
      </div>
      <ul v-if="userHasChanges" class="text-sm max-h-44 overflow-auto space-y-2">
        <li v-for="id in userChange.grant" :key="`grant:${id}`">
          新增：{{ overview?.roles.find(role => role.id === id)?.name || id }}
        </li>
        <li v-for="id in userChange.revoke" :key="`revoke:${id}`">
          撤销：{{ overview?.roles.find(role => role.id === id)?.name || `历史角色 ${id}` }}
        </li>
      </ul>
    </div>
    <p v-if="userError" class="text-sm mt-3 p-3 border rounded-lg" role="alert">
      {{ userError }}
    </p>
    <template #footer>
      <YdButton variant="outline" :disabled="saving" @click="userVisible = false">
        取消
      </YdButton>
      <YdButton :loading="saving" :disabled="!userHasChanges || userLoading || blocked || busy" @click="saveUserRoles">
        确认保存角色分配
      </YdButton>
    </template>
  </YdModal>
</template>
