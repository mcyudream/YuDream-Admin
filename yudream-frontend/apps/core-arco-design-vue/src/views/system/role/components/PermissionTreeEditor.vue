<script setup lang="ts">
import type { YdTreeNode } from '@yudream/components'
import type { PermissionEditorNode } from './permission-tree-editor'
import type { PermissionItem } from '@/api/modules/system-role'
import { mergeVisiblePermissions } from '@/utils/permission-catalog'
import { createPermissionTreeCatalog, permissionEditorCheckedKeys, permissionEditorDefaultPageSize, permissionEditorMatches, permissionEditorPage, permissionEditorPageSizes } from './permission-tree-editor'

const props = defineProps<{
  permissions: PermissionItem[]
  disabled?: boolean
  loading?: boolean
  implicitWildcard?: boolean
}>()
const model = defineModel<string[]>({ required: true })
const keyword = ref('')
const appliedKeyword = ref('')
const page = ref(1)
const pageSize = ref(permissionEditorDefaultPageSize)
const normalExpanded = ref<string[]>([])
const searchExpanded = ref<string[]>([])
let initializedExpansion = false

const catalog = computed(() => createPermissionTreeCatalog(props.permissions))
const matchingCodes = computed(() => permissionEditorMatches(catalog.value, appliedKeyword.value))
const view = computed(() => permissionEditorPage(catalog.value, matchingCodes.value, page.value, pageSize.value))
const visibleCodes = computed(() => new Set(view.value.leafCodes))
const visibleBranches = computed(() => new Set(view.value.branchKeys))
const activeCodes = computed(() => new Set(catalog.value.leafCodes))
const selectedCount = computed(() => model.value.filter(code => activeCodes.value.has(code)).length)
const retainedCount = computed(() => model.value.filter(code => !activeCodes.value.has(code)).length)
const filterPending = computed(() => keyword.value.trim() !== appliedKeyword.value)
const locked = computed(() => props.disabled || props.loading)
const displayData = computed(() => locked.value ? view.value.data.map(node => ({ ...node, disabled: true })) : view.value.data)
const checkedKeys = computed({
  get: () => permissionEditorCheckedKeys(view.value, model.value),
  set: (keys: string[]) => {
    if (!locked.value) {
      model.value = mergeVisiblePermissions(model.value, keys, visibleCodes.value)
    }
  },
})
const expandedKeys = computed({
  get: () => (appliedKeyword.value ? searchExpanded.value : normalExpanded.value).filter(key => visibleBranches.value.has(key)),
  set: (keys: string[]) => {
    if (appliedKeyword.value) {
      searchExpanded.value = keys
    }
    else {
      normalExpanded.value = [...normalExpanded.value.filter(key => !visibleBranches.value.has(key)), ...keys]
    }
  },
})

watch(keyword, (value, _previous, onCleanup) => {
  if (!value.trim()) {
    appliedKeyword.value = ''
    return
  }
  const timer = setTimeout(() => appliedKeyword.value = keyword.value.trim(), 180)
  onCleanup(() => clearTimeout(timer))
})
watch([appliedKeyword, pageSize], () => page.value = 1)
watch(catalog, (current) => {
  normalExpanded.value = normalExpanded.value.filter(key => current.nodeIndex.has(key))
  if (!initializedExpansion && current.data.length) {
    normalExpanded.value = current.data.map(node => node.key)
    initializedExpansion = true
  }
}, { immediate: true })
watch(view, (current) => {
  if (page.value !== current.page) {
    page.value = current.page
  }
  if (appliedKeyword.value) {
    searchExpanded.value = current.branchKeys
  }
}, { immediate: true })

function selectAll() {
  if (!locked.value) {
    model.value = [...new Set([...model.value, ...catalog.value.leafCodes])]
  }
}

function clearAll() {
  if (!locked.value) {
    model.value = []
  }
}

function selectPage() {
  if (!locked.value) {
    model.value = [...new Set([...model.value, ...view.value.leafCodes])]
  }
}

function clearPage() {
  if (!locked.value) {
    model.value = mergeVisiblePermissions(model.value, [], visibleCodes.value)
  }
}

function expandAll() {
  expandedKeys.value = view.value.branchKeys
}

function collapseAll() {
  expandedKeys.value = []
}

function reset() {
  keyword.value = ''
  appliedKeyword.value = ''
  page.value = 1
  normalExpanded.value = catalog.value.data.map(node => node.key)
  searchExpanded.value = []
}

function nodeVariant(node: YdTreeNode | undefined) {
  return (node as PermissionEditorNode | undefined)?.variant
}

function nodeCode(node: YdTreeNode | undefined) {
  return (node as PermissionEditorNode | undefined)?.code
}

defineExpose({ reset, expandAll, collapseAll })
</script>

<template>
  <section class="permission-tree-editor" aria-label="角色权限分配" :aria-busy="loading">
    <div class="permission-editor-toolbar">
      <YdInput v-model="keyword" clearable placeholder="搜索完整权限目录：名称 / 编码 / 资源 / 分类" aria-label="搜索完整权限目录" class="flex-1 min-w-48" />
      <span class="permission-editor-caption">已选 {{ selectedCount }} / {{ catalog.leafCodes.length }} 项</span>
      <YdButton size="sm" variant="outline" :disabled="locked || !view.leafCodes.length" title="只授予当前页权限，保留其他页及历史配置" @click="selectPage">
        全选本页
      </YdButton>
      <YdButton size="sm" variant="outline" :disabled="locked || !view.leafCodes.length" title="只撤销当前页权限，保留其他页及历史配置" @click="clearPage">
        取消本页
      </YdButton>
      <YdButton size="sm" variant="outline" :disabled="locked" title="授予完整目录的全部 ACTIVE 权限，跨搜索和分页，保留历史或暂失效配置" @click="selectAll">
        全选全部权限
      </YdButton>
      <YdButton size="sm" variant="outline" :disabled="locked" title="明确清空全部配置，包含通配、历史和暂失效授权" @click="clearAll">
        清空全部配置
      </YdButton>
      <YdButton size="sm" variant="outline" :disabled="!view.data.length" @click="expandAll">
        展开本页
      </YdButton>
      <YdButton size="sm" variant="outline" :disabled="!view.data.length" @click="collapseAll">
        收起
      </YdButton>
    </div>
    <p class="permission-editor-caption" role="status" aria-live="polite">
      本页 {{ view.from }}–{{ view.to }} / 匹配 {{ view.total }} 项，目录共登记 {{ permissions.length }} 项，其中 {{ catalog.leafCodes.length }} 项可用。
      {{ filterPending ? '正在筛选完整目录…' : appliedKeyword ? '搜索覆盖完整目录，本页命中路径已展开；清除搜索回到第一页。' : '默认只展开业务域，展开本页仍使用虚拟列表。' }}
      树中的分组勾选只作用于本页，分组计数也是本页数量；其他页和搜索隐藏的授权继续保留。
      {{ retainedCount ? `另保留 ${retainedCount} 项历史、通配或暂失效配置。` : '' }}
    </p>
    <YdAlert
      v-if="implicitWildcard || model.includes('*')"
      :title="implicitWildcard ? '系统超级管理员自动授权' : '配置含通配授权'"
      :description="implicitWildcard ? '系统超级管理员自动获得通配权限，修改单项配置不会限制其实际权限。' : '此角色通过 * 获得全部权限，单项勾选无法限制通配。清空会明确移除通配及其他配置，再按需重新授予权限。'"
    />
    <div class="permission-editor-tree">
      <YdLoading v-if="loading && !catalog.leafCodes.length" :size="24" text="正在读取权限目录…" />
      <YdTree
        v-else
        v-model:checked-keys="checkedKeys"
        v-model:expanded-keys="expandedKeys"
        :data="displayData"
        :selectable="false"
        :virtual-list="view.virtualList"
        :default-expand-all="view.defaultExpandAll"
        checkable
      >
        <template #default="{ node }">
          <YdTag v-if="nodeVariant(node)" :variant="nodeVariant(node)">
            {{ node?.label }}
          </YdTag>
          <span v-else class="permission-editor-node" :title="nodeCode(node) ? `${node?.label} · ${nodeCode(node)}` : node?.label">
            <span :class="{ 'font-semibold': !nodeCode(node) }" class="permission-editor-name">{{ node?.label }}</span>
            <span v-if="nodeCode(node)" class="permission-editor-code">{{ nodeCode(node) }}</span>
          </span>
        </template>
        <template #empty>
          <YdEmpty :title="appliedKeyword ? '没有匹配权限' : '暂无可用权限'" :description="appliedKeyword ? '调整搜索内容，原有授权继续保留。' : '权限目录没有已启用的登记项，原有授权继续保留。'" />
        </template>
      </YdTree>
    </div>
    <YdPagination
      v-model:page="page"
      v-model:size="pageSize"
      :total="matchingCodes.length"
      :sizes="permissionEditorPageSizes"
      layout="total, sizes, ->, pager"
      :text-templates="{ total: total => `匹配 ${total} 项`, sizes: size => `${size} 项/页` }"
    />
  </section>
</template>

<style scoped>
.permission-tree-editor {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-width: 0;
}

.permission-editor-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

.permission-editor-caption {
  font-size: 12px;
  line-height: 1.7;
  color: var(--color-text-3);
}

.permission-editor-tree {
  min-width: 0;
  padding: 4px 8px;
  border: 1px solid var(--color-border-2);
  border-radius: 6px;
}

.permission-editor-node {
  display: inline-flex;
  gap: 8px;
  align-items: center;
  max-width: 100%;
  white-space: nowrap;
}

.permission-editor-name {
  overflow: hidden;
  text-overflow: ellipsis;
}

.permission-editor-code {
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 12px;
  color: var(--color-text-3);
}
</style>
