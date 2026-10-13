<script setup lang="ts">
import type { TableColumn } from '@yudream/components'
import type { DeptManageItem } from '@/api/modules/system-dept'
import type { PermissionOverview, PermissionOverviewQuery } from '@/api/modules/system-permission'
import type { IdValue, PermissionItem, RoleLevel, RoleManageItem, RolePayload, RoleStatus } from '@/api/modules/system-role'
import apiDept from '@/api/modules/system-dept'
import apiExcel from '@/api/modules/system-excel'
import apiPermission from '@/api/modules/system-permission'
import apiRole from '@/api/modules/system-role'
import { excelForm, importResultMessage, pickExcelFile, saveExcelResponse } from '@/utils/excel'
import { buildPermissionTree, mergeVisiblePermissions, permissionCategoryOf, permissionResourceOf, permissionTreeKeys } from '@/utils/permission-catalog'
import PermissionConstellation from './components/PermissionConstellation.vue'
import PermissionMatrix from './components/PermissionMatrix.vue'

const modal = useFaModal()
const toast = useFaToast()
const router = useRouter()
const { auth } = useAppAuth()
const activeView = ref<string | number>('list')
const viewOptions = [
  { value: 'list', label: '列表', icon: 'morph:list' },
  { value: 'matrix', label: '矩阵', icon: 'morph:table' },
  { value: 'trace', label: '溯源', icon: 'morph:orbit' },
]
const overview = ref<PermissionOverview>()
const overviewLoading = ref(false)
const overviewError = ref('')
const overviewQuery = reactive<PermissionOverviewQuery>({ userPage: 1, userSize: 30 })
let overviewRequest = 0

const loading = ref(false)
const rows = ref<RoleManageItem[]>([])
const depts = ref<DeptManageItem[]>([])
const permissions = ref<PermissionItem[]>([])
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
const legacyPermissionCount = computed(() => permissions.value.filter(item => item.status === 'ACTIVE' && (!permissionResourceOf(item).valid || permissionCategoryOf(item.code).key === 'other')).length)

const permissionKeyword = ref('')
const expandedKeys = ref<string[]>([])
const permissionTreeData = computed(() => buildPermissionTree(permissions.value, permissionKeyword.value))
// 树当前实际渲染出的权限码集合。搜索过滤后只剩匹配项，勾选事件回传也只反映这部分状态
const visibleCodeSet = computed(() => new Set(permissionTreeKeys(permissionTreeData.value, true)))

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
  await loadRoles()
  if (overview.value || activeView.value !== 'list') {
    await loadOverview()
  }
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

watch(permissionKeyword, (keyword) => {
  if (keyword.trim()) {
    expandAllPermissions()
  }
})

async function loadOptions() {
  const [deptRes, permissionRes] = await Promise.all([apiDept.tree(), apiRole.permissions()])
  depts.value = deptRes.data
  permissions.value = permissionRes.data
}

async function loadRoles() {
  loading.value = true
  try {
    const res = await apiRole.page({
      page: pagination.page,
      size: pagination.size,
      keyword: search.keyword || undefined,
      deptId: search.deptId,
      status: search.status,
    })
    // 权限数按现存权限计数展示（失效码不出现在树中）；row.permissions 保留原始数据，编辑回显与保存不丢
    rows.value = res.data.records.map(row => ({
      ...row,
      permissionCount: row.permissions.filter(code => permissionCodeSet.value.has(code)).length,
    }))
    pagination.total = res.data.total
  }
  finally {
    loading.value = false
  }
}

function resetSearch() {
  search.keyword = ''
  search.deptId = undefined
  search.status = undefined
  pagination.page = 1
  loadRoles()
}

function treeGroupKeys() {
  return permissionTreeKeys(permissionTreeData.value)
}

function expandAllPermissions() {
  expandedKeys.value = treeGroupKeys()
}

function collapseAllPermissions() {
  expandedKeys.value = []
}

function selectAllPermissions() {
  form.permissions = [...new Set([...form.permissions, ...permissionCodeSet.value])]
}

function clearPermissions() {
  form.permissions = []
}

function onPermissionCheck(checkedKeys: (string | number)[]) {
  // 树外的勾选（被搜索过滤隐藏、或权限已失效）不在 checkedKeys 里，直接全量重算会把它们清掉
  form.permissions = mergeVisiblePermissions(form.permissions, checkedKeys, visibleCodeSet.value)
}

function resetPermissionPanel() {
  permissionKeyword.value = ''
  expandAllPermissions()
}

function openCreate() {
  editing.value = null
  Object.assign(form, {
    name: '',
    code: '',
    deptId: deptOptions.value[0]?.value,
    level: 'USER' as RoleLevel,
    status: 'ACTIVE' as RoleStatus,
    permissions: [],
  })
  resetPermissionPanel()
  formVisible.value = true
}

function openEdit(row: RoleManageItem) {
  editing.value = row
  Object.assign(form, {
    name: row.name,
    code: row.code,
    deptId: row.deptId,
    level: row.level,
    status: row.status,
    permissions: [...row.permissions],
  })
  resetPermissionPanel()
  formVisible.value = true
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
    <FaPageHeader title="角色管理" description="配置角色权限，查看授权矩阵并追溯用户与菜单的关系。" main-class="flex-[1_1_240px]" class="mb-0">
      <YdTabs v-model="activeView" :list="viewOptions" class="min-w-56" />
      <FaButton v-if="activeView === 'list'" v-auth="'system:role:export'" variant="outline" @click="exportRoles">
        <FaIcon name="morph:file-spreadsheet" />
        导出
      </FaButton>
      <FaButton v-if="activeView === 'list'" v-auth="'system:role:import'" variant="outline" @click="downloadRoleTemplate">
        <FaIcon name="morph:download" />
        模板
      </FaButton>
      <FaButton v-if="activeView === 'list'" v-auth="'system:role:import'" variant="outline" @click="importRoles">
        <FaIcon name="morph:upload" />
        导入
      </FaButton>
      <FaButton v-auth="'system:role:create'" @click="openCreate">
        <FaIcon name="i-ri:user-star-line" />
        新增角色
      </FaButton>
    </FaPageHeader>

    <FaPageMain v-if="activeView === 'list'">
      <FaResponsiveTable
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
          <FaSearchBar class="w-full">
            <div class="gap-3 grid grid-cols-1 md:grid-cols-[repeat(auto-fit,minmax(200px,1fr))]">
              <FaInput v-model="search.keyword" clearable placeholder="角色名称 / 编码" class="w-full" @keydown.enter="loadRoles" @clear="loadRoles" />
              <FaSelect v-model="search.deptId" :options="deptOptions" placeholder="部门" class="w-full" />
              <FaSelect v-model="search.status" :options="statusOptions" placeholder="状态" class="w-full" />
              <div class="flex gap-2 col-end--1 justify-end">
                <FaButton variant="outline" @click="resetSearch">
                  重置
                </FaButton>
                <FaButton @click="loadRoles">
                  <FaIcon name="morph:search" />
                  筛选
                </FaButton>
              </div>
            </div>
          </FaSearchBar>
        </template>
        <template #cell-systemRole="{ row }">
          <FaTag :variant="row.original.systemRole ? 'default' : 'secondary'">
            {{ row.original.systemRole ? '系统角色' : '自定义' }}
          </FaTag>
        </template>
        <template #cell-status="{ row }">
          <FaTag :variant="row.original.status === 'ACTIVE' ? 'default' : 'secondary'">
            {{ row.original.status === 'ACTIVE' ? '启用' : '停用' }}
          </FaTag>
        </template>
        <template #cell-operation="{ row }">
          <div class="flex-center gap-2">
            <FaButton v-auth="'system:role:edit'" variant="outline" size="sm" @click="openEdit(row.original)">
              编辑
            </FaButton>
            <FaButton
              v-if="row.original.status === 'DEPRECATED'"
              v-auth="'system:role:edit'"
              variant="outline"
              size="sm"
              @click="confirmEnable(row.original)"
            >
              启用
            </FaButton>
            <FaButton
              v-if="row.original.status === 'ACTIVE'"
              v-auth="'system:role:delete'"
              variant="destructive"
              size="sm"
              :disabled="row.original.systemRole"
              @click="confirmDisable(row.original)"
            >
              停用
            </FaButton>
          </div>
        </template>

        <template #card="{ row }">
          <FaCard class="w-full">
            <div class="flex flex-col gap-3">
              <div class="flex items-center justify-between gap-2">
                <span class="min-w-0 break-words text-base font-semibold">{{ row.name }}</span>
                <div class="flex gap-1">
                  <FaTag :variant="row.systemRole ? 'default' : 'secondary'">
                    {{ row.systemRole ? '系统角色' : '自定义' }}
                  </FaTag>
                  <FaTag :variant="row.status === 'ACTIVE' ? 'default' : 'secondary'">
                    {{ row.status === 'ACTIVE' ? '启用' : '停用' }}
                  </FaTag>
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
                <FaButton v-auth="'system:role:edit'" variant="outline" size="sm" @click="openEdit(row)">
                  编辑
                </FaButton>
                <FaButton v-if="row.status === 'DEPRECATED'" v-auth="'system:role:edit'" variant="outline" size="sm" @click="confirmEnable(row)">
                  启用
                </FaButton>
                <FaButton v-if="row.status === 'ACTIVE'" v-auth="'system:role:delete'" variant="destructive" size="sm" :disabled="row.systemRole" @click="confirmDisable(row)">
                  停用
                </FaButton>
              </div>
            </div>
          </FaCard>
        </template>
      </FaResponsiveTable>

      <FaPagination
        v-model:page="pagination.page"
        v-model:size="pagination.size"
        :total="pagination.total"
        class="mt-3"
        @page-change="onPageChange"
        @size-change="onSizeChange"
      />
    </FaPageMain>

    <YdPageMain v-else>
      <div v-if="overviewError" class="flex flex-wrap items-center justify-between gap-3 p-6" role="alert">
        <span>{{ overviewError }}</span>
        <YdButton variant="outline" @click="loadOverview">重试</YdButton>
      </div>
      <PermissionMatrix
        v-else-if="activeView === 'matrix'"
        :roles="overview?.roles || []"
        :permissions="overview?.permissions || []"
        :loading="overviewLoading"
        @edit-role="editOverviewRole"
      />
      <PermissionConstellation
        v-else-if="overview"
        :overview="overview"
        :loading="overviewLoading"
        :user-keyword="overviewQuery.userKeyword"
        :user-unassigned-only="overviewQuery.userUnassignedOnly"
        @edit-role="editOverviewRole"
        @edit-user="editOverviewUser"
        @edit-menu="editOverviewMenu"
        @user-page="pageOverviewUsers"
        @user-search="searchOverviewUsers"
        @user-unassigned="showUnassignedUsers"
        @refresh="loadOverview"
      />
      <div v-else class="p-16 text-center" role="status">正在加载权限全景…</div>
    </YdPageMain>

    <FaModal v-model="formVisible" :title="editing ? '编辑角色' : '新增角色'" show-cancel-button class="sm:max-w-3xl" @confirm="saveForm">
      <a-form :model="form" layout="vertical">
        <a-grid :cols="2" :col-gap="16">
          <a-grid-item>
            <a-form-item label="角色名称" required>
              <FaInput v-model="form.name" class="w-full" />
            </a-form-item>
          </a-grid-item>
          <a-grid-item>
            <a-form-item label="角色编码" required>
              <FaInput v-model="form.code" :disabled="editing?.systemRole" class="w-full" />
            </a-form-item>
          </a-grid-item>
          <a-grid-item>
            <a-form-item label="所属部门" required>
              <FaSelect v-model="form.deptId" :options="deptOptions" class="w-full" />
            </a-form-item>
          </a-grid-item>
          <a-grid-item>
            <a-form-item label="等级">
              <FaSelect v-model="form.level" :options="levelOptions" class="w-full" />
            </a-form-item>
          </a-grid-item>
          <a-grid-item v-if="editing">
            <a-form-item label="状态">
              <FaSelect v-model="form.status" :disabled="editing.systemRole" :options="statusOptions" class="w-full" />
            </a-form-item>
          </a-grid-item>
        </a-grid>

        <a-form-item label="权限">
          <div class="permission-panel">
            <p class="text-xs text-secondary-foreground/70">
              按业务域、资源和动作分类授权。{{ legacyPermissionCount ? `另有 ${legacyPermissionCount} 项历史或非标准权限，按原码兼容展示。` : '' }}
            </p>
            <div class="permission-toolbar">
              <FaInput v-model="permissionKeyword" clearable placeholder="搜索权限名称 / 编码" class="min-w-[200px] flex-1" />
              <span class="permission-count">已选 {{ form.permissions.filter(code => permissionCodeSet.has(code)).length }} 项</span>
              <FaButton size="sm" variant="outline" @click="selectAllPermissions">
                全选
              </FaButton>
              <FaButton size="sm" variant="outline" @click="clearPermissions">
                清空
              </FaButton>
              <FaButton size="sm" variant="outline" @click="expandAllPermissions">
                展开
              </FaButton>
              <FaButton size="sm" variant="outline" @click="collapseAllPermissions">
                收起
              </FaButton>
            </div>
            <div class="permission-tree">
              <a-tree
                v-model:expanded-keys="expandedKeys"
                :checked-keys="form.permissions"
                :data="permissionTreeData"
                :selectable="false"
                checkable
                block-node
                @check="onPermissionCheck"
              >
                <template #title="node">
                  <FaTag v-if="node.variant" :variant="node.variant">
                    {{ node.title }}
                  </FaTag>
                  <template v-else>
                    <span :class="{ 'font-semibold': !node.code }">{{ node.title }}</span>
                    <span v-if="node.code" class="permission-code">{{ node.code }}</span>
                  </template>
                </template>
              </a-tree>
              <div v-if="!permissionTreeData.length" class="permission-empty">
                无匹配权限
              </div>
            </div>
          </div>
        </a-form-item>
      </a-form>
    </FaModal>
  </div>
</template>

<style scoped>
.permission-panel {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.permission-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

.permission-count {
  font-size: 12px;
  color: var(--color-text-3);
  white-space: nowrap;
}

.permission-tree {
  max-height: 360px;
  padding: 4px 8px;
  overflow: auto;
  border: 1px solid var(--color-border-2);
  border-radius: 6px;
}

.permission-code {
  margin-left: 8px;
  font-size: 12px;
  color: var(--color-text-3);
}

.permission-empty {
  padding: 16px;
  font-size: 13px;
  color: var(--color-text-3);
  text-align: center;
}
</style>
