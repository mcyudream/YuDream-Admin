<script setup lang="ts">
import type { ActionMenuItem, SearchOptionsSource, StatusColumn, TableColumn, YdTablePageAction, YdTablePickerQuery, YdTablePickerResult } from '@yudream/components'
import type { DeptManageItem, DeptPayload, DeptStatus } from '@/api/modules/system-dept'
import type { UserManageItem, UserPickerRow } from '@/api/modules/system-user'
import { useTablePage } from '@yudream/components'
import apiDept, { DEPT_COLOR_PRESETS } from '@/api/modules/system-dept'
import apiExcel from '@/api/modules/system-excel'
import { fetchSearchConfig } from '@/api/modules/system-search-config'
import apiUser from '@/api/modules/system-user'
import RelatedEntity from '@/components/RelatedEntity/index.vue'
import { toBackendAssetUrl } from '@/utils/backend-url'
import { deptFallbackIcon } from '@/utils/dept-visual'
import { entityTintStyle, normalizeEntityColor } from '@/utils/entity-visual'
import { excelForm, importResultMessage, pickExcelFile, saveExcelResponse } from '@/utils/excel'
import { templateImportFeedbackKind } from '@/utils/template-import-feedback'

const modal = useYdModal()
const toast = useYdToast()
const { auth } = useAppAuth()

/** 状态选项：搜索下拉与表单共用 */
const statusOptions = [
  { label: '启用', value: 'ACTIVE' },
  { label: '停用', value: 'DEPRECATED' },
]

/** 搜索选项数据源：上级部门从部门树展开，状态用本地词表 */
const optionsSources: SearchOptionsSource[] = [
  {
    id: 'parentDeptOptions',
    loader: async () => {
      const res = await apiDept.tree()
      return flattenDepts(withBackendAvatars(res.data))
        .map(item => ({ label: item.name, value: item.id }))
    },
  },
  { id: 'deptStatusOptions', loader: () => statusOptions },
]

const {
  loading,
  rows,
  searchQuery,
  searchConfig,
  load,
  search,
  resetSearch,
} = useTablePage<DeptManageItem>({
  queryClass: 'online.yudream.base.application.system.user.query.DeptTreeQuery',
  loadApi: async (query) => {
    const res = await apiDept.tree({
      keyword: query.keyword || undefined,
      parentId: query.parentId || undefined,
      status: query.status || undefined,
    })
    const tree = withBackendAvatars(res.data)
    return { records: tree, total: tree.length, page: 1, size: tree.length || 1 }
  },
  loadSearchConfig: queryClass => fetchSearchConfig(queryClass).then(res => res.data),
})

/** 关联导航落地：检索词写入搜索模型、目标行定位高亮；部门是树，判断目标需摊平子节点 */
useLinkedFocus<DeptManageItem>({
  rows,
  idOf: row => row.id,
  queryKey: 'focusDept',
  applyKeyword: (keyword) => {
    searchQuery.keyword = keyword
  },
  flatten: (nodes) => {
    const flat: DeptManageItem[] = []
    const walk = (list: DeptManageItem[]) => {
      for (const node of list || []) {
        flat.push(node)
        walk(node.children || [])
      }
    }
    walk(nodes)
    return flat
  },
})

const formVisible = ref(false)
const editing = ref<DeptManageItem | null>(null)
const formAvatar = ref('')
const avatarInput = ref<InstanceType<typeof YdFilePicker>>()
const avatarUploading = ref(false)

const form = reactive<DeptPayload>({
  name: '',
  description: '',
  color: DEPT_COLOR_PRESETS[0],
  leaderId: undefined,
  phone: '',
  parentId: undefined,
  sortOrder: 0,
  status: 'ACTIVE',
})

const deptOptions = computed(() => flattenDepts(rows.value)
  .filter(item => String(item.id) !== String(editing.value?.id ?? ''))
  .map(item => ({ label: item.name, value: item.id })))
const leaderColumns: TableColumn<UserPickerRow>[] = [
  { id: 'leaderAvatar', header: '', width: 56 },
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
    list: result.records.map((user: UserManageItem) => ({
      id: String(user.id),
      username: user.username,
      nickname: user.nickname || user.username,
      avatar: user.avatar ? toBackendAssetUrl(user.avatar) : undefined,
    })),
    total: result.total,
  }
}
const sortOrderValue = computed({
  get: () => form.sortOrder ?? 0,
  set: (value: number) => {
    form.sortOrder = value
  },
})

/** 部门身份列：上传头像优先，未上传时按名称关键词回退 Morphicons 图标（底色取部门主色）。 */
function deptAvatarFallback(row: DeptManageItem) {
  const name = (row.name || '').trim()
  return name ? name.slice(0, 2) : '部门'
}

/** 负责人选择器无头像时的首字兜底（中文取前 2 字，拉丁取首字母）。 */
function leaderAvatarFallback(user?: UserPickerRow) {
  const name = (user?.nickname || user?.username || '').trim()
  if (!name) {
    return '?'
  }
  return name.charCodeAt(0) < 128 ? name.slice(0, 1).toUpperCase() : name.slice(0, 2)
}

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

/** 页头操作：新增为主操作，导入导出类收进「更多」 */
const pageActions = computed<YdTablePageAction[]>(() => [
  ...(auth('system:dept:create')
    ? [{ label: '新增部门', icon: 'morph:network', onClick: () => openCreate() }]
    : []),
  ...(auth('system:dept:export')
    ? [{ label: '导出', icon: 'morph:file-spreadsheet', variant: 'outline' as const, autoFold: true, onClick: exportDepts }]
    : []),
  ...(auth('system:dept:import')
    ? [
        { label: '模板', icon: 'morph:download', variant: 'outline' as const, autoFold: true, onClick: downloadDeptTemplate },
        { label: '导入', icon: 'morph:upload', variant: 'outline' as const, autoFold: true, onClick: importDepts },
      ]
    : []),
])

/** 时间列统一去掉 ISO 的 T 并截到秒 */
function dateText(value?: string) {
  return value ? value.replace('T', ' ').slice(0, 19) : ''
}

const tableColumns = computed<TableColumn<DeptManageItem>[]>(() => [
  { id: 'name', header: '部门名称', width: 220, fixed: 'left' },
  { id: 'leaderName', header: '负责人', width: 160 },
  { accessorKey: 'phone', header: '电话', width: 140 },
  { accessorKey: 'sortOrder', header: '排序', width: 90, align: 'center', defaultHidden: true },
  { id: 'systemDept', header: '类型', width: 110, align: 'center' },
  { accessorKey: 'description', header: '描述', minWidth: 220, defaultHidden: true },
  { id: 'createTime', header: '创建时间', width: 180, defaultHidden: true },
  { id: 'updateTime', header: '更新时间', width: 180, defaultHidden: true },
])

/** 后端返回 /api/files/... 相对地址，列表落地前统一补后端基址 */
function withBackendAvatars(items: DeptManageItem[]): DeptManageItem[] {
  return items.map(item => ({
    ...item,
    avatar: item.avatar ? toBackendAssetUrl(item.avatar) : undefined,
    children: item.children ? withBackendAvatars(item.children) : undefined,
  }))
}

function openCreate(parent?: DeptManageItem) {
  editing.value = null
  formAvatar.value = ''
  Object.assign(form, {
    name: '',
    description: '',
    color: DEPT_COLOR_PRESETS[0],
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
    color: normalizeEntityColor(row.color) ?? DEPT_COLOR_PRESETS[0],
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
  await load()
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
    await load()
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
      await load()
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
      await load()
    },
  })
}

function flattenDepts(items: DeptManageItem[]): DeptManageItem[] {
  return items.flatMap(item => [item, ...flattenDepts(item.children || [])])
}

async function exportDepts() {
  const res = await apiExcel.exportDepts({
    keyword: searchQuery.keyword || undefined,
    status: searchQuery.status || undefined,
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
    await load()
  })
}
</script>

<template>
  <div>
    <YdTablePageLayout
      title="部门管理"
      :actions="pageActions"
      :actions-threshold="3"
    >
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
        <YdLoadingOverlay :loading="loading">
          <YdResponsiveEnhancedTable
            row-key="id"
            table-root-class="rounded-lg overflow-hidden"
            table-class="min-w-[1120px]"

            column-visibility tree
            :default-expanded="true"
            :status-column="deptStatusColumn"
            :actions="deptActions"
            :card-hidden-columns="['systemDept', 'phone']"
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
                entity-type="dept"
                :entity-id="row.original.id"
                :label="row.original.name"
                :avatar="row.original.avatar"
                @changed="load"
              >
                <div class="yd-entity-trigger flex gap-2 min-w-0 items-center">
                  <YdAvatar
                    v-if="row.original.avatar"
                    :src="row.original.avatar"
                    :fallback="deptAvatarFallback(row.original)"
                    :alt="row.original.name"
                    class="shrink-0 size-9"
                  />
                  <span v-else class="dept-chip" :style="entityTintStyle(row.original.color)" aria-hidden="true">
                    <YdIcon :name="deptFallbackIcon(row.original.name)" />
                  </span>
                  <span class="font-medium truncate">{{ row.original.name }}</span>
                </div>
              </RelatedEntity>
            </template>
            <template #cell-leaderName="{ row }">
              <RelatedEntity
                v-if="row.original.leaderId"
                entity-type="user"
                :entity-id="row.original.leaderId"
                :label="row.original.leaderName"
                @changed="load"
              />
              <span v-else-if="row.original.leaderName">{{ row.original.leaderName }}</span>
              <span v-else class="text-muted-foreground/40">-</span>
            </template>
            <template #cell-systemDept="{ row }">
              <YdTag :variant="row.original.systemDept ? 'default' : 'secondary'">
                {{ row.original.systemDept ? '系统部门' : '普通部门' }}
              </YdTag>
            </template>
          </ydresponsiveenhancedtable>
        </ydloadingoverlay>
      </template>
    </YdTablePageLayout>

    <YdModal v-model="formVisible" :title="editing ? '编辑部门' : '新增部门'" show-cancel-button class="sm:max-w-3xl" @confirm="saveForm">
      <YdForm :model="form" layout="vertical">
        <YdFormItem v-if="editing" label="部门头像">
          <div class="flex flex-wrap gap-3 items-center">
            <YdAvatar v-if="formAvatar" :src="formAvatar" :fallback="form.name || '部门'" class="size-12" />
            <span v-else class="dept-chip dept-chip--lg" :style="entityTintStyle(form.color)" aria-hidden="true">
              <YdIcon :name="deptFallbackIcon(form.name)" />
            </span>
            <YdButton v-auth="'system:dept:edit'" variant="outline" size="sm" :loading="avatarUploading" @click="pickDeptAvatar">
              <YdIcon name="morph:cloud-upload" />
              上传头像
            </YdButton>
            <span class="text-xs text-muted-foreground">仅支持图片文件；无头像时按部门名称展示兜底图标，可搭配部门主色</span>
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
              >
                <template #cell-leaderAvatar="{ row }">
                  <YdAvatar :src="row.original.avatar || ''" :fallback="leaderAvatarFallback(row.original)" class="size-8" />
                </template>
              </YdTablePicker>
            </YdFormItem>
          </div>
          <div>
            <YdFormItem label="部门主色" extra="用于部门图标底色与列表标识；保存为 #RRGGBB 小写 hex">
              <div class="flex gap-2 w-full items-center">
                <YdColorPicker v-model="form.color" :presets="DEPT_COLOR_PRESETS" aria-label="选择部门主色" class="flex-1" />
                <span class="dept-chip shrink-0" :style="entityTintStyle(form.color)" aria-hidden="true">
                  <YdIcon :name="deptFallbackIcon(form.name)" />
                </span>
              </div>
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

<style scoped>
/*
  部门图标兜底色块：底色与前景色来自部门主色（内联 style 只承载数据色），
  未设色时回退中性语义变量；列表身份列与表单预览共用同一套取色助手。
*/
.dept-chip {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  font-size: 20px;
  line-height: 1;
  border-radius: 10px;
}

.dept-chip--lg {
  width: 48px;
  height: 48px;
  font-size: 24px;
}
</style>
