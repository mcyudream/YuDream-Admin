<script setup lang="ts">
import type { ActionMenuItem, StatusColumn, TableColumn, UserColumn, YdTablePickerQuery, YdTablePickerResult } from '@yudream/components'
import type { DeptManageItem, DeptPayload, DeptStatus } from '@/api/modules/system-dept'
import type { UserManageItem, UserPickerRow } from '@/api/modules/system-user'
import apiDept from '@/api/modules/system-dept'
import apiExcel from '@/api/modules/system-excel'
import apiUser from '@/api/modules/system-user'
import { toBackendAssetUrl } from '@/utils/backend-url'
import { excelForm, importResultMessage, pickExcelFile, saveExcelResponse } from '@/utils/excel'

const modal = useYdModal()
const toast = useYdToast()

const loading = ref(false)
const rows = ref<DeptManageItem[]>([])
const search = reactive<{ keyword: string, status?: DeptStatus }>({ keyword: '' })
const formVisible = ref(false)
const editing = ref<DeptManageItem | null>(null)
const formAvatar = ref('')
const avatarInput = ref<InstanceType<typeof YdFilePicker>>()
const avatarUploading = ref(false)

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
const leaderColumns: TableColumn<UserPickerRow>[] = [
  { accessorKey: 'nickname', header: '姓名', minWidth: 140 },
  { accessorKey: 'username', header: '账号', minWidth: 160 },
]

const leaderPickerValue = computed<string[]>({
  get: () => form.leaderId ? [String(form.leaderId)] : [],
  set: (value) => { form.leaderId = value[0] || undefined },
})

const leaderInitialLabels = computed<Record<string, string>>(() => {
  const id = form.leaderId ? String(form.leaderId) : ''
  const name = editing.value?.leaderName
  return id && name ? { [id]: name } : {}
})

async function fetchLeaders(query: YdTablePickerQuery): Promise<YdTablePickerResult<UserPickerRow>> {
  const result = (await apiUser.page({ page: query.page, size: query.size, keyword: query.keyword })).data
  return {
    list: result.records.map((user: UserManageItem) => ({ id: String(user.id), username: user.username, nickname: user.nickname || user.username })),
    total: result.total,
  }
}
const sortOrderValue = computed({
  get: () => form.sortOrder ?? 0,
  set: (value: number) => {
    form.sortOrder = value
  },
})
const { auth } = useAppAuth()

const deptUserColumn: UserColumn<DeptManageItem> = { nameKey: 'name', emailKey: 'leaderName', avatarKey: 'avatar', header: '部门', width: 240 }

const deptStatusColumn: StatusColumn<DeptManageItem> = {
  statusKey: 'status',
  header: '状态',
  width: 90,
  statusMap: {
    ACTIVE: { label: '启用', variant: 'default' },
    DEPRECATED: { label: '停用', variant: 'secondary' },
  },
}

/** 行操作统一收进下拉 */
const deptActions: ActionMenuItem<DeptManageItem>[] = [
  { label: '新建子部门', icon: 'morph:folder-plus', visible: () => auth('system:dept:create'), onClick: openCreate },
  { label: '编辑', icon: 'morph:pencil', visible: () => auth('system:dept:edit'), onClick: openEdit },
  { label: '启用', icon: 'morph:circle-check', visible: row => row.status === 'DEPRECATED' && auth('system:dept:edit'), onClick: confirmEnable },
  { label: '停用', icon: 'morph:ban', danger: true, visible: row => row.status === 'ACTIVE' && auth('system:dept:delete'), disabled: row => Boolean(row.systemDept), onClick: confirmDisable },
]

const tableColumns = computed<TableColumn<DeptManageItem>[]>(() => [
  { accessorKey: 'name', header: '部门名称', width: 220, fixed: 'left' },
  { accessorKey: 'leaderName', header: '负责人', width: 140 },
  { accessorKey: 'phone', header: '电话', width: 140 },
  { accessorKey: 'sortOrder', header: '排序', width: 90, align: 'center' },
  { id: 'systemDept', header: '类型', width: 110, align: 'center' },
  { accessorKey: 'description', header: '描述', minWidth: 220 },
])

onMounted(loadTree)

async function loadTree() {
  loading.value = true
  try {
    const res = await apiDept.tree({
      keyword: search.keyword || undefined,
      status: search.status,
    })
    rows.value = withBackendAvatars(res.data)
  }
  finally {
    loading.value = false
  }
}

/** 后端返回 /api/files/... 相对地址，列表落地前统一补后端基址 */
function withBackendAvatars(items: DeptManageItem[]): DeptManageItem[] {
  return items.map(item => ({
    ...item,
    avatar: item.avatar ? toBackendAssetUrl(item.avatar) : undefined,
    children: item.children ? withBackendAvatars(item.children) : undefined,
  }))
}

function resetSearch() {
  search.keyword = ''
  search.status = undefined
  loadTree()
}

function openCreate(parent?: DeptManageItem) {
  editing.value = null
  formAvatar.value = ''
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
  formAvatar.value = toBackendAssetUrl(row.avatar)
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

function pickDeptAvatar() {
  avatarInput.value?.open()
}

async function uploadDeptAvatar(files: File[]) {
  avatarInput.value?.reset()
  const file = files[0]
  const target = editing.value
  if (!file || !target) {
    return
  }
  const data = new FormData()
  data.append('file', file)
  avatarUploading.value = true
  try {
    const res = await apiDept.uploadAvatar(target.id, data)
    const avatar = toBackendAssetUrl(res.data.avatar)
    formAvatar.value = avatar
    target.avatar = avatar
    toast.success('部门头像已更新')
    await loadTree()
  }
  finally {
    avatarUploading.value = false
  }
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

function confirmEnable(row: DeptManageItem) {
  modal.confirm({
    title: '确认信息',
    content: `确认启用「${row.name}」吗？`,
    onConfirm: async () => {
      await apiDept.enable(row.id)
      toast.success('启用成功')
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
    <YdPageHeader title="部门管理" class="mb-0">
      <YdButton v-auth="'system:dept:export'" variant="outline" @click="exportDepts">
        <YdIcon name="morph:file-spreadsheet" />
        导出
      </YdButton>
      <YdButton v-auth="'system:dept:import'" variant="outline" @click="downloadDeptTemplate">
        <YdIcon name="morph:download" />
        模板
      </YdButton>
      <YdButton v-auth="'system:dept:import'" variant="outline" @click="importDepts">
        <YdIcon name="morph:upload" />
        导入
      </YdButton>
      <YdButton v-auth="'system:dept:create'" @click="openCreate()">
        <YdIcon name="morph:network" />
        新增部门
      </YdButton>
    </YdPageHeader>

    <YdPageMain>
      <YdLoadingOverlay :loading="loading">
        <YdResponsiveEnhancedTable
          table-root-class="rounded-lg overflow-hidden"
          table-class="min-w-[1120px]"

          column-visibility tree
          :default-expanded="true"
          :user-column="deptUserColumn"
          :status-column="deptStatusColumn"
          :actions="deptActions"
          :card-hidden-columns="['systemDept', 'phone']"
          :columns="tableColumns"
          :data="rows"
        >
          <template #toolbar>
            <YdSearchBar class="w-full">
              <div class="gap-3 grid grid-cols-1 md:grid-cols-[repeat(auto-fit,minmax(200px,1fr))]">
                <YdInput v-model="search.keyword" clearable placeholder="部门名称 / 描述" class="w-full" @keydown.enter="loadTree" @clear="loadTree" />
                <YdSelect v-model="search.status" :options="statusOptions" placeholder="状态" class="w-full" />
                <div class="flex gap-2 col-end--1 justify-end">
                  <YdButton variant="outline" @click="resetSearch">
                    重置
                  </YdButton>
                  <YdButton @click="loadTree">
                    <YdIcon name="morph:search" />
                    筛选
                  </YdButton>
                </div>
              </div>
            </YdSearchBar>
          </template>
          <template #cell-systemDept="{ row }">
            <YdTag :variant="row.original.systemDept ? 'default' : 'secondary'">
              {{ row.original.systemDept ? '系统部门' : '普通部门' }}
            </YdTag>
          </template>
        </ydresponsiveenhancedtable>
      </ydloadingoverlay>
    </YdPageMain>

    <YdModal v-model="formVisible" :title="editing ? '编辑部门' : '新增部门'" show-cancel-button class="sm:max-w-3xl" @confirm="saveForm">
      <YdForm :model="form" layout="vertical">
        <YdFormItem v-if="editing" label="部门头像">
          <div class="flex flex-wrap gap-3 items-center">
            <YdAvatar :src="formAvatar" :fallback="form.name || '部门'" class="size-12" />
            <YdButton v-auth="'system:dept:edit'" variant="outline" size="sm" :loading="avatarUploading" @click="pickDeptAvatar">
              <YdIcon name="morph:cloud-upload" />
              上传头像
            </YdButton>
            <span class="text-xs text-muted-foreground">仅支持图片文件，无头像时展示部门名称首字</span>
            <YdFilePicker ref="avatarInput" accept="image/*" label="选择部门头像" class="hidden" @select="uploadDeptAvatar">
              <template #default />
            </YdFilePicker>
          </div>
        </YdFormItem>
        <div class="gap-x-4 grid grid-cols-1 md:grid-cols-2">
          <div>
            <YdFormItem label="部门名称" required>
              <YdInput v-model="form.name" class="w-full" />
            </YdFormItem>
          </div>
          <div>
            <YdFormItem label="上级部门">
              <YdSelect v-model="form.parentId" :options="deptOptions" placeholder="请选择上级部门" class="w-full" />
            </YdFormItem>
          </div>
          <div>
            <YdFormItem label="负责人ID">
              <YdTablePicker
                v-model="leaderPickerValue"
                :columns="leaderColumns"
                :fetcher="fetchLeaders"
                row-key="id"
                label-key="nickname"
                :multiple="false"
                :initial-labels="leaderInitialLabels"
                title="选择部门负责人"
                placeholder="请选择负责人"
              />
            </YdFormItem>
          </div>
          <div>
            <YdFormItem label="电话">
              <YdInput v-model="form.phone" class="w-full" />
            </YdFormItem>
          </div>
          <div>
            <YdFormItem label="排序">
              <YdNumberField v-model="sortOrderValue" :min="0" class="w-full" />
            </YdFormItem>
          </div>
          <div v-if="editing">
            <YdFormItem label="状态">
              <YdSelect v-model="form.status" :disabled="editing.systemDept" :options="statusOptions" class="w-full" />
            </YdFormItem>
          </div>
        </div>
        <YdFormItem label="描述">
          <YdTextarea v-model="form.description" rows="3" class="w-full" input-class="min-h-24" />
        </YdFormItem>
      </YdForm>
    </YdModal>
  </div>
</template>
