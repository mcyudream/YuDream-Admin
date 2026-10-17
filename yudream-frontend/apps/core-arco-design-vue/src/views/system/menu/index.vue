<script setup lang="ts">
import type { ActionMenuItem, SearchOptionsSource, StatusColumn, TableColumn, UserColumn, YdTablePageAction, YdTablePickerQuery, YdTablePickerResult, YdTreeNode } from '@yudream/components'
import type { MenuCandidate, MenuManageItem, MenuNodeType, MenuPayload, MenuSource, MenuStatus } from '@/api/modules/system-menu'
import { useTablePage } from '@yudream/components'
import apiExcel from '@/api/modules/system-excel'
import apiMenu from '@/api/modules/system-menu'
import { fetchSearchConfig } from '@/api/modules/system-search-config'
import { refreshDynamicRoutes } from '@/router/dynamic'
import { excelForm, importResultMessage, pickExcelFile, saveExcelResponse } from '@/utils/excel'

interface TreeNode extends YdTreeNode {
  menu?: MenuManageItem
  children?: TreeNode[]
}

const ROOT_LABEL = '根目录'
const DEFAULT_MODULE = '系统管理'

const modal = useYdModal()
const toast = useYdToast()
const router = useRouter()
const route = useRoute()
const { auth } = useAppAuth()

const selectedKeys = ref<string[]>([])
const formVisible = ref(false)
const editing = ref<MenuManageItem | null>(null)

const form = reactive<MenuPayload>({
  code: '',
  name: '',
  type: 'CATEGORY',
  parentCode: undefined,
  module: DEFAULT_MODULE,
  icon: '',
  path: '',
  component: '',
  link: '',
  sort: 0,
  visible: true,
  permission: '',
  status: 'ACTIVE',
})

const typeOptions = [
  { label: '目录', value: 'CATEGORY' },
  { label: '布局', value: 'LAYOUT' },
  { label: '菜单', value: 'MENU' },
  { label: '外链', value: 'LINK' },
  { label: '按钮', value: 'BUTTON' },
]

const statusOptions = [
  { label: '正常', value: 'ACTIVE' },
  { label: '停用', value: 'DISABLED' },
]

/** 搜索选项数据源：菜单类型/状态用本地词表（与表单下拉共用同一份文案） */
const optionsSources: SearchOptionsSource[] = [
  { id: 'menuTypeOptions', loader: () => typeOptions },
  { id: 'menuStatusOptions', loader: () => statusOptions },
]

/**
 * 菜单管理是按树加载的列表（无分页），搜索条件来自 MenuTreeQuery 的 @Searchable 注解；
 * 加载完成后的选中项校正见下方对 rows 的监听。
 */
const {
  loading,
  rows,
  searchQuery,
  searchConfig,
  load,
  search,
  resetSearch,
} = useTablePage<MenuManageItem>({
  queryClass: 'online.yudream.base.application.system.menu.query.MenuTreeQuery',
  loadApi: async (query) => {
    const res = await apiMenu.tree({
      keyword: query.keyword || undefined,
      type: query.type || undefined,
      status: query.status || undefined,
    })
    const tree = res.data
    return { records: tree, total: tree.length, page: 1, size: tree.length || 1 }
  },
  loadSearchConfig: queryClass => fetchSearchConfig(queryClass).then(res => res.data),
  autoLoad: false,
})

const selectedMenu = computed(() => findMenu(rows.value, selectedKeys.value[0]))
const selectedDisplayParent = computed(() => selectedMenu.value
  ? findMenu(rows.value, selectedMenu.value.displayParentCode || selectedMenu.value.parentCode)
  : undefined)
const selectedActualParent = computed(() => selectedMenu.value
  ? findMenu(rows.value, selectedMenu.value.parentCode)
  : undefined)
const canCreateChild = computed(() => !!selectedMenu.value && selectedMenu.value.type !== 'BUTTON' && selectedMenu.value.type !== 'LINK')
const buttonRows = computed(() => selectedMenu.value?.children?.filter(item => item.type === 'BUTTON') || [])
const isPluginEditing = computed(() => editing.value?.source === 'PLUGIN')
const selectedDisplayParentLabel = computed(() => selectedDisplayParent.value?.name || ROOT_LABEL)
const selectedActualParentLabel = computed(() => {
  if (!selectedMenu.value?.parentCode) {
    return ROOT_LABEL
  }
  return selectedActualParent.value
    ? `${selectedActualParent.value.name} (${selectedActualParent.value.code})`
    : selectedMenu.value.parentCode
})

const parentColumns: TableColumn<MenuCandidate>[] = [
  { accessorKey: 'name', header: '菜单名称', minWidth: 180 },
  { accessorKey: 'code', header: '编码', minWidth: 180 },
  { accessorKey: 'module', header: '模块', width: 140 },
]

const parentPickerValue = computed<string[]>({
  get: () => form.parentCode ? [form.parentCode] : [],
  set: (value) => { form.parentCode = value[0] || undefined },
})

const parentInitialLabels = computed<Record<string, string>>(() => {
  const parent = form.parentCode ? findMenu(rows.value, form.parentCode) : undefined
  return parent ? { [parent.code]: `${parent.name} (${parent.code})` } : {}
})

async function fetchParentCandidates(query: YdTablePickerQuery): Promise<YdTablePickerResult<MenuCandidate>> {
  const result = (await apiMenu.candidates({
    ...query,
    currentCode: editing.value?.code,
    source: editing.value?.source === 'PLUGIN' ? undefined : 'SYSTEM',
  })).data
  return { list: result.records, total: result.total }
}

const sortValue = computed({
  get: () => form.sort ?? 0,
  set: (value: number) => {
    form.sort = value
  },
})

const treeData = computed<TreeNode[]>(() => [{
  key: '__root__',
  label: ROOT_LABEL,
  children: buildTreeNodes(rows.value),
}])

const buttonUserColumn: UserColumn<MenuManageItem> = { nameKey: 'name', emailKey: 'permission', header: '按钮', width: 260 }

const buttonStatusColumn: StatusColumn<MenuManageItem> = {
  statusKey: 'status',
  header: '状态',
  width: 100,
  // 语义色胶囊：按钮权限长列表里能一眼扫出停用项
  statusMap: {
    ACTIVE: { label: '启用', variant: 'default', tone: 'success' },
    DISABLED: { label: '停用', variant: 'secondary', tone: 'neutral' },
  },
}

/** 行操作统一收进下拉 */
const buttonActions: ActionMenuItem<MenuManageItem>[] = [
  { label: '编辑', icon: 'morph:pencil', visible: () => auth('system:menu:edit'), onClick: openEdit },
  { label: '启用', icon: 'morph:circle-play', visible: row => row.status === 'DISABLED' && auth('system:menu:edit'), onClick: confirmEnable },
  { label: '停用', icon: 'morph:circle-pause', visible: row => row.status === 'ACTIVE' && auth('system:menu:edit'), onClick: confirmDisable },
  { label: '删除', icon: 'morph:trash-2', danger: true, visible: () => auth('system:menu:delete'), onClick: confirmDelete },
]

const buttonColumns = computed<TableColumn<MenuManageItem>[]>(() => [
  { id: 'index', header: '', width: 56, align: 'center' },
  { accessorKey: 'name', header: '按钮名称', minWidth: 180 },
  { accessorKey: 'permission', header: '权限标识', minWidth: 240 },
  { accessorKey: 'sort', header: '排序', width: 100, align: 'center' },
])

/** 页头操作：新增/编辑/状态开关保持可见，导入导出模板收进「更多」，删除按危险操作收进「更多」 */
const pageActions = computed<YdTablePageAction[]>(() => [
  ...(auth('system:menu:create')
    ? [
        { label: '新增菜单', icon: 'morph:plus', onClick: () => openCreate() },
        { label: '新增子菜单', icon: 'morph:git-branch-plus', variant: 'outline' as const, disabled: !canCreateChild.value, onClick: openCreateFromSelected },
      ]
    : []),
  ...(auth('system:menu:edit')
    ? [{ label: '编辑', icon: 'morph:pencil', variant: 'outline' as const, disabled: !selectedMenu.value, onClick: () => openEdit() }]
    : []),
  ...(auth('system:menu:edit') && selectedMenu.value?.status === 'DISABLED'
    ? [{ label: '启用', icon: 'morph:circle-play', variant: 'outline' as const, onClick: () => confirmEnable() }]
    : []),
  ...(auth('system:menu:edit') && selectedMenu.value?.status === 'ACTIVE'
    ? [{ label: '停用', icon: 'morph:circle-pause', variant: 'outline' as const, onClick: () => confirmDisable() }]
    : []),
  ...(auth('system:menu:export')
    ? [{ label: '导出', icon: 'morph:file-spreadsheet', variant: 'outline' as const, autoFold: true, onClick: exportMenus }]
    : []),
  ...(auth('system:menu:import')
    ? [
        { label: '模板', icon: 'morph:download', variant: 'outline' as const, autoFold: true, onClick: downloadMenuTemplate },
        { label: '导入', icon: 'morph:upload', variant: 'outline' as const, autoFold: true, onClick: importMenus },
      ]
    : []),
  ...(auth('system:menu:delete')
    ? [{ label: '删除', icon: 'morph:trash-2', variant: 'destructive' as const, autoFold: true, danger: true, disabled: !selectedMenu.value, onClick: () => confirmDelete() }]
    : []),
])

onMounted(async () => {
  await load()
  await openLinkedMenu()
})

watch(() => route.query.editMenu, () => openLinkedMenu())

/** 树形列表筛选后原选中项可能已不在树里：每次列表落地都校正一次详情面板的选中项 */
watch(rows, () => syncSelectedMenu())
let linkedMenuRequest = 0

async function openLinkedMenu() {
  const code = route.query.editMenu
  if (typeof code !== 'string' || !code || !auth('system:menu:view') || !auth('system:menu:edit')) {
    return
  }
  const request = ++linkedMenuRequest
  try {
    let menu = findMenu(rows.value, code)
    if (!menu) {
      const response = await apiMenu.tree()
      if (request !== linkedMenuRequest || route.query.editMenu !== code) {
        return
      }
      rows.value = response.data
      clearSearchQuery()
      menu = findMenu(rows.value, code)
    }
    if (menu) {
      selectedKeys.value = [menu.code]
      openEdit(menu)
    }
    else {
      toast.warning('菜单已不可用，请刷新菜单列表')
    }
  }
  finally {
    if (request === linkedMenuRequest && route.query.editMenu === code) {
      const query = { ...route.query }
      delete query.editMenu
      await router.replace({ query })
    }
  }
}

/** 直达深链时列表已整体重取，搜索表单同步回到未筛选状态 */
function clearSearchQuery() {
  searchQuery.keyword = undefined
  searchQuery.type = undefined
  searchQuery.status = undefined
}

function syncSelectedMenu() {
  const current = selectedKeys.value[0]
  if (current && findMenu(rows.value, current)) {
    return
  }
  const first = flattenMenus(rows.value).find(item => item.type !== 'BUTTON')
  selectedKeys.value = first ? [first.code] : []
}

function openCreate(parent?: MenuManageItem) {
  editing.value = null
  Object.assign(form, {
    code: '',
    name: '',
    type: !parent ? 'CATEGORY' : parent.type === 'MENU' ? 'BUTTON' : 'MENU',
    parentCode: parent?.code,
    module: parent?.module || selectedMenu.value?.module || DEFAULT_MODULE,
    icon: '',
    path: '',
    component: '',
    link: '',
    sort: 0,
    visible: true,
    permission: '',
    status: 'ACTIVE' as MenuStatus,
  })
  formVisible.value = true
}

function openCreateFromSelected() {
  openCreate(canCreateChild.value ? selectedMenu.value ?? undefined : undefined)
}

function openEdit(row?: MenuManageItem) {
  const target = row || selectedMenu.value
  if (!target) {
    toast.warning('请先选择菜单')
    return
  }
  editing.value = target
  Object.assign(form, {
    code: target.code,
    name: target.name,
    type: target.type,
    parentCode: target.parentCode,
    module: target.module,
    icon: target.icon,
    path: target.path,
    component: target.component,
    link: target.link,
    sort: target.sort || 0,
    visible: target.visible !== false,
    permission: target.permission,
    status: target.status,
  })
  formVisible.value = true
}

async function saveForm() {
  const payload = normalizePayload()
  const currentEditing = editing.value
  if (currentEditing) {
    await apiMenu.update(currentEditing.code, payload)
  }
  else {
    await apiMenu.create(payload)
  }
  toast.success(currentEditing ? '编辑成功' : '新增成功')
  formVisible.value = false
  await load()
  await refreshDynamicRoutes(router)
  if (!currentEditing && payload.code) {
    selectedKeys.value = [payload.code]
  }
}

function confirmDisable(row?: MenuManageItem) {
  const target = row || selectedMenu.value
  if (!target) {
    toast.warning('请先选择菜单')
    return
  }
  modal.confirm({
    title: '确认信息',
    content: `确认停用“${target.name}”吗？`,
    onConfirm: async () => {
      await apiMenu.disable(target.code)
      toast.success('停用成功')
      await load()
      await refreshDynamicRoutes(router)
    },
  })
}

function confirmDelete(row?: MenuManageItem) {
  const target = row || selectedMenu.value
  if (!target) {
    toast.warning('请先选择菜单')
    return
  }
  modal.confirm({
    title: '确认信息',
    content: `确认删除“${target.name}”及其全部子菜单、按钮权限吗？删除后无法恢复。`,
    onConfirm: async () => {
      await apiMenu.remove(target.code)
      toast.success('删除成功')
      if (selectedKeys.value[0] === target.code) {
        selectedKeys.value = []
      }
      await load()
      await refreshDynamicRoutes(router)
    },
  })
}

function confirmEnable(row?: MenuManageItem) {
  const target = row || selectedMenu.value
  if (!target) {
    toast.warning('请先选择菜单')
    return
  }
  modal.confirm({
    title: '确认信息',
    content: `确认启用“${target.name}”吗？`,
    onConfirm: async () => {
      await apiMenu.enable(target.code)
      toast.success('启用成功')
      await load()
      await refreshDynamicRoutes(router)
    },
  })
}

function normalizePayload(): MenuPayload {
  const payload: MenuPayload = {
    ...form,
    parentCode: form.parentCode || undefined,
    module: form.module || undefined,
    icon: form.icon || undefined,
    path: ['MENU', 'LAYOUT', 'LINK'].includes(form.type) ? form.path || undefined : undefined,
    component: form.type === 'MENU'
      ? form.component || undefined
      : ['LAYOUT', 'LINK'].includes(form.type)
          ? 'Layout'
          : undefined,
    link: form.type === 'LINK' ? form.link || undefined : undefined,
    permission: !['CATEGORY', 'LAYOUT'].includes(form.type) ? form.permission || form.code : undefined,
    sort: form.sort ?? 0,
  }
  if (editing.value) {
    delete payload.code
  }
  return payload
}

function typeText(type?: MenuNodeType) {
  return typeOptions.find(item => item.value === type)?.label || type || '-'
}

function statusText(status?: MenuStatus) {
  return statusOptions.find(item => item.value === status)?.label || status || '-'
}

function sourceText(source?: MenuSource) {
  return source === 'PLUGIN' ? '插件' : '系统'
}

function typeVariant(type?: MenuNodeType) {
  if (type === 'BUTTON') {
    return 'outline'
  }
  if (type === 'MENU' || type === 'LINK') {
    return 'default'
  }
  return 'secondary'
}

function statusVariant(status?: MenuStatus) {
  return status === 'ACTIVE' ? 'default' : 'secondary'
}

function sourceVariant(source?: MenuSource) {
  return source === 'PLUGIN' ? 'outline' : 'secondary'
}

function runtimeText(menu?: MenuManageItem) {
  return menu?.runtimeAvailable === false ? '已隐藏' : '可用'
}

function runtimeVariant(menu?: MenuManageItem) {
  return menu?.runtimeAvailable === false ? 'secondary' : 'default'
}

function visibleText(menu?: MenuManageItem) {
  return menu?.visible === false ? '隐藏' : '显示'
}

function visibleVariant(menu?: MenuManageItem) {
  return menu?.visible === false ? 'secondary' : 'default'
}

function isExternal(menu?: MenuManageItem) {
  return menu?.type === 'LINK' || !!menu?.link
}

function displayValue(value?: string | number | boolean | null) {
  if (value === true) {
    return '是'
  }
  if (value === false) {
    return '否'
  }
  return value ?? '-'
}

/** 长权限标识中间截断，完整值靠 title 悬浮查看 */
function truncateMiddle(value?: string | null, head = 8, tail = 4) {
  if (!value) {
    return ''
  }
  if (value.length <= head + tail + 1) {
    return value
  }
  return `${value.slice(0, head)}…${value.slice(-tail)}`
}

function menuForTreeNode(node?: YdTreeNode) {
  return node ? findMenu(rows.value, node.key) : undefined
}

function menuIcon(menu?: MenuManageItem) {
  return menu?.icon || treeIcon(menu?.type || 'CATEGORY')
}

function treeIcon(type?: MenuNodeType) {
  const icons: Record<MenuNodeType, string> = {
    CATEGORY: 'morph:folder',
    LAYOUT: 'morph:layout-dashboard',
    MENU: 'morph:menu',
    LINK: 'morph:link',
    BUTTON: 'morph:circle-check',
  }
  return icons[type || 'MENU']
}

function buildTreeNodes(items: MenuManageItem[]): TreeNode[] {
  return items
    .filter(item => item.type !== 'BUTTON')
    .map(item => ({
      key: item.code,
      label: item.name,
      menu: item,
      children: buildTreeNodes(item.children || []),
    }))
}

function flattenMenus(items: MenuManageItem[]): MenuManageItem[] {
  return items.flatMap(item => [item, ...flattenMenus(item.children || [])])
}

function findMenu(items: MenuManageItem[], code?: string): MenuManageItem | undefined {
  if (!code || code === '__root__') {
    return undefined
  }
  for (const item of items) {
    if (item.code === code) {
      return item
    }
    const child = findMenu(item.children || [], code)
    if (child) {
      return child
    }
  }
  return undefined
}

async function exportMenus() {
  const res = await apiExcel.exportMenus({ keyword: searchQuery.keyword || undefined })
  saveExcelResponse(res, '菜单管理.xlsx')
}

async function downloadMenuTemplate() {
  const res = await apiExcel.menuTemplate()
  saveExcelResponse(res, '菜单导入模板.xlsx')
}

function importMenus() {
  pickExcelFile(async (file) => {
    const res = await apiExcel.importMenus(excelForm(file))
    toast.success(importResultMessage(res.data))
    await load()
  })
}
</script>

<template>
  <div>
    <YdTablePageLayout
      title="菜单管理"
      :actions="pageActions"
      :actions-threshold="4"
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
          <div class="menu-workbench">
            <aside class="menu-sidebar">
              <div class="menu-sidebar__header">
                <h2>菜单列表</h2>
              </div>

              <div class="menu-tree">
                <YdTree
                  v-model:selected-keys="selectedKeys"
                  :data="treeData"
                  :default-expand-all="true"
                >
                  <template #default="{ node }">
                    <div class="menu-tree-node">
                      <span class="menu-tree-node__main">
                        <span class="menu-tree-node__icon">
                          <YdIcon :name="menuIcon(menuForTreeNode(node))" />
                        </span>
                        <span>{{ node?.label ?? '' }}</span>
                        <span
                          v-if="menuForTreeNode(node)?.source === 'PLUGIN'"
                          class="plugin-source-marker"
                          title="插件菜单"
                          aria-label="插件菜单"
                        />
                      </span>
                      <YdButton
                        v-if="menuForTreeNode(node) && menuForTreeNode(node)?.type !== 'LINK'"
                        v-auth="'system:menu:create'"
                        variant="ghost"
                        size="sm"
                        title="新增子菜单"
                        @click.stop="openCreate(menuForTreeNode(node))"
                      >
                        <YdIcon name="morph:plus" />
                      </YdButton>
                    </div>
                  </template>
                </YdTree>
              </div>
            </aside>

            <main class="menu-content">
              <section class="menu-section">
                <div class="menu-section__header">
                  <h2>菜单详情</h2>
                </div>

                <div v-if="selectedMenu" class="detail-grid">
                  <div class="detail-cell detail-cell--label">
                    菜单类型
                  </div>
                  <div class="detail-cell">
                    <YdTag :variant="typeVariant(selectedMenu.type)">
                      {{ typeText(selectedMenu.type) }}
                    </YdTag>
                  </div>
                  <div class="detail-cell detail-cell--label">
                    菜单状态
                  </div>
                  <div class="detail-cell">
                    <YdTag :variant="statusVariant(selectedMenu.status)">
                      {{ statusText(selectedMenu.status) }}
                    </YdTag>
                  </div>
                  <div class="detail-cell detail-cell--label">
                    来源
                  </div>
                  <div class="detail-cell">
                    <YdTag :variant="sourceVariant(selectedMenu.source)">
                      {{ sourceText(selectedMenu.source) }}
                    </YdTag>
                  </div>
                  <div class="detail-cell detail-cell--label">
                    运行状态
                  </div>
                  <div class="detail-cell">
                    <YdTag :variant="runtimeVariant(selectedMenu)">
                      {{ runtimeText(selectedMenu) }}
                    </YdTag>
                  </div>
                  <div class="detail-cell detail-cell--label">
                    菜单名称
                  </div>
                  <div class="detail-cell detail-cell__name">
                    <YdIcon :name="menuIcon(selectedMenu)" />
                    <span>{{ selectedMenu.name }}</span>
                  </div>
                  <div class="detail-cell detail-cell--label">
                    路由地址
                  </div>
                  <div class="detail-cell">
                    {{ displayValue(selectedMenu.path) }}
                  </div>
                  <div class="detail-cell detail-cell--label">
                    菜单编码
                  </div>
                  <div class="detail-cell">
                    {{ selectedMenu.code }}
                  </div>
                  <div class="detail-cell detail-cell--label">
                    权限标识
                  </div>
                  <div class="detail-cell">
                    {{ displayValue(selectedMenu.permission) }}
                  </div>
                  <div class="detail-cell detail-cell--label">
                    展示父级
                  </div>
                  <div class="detail-cell">
                    {{ selectedDisplayParentLabel }}
                  </div>
                  <div class="detail-cell detail-cell--label">
                    配置父级
                  </div>
                  <div class="detail-cell">
                    {{ selectedActualParentLabel }}
                  </div>
                  <div class="detail-cell detail-cell--label">
                    组件路径
                  </div>
                  <div class="detail-cell">
                    {{ displayValue(selectedMenu.component) }}
                  </div>
                  <div class="detail-cell detail-cell--label">
                    菜单显示
                  </div>
                  <div class="detail-cell">
                    <YdTag :variant="visibleVariant(selectedMenu)">
                      {{ visibleText(selectedMenu) }}
                    </YdTag>
                  </div>
                  <div class="detail-cell detail-cell--label">
                    是否外链
                  </div>
                  <div class="detail-cell">
                    <YdTag :variant="isExternal(selectedMenu) ? 'outline' : 'secondary'">
                      {{ displayValue(isExternal(selectedMenu)) }}
                    </YdTag>
                  </div>
                  <div class="detail-cell detail-cell--label">
                    外链地址
                  </div>
                  <div class="detail-cell detail-cell--wide">
                    {{ displayValue(selectedMenu.link) }}
                  </div>
                  <template v-if="selectedMenu.source === 'PLUGIN'">
                    <div class="detail-cell detail-cell--label">
                      插件编码
                    </div>
                    <div class="detail-cell">
                      {{ displayValue(selectedMenu.pluginCode) }}
                    </div>
                    <div class="detail-cell detail-cell--label">
                      插件模块
                    </div>
                    <div class="detail-cell">
                      {{ displayValue(selectedMenu.pluginModuleName) }}
                    </div>
                  </template>
                </div>
                <div v-else class="empty-state">
                  请选择左侧菜单
                </div>
              </section>

              <section class="menu-section">
                <div class="menu-section__header">
                  <h2>按钮权限列表</h2>
                </div>

                <YdResponsiveEnhancedTable

                  row-key="code"
                  table-root-class="rounded-lg overflow-hidden"
                  table-class="min-w-[720px]"
                  :user-column="buttonUserColumn"
                  :status-column="buttonStatusColumn"
                  :actions="buttonActions"
                  :columns="buttonColumns"
                  :data="buttonRows"
                >
                  <template #cell-index="{ row }">
                    {{ row.index + 1 }}
                  </template>
                  <template #cell-permission="{ row }">
                    <span v-if="row.original.permission" :title="row.original.permission">{{ truncateMiddle(row.original.permission, 18, 10) }}</span>
                    <span v-else class="text-muted-foreground/40">-</span>
                  </template>
                </YdResponsiveEnhancedTable>
              </section>
            </main>
          </div>
        </YdLoadingOverlay>
      </template>
    </YdTablePageLayout>

    <YdModal v-model="formVisible" :title="editing ? '编辑菜单' : '新增菜单'" show-cancel-button class="sm:max-w-3xl" @confirm="saveForm">
      <YdForm :model="form" layout="vertical">
        <div class="gap-x-4 grid grid-cols-2">
          <div>
            <YdFormItem label="菜单编码" required>
              <YdInput
                v-model="form.code"
                :disabled="!!editing"
                placeholder="system:menu"
                class="w-full"
              />
            </YdFormItem>
            <div v-if="isPluginEditing" class="form-help-text">
              插件菜单编码用于恢复插件注册关系，编辑时仅支持查看。
            </div>
          </div>
          <div>
            <YdFormItem label="菜单名称" required>
              <YdInput v-model="form.name" class="w-full" />
            </YdFormItem>
          </div>
          <div>
            <YdFormItem label="类型" required>
              <YdSelect v-model="form.type" :options="typeOptions" class="w-full" />
            </YdFormItem>
            <p class="form-help-text">
              一级大图标导航默认使用业务分类（如 MC、待办、社交），功能菜单归入分类。旧菜单和明确需要独立入口的个例可保留原结构。
            </p>
          </div>
          <div>
            <YdFormItem label="上级菜单">
              <YdTablePicker
                v-model="parentPickerValue"
                :columns="parentColumns"
                :fetcher="fetchParentCandidates"
                row-key="code"
                label-key="name"
                :multiple="false"
                :initial-labels="parentInitialLabels"
                title="选择父级菜单"
                placeholder="根目录"
                class="w-full"
              />
            </YdFormItem>
          </div>
          <div>
            <YdFormItem label="模块">
              <YdInput v-model="form.module" :placeholder="DEFAULT_MODULE" class="w-full" />
            </YdFormItem>
          </div>
          <div>
            <YdFormItem label="排序">
              <YdNumberField v-model="sortValue" :min="0" class="w-full" />
            </YdFormItem>
          </div>
          <div>
            <YdFormItem label="导航菜单">
              <div class="pt-1 flex gap-3 items-center">
                <YdSwitch v-model="form.visible" />
                <span class="text-sm text-muted-foreground">{{ form.visible ? '显示在导航中' : '仅保留路由访问' }}</span>
              </div>
            </YdFormItem>
          </div>
          <div v-if="form.type !== 'BUTTON'">
            <YdFormItem label="图标">
              <YdIconPicker v-model="form.icon" class="w-full" />
            </YdFormItem>
          </div>
          <div v-if="['MENU', 'LAYOUT', 'LINK'].includes(form.type)">
            <YdFormItem label="路由路径">
              <YdInput v-model="form.path" :placeholder="form.type === 'LINK' ? '/external/docs' : '/system/menu'" class="w-full" />
            </YdFormItem>
          </div>
          <div v-if="form.type === 'MENU'">
            <YdFormItem label="组件路径">
              <YdInput v-model="form.component" placeholder="system/menu/index.vue" class="w-full" />
            </YdFormItem>
          </div>
          <div v-if="form.type === 'LAYOUT'">
            <YdFormItem label="组件路径">
              <YdInput model-value="Layout" disabled class="w-full" />
            </YdFormItem>
          </div>
          <div v-if="form.type === 'LINK'">
            <YdFormItem label="外链地址">
              <YdInput v-model="form.link" placeholder="https://www.yudream.online" class="w-full" />
            </YdFormItem>
          </div>
          <div v-if="!['CATEGORY', 'LAYOUT'].includes(form.type)">
            <YdFormItem label="权限标识">
              <YdInput v-model="form.permission" placeholder="为空时使用菜单编码" class="w-full" />
            </YdFormItem>
          </div>
          <div v-if="editing">
            <YdFormItem label="状态">
              <YdSelect v-model="form.status" :options="statusOptions" class="w-full" />
            </YdFormItem>
          </div>
        </div>
      </YdForm>
    </YdModal>
  </div>
</template>

<style scoped>
/*
  工作台铺满表格卡片：单行两列、行高定义为剩余高度，左树与右侧详情各自内滚，
  避免出现两个滚动盒叠加（页面整体滚动已由 YdTablePageLayout 接管）。
*/
.menu-workbench {
  display: grid;
  grid-template-columns: minmax(300px, 340px) minmax(0, 1fr);
  grid-template-rows: minmax(0, 1fr);
  gap: 12px;
  height: 100%;
  min-height: 0;
}

.menu-sidebar,
.menu-section {
  border: 1px solid var(--color-border-2);
  border-radius: 6px;
  background: var(--color-bg-2);
}

.menu-sidebar {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 14px;
  min-height: 0;
  overflow: hidden;
}

.menu-sidebar__header,
.menu-section__header,
.menu-tree-node,
.menu-tree-node__main {
  display: flex;
  align-items: center;
}

.menu-sidebar__header,
.menu-section__header {
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.menu-sidebar h2,
.menu-section h2 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
}

.menu-tree {
  overflow: auto;
  min-height: 0;
  padding-right: 2px;
}

.menu-tree-node {
  justify-content: space-between;
  width: 100%;
  min-height: 32px;
  gap: 8px;
}

.menu-tree-node__main {
  min-width: 0;
  gap: 8px;
}

.menu-tree-node__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  flex: 0 0 18px;
  color: var(--color-text-2);
}

.menu-tree-node__main span:nth-child(2) {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.plugin-source-marker {
  width: 6px;
  height: 6px;
  margin-left: 2px;
  border-radius: 50%;
  background: var(--color-text-4);
  flex: 0 0 auto;
}

.menu-content {
  display: grid;
  align-content: start;
  gap: 12px;
  min-width: 0;
  min-height: 0;
  overflow-y: auto;
}

.menu-section {
  padding: 14px;
  min-width: 0;
  overflow-x: auto;
}

.detail-grid {
  display: grid;
  grid-template-columns: 130px minmax(160px, 1fr) 130px minmax(160px, 1fr);
  min-width: 720px;
  margin-top: 12px;
  overflow: hidden;
  border: 1px solid var(--color-border-2);
  border-radius: 4px;
}

.detail-cell {
  min-height: 40px;
  padding: 9px 12px;
  border-right: 1px solid var(--color-border-2);
  border-bottom: 1px solid var(--color-border-2);
  word-break: break-all;
  font-size: 14px;
}

.detail-cell:nth-child(4n) {
  border-right: 0;
}

.detail-cell:nth-last-child(-n + 2) {
  border-bottom: 0;
}

.detail-cell--label {
  font-weight: 600;
  background: var(--color-fill-1);
}

.detail-cell__name {
  display: flex;
  align-items: center;
  gap: 8px;
}

.detail-cell--wide {
  grid-column: span 3;
  border-right: 0;
}

.form-help-text {
  margin-top: -4px;
  color: var(--color-text-3);
  font-size: 12px;
  line-height: 1.5;
}

:deep(.arco-tree-node-title) {
  width: 100%;
}

:deep(.arco-tree-node-title:hover) {
  background: var(--color-fill-1);
}

:deep(.arco-table-th),
:deep(.arco-table-td) {
  height: 40px;
}

.empty-state {
  display: grid;
  place-items: center;
  min-height: 140px;
  color: var(--color-text-3);
}

@media (max-width: 1280px) {
  /* 窄屏改为上下两段：树限高内滚，详情与按钮表占据剩余高度并内滚 */
  .menu-workbench {
    grid-template-columns: 1fr;
    grid-template-rows: minmax(0, 360px) minmax(0, 1fr);
  }

  .menu-sidebar {
    max-height: 360px;
  }
}

@media (max-width: 768px) {
  .detail-grid {
    grid-template-columns: 120px minmax(0, 1fr);
    min-width: 0;
  }

  .detail-cell:nth-child(4n) {
    border-right: 1px solid var(--color-border-2);
  }

  .detail-cell:nth-child(2n) {
    border-right: 0;
  }

  .detail-cell:nth-last-child(-n + 2) {
    border-bottom: 1px solid var(--color-border-2);
  }

  .detail-cell:last-child {
    border-bottom: 0;
  }

  .detail-cell--wide {
    grid-column: span 1;
  }
}
</style>
