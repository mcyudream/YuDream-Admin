<script setup lang="ts">
import type { TableColumn } from '@yudream/components'
import type { MenuManageItem, MenuNodeType, MenuPayload, MenuSource, MenuStatus } from '@/api/modules/system-menu'
import apiExcel from '@/api/modules/system-excel'
import apiMenu from '@/api/modules/system-menu'
import { refreshDynamicRoutes } from '@/router/dynamic'
import { excelForm, importResultMessage, pickExcelFile, saveExcelResponse } from '@/utils/excel'

interface TreeNode {
  key: string
  title: string
  menu?: MenuManageItem
  children?: TreeNode[]
}

const ROOT_LABEL = '根目录'
const DEFAULT_MODULE = '系统管理'

const modal = useFaModal()
const toast = useFaToast()
const router = useRouter()

const loading = ref(false)
const rows = ref<MenuManageItem[]>([])
const searchKeyword = ref('')
const selectedKeys = ref<string[]>([])
const formVisible = ref(false)
const editing = ref<MenuManageItem | null>(null)

const form = reactive<MenuPayload>({
  code: '',
  name: '',
  type: 'MENU',
  parentCode: undefined,
  module: DEFAULT_MODULE,
  icon: '',
  path: '',
  component: '',
  link: '',
  sort: 0,
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

const parentOptions = computed(() => {
  const disabledCodes = editing.value ? new Set([editing.value.code, ...collectDescendantCodes(editing.value)]) : new Set<string>()
  const allowPluginParents = editing.value?.source === 'PLUGIN'
  return flattenMenus(rows.value)
    .filter(item => item.type !== 'BUTTON' && item.type !== 'LINK')
    .filter(item => allowPluginParents || item.source !== 'PLUGIN')
    .filter(item => !disabledCodes.has(item.code))
    .map(item => ({
      label: `${item.name} (${item.code})`,
      value: item.code,
    }))
})

const sortValue = computed({
  get: () => form.sort ?? 0,
  set: (value: number) => {
    form.sort = value
  },
})

const treeData = computed<TreeNode[]>(() => [{
  key: '__root__',
  title: ROOT_LABEL,
  children: buildTreeNodes(rows.value),
}])

const buttonColumns = computed<TableColumn<MenuManageItem>[]>(() => [
  { id: 'index', header: '', width: 56, align: 'center' },
  { accessorKey: 'name', header: '按钮名称', minWidth: 180 },
  { accessorKey: 'permission', header: '权限标识', minWidth: 240 },
  { id: 'status', header: '状态', width: 120, align: 'center' },
  { accessorKey: 'sort', header: '排序', width: 100, align: 'center' },
  { id: 'operation', header: '操作', width: 140, align: 'center' },
])

onMounted(loadTree)

async function loadTree() {
  loading.value = true
  try {
    const res = await apiMenu.tree({
      keyword: searchKeyword.value || undefined,
    })
    rows.value = res.data
    syncSelectedMenu()
  }
  finally {
    loading.value = false
  }
}

function syncSelectedMenu() {
  const current = selectedKeys.value[0]
  if (current && findMenu(rows.value, current)) {
    return
  }
  const first = flattenMenus(rows.value).find(item => item.type !== 'BUTTON')
  selectedKeys.value = first ? [first.code] : []
}

function resetSearch() {
  searchKeyword.value = ''
  loadTree()
}

function openCreate(parent?: MenuManageItem) {
  editing.value = null
  Object.assign(form, {
    code: '',
    name: '',
    type: parent?.type === 'MENU' ? 'BUTTON' : 'MENU',
    parentCode: parent?.code,
    module: parent?.module || selectedMenu.value?.module || DEFAULT_MODULE,
    icon: '',
    path: '',
    component: '',
    link: '',
    sort: 0,
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
  await loadTree()
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
      await loadTree()
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
      await loadTree()
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

function menuIcon(menu?: MenuManageItem) {
  return menu?.icon || treeIcon(menu?.type || 'CATEGORY')
}

function treeIcon(type?: MenuNodeType) {
  const icons: Record<MenuNodeType, string> = {
    CATEGORY: 'i-ri:folder-3-line',
    LAYOUT: 'i-ri:layout-2-line',
    MENU: 'i-ri:menu-2-line',
    LINK: 'i-ri:links-line',
    BUTTON: 'i-ri:checkbox-circle-line',
  }
  return icons[type || 'MENU']
}

function buildTreeNodes(items: MenuManageItem[]): TreeNode[] {
  return items
    .filter(item => item.type !== 'BUTTON')
    .map(item => ({
      key: item.code,
      title: item.name,
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

function collectDescendantCodes(item: MenuManageItem): string[] {
  return (item.children || []).flatMap(child => [child.code, ...collectDescendantCodes(child)])
}

async function exportMenus() {
  const res = await apiExcel.exportMenus({ keyword: searchKeyword.value || undefined })
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
    await loadTree()
  })
}
</script>

<template>
  <div class="menu-page">
    <div class="menu-workbench">
      <aside class="menu-sidebar">
        <div class="menu-sidebar__header">
          <h2>菜单列表</h2>
          <div class="menu-toolbar">
            <FaButton v-auth="'system:menu:export'" variant="ghost" size="sm" title="导出菜单" @click="exportMenus">
              <FaIcon name="i-ri:file-excel-2-line" />
            </FaButton>
            <FaButton v-auth="'system:menu:import'" variant="ghost" size="sm" title="下载模板" @click="downloadMenuTemplate">
              <FaIcon name="i-ri:download-2-line" />
            </FaButton>
            <FaButton v-auth="'system:menu:import'" variant="ghost" size="sm" title="导入菜单" @click="importMenus">
              <FaIcon name="i-ri:upload-2-line" />
            </FaButton>
            <FaButton v-auth="'system:menu:create'" variant="ghost" size="sm" title="新增菜单" @click="openCreate()">
              <FaIcon name="i-ri:add-line" />
            </FaButton>
            <FaButton
              v-if="selectedMenu?.status === 'DISABLED'"
              v-auth="'system:menu:edit'"
              variant="ghost"
              size="sm"
              title="启用菜单"
              @click="confirmEnable()"
            >
              <FaIcon name="i-ri:play-circle-line" />
            </FaButton>
            <FaButton
              v-if="selectedMenu?.status === 'ACTIVE'"
              v-auth="'system:menu:delete'"
              variant="ghost"
              size="sm"
              title="停用菜单"
              @click="confirmDisable()"
            >
              <FaIcon name="i-ri:delete-bin-line" />
            </FaButton>
          </div>
        </div>

        <FaInput
          v-model="searchKeyword"
          clearable
          placeholder="请输入菜单名称"
          class="w-full"
          @keydown.enter="loadTree"
          @clear="resetSearch"
        />

        <div class="menu-tree">
          <a-tree
            v-model:selected-keys="selectedKeys"
            :data="treeData"
            :default-expand-all="true"
            block-node
          >
            <template #title="node">
              <div class="menu-tree-node">
                <span class="menu-tree-node__main">
                  <span class="menu-tree-node__icon">
                    <FaIcon :name="menuIcon(node.menu)" />
                  </span>
                  <span>{{ node.title }}</span>
                  <span
                    v-if="node.menu?.source === 'PLUGIN'"
                    class="plugin-source-marker"
                    title="插件菜单"
                    aria-label="插件菜单"
                  />
                </span>
                <FaButton
                  v-if="node.menu && node.menu.type !== 'LINK'"
                  v-auth="'system:menu:create'"
                  variant="ghost"
                  size="sm"
                  title="新增子菜单"
                  @click.stop="openCreate(node.menu)"
                >
                  <FaIcon name="i-ri:add-line" />
                </FaButton>
              </div>
            </template>
          </a-tree>
        </div>
      </aside>

      <main class="menu-content">
        <section class="menu-section">
          <div class="menu-section__header">
            <h2>菜单详情</h2>
            <div class="menu-actions">
              <FaButton v-auth="'system:menu:create'" variant="outline" size="sm" :disabled="!canCreateChild" @click="openCreateFromSelected">
                <FaIcon name="i-ri:add-line" />
                新增子菜单
              </FaButton>
              <FaButton v-auth="'system:menu:edit'" variant="outline" size="sm" :disabled="!selectedMenu" @click="openEdit()">
                <FaIcon name="i-ri:edit-2-line" />
                编辑
              </FaButton>
              <FaButton
                v-if="selectedMenu?.status === 'DISABLED'"
                v-auth="'system:menu:edit'"
                variant="outline"
                size="sm"
                @click="confirmEnable()"
              >
                <FaIcon name="i-ri:play-circle-line" />
                启用
              </FaButton>
              <FaButton
                v-if="selectedMenu?.status === 'ACTIVE'"
                v-auth="'system:menu:delete'"
                variant="destructive"
                size="sm"
                @click="confirmDisable()"
              >
                <FaIcon name="i-ri:delete-bin-line" />
                停用
              </FaButton>
            </div>
          </div>

          <div v-if="selectedMenu" class="detail-grid">
            <div class="detail-cell detail-cell--label">菜单类型</div>
            <div class="detail-cell">
              <FaTag :variant="typeVariant(selectedMenu.type)">
                {{ typeText(selectedMenu.type) }}
              </FaTag>
            </div>
            <div class="detail-cell detail-cell--label">菜单状态</div>
            <div class="detail-cell">
              <FaTag :variant="statusVariant(selectedMenu.status)">
                {{ statusText(selectedMenu.status) }}
              </FaTag>
            </div>
            <div class="detail-cell detail-cell--label">来源</div>
            <div class="detail-cell">
              <FaTag :variant="sourceVariant(selectedMenu.source)">
                {{ sourceText(selectedMenu.source) }}
              </FaTag>
            </div>
            <div class="detail-cell detail-cell--label">运行状态</div>
            <div class="detail-cell">
              <FaTag :variant="runtimeVariant(selectedMenu)">
                {{ runtimeText(selectedMenu) }}
              </FaTag>
            </div>
            <div class="detail-cell detail-cell--label">菜单名称</div>
            <div class="detail-cell detail-cell__name">
              <FaIcon :name="menuIcon(selectedMenu)" />
              <span>{{ selectedMenu.name }}</span>
            </div>
            <div class="detail-cell detail-cell--label">路由地址</div>
            <div class="detail-cell">{{ displayValue(selectedMenu.path) }}</div>
            <div class="detail-cell detail-cell--label">菜单编码</div>
            <div class="detail-cell">{{ selectedMenu.code }}</div>
            <div class="detail-cell detail-cell--label">权限标识</div>
            <div class="detail-cell">{{ displayValue(selectedMenu.permission) }}</div>
            <div class="detail-cell detail-cell--label">展示父级</div>
            <div class="detail-cell">{{ selectedDisplayParentLabel }}</div>
            <div class="detail-cell detail-cell--label">配置父级</div>
            <div class="detail-cell">{{ selectedActualParentLabel }}</div>
            <div class="detail-cell detail-cell--label">组件路径</div>
            <div class="detail-cell">{{ displayValue(selectedMenu.component) }}</div>
            <div class="detail-cell detail-cell--label">菜单显示</div>
            <div class="detail-cell">
              <FaTag :variant="visibleVariant(selectedMenu)">
                {{ visibleText(selectedMenu) }}
              </FaTag>
            </div>
            <div class="detail-cell detail-cell--label">是否外链</div>
            <div class="detail-cell">
              <FaTag :variant="isExternal(selectedMenu) ? 'outline' : 'secondary'">
                {{ displayValue(isExternal(selectedMenu)) }}
              </FaTag>
            </div>
            <div class="detail-cell detail-cell--label">外链地址</div>
            <div class="detail-cell detail-cell--wide">{{ displayValue(selectedMenu.link) }}</div>
            <template v-if="selectedMenu.source === 'PLUGIN'">
              <div class="detail-cell detail-cell--label">插件编码</div>
              <div class="detail-cell">{{ displayValue(selectedMenu.pluginCode) }}</div>
              <div class="detail-cell detail-cell--label">插件模块</div>
              <div class="detail-cell">{{ displayValue(selectedMenu.pluginModuleName) }}</div>
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

          <FaTable
            v-loading="loading"
            row-key="code"
            table-root-class="rounded-lg overflow-hidden"
            table-class="min-w-[720px]"
            border
            stripe
            :columns="buttonColumns"
            :data="buttonRows"
          >
            <template #cell-index="{ row }">
              {{ row.index + 1 }}
            </template>
            <template #cell-status="{ row }">
              <FaTag :variant="statusVariant(row.original.status)">
                {{ statusText(row.original.status) }}
              </FaTag>
            </template>
            <template #cell-operation="{ row }">
              <div class="flex-center gap-2">
                <FaButton v-auth="'system:menu:edit'" variant="ghost" size="sm" title="编辑" @click="openEdit(row.original)">
                  <FaIcon name="i-ri:edit-2-line" />
                </FaButton>
                <FaButton
                  v-if="row.original.status === 'DISABLED'"
                  v-auth="'system:menu:edit'"
                  variant="ghost"
                  size="sm"
                  title="启用"
                  @click="confirmEnable(row.original)"
                >
                  <FaIcon name="i-ri:play-circle-line" />
                </FaButton>
                <FaButton
                  v-if="row.original.status === 'ACTIVE'"
                  v-auth="'system:menu:delete'"
                  variant="ghost"
                  size="sm"
                  title="停用"
                  @click="confirmDisable(row.original)"
                >
                  <FaIcon name="i-ri:delete-bin-line" />
                </FaButton>
              </div>
            </template>
          </FaTable>
        </section>
      </main>
    </div>

    <FaModal v-model="formVisible" :title="editing ? '编辑菜单' : '新增菜单'" show-cancel-button class="sm:max-w-3xl" @confirm="saveForm">
      <a-form :model="form" layout="vertical">
        <a-grid :cols="2" :col-gap="16">
          <a-grid-item>
            <a-form-item label="菜单编码" required>
              <FaInput
                v-model="form.code"
                :disabled="!!editing"
                placeholder="system:menu"
                class="w-full"
              />
            </a-form-item>
            <div v-if="isPluginEditing" class="form-help-text">
              插件菜单编码用于恢复插件注册关系，编辑时仅支持查看。
            </div>
          </a-grid-item>
          <a-grid-item>
            <a-form-item label="菜单名称" required>
              <FaInput v-model="form.name" class="w-full" />
            </a-form-item>
          </a-grid-item>
          <a-grid-item>
            <a-form-item label="类型" required>
              <FaSelect v-model="form.type" :options="typeOptions" class="w-full" />
            </a-form-item>
          </a-grid-item>
          <a-grid-item>
            <a-form-item label="上级菜单">
              <FaSelect v-model="form.parentCode" :options="parentOptions" placeholder="根节点" class="w-full" />
            </a-form-item>
          </a-grid-item>
          <a-grid-item>
            <a-form-item label="模块">
              <FaInput v-model="form.module" :placeholder="DEFAULT_MODULE" class="w-full" />
            </a-form-item>
          </a-grid-item>
          <a-grid-item>
            <a-form-item label="排序">
              <FaNumberField v-model="sortValue" :min="0" class="w-full" />
            </a-form-item>
          </a-grid-item>
          <a-grid-item v-if="form.type !== 'BUTTON'">
            <a-form-item label="图标">
              <FaInput v-model="form.icon" placeholder="i-ri:settings-3-line" class="w-full" />
            </a-form-item>
          </a-grid-item>
          <a-grid-item v-if="['MENU', 'LAYOUT', 'LINK'].includes(form.type)">
            <a-form-item label="路由路径">
              <FaInput v-model="form.path" :placeholder="form.type === 'LINK' ? '/external/docs' : '/system/menu'" class="w-full" />
            </a-form-item>
          </a-grid-item>
          <a-grid-item v-if="form.type === 'MENU'">
            <a-form-item label="组件路径">
              <FaInput v-model="form.component" placeholder="system/menu/index.vue" class="w-full" />
            </a-form-item>
          </a-grid-item>
          <a-grid-item v-if="form.type === 'LAYOUT'">
            <a-form-item label="组件路径">
              <FaInput model-value="Layout" disabled class="w-full" />
            </a-form-item>
          </a-grid-item>
          <a-grid-item v-if="form.type === 'LINK'">
            <a-form-item label="外链地址">
              <FaInput v-model="form.link" placeholder="https://www.yudream.online" class="w-full" />
            </a-form-item>
          </a-grid-item>
          <a-grid-item v-if="!['CATEGORY', 'LAYOUT'].includes(form.type)">
            <a-form-item label="权限标识">
              <FaInput v-model="form.permission" placeholder="为空时使用菜单编码" class="w-full" />
            </a-form-item>
          </a-grid-item>
          <a-grid-item v-if="editing">
            <a-form-item label="状态">
              <FaSelect v-model="form.status" :options="statusOptions" class="w-full" />
            </a-form-item>
          </a-grid-item>
        </a-grid>
      </a-form>
    </FaModal>
  </div>
</template>

<style scoped>
.menu-workbench {
  display: grid;
  grid-template-columns: minmax(300px, 340px) minmax(0, 1fr);
  gap: 12px;
}

.menu-page {
  min-height: calc(100vh - 104px);
  padding: 16px;
  background: var(--color-fill-1);
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
  max-height: calc(100vh - 136px);
}

.menu-sidebar__header,
.menu-section__header,
.menu-actions,
.menu-toolbar,
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

.menu-toolbar,
.menu-actions {
  gap: 8px;
  flex-wrap: wrap;
  justify-content: flex-end;
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

:deep(.arco-tree-node-selected .arco-tree-node-title) {
  color: rgb(var(--primary-6));
  background: rgb(var(--primary-1));
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
  .menu-page {
    padding: 12px;
  }

  .menu-workbench {
    grid-template-columns: 1fr;
  }

  .menu-sidebar {
    min-height: 360px;
    max-height: 460px;
  }
}

@media (max-width: 768px) {
  .menu-actions {
    justify-content: flex-start;
  }

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
