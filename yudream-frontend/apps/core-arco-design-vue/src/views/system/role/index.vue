<script setup lang="ts">
import type { TableColumn } from '@yudream/components'
import type { DeptManageItem } from '@/api/modules/system-dept'
import type { IdValue, PermissionItem, RoleLevel, RoleManageItem, RolePayload, RoleStatus } from '@/api/modules/system-role'
import apiDept from '@/api/modules/system-dept'
import apiExcel from '@/api/modules/system-excel'
import apiRole from '@/api/modules/system-role'
import { excelForm, importResultMessage, pickExcelFile, saveExcelResponse } from '@/utils/excel'

const modal = useFaModal()
const toast = useFaToast()

const loading = ref(false)
const rows = ref<RoleManageItem[]>([])
const depts = ref<DeptManageItem[]>([])
const permissions = ref<PermissionItem[]>([])
const pagination = reactive({ page: 1, size: 10, total: 0 })
const search = reactive<{ keyword: string; deptId?: IdValue; status?: RoleStatus }>({ keyword: '' })

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
const permissionGroups = computed(() => {
  const groups: Record<string, PermissionItem[]> = {}
  permissions.value.forEach((item) => {
    const key = item.module || 'default'
    groups[key] ||= []
    groups[key].push(item)
  })
  return groups
})
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
  await Promise.all([loadOptions(), loadRoles()])
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
    rows.value = res.data.records
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
  formVisible.value = true
}

async function saveForm() {
  if (editing.value) {
    await apiRole.update(editing.value.id, form)
  }
  else {
    await apiRole.create(form)
  }
  toast.success(editing.value ? '编辑成功' : '新增成功')
  formVisible.value = false
  await loadRoles()
}

function confirmDisable(row: RoleManageItem) {
  modal.confirm({
    title: '确认信息',
    content: `确认停用「${row.name}」吗？`,
    onConfirm: async () => {
      await apiRole.disable(row.id)
      toast.success('停用成功')
      await loadRoles()
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
      await loadRoles()
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
    await loadRoles()
  })
}
</script>

<template>
  <div>
    <FaPageHeader title="角色管理" class="mb-0">
      <FaButton v-auth="'system:role:export'" variant="outline" @click="exportRoles">
        <FaIcon name="i-ri:file-excel-2-line" />
        导出
      </FaButton>
      <FaButton v-auth="'system:role:import'" variant="outline" @click="downloadRoleTemplate">
        <FaIcon name="i-ri:download-2-line" />
        模板
      </FaButton>
      <FaButton v-auth="'system:role:import'" variant="outline" @click="importRoles">
        <FaIcon name="i-ri:upload-2-line" />
        导入
      </FaButton>
      <FaButton v-auth="'system:role:create'" @click="openCreate">
        <FaIcon name="i-ri:user-star-line" />
        新增角色
      </FaButton>
    </FaPageHeader>

    <FaPageMain>
      <FaTable
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
                  <FaIcon name="i-ri:search-line" />
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
      </FaTable>

      <FaPagination
        v-model:page="pagination.page"
        v-model:size="pagination.size"
        :total="pagination.total"
        class="mt-3"
        @page-change="onPageChange"
        @size-change="onSizeChange"
      />
    </FaPageMain>

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
            <div v-for="(items, module) in permissionGroups" :key="module" class="permission-group">
              <div class="permission-title">
                {{ module }}
              </div>
              <a-checkbox-group v-model="form.permissions">
                <a-space wrap>
                  <a-checkbox v-for="item in items" :key="item.code" :value="item.code">
                    {{ item.name }}
                  </a-checkbox>
                </a-space>
              </a-checkbox-group>
            </div>
          </div>
        </a-form-item>
      </a-form>
    </FaModal>
  </div>
</template>

<style scoped>
.permission-panel {
  display: grid;
  gap: 12px;
  max-height: 320px;
  overflow: auto;
}

.permission-group {
  padding: 12px;
  border: 1px solid var(--color-border-2);
  border-radius: 6px;
}

.permission-title {
  margin-bottom: 8px;
  font-weight: 600;
}
</style>
