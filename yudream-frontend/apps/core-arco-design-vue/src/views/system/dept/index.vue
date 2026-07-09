<script setup lang="ts">
import type { TableColumn } from '@fantastic-admin/components'
import type { DeptManageItem, DeptPayload, DeptStatus } from '@/api/modules/system-dept'
import apiDept from '@/api/modules/system-dept'
import apiExcel from '@/api/modules/system-excel'
import { excelForm, importResultMessage, pickExcelFile, saveExcelResponse } from '@/utils/excel'

const modal = useFaModal()
const toast = useFaToast()

const loading = ref(false)
const rows = ref<DeptManageItem[]>([])
const search = reactive<{ keyword: string; status?: DeptStatus }>({ keyword: '' })
const formVisible = ref(false)
const editing = ref<DeptManageItem | null>(null)

const form = reactive<DeptPayload>({
  name: '',
  description: '',
  leaderId: undefined,
  phone: '',
  parentId: undefined,
  sortOrder: 0,
  status: 'ACTIVE',
})

const statusOptions = [
  { label: '启用', value: 'ACTIVE' },
  { label: '停用', value: 'DEPRECATED' },
]
const deptOptions = computed(() => flattenDepts(rows.value)
  .filter(item => String(item.id) !== String(editing.value?.id ?? ''))
  .map(item => ({ label: item.name, value: item.id })))
const leaderIdValue = computed({
  get: () => form.leaderId === undefined ? '' : String(form.leaderId),
  set: (value: string) => {
    form.leaderId = value || undefined
  },
})
const sortOrderValue = computed({
  get: () => form.sortOrder ?? 0,
  set: (value: number) => {
    form.sortOrder = value
  },
})
const tableColumns = computed<TableColumn<DeptManageItem>[]>(() => [
  { accessorKey: 'name', header: '部门名称', width: 220, fixed: 'left' },
  { accessorKey: 'leaderName', header: '负责人', width: 140 },
  { accessorKey: 'phone', header: '电话', width: 140 },
  { accessorKey: 'sortOrder', header: '排序', width: 90, align: 'center' },
  { id: 'systemDept', header: '类型', width: 110, align: 'center' },
  { id: 'status', header: '状态', width: 90, align: 'center' },
  { accessorKey: 'description', header: '描述', minWidth: 220 },
  { id: 'operation', header: '操作', width: 240, align: 'center', fixed: 'right' },
])

onMounted(loadTree)

async function loadTree() {
  loading.value = true
  try {
    const res = await apiDept.tree({
      keyword: search.keyword || undefined,
      status: search.status,
    })
    rows.value = res.data
  }
  finally {
    loading.value = false
  }
}

function resetSearch() {
  search.keyword = ''
  search.status = undefined
  loadTree()
}

function openCreate(parent?: DeptManageItem) {
  editing.value = null
  Object.assign(form, {
    name: '',
    description: '',
    leaderId: undefined,
    phone: '',
    parentId: parent?.id,
    sortOrder: 0,
    status: 'ACTIVE' as DeptStatus,
  })
  formVisible.value = true
}

function openEdit(row: DeptManageItem) {
  editing.value = row
  Object.assign(form, {
    name: row.name,
    description: row.description,
    leaderId: row.leaderId,
    phone: row.phone,
    parentId: row.parentId,
    sortOrder: row.sortOrder || 0,
    status: row.status,
  })
  formVisible.value = true
}

async function saveForm() {
  if (editing.value) {
    await apiDept.update(editing.value.id, form)
  }
  else {
    await apiDept.create(form)
  }
  toast.success(editing.value ? '编辑成功' : '新增成功')
  formVisible.value = false
  await loadTree()
}

function confirmDisable(row: DeptManageItem) {
  modal.confirm({
    title: '确认信息',
    content: `确认停用「${row.name}」吗？`,
    onConfirm: async () => {
      await apiDept.disable(row.id)
      toast.success('停用成功')
      await loadTree()
    },
  })
}

function flattenDepts(items: DeptManageItem[]): DeptManageItem[] {
  return items.flatMap(item => [item, ...flattenDepts(item.children || [])])
}

async function exportDepts() {
  const res = await apiExcel.exportDepts({
    keyword: search.keyword || undefined,
    status: search.status,
  })
  saveExcelResponse(res, '部门管理.xlsx')
}

async function downloadDeptTemplate() {
  const res = await apiExcel.deptTemplate()
  saveExcelResponse(res, '部门导入模板.xlsx')
}

function importDepts() {
  pickExcelFile(async (file) => {
    const res = await apiExcel.importDepts(excelForm(file))
    toast.success(importResultMessage(res.data))
    await loadTree()
  })
}
</script>

<template>
  <div>
    <FaPageHeader title="部门管理" class="mb-0">
      <FaButton v-auth="'system:dept:export'" variant="outline" @click="exportDepts">
        <FaIcon name="i-ri:file-excel-2-line" />
        导出
      </FaButton>
      <FaButton v-auth="'system:dept:import'" variant="outline" @click="downloadDeptTemplate">
        <FaIcon name="i-ri:download-2-line" />
        模板
      </FaButton>
      <FaButton v-auth="'system:dept:import'" variant="outline" @click="importDepts">
        <FaIcon name="i-ri:upload-2-line" />
        导入
      </FaButton>
      <FaButton v-auth="'system:dept:create'" @click="openCreate()">
        <FaIcon name="i-ri:organization-chart" />
        新增部门
      </FaButton>
    </FaPageHeader>

    <FaPageMain>
      <FaTable
        v-loading="loading"
        row-key="id"
        table-root-class="rounded-lg overflow-hidden"
        table-class="min-w-[1120px]"
        column-visibility
        border
        stripe
        tree
        :default-expanded="true"
        :columns="tableColumns"
        :data="rows"
      >
        <template #toolbar>
          <FaSearchBar class="w-full">
            <div class="gap-3 grid grid-cols-1 md:grid-cols-[repeat(auto-fit,minmax(200px,1fr))]">
              <FaInput v-model="search.keyword" clearable placeholder="部门名称 / 描述" class="w-full" @keydown.enter="loadTree" @clear="loadTree" />
              <FaSelect v-model="search.status" :options="statusOptions" placeholder="状态" class="w-full" />
              <div class="flex gap-2 col-end--1 justify-end">
                <FaButton variant="outline" @click="resetSearch">
                  重置
                </FaButton>
                <FaButton @click="loadTree">
                  <FaIcon name="i-ri:search-line" />
                  筛选
                </FaButton>
              </div>
            </div>
          </FaSearchBar>
        </template>
        <template #cell-systemDept="{ row }">
          <FaTag :variant="row.original.systemDept ? 'default' : 'secondary'">
            {{ row.original.systemDept ? '系统部门' : '普通部门' }}
          </FaTag>
        </template>
        <template #cell-status="{ row }">
          <FaTag :variant="row.original.status === 'ACTIVE' ? 'default' : 'secondary'">
            {{ row.original.status === 'ACTIVE' ? '启用' : '停用' }}
          </FaTag>
        </template>
        <template #cell-operation="{ row }">
          <div class="flex-center gap-2">
            <FaButton v-auth="'system:dept:create'" variant="outline" size="sm" @click="openCreate(row.original)">
              子部门
            </FaButton>
            <FaButton v-auth="'system:dept:edit'" variant="outline" size="sm" @click="openEdit(row.original)">
              编辑
            </FaButton>
            <FaButton
              v-auth="'system:dept:delete'"
              variant="destructive"
              size="sm"
              :disabled="row.original.systemDept || row.original.status === 'DEPRECATED'"
              @click="confirmDisable(row.original)"
            >
              停用
            </FaButton>
          </div>
        </template>
      </FaTable>
    </FaPageMain>

    <FaModal v-model="formVisible" :title="editing ? '编辑部门' : '新增部门'" show-cancel-button class="sm:max-w-3xl" @confirm="saveForm">
      <a-form :model="form" layout="vertical">
        <a-grid :cols="2" :col-gap="16">
          <a-grid-item>
            <a-form-item label="部门名称" required>
              <FaInput v-model="form.name" class="w-full" />
            </a-form-item>
          </a-grid-item>
          <a-grid-item>
            <a-form-item label="上级部门">
              <FaSelect v-model="form.parentId" :options="deptOptions" placeholder="请选择上级部门" class="w-full" />
            </a-form-item>
          </a-grid-item>
          <a-grid-item>
            <a-form-item label="负责人ID">
              <FaInput v-model="leaderIdValue" class="w-full" />
            </a-form-item>
          </a-grid-item>
          <a-grid-item>
            <a-form-item label="电话">
              <FaInput v-model="form.phone" class="w-full" />
            </a-form-item>
          </a-grid-item>
          <a-grid-item>
            <a-form-item label="排序">
              <FaNumberField v-model="sortOrderValue" :min="0" class="w-full" />
            </a-form-item>
          </a-grid-item>
          <a-grid-item v-if="editing">
            <a-form-item label="状态">
              <FaSelect v-model="form.status" :disabled="editing.systemDept" :options="statusOptions" class="w-full" />
            </a-form-item>
          </a-grid-item>
        </a-grid>
        <a-form-item label="描述">
          <FaTextarea v-model="form.description" rows="3" class="w-full" input-class="min-h-24" />
        </a-form-item>
      </a-form>
    </FaModal>
  </div>
</template>
