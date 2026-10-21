<script setup lang="ts">
import type { ActionMenuItem, SearchOptionsSource, StatusColumn, TableColumn, YdTablePageAction } from '@yudream/components'
import type { DeptManageItem } from '@/api/modules/system-dept'
import type { PermissionOverview, PermissionOverviewQuery, PermissionRoleChange } from '@/api/modules/system-permission'
import type { PermissionItem, RoleLevel, RoleManageItem, RolePayload, RoleStatus } from '@/api/modules/system-role'
import type { UserManageItem } from '@/api/modules/system-user'
import { useTablePage, useYdModal, useYdToast } from '@yudream/components'
import apiDept from '@/api/modules/system-dept'
import apiExcel from '@/api/modules/system-excel'
import apiPermission from '@/api/modules/system-permission'
import apiRole, { ROLE_COLOR_PRESETS } from '@/api/modules/system-role'
import { fetchSearchConfig } from '@/api/modules/system-search-config'
import RelatedEntity from '@/components/RelatedEntity/index.vue'

import { entityTintStyle, normalizeEntityColor } from '@/utils/entity-visual'
import { excelForm, importResultMessage, pickExcelFile, saveExcelResponse } from '@/utils/excel'
import { templateImportFeedbackKind } from '@/utils/template-import-feedback'
import { permissionOperationError } from './components/permission-operations'
import PermissionConstellation from './components/PermissionConstellation.vue'
import PermissionMatrix from './components/PermissionMatrix.vue'
import PermissionOperations from './components/PermissionOperations.vue'
import PermissionTreeEditor from './components/PermissionTreeEditor.vue'

const modal = useYdModal()
const toast = useYdToast()
const router = useRouter()
const { auth } = useAppAuth()
const activeView = ref('list')
/** 视图切换用选择框：与页头按钮同高（都走 --yd-control-height），不用 tab 拼样式 */
const viewOptions = [
  { value: 'list', label: '列表' },
  { value: 'matrix', label: '矩阵' },
  { value: 'trace', label: '溯源' },
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

const depts = ref<DeptManageItem[]>([])
const permissions = shallowRef<PermissionItem[]>([])

const formVisible = ref(false)
const editing = ref<RoleManageItem | null>(null)
const form = reactive<RolePayload>({
  name: '',
  code: '',
  deptId: undefined,
  level: 'USER',
  status: 'ACTIVE',
  icon: undefined,
  color: ROLE_COLOR_PRESETS[0],
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
/** 搜索选项数据源：部门从部门树展开，状态用本地词表 */
const optionsSources: SearchOptionsSource[] = [
  {
    id: 'deptOptions',
    loader: async () => {
      const res = await apiDept.tree()
      return flattenDepts(res.data).map(item => ({ label: item.name, value: item.id }))
    },
  },
  { id: 'roleStatusOptions', loader: () => statusOptions },
]

const {
  loading,
  rows,
  pagination,
  searchQuery,
  searchConfig,
  load,
  search,
  resetSearch,
  onPageChange,
  onSizeChange,
  invalidatePending,
} = useTablePage<RoleManageItem>({
  queryClass: 'online.yudream.base.application.system.user.query.RolePageQuery',
  loadApi: async (query) => {
    const res = await apiRole.page({
      page: query.page,
      size: query.size,
      keyword: query.keyword || undefined,
      deptId: query.deptId || undefined,
      status: query.status || undefined,
    })
    return {
      // 保留原权限快照供原编辑流程使用，派生计数只作用于列表展示
      records: res.data.records.map(row => ({
        ...row,
        permissionCount: row.permissions.filter(code => permissionCodeSet.value.has(code)).length,
      })),
      total: res.data.total,
      page: res.data.page,
      size: res.data.size,
    }
  },
  loadSearchConfig: queryClass => fetchSearchConfig(queryClass).then(res => res.data),
  autoLoad: false,
})

/** 代表色取色：接口返回的 hex 归一化后直接驱动色块与列表标识；未设置时用中性色。 */
function roleAccentColor(color?: string) {
  return normalizeEntityColor(color)
}

function roleAccentLabel(color?: string): string {
  return roleAccentColor(color) ?? '未设置'
}

const roleStatusColumn: StatusColumn<RoleManageItem> = {
  statusKey: 'status',
  header: '状态',
  width: 90,
  statusMap: {
    ACTIVE: { label: '启用', variant: 'default' },
    DEPRECATED: { label: '停用', variant: 'secondary' },
  },
}

/** 页头操作(仅列表视图):新增为主操作,导入导出类收进「更多」 */
const listPageActions = computed<YdTablePageAction[]>(() => [
  ...(auth('system:role:create')
    ? [{ label: '新增角色', icon: 'i-ri:user-star-line', onClick: openCreate }]
    : []),
  ...(auth('system:role:export')
    ? [{ label: '导出', icon: 'morph:file-spreadsheet', variant: 'outline' as const, autoFold: true, onClick: exportRoles }]
    : []),
  ...(auth('system:role:import')
    ? [
        { label: '模板', icon: 'morph:download', variant: 'outline' as const, autoFold: true, onClick: downloadRoleTemplate },
        { label: '导入', icon: 'morph:upload', variant: 'outline' as const, autoFold: true, onClick: importRoles },
      ]
    : []),
])

/** 行操作统一收进下拉 */
const roleActions: ActionMenuItem<RoleManageItem>[] = [
  { label: '编辑', icon: 'morph:pencil', visible: () => auth('system:role:edit'), onClick: openEdit },
  { label: '启用', icon: 'morph:circle-check', visible: row => row.status === 'DEPRECATED' && auth('system:role:edit'), onClick: confirmEnable },
  { label: '停用', icon: 'morph:ban', danger: true, visible: row => row.status === 'ACTIVE' && auth('system:role:delete'), disabled: row => Boolean(row.systemRole), onClick: confirmDisable },
]

/** 关联导航落地：检索词写入搜索模型、目标行定位高亮（宿主统一实现，见 useLinkedFocus） */
useLinkedFocus<RoleManageItem>({
  rows,
  idOf: row => row.id,
  queryKey: 'focusRole',
  applyKeyword: (keyword) => {
    searchQuery.keyword = keyword
  },
})

/** 时间列统一去掉 ISO 的 T 并截到秒 */
function dateText(value?: string) {
  return value ? value.replace('T', ' ').slice(0, 19) : ''
}

const tableColumns = computed<TableColumn<RoleManageItem>[]>(() => [
  // 角色名/部门都是关联实体入口：点开就地查看编辑、可跳完整管理
  { id: 'name', header: '角色名称', width: 220, fixed: 'left' },
  { id: 'visual', header: '标识', width: 88, align: 'center' },
  { accessorKey: 'code', header: '编码', width: 180, defaultHidden: true },
  { id: 'deptName', header: '部门', width: 180 },
  { accessorKey: 'level', header: '等级', width: 120 },
  { accessorKey: 'permissionCount', header: '权限数', width: 100, align: 'center' },
  { id: 'systemRole', header: '类型', width: 110, align: 'center' },
  { id: 'createTime', header: '创建时间', width: 180, defaultHidden: true },
  { id: 'updateTime', header: '更新时间', width: 180, defaultHidden: true },
])

onMounted(async () => {
  await loadOptions()
  await load()
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
    load(),
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
  invalidatePending()
  overviewLoading.value = false
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
    icon: undefined,
    color: ROLE_COLOR_PRESETS[0],
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
    icon: row.icon,
    color: roleAccentColor(row.color) ?? ROLE_COLOR_PRESETS[0],
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

function flattenDepts(items: DeptManageItem[]): DeptManageItem[] {
  return items.flatMap(item => [item, ...flattenDepts(item.children || [])])
}

async function exportRoles() {
  const res = await apiExcel.exportRoles({
    keyword: searchQuery.keyword || undefined,
    deptId: searchQuery.deptId || undefined,
    status: searchQuery.status || undefined,
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
    const feedback = templateImportFeedbackKind(res.data)
    if (feedback === 'error') {
      toast.error('导入完成，存在失败行', { description: importResultMessage(res.data) })
    }
    else if (feedback === 'warning') {
      toast.warning('导入完成，但有提示', { description: importResultMessage(res.data) })
    }
    else {
      toast.success(importResultMessage(res.data))
    }
    await refreshViews()
  })
}
</script>

<template>
  <div>
    <YdTablePageLayout
      title="角色管理"
      :show-search="activeView === 'list'"
      :actions="activeView === 'list' ? listPageActions : []"
      :actions-threshold="3"
    >
      <!-- 视图切换：列表 / 矩阵 / 溯源 -->
      <template #tabs>
        <YdSegmented v-model="activeView" :options="viewOptions" aria-label="视图切换" />
      </template>

      <template #search="{ fold }">
        <YdDynamicSearchForm
          v-model="searchQuery"
          :config="searchConfig"
          :fold="fold"
          :options-sources="optionsSources"
          @search="search"
          @reset="resetSearch"
        />
      </template>

      <template #table>
        <div v-show="activeView === 'list'">
          <YdLoadingOverlay :loading="loading">
            <YdResponsiveEnhancedTable

              row-key="id"
              table-root-class="rounded-lg overflow-hidden"
              table-class="min-w-[1128px]"

              column-visibility stripe border
              :status-column="roleStatusColumn"
              :actions="roleActions"
              :card-hidden-columns="['systemRole']"
              :columns="tableColumns"
              :data="rows"
            >
              <template #cell-createTime="{ row }">
                <span v-if="row.original.createTime">{{ dateText(row.original.createTime) }}</span>
                <span v-else class="text-muted-foreground/40">-</span>
              </template>
              <template #cell-updateTime="{ row }">
                <span v-if="row.original.updateTime">{{ dateText(row.original.updateTime) }}</span>
                <span v-else class="text-muted-foreground/40">-</span>
              </template>
              <template #cell-name="{ row }">
                <RelatedEntity
                  entity-type="role"
                  :entity-id="row.original.id"
                  :label="row.original.name"
                  :secondary="row.original.code"
                  @changed="load"
                />
              </template>
              <template #cell-deptName="{ row }">
                <RelatedEntity
                  v-if="row.original.deptId"
                  entity-type="dept"
                  :entity-id="row.original.deptId"
                  :label="row.original.deptName"
                  @changed="load"
                />
                <span v-else-if="row.original.deptName">{{ row.original.deptName }}</span>
                <span v-else class="text-muted-foreground/40">-</span>
              </template>
              <template #cell-visual="{ row }">
                <span class="role-chip" :style="entityTintStyle(row.original.color)">
                  <YdIcon v-if="row.original.icon" :name="row.original.icon" aria-hidden="true" />
                  <span v-else class="role-chip__dot" aria-hidden="true" />
                  <span class="sr-only">代表色：{{ roleAccentLabel(row.original.color) }}</span>
                </span>
              </template>
              <template #cell-systemRole="{ row }">
                <YdTag :variant="row.original.systemRole ? 'default' : 'secondary'">
                  {{ row.original.systemRole ? '系统角色' : '自定义' }}
                </YdTag>
              </template>
            </YdResponsiveEnhancedTable>
          </YdLoadingOverlay>
        </div>

        <!-- 矩阵 / 溯源视图 -->
        <div v-show="activeView !== 'list'">
          <div v-if="overviewError" class="p-6 flex flex-wrap gap-3 items-center justify-between" role="alert">
            <span>{{ overviewError }}</span>
            <YdButton variant="outline" @click="loadOverview">
              重试
            </YdButton>
          </div>
          <PermissionMatrix
            v-show="activeView === 'matrix' && !overviewError"
            ref="matrixRef"
            :roles="overview?.roles || []"
            :permissions="overview?.permissions || []"
            :loading="overviewLoading"
            :saving="matrixSaving || operationBusy"
            @edit-role="editOverviewRole"
            @save-changes="saveMatrixChanges"
            @dirty-change="matrixDirty = $event"
            @inspect-permission="operationsRef?.inspectPermission($event)"
          />
          <div v-if="activeView === 'matrix' && matrixResults.length" class="p-4 border-t flex flex-col gap-2" aria-live="polite">
            <p v-for="result in matrixResults" :key="result.roleId" class="text-sm flex flex-wrap gap-2 items-center">
              <YdTag :variant="result.success ? 'secondary' : 'destructive'">
                {{ result.success ? '成功' : '需复核' }}
              </YdTag>
              <span>{{ result.name }}：{{ result.message }}</span>
            </p>
            <p class="text-xs text-secondary-foreground/70">
              各角色独立保存，失败项的草稿保留；发生冲突需刷新复核后重新选择。
            </p>
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
          <div v-if="activeView === 'trace' && !overview && !overviewError" class="p-16 text-center" role="status">
            正在加载权限全景…
          </div>
        </div>
      </template>

      <template v-if="activeView === 'list'" #footer>
        <YdPagination
          v-model:page="pagination.page"
          v-model:size="pagination.size"
          :total="pagination.total"
          @page-change="onPageChange"
          @size-change="onSizeChange"
        />
      </template>
    </YdTablePageLayout>

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
        <div class="gap-4 grid grid-cols-1 sm:grid-cols-2">
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
          <div>
            <YdFormItem label="图标">
              <YdIconPicker v-model="form.icon" placeholder="选择角色图标" class="w-full" />
            </YdFormItem>
          </div>
          <div>
            <YdFormItem label="代表色" extra="支持自由取色；留空场景按中性色渲染，保存为 #RRGGBB 小写 hex">
              <div class="flex gap-2 w-full items-center">
                <YdColorPicker v-model="form.color" :presets="ROLE_COLOR_PRESETS" aria-label="选择角色代表色" class="flex-1" />
                <span class="role-chip shrink-0" :style="entityTintStyle(form.color)" aria-hidden="true">
                  <YdIcon v-if="form.icon" :name="form.icon" />
                  <span v-else class="role-chip__dot" />
                </span>
              </div>
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

<style scoped>
/*
  角色标识色块：底色与前景色来自接口返回的代表色（内联 style 只承载数据色）。
  列表标识列与表单预览共用同一套取色助手 entityTintStyle，未设色时回退中性语义变量。
*/
.role-chip {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  font-size: 16px;
  line-height: 1;
  border-radius: 8px;
}

.role-chip__dot {
  width: 8px;
  height: 8px;
  background: currentcolor;
  border-radius: 999px;
}
</style>
