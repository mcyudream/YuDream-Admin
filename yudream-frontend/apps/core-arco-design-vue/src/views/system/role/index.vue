<script setup lang="ts">
import type { TableColumn } from '@yudream/components'
import type { DeptManageItem } from '@/api/modules/system-dept'
import type { PermissionOverview, PermissionOverviewQuery, PermissionRoleChange } from '@/api/modules/system-permission'
import type { IdValue, PermissionItem, RoleLevel, RoleManageItem, RolePayload, RoleStatus } from '@/api/modules/system-role'
import type { UserManageItem } from '@/api/modules/system-user'
import { useYdModal, useYdToast } from '@yudream/components'
import apiDept from '@/api/modules/system-dept'
import apiExcel from '@/api/modules/system-excel'
import apiPermission from '@/api/modules/system-permission'
import apiRole from '@/api/modules/system-role'
import { excelForm, importResultMessage, pickExcelFile, saveExcelResponse } from '@/utils/excel'
import { permissionOperationError } from './components/permission-operations'
import PermissionConstellation from './components/PermissionConstellation.vue'
import PermissionMatrix from './components/PermissionMatrix.vue'
import PermissionOperations from './components/PermissionOperations.vue'
import PermissionTreeEditor from './components/PermissionTreeEditor.vue'

const modal = useYdModal()
const toast = useYdToast()
const router = useRouter()
const { auth } = useAppAuth()
const activeView = ref<string | number>('list')
const viewOptions = [
  { value: 'list', label: '列表', icon: 'morph:list' },
  { value: 'matrix', label: '矩阵', icon: 'morph:table' },
  { value: 'trace', label: '溯源', icon: 'morph:orbit' },
]
const overview = shallowRef<PermissionOverview>()
const overviewLoading = ref(false)
const overviewError = ref('')
const overviewQuery = reactive<PermissionOverviewQuery>({ userPage: 1, userSize: 30 })
let overviewRequest = 0
const matrixRef = ref<InstanceType<typeof PermissionMatrix>>()
const operationsRef = ref<InstanceType<typeof PermissionOperations>>()
const permissionTreeRef = ref<InstanceType<typeof PermissionTreeEditor>>()
const matrixDirty = ref(false)
const matrixSaving = ref(false)
const operationBusy = ref(false)
const matrixResults = ref<{ roleId: string, name: string, success: boolean, message: string }[]>([])

const loading = ref(false)
let rolesRequest = 0
const rows = ref<RoleManageItem[]>([])
const depts = ref<DeptManageItem[]>([])
const permissions = shallowRef<PermissionItem[]>([])
const pagination = reactive({ page: 1, size: 10, total: 0 })
const search = reactive<{ keyword: string, deptId?: IdValue, status?: RoleStatus }>({ keyword: '' })

const formVisible = ref(false)
const editing = ref<RoleManageItem | null>(null)
const form = reactive<RolePayload>({
  name: '',
  code: '',
  deptId: undefined,
  level: 'USER',
  status: 'ACTIVE',
  permissions: [],
})

const statusOptions = [
  { label: '启用', value: 'ACTIVE' },
  { label: '停用', value: 'DEPRECATED' },
]
const levelOptions = [
  { label: '超级管理员', value: 'SUPER_ADMIN' },
  { label: '管理员', value: 'ADMIN' },
  { label: '用户', value: 'USER' },
  { label: '访客', value: 'GUEST' },
]
const deptOptions = computed(() => flattenDepts(depts.value).map(item => ({ label: item.name, value: item.id })))
const permissionCodeSet = computed(() => new Set(permissions.value.filter(item => item.status === 'ACTIVE').map(item => item.code)))
const tableColumns = computed<TableColumn<RoleManageItem>[]>(() => [
  { accessorKey: 'name', header: '角色名称', width: 160, fixed: 'left' },
  { accessorKey: 'code', header: '编码', width: 180 },
  { accessorKey: 'deptName', header: '部门', width: 160 },
  { accessorKey: 'level', header: '等级', width: 120 },
  { accessorKey: 'permissionCount', header: '权限数', width: 100, align: 'center' },
  { id: 'systemRole', header: '类型', width: 110, align: 'center' },
  { id: 'status', header: '状态', width: 90, align: 'center' },
  { id: 'operation', header: '操作', width: 160, align: 'center', fixed: 'right' },
])

onMounted(async () => {
  await loadOptions()
  await loadRoles()
})

watch(activeView, (value) => {
  if (value !== 'list' && !overview.value) {
    loadOverview()
  }
})

onActivated(() => {
  if (overview.value) {
    loadOverview()
  }
})

async function loadOverview() {
  const request = ++overviewRequest
  overviewLoading.value = true
  overviewError.value = ''
  try {
    const response = await apiPermission.overview(overviewQuery)
    if (request === overviewRequest) {
      overview.value = response.data
    }
  }
  catch {
    if (request === overviewRequest) {
      overviewError.value = '权限全景加载失败，请重试。'
    }
  }
  finally {
    if (request === overviewRequest) {
      overviewLoading.value = false
    }
  }
}

async function refreshViews() {
  await Promise.allSettled([
    loadRoles(),
    ...(overview.value || activeView.value !== 'list' ? [loadOverview()] : []),
  ])
}

function searchOverviewUsers(keyword: string) {
  overviewQuery.userKeyword = keyword || undefined
  overviewQuery.userUnassignedOnly = undefined
  overviewQuery.userPage = 1
  loadOverview()
}

function pageOverviewUsers(page: number) {
  overviewQuery.userPage = page
  loadOverview()
}

function showUnassignedUsers(enabled: boolean) {
  overviewQuery.userKeyword = undefined
  overviewQuery.userUnassignedOnly = enabled || undefined
  overviewQuery.userPage = 1
  loadOverview()
}

function editOverviewRole(row: RoleManageItem) {
  if (auth('system:role:edit')) {
    openEdit(row)
  }
}

function updateOverviewRole(role: RoleManageItem) {
  ++overviewRequest
  ++rolesRequest
  overviewLoading.value = false
  loading.value = false
  rows.value = rows.value.map(item => item.id === role.id
    ? { ...role, permissionCount: role.permissions.filter(code => permissionCodeSet.value.has(code)).length }
    : item)
  if (overview.value) {
    overview.value = { ...overview.value, roles: overview.value.roles.map(item => item.id === role.id ? role : item) }
  }
}

async function afterRoleSaved(role: RoleManageItem) {
  updateOverviewRole(role)
  await refreshViews()
}

async function afterUserSaved(user: UserManageItem) {
  ++overviewRequest
  overviewLoading.value = false
  if (overview.value) {
    overview.value = {
      ...overview.value,
      users: overview.value.users.map(item => item.id === user.id
        ? {
            ...item,
            roleIds: [...user.roleIds],
            deptIds: [...user.deptIds],
            status: user.status,
            emailVerified: user.emailVerified,
            currentRoleId: user.roleIds.includes(item.currentRoleId || '') ? item.currentRoleId : undefined,
          }
        : item),
    }
  }
  await refreshViews()
}

async function saveMatrixChanges(changes: PermissionRoleChange[]) {
  if (!auth('system:role:edit') || matrixSaving.value || operationBusy.value || !changes.length) {
    return
  }
  matrixSaving.value = true
  matrixResults.value = []
  ++overviewRequest
  overviewLoading.value = false
  try {
    for (const change of changes) {
      const name = overview.value?.roles.find(role => role.id === change.roleId)?.name || change.roleId
      try {
        const response = await apiPermission.changeRolePermissions(change)
        updateOverviewRole(response.data)
        matrixResults.value.push({ roleId: change.roleId, name, success: true, message: '授权已保存' })
      }
      catch (error) {
        matrixResults.value.push({ roleId: change.roleId, name, success: false, message: permissionOperationError(error) })
      }
    }
    const success = matrixResults.value.filter(item => item.success).length
    if (success) {
      toast.success(`已保存 ${success} 个角色的授权`)
    }
    await refreshViews()
  }
  finally {
    matrixSaving.value = false
  }
}

onBeforeRouteLeave(async () => {
  if (matrixSaving.value || operationBusy.value) {
    toast.warning('授权操作正在保存，请完成后再离开。')
    return false
  }
  if (matrixDirty.value) {
    const confirmed = await new Promise<boolean>((resolve) => {
      modal.confirm({
        title: '矩阵授权尚未保存',
        content: '离开页面将丢弃这些授权草稿。已保存的权限不会被撤回。',
        confirmButtonText: '丢弃草稿并离开',
        cancelButtonText: '继续编辑',
        closeOnPressEscape: false,
        onConfirm: () => resolve(true),
        onCancel: () => resolve(false),
      })
    })
    if (!confirmed) {
      return false
    }
    matrixRef.value?.discardChanges()
  }
  operationsRef.value?.closeDialogs()
  return true
})

function canEditOriginal() {
  if (matrixSaving.value || operationBusy.value) {
    return false
  }
  if (matrixDirty.value) {
    toast.warning('请先保存或丢弃矩阵中的授权草稿，再打开原权限树。')
    return false
  }
  return true
}

function editOverviewUser(id: string) {
  if (auth('system:user:view') && auth('system:user:edit')) {
    router.push({ path: '/system/user', query: { editUser: id } })
  }
}

function editOverviewMenu(code: string) {
  if (auth('system:menu:view') && auth('system:menu:edit')) {
    router.push({ path: '/system/menu', query: { editMenu: code } })
  }
}

async function loadOptions() {
  const [deptRes, permissionRes] = await Promise.all([apiDept.tree(), apiRole.permissions()])
  depts.value = deptRes.data
  permissions.value = permissionRes.data
}

async function loadRoles() {
  const request = ++rolesRequest
  loading.value = true
  try {
    const res = await apiRole.page({
      page: pagination.page,
      size: pagination.size,
      keyword: search.keyword || undefined,
      deptId: search.deptId,
      status: search.status,
    })
    if (request === rolesRequest) {
      // 只让最新请求更新列表；保留原权限快照供原编辑流程使用。
      rows.value = res.data.records.map(row => ({
        ...row,
        permissionCount: row.permissions.filter(code => permissionCodeSet.value.has(code)).length,
      }))
      pagination.total = res.data.total
    }
  }
  finally {
    if (request === rolesRequest) {
      loading.value = false
    }
  }
}

function resetSearch() {
  search.keyword = ''
  search.deptId = undefined
  search.status = undefined
  pagination.page = 1
  loadRoles()
}

function openCreate() {
  if (!canEditOriginal()) {
    return
  }
  editing.value = null
  Object.assign(form, {
    name: '',
    code: '',
    deptId: deptOptions.value[0]?.value,
    level: 'USER' as RoleLevel,
    status: 'ACTIVE' as RoleStatus,
    permissions: [],
  })
  formVisible.value = true
  nextTick(() => permissionTreeRef.value?.reset())
}

function openEdit(row: RoleManageItem) {
  if (!canEditOriginal()) {
    return
  }
  editing.value = row
  Object.assign(form, {
    name: row.name,
    code: row.code,
    deptId: row.deptId,
    level: row.level,
    status: row.status,
    permissions: [...row.permissions],
  })
  formVisible.value = true
  nextTick(() => permissionTreeRef.value?.reset())
}

async function saveForm() {
  // 权限码原样提交：失效的插件权限（更新/重载期间）保留在角色中，插件恢复后授权自动生效
  if (editing.value) {
    await apiRole.update(editing.value.id, form)
  }
  else {
    await apiRole.create(form)
  }
  toast.success(editing.value ? '编辑成功' : '新增成功')
  formVisible.value = false
  await refreshViews()
}

function confirmDisable(row: RoleManageItem) {
  modal.confirm({
    title: '确认信息',
    content: `确认停用「${row.name}」吗？`,
    onConfirm: async () => {
      await apiRole.disable(row.id)
      toast.success('停用成功')
      await refreshViews()
    },
  })
}

function confirmEnable(row: RoleManageItem) {
  modal.confirm({
    title: '确认信息',
    content: `确认启用「${row.name}」吗？`,
    onConfirm: async () => {
      await apiRole.enable(row.id)
      toast.success('启用成功')
      await refreshViews()
    },
  })
}

function onPageChange(page: number) {
  pagination.page = page
  loadRoles()
}

function onSizeChange(size: number) {
  pagination.size = size
  pagination.page = 1
  loadRoles()
}

function flattenDepts(items: DeptManageItem[]): DeptManageItem[] {
  return items.flatMap(item => [item, ...flattenDepts(item.children || [])])
}

async function exportRoles() {
  const res = await apiExcel.exportRoles({
    keyword: search.keyword || undefined,
    deptId: search.deptId,
    status: search.status,
  })
  saveExcelResponse(res, '角色管理.xlsx')
}

async function downloadRoleTemplate() {
  const res = await apiExcel.roleTemplate()
  saveExcelResponse(res, '角色导入模板.xlsx')
}

function importRoles() {
  pickExcelFile(async (file) => {
    const res = await apiExcel.importRoles(excelForm(file))
    toast.success(importResultMessage(res.data))
    await refreshViews()
  })
}
</script>

<template>
  <div>
    <YdPageHeader title="角色管理" description="配置角色权限，查看授权矩阵并追溯用户与菜单的关系。" main-class="flex-[1_1_240px]" class="mb-0">
      <YdTabs v-model="activeView" :list="viewOptions" class="min-w-56" />
      <YdButton v-if="activeView === 'list'" v-auth="'system:role:export'" variant="outline" @click="exportRoles">
        <YdIcon name="morph:file-spreadsheet" />
        导出
      </YdButton>
      <YdButton v-if="activeView === 'list'" v-auth="'system:role:import'" variant="outline" @click="downloadRoleTemplate">
        <YdIcon name="morph:download" />
        模板
      </YdButton>
      <YdButton v-if="activeView === 'list'" v-auth="'system:role:import'" variant="outline" @click="importRoles">
        <YdIcon name="morph:upload" />
        导入
      </YdButton>
      <YdButton v-auth="'system:role:create'" @click="openCreate">
        <YdIcon name="i-ri:user-star-line" />
        新增角色
      </YdButton>
    </YdPageHeader>

    <YdPageMain v-if="activeView === 'list'">
      <YdResponsiveTable
        v-loading="loading"
        row-key="id"
        table-root-class="rounded-lg overflow-hidden"
        table-class="min-w-[1040px]"
        column-visibility
        border
        stripe
        :columns="tableColumns"
        :data="rows"
      >
        <template #toolbar>
          <YdSearchBar class="w-full">
            <div class="gap-3 grid grid-cols-1 md:grid-cols-[repeat(auto-fit,minmax(200px,1fr))]">
              <YdInput v-model="search.keyword" clearable placeholder="角色名称 / 编码" class="w-full" @keydown.enter="loadRoles" @clear="loadRoles" />
              <YdSelect v-model="search.deptId" :options="deptOptions" placeholder="部门" class="w-full" />
              <YdSelect v-model="search.status" :options="statusOptions" placeholder="状态" class="w-full" />
              <div class="flex gap-2 col-end--1 justify-end">
                <YdButton variant="outline" @click="resetSearch">
                  重置
                </YdButton>
                <YdButton @click="loadRoles">
                  <YdIcon name="morph:search" />
                  筛选
                </YdButton>
              </div>
            </div>
          </YdSearchBar>
        </template>
        <template #cell-systemRole="{ row }">
          <YdTag :variant="row.original.systemRole ? 'default' : 'secondary'">
            {{ row.original.systemRole ? '系统角色' : '自定义' }}
          </YdTag>
        </template>
        <template #cell-status="{ row }">
          <YdTag :variant="row.original.status === 'ACTIVE' ? 'default' : 'secondary'">
            {{ row.original.status === 'ACTIVE' ? '启用' : '停用' }}
          </YdTag>
        </template>
        <template #cell-operation="{ row }">
          <div class="flex-center gap-2">
            <YdButton v-auth="'system:role:edit'" variant="outline" size="sm" @click="openEdit(row.original)">
              编辑
            </YdButton>
            <YdButton
              v-if="row.original.status === 'DEPRECATED'"
              v-auth="'system:role:edit'"
              variant="outline"
              size="sm"
              @click="confirmEnable(row.original)"
            >
              启用
            </YdButton>
            <YdButton
              v-if="row.original.status === 'ACTIVE'"
              v-auth="'system:role:delete'"
              variant="destructive"
              size="sm"
              :disabled="row.original.systemRole"
              @click="confirmDisable(row.original)"
            >
              停用
            </YdButton>
          </div>
        </template>

        <template #card="{ row }">
          <YdCard class="w-full">
            <div class="flex flex-col gap-3">
              <div class="flex items-center justify-between gap-2">
                <span class="min-w-0 break-words text-base font-semibold">{{ row.name }}</span>
                <div class="flex gap-1">
                  <YdTag :variant="row.systemRole ? 'default' : 'secondary'">
                    {{ row.systemRole ? '系统角色' : '自定义' }}
                  </YdTag>
                  <YdTag :variant="row.status === 'ACTIVE' ? 'default' : 'secondary'">
                    {{ row.status === 'ACTIVE' ? '启用' : '停用' }}
                  </YdTag>
                </div>
              </div>
              <div class="flex flex-col gap-1 text-sm">
                <div class="flex gap-2">
                  <span class="shrink-0 text-secondary-foreground/60">编码</span>
                  <span class="break-all">{{ row.code }}</span>
                </div>
                <div v-if="row.deptName" class="flex gap-2">
                  <span class="shrink-0 text-secondary-foreground/60">部门</span>
                  <span>{{ row.deptName }}</span>
                </div>
                <div class="flex gap-2">
                  <span class="shrink-0 text-secondary-foreground/60">等级</span>
                  <span>{{ levelOptions.find(item => item.value === row.level)?.label || row.level }}</span>
                </div>
                <div class="flex gap-2">
                  <span class="shrink-0 text-secondary-foreground/60">权限数</span>
                  <span>{{ row.permissionCount }}</span>
                </div>
              </div>
              <div class="flex flex-wrap gap-2 border-t pt-3">
                <YdButton v-auth="'system:role:edit'" variant="outline" size="sm" @click="openEdit(row)">
                  编辑
                </YdButton>
                <YdButton v-if="row.status === 'DEPRECATED'" v-auth="'system:role:edit'" variant="outline" size="sm" @click="confirmEnable(row)">
                  启用
                </YdButton>
                <YdButton v-if="row.status === 'ACTIVE'" v-auth="'system:role:delete'" variant="destructive" size="sm" :disabled="row.systemRole" @click="confirmDisable(row)">
                  停用
                </YdButton>
              </div>
            </div>
          </YdCard>
        </template>
      </YdResponsiveTable>

      <YdPagination
        v-model:page="pagination.page"
        v-model:size="pagination.size"
        :total="pagination.total"
        class="mt-3"
        @page-change="onPageChange"
        @size-change="onSizeChange"
      />
    </YdPageMain>

    <YdPageMain v-show="activeView !== 'list'">
      <div v-if="overviewError" class="flex flex-wrap items-center justify-between gap-3 p-6" role="alert">
        <span>{{ overviewError }}</span>
        <YdButton variant="outline" @click="loadOverview">重试</YdButton>
      </div>
      <PermissionMatrix
        ref="matrixRef"
        v-show="activeView === 'matrix' && !overviewError"
        :roles="overview?.roles || []"
        :permissions="overview?.permissions || []"
        :loading="overviewLoading"
        :saving="matrixSaving || operationBusy"
        @edit-role="editOverviewRole"
        @save-changes="saveMatrixChanges"
        @dirty-change="matrixDirty = $event"
        @inspect-permission="operationsRef?.inspectPermission($event)"
      />
      <div v-if="activeView === 'matrix' && matrixResults.length" class="flex flex-col gap-2 border-t p-4" aria-live="polite">
        <p v-for="result in matrixResults" :key="result.roleId" class="flex flex-wrap items-center gap-2 text-sm">
          <YdTag :variant="result.success ? 'secondary' : 'destructive'">{{ result.success ? '成功' : '需复核' }}</YdTag>
          <span>{{ result.name }}：{{ result.message }}</span>
        </p>
        <p class="text-xs text-secondary-foreground/70">各角色独立保存，失败项的草稿保留；发生冲突需刷新复核后重新选择。</p>
      </div>
      <PermissionConstellation
        v-if="activeView === 'trace' && overview && !overviewError"
        :overview="overview"
        :loading="overviewLoading"
        :user-keyword="overviewQuery.userKeyword"
        :user-unassigned-only="overviewQuery.userUnassignedOnly"
        @edit-role="editOverviewRole"
        @edit-user="editOverviewUser"
        @edit-menu="editOverviewMenu"
        @grant-permission="operationsRef?.grantPermission($event)"
        @revoke-permission="(roleId, code) => operationsRef?.revokePermission(roleId, code)"
        @assign-user-roles="operationsRef?.assignUserRoles($event)"
        @inspect-permission="operationsRef?.inspectPermission($event)"
        @user-page="pageOverviewUsers"
        @user-search="searchOverviewUsers"
        @user-unassigned="showUnassignedUsers"
        @refresh="loadOverview"
      />
      <div v-if="activeView === 'trace' && !overview && !overviewError" class="p-16 text-center" role="status">正在加载权限全景…</div>
    </YdPageMain>

    <PermissionOperations
      ref="operationsRef"
      :overview="overview"
      :blocked="matrixDirty"
      :busy="matrixSaving"
      @role-saved="afterRoleSaved"
      @user-saved="afterUserSaved"
      @edit-role="editOverviewRole"
      @busy-change="operationBusy = $event"
    />

    <YdModal v-model="formVisible" :title="editing ? '编辑角色' : '新增角色'" show-cancel-button class="sm:max-w-3xl" @confirm="saveForm">
      <YdForm :model="form" layout="vertical">
        <div class="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <div>
            <YdFormItem label="角色名称" required>
              <YdInput v-model="form.name" class="w-full" />
            </YdFormItem>
          </div>
          <div>
            <YdFormItem label="角色编码" required>
              <YdInput v-model="form.code" :disabled="editing?.systemRole" class="w-full" />
            </YdFormItem>
          </div>
          <div>
            <YdFormItem label="所属部门" required>
              <YdSelect v-model="form.deptId" :options="deptOptions" class="w-full" />
            </YdFormItem>
          </div>
          <div>
            <YdFormItem label="等级">
              <YdSelect v-model="form.level" :options="levelOptions" class="w-full" />
            </YdFormItem>
          </div>
          <div v-if="editing">
            <YdFormItem label="状态">
              <YdSelect v-model="form.status" :disabled="editing.systemRole" :options="statusOptions" class="w-full" />
            </YdFormItem>
          </div>
        </div>

        <YdFormItem label="权限">
          <PermissionTreeEditor ref="permissionTreeRef" v-model="form.permissions" :permissions="permissions" :implicit-wildcard="editing?.systemType === 'SUPER_ADMIN'" />
        </YdFormItem>
      </YdForm>
    </YdModal>
  </div>
</template>
