<script setup lang="ts">
import type { ActionMenuItem, StatusColumn, TableColumn, YdFilePickerInstance, YdTablePageAction } from '@yudream/components'
import type { AgentSkill } from '@/api/modules/platform-agent-skill'

import { useTablePage } from '@yudream/components'
import apiSkill from '@/api/modules/platform-agent-skill'
import { fetchSearchConfig } from '@/api/modules/system-search-config'

const toast = useYdToast()
const modal = useYdModal()
const { auth } = useAppAuth()

const visible = ref(false)
const editing = ref<AgentSkill | null>(null)
const selectedRows = shallowRef<AgentSkill[]>([])
const resourcesText = ref('{}')
const uploadInput = ref<YdFilePickerInstance | null>(null)
const form = reactive({ name: '', description: '', content: '', enabled: true })

/** 列表：搜索条件来自 AgentSkillPageQuery 的 @Searchable 注解，分页由后端切片 */
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
} = useTablePage<AgentSkill>({
  queryClass: 'online.yudream.base.application.platform.agent.query.AgentSkillPageQuery',
  loadApi: async (query) => {
    const res = await apiSkill.page({
      page: query.page,
      size: query.size,
      keyword: query.keyword || undefined,
    })
    selectedRows.value = []
    return {
      records: res.data.records,
      total: Number(res.data.total || 0),
      page: res.data.page,
      size: res.data.size,
    }
  },
  loadSearchConfig: queryClass => fetchSearchConfig(queryClass).then(res => res.data),
})

const columns = computed<TableColumn<AgentSkill>[]>(() => [
  // 行选择必须显式给选择列：表格的行可选性依赖这一列存在（多选再加 multiple）
  { type: 'selection', fixed: 'left', width: 44 },
  { id: 'name', header: '名称', minWidth: 180 },
  { accessorKey: 'description', header: '描述', minWidth: 280 },
  { id: 'builtin', header: '来源', width: 100, align: 'center' },
  // 审计时间字段：可勾选但默认不展示（列显隐菜单里「只看常用字段 / 显示全部字段」切换）
  { id: 'updateTime', header: '更新时间', width: 180, defaultHidden: true },
])

const statusColumn: StatusColumn<AgentSkill> = {
  statusKey: 'enabled',
  header: '状态',
  width: 90,
  statusMap: {
    true: { label: '启用', variant: 'default', tone: 'success' },
    false: { label: '停用', variant: 'secondary', tone: 'neutral' },
  },
}

/** 行操作：编辑/启停/删除统一走表格内置操作列（移动端卡片同一套） */
const rowActions: ActionMenuItem<AgentSkill>[] = [
  { label: '编辑', icon: 'morph:pencil', visible: () => auth('platform:agent-skill:edit'), onClick: edit },
  { label: '启用', icon: 'morph:circle-check', visible: row => !row.enabled && auth('platform:agent-skill:edit'), onClick: row => confirmToggle(row) },
  { label: '停用', icon: 'morph:ban', visible: row => row.enabled && auth('platform:agent-skill:edit'), onClick: row => confirmToggle(row) },
  { label: '删除', icon: 'morph:trash-2', danger: true, visible: row => !row.builtin && auth('platform:agent-skill:delete'), onClick: remove },
]

/** 页头操作：新建常用直接显示；上传与批量动作收进「更多」，删除危险标红 */
const pageActions = computed<YdTablePageAction[]>(() => [
  ...(auth('platform:agent-skill:create')
    ? [{ label: '新建技能', icon: 'morph:plus', onClick: create }]
    : []),
  ...(auth('platform:agent-skill:create')
    ? [{ label: '上传压缩包', icon: 'morph:upload', variant: 'outline' as const, autoFold: true, onClick: pickZip }]
    : []),
  ...(selectedRows.value.length && auth('platform:agent-skill:edit')
    ? [
        { label: '批量启用', icon: 'morph:circle-check', variant: 'outline' as const, autoFold: true, onClick: () => batchSetEnabled(true) },
        { label: '批量停用', icon: 'morph:ban', variant: 'outline' as const, autoFold: true, onClick: () => batchSetEnabled(false) },
      ]
    : []),
  ...(selectedRows.value.length && auth('platform:agent-skill:delete')
    ? [{ label: '批量删除', icon: 'morph:trash-2', variant: 'destructive' as const, danger: true, onClick: batchRemove }]
    : []),
])

function onSelectionChange(selected: AgentSkill[]) {
  selectedRows.value = selected
}

function resetForm() {
  Object.assign(form, { name: '', description: '', content: '', enabled: true })
  resourcesText.value = '{}'
}

function create() {
  editing.value = null
  resetForm()
  visible.value = true
}

function edit(row: AgentSkill) {
  editing.value = row
  Object.assign(form, { name: row.name, description: row.description, content: row.content, enabled: row.enabled })
  resourcesText.value = JSON.stringify(row.resources ?? {}, null, 2)
  visible.value = true
}

function parseResources(): Record<string, string> | null {
  const text = resourcesText.value.trim()
  if (!text) {
    return {}
  }
  try {
    const parsed = JSON.parse(text)
    if (typeof parsed !== 'object' || parsed === null || Array.isArray(parsed)) {
      return null
    }
    const result: Record<string, string> = {}
    for (const [key, value] of Object.entries(parsed)) {
      if (typeof value !== 'string') {
        return null
      }
      result[key] = value
    }
    return result
  }
  catch {
    return null
  }
}

async function save() {
  if (!form.name.trim() || !form.description.trim() || !form.content.trim()) {
    toast.error('请填写技能名称、描述和内容')
    return
  }
  const resources = parseResources()
  if (resources === null) {
    toast.error('资源必须是 { "相对路径": "文本内容" } 形式的 JSON')
    return
  }
  const payload = { ...form, resources }
  if (editing.value) {
    await apiSkill.create({ ...payload, id: editing.value.id })
  }
  else {
    await apiSkill.create(payload)
  }
  toast.success('技能已保存')
  visible.value = false
  await load()
}

async function setEnabled(row: AgentSkill, enabling: boolean) {
  await apiSkill.setEnabled(row.id, enabling)
  toast.success('已保存')
  await load()
}

function confirmToggle(row: AgentSkill) {
  const enabling = !row.enabled
  modal.confirm({
    title: enabling ? '确认启用' : '确认停用',
    content: `确认${enabling ? '启用' : '停用'}技能「${row.name}」吗？${enabling ? '启用后所有 Agent 可在技能目录中发现并加载它。' : '停用后 Agent 不再看到该技能。'}`,
    onConfirm: () => setEnabled(row, enabling),
  })
}

function remove(row: AgentSkill) {
  modal.confirm({
    title: '删除技能',
    content: `确定删除「${row.name}」吗？删除后所有 Agent 不再提供该技能。`,
    onConfirm: async () => {
      await apiSkill.delete(row.id)
      toast.success('已删除')
      await load()
    },
  })
}

async function batchSetEnabled(enabling: boolean) {
  const targets = selectedRows.value
  if (!targets.length) {
    toast.warning('请先勾选技能')
    return
  }
  modal.confirm({
    title: enabling ? '批量启用' : '批量停用',
    content: `确认${enabling ? '启用' : '停用'}选中的 ${targets.length} 个技能吗？`,
    onConfirm: async () => {
      for (const row of targets) {
        await apiSkill.setEnabled(row.id, enabling)
      }
      toast.success(`已${enabling ? '启用' : '停用'} ${targets.length} 个技能`)
      await load()
    },
  })
}

function batchRemove() {
  const targets = selectedRows.value.filter(row => !row.builtin)
  if (!selectedRows.value.length) {
    toast.warning('请先勾选技能')
    return
  }
  if (!targets.length) {
    toast.warning('勾选的都是内置技能，不可删除')
    return
  }
  modal.confirm({
    title: '批量删除',
    content: `确定删除选中的 ${targets.length} 个技能吗？内置技能已自动跳过。`,
    onConfirm: async () => {
      for (const row of targets) {
        await apiSkill.delete(row.id)
      }
      toast.success(`已删除 ${targets.length} 个技能`)
      await load()
    },
  })
}

function pickZip() {
  uploadInput.value?.open()
}

async function onZipChange(files: File[]) {
  const file = files[0]
  uploadInput.value?.reset()
  if (!file) {
    return
  }
  if (!file.name.toLowerCase().endsWith('.zip')) {
    toast.error('请选择 .zip 压缩包')
    return
  }
  const res = await apiSkill.uploadZip(file)
  toast.success(`技能「${res.data.name}」已导入${res.data.enabled ? '' : '（当前为停用状态）'}`)
  await load()
}

function formatTime(value?: string) {
  return value ? value.replace('T', ' ').slice(0, 19) : '-'
}
</script>

<template>
  <div>
    <YdTablePageLayout
      title="平台技能库"
      :actions="pageActions"
      :actions-threshold="3"
    >
      <!-- 选中数量是只读情报，按规范放页头而不占操作位 -->
      <template #page-title>
        <span v-if="selectedRows.length" class="text-sm text-muted-foreground">已选 {{ selectedRows.length }} 项</span>
      </template>

      <template #search="{ fold }">
        <YdDynamicSearchForm
          v-model="searchQuery"
          :config="searchConfig"
          :fold="fold"
          @search="search"
          @reset="resetSearch"
        />
      </template>

      <template #table>
        <YdLoadingOverlay :loading="loading">
          <YdResponsiveEnhancedTable
            row-key="id"
            table-root-class="rounded-lg overflow-hidden"
            table-class="min-w-[1080px]"
            column-visibility
            stripe
            selectable
            multiple
            :status-column="statusColumn"
            :actions="rowActions"
            :columns="columns"
            :data="rows"
            @selection-change="onSelectionChange"
          >
            <template #cell-name="{ row }">
              <span class="font-medium break-all">{{ row.original.name }}</span>
            </template>
            <template #cell-builtin="{ row }">
              <YdTag :variant="row.original.builtin ? 'secondary' : 'default'">
                {{ row.original.builtin ? '内置' : '自定义' }}
              </YdTag>
            </template>
            <template #cell-updateTime="{ row }">
              <span v-if="row.original.updateTime">{{ formatTime(row.original.updateTime) }}</span>
              <span v-else class="text-muted-foreground/40">-</span>
            </template>
            <template #card="{ row }">
              <YdCard class="w-full">
                <div class="flex flex-col gap-3">
                  <div class="flex gap-2 items-center justify-between">
                    <span class="text-base font-semibold min-w-0 break-words">{{ row.name }}</span>
                    <YdTag :variant="row.enabled ? 'default' : 'secondary'">
                      {{ row.enabled ? '启用' : '停用' }}
                    </YdTag>
                  </div>
                  <div class="text-sm flex flex-col gap-1">
                    <div class="flex gap-2 min-w-0">
                      <span class="text-secondary-foreground/60 shrink-0">描述</span>
                      <span class="break-all">{{ row.description }}</span>
                    </div>
                    <div class="flex gap-2">
                      <span class="text-secondary-foreground/60 shrink-0">来源</span>
                      <span>{{ row.builtin ? '内置' : '自定义' }}</span>
                    </div>
                    <div class="flex gap-2">
                      <span class="text-secondary-foreground/60 shrink-0">更新</span>
                      <span>{{ formatTime(row.updateTime) }}</span>
                    </div>
                  </div>
                  <div class="pt-3 border-t flex flex-wrap gap-2">
                    <YdButton v-auth="'platform:agent-skill:edit'" size="sm" variant="outline" @click="confirmToggle(row)">
                      {{ row.enabled ? '停用' : '启用' }}
                    </YdButton>
                    <YdButton v-auth="'platform:agent-skill:edit'" size="sm" variant="outline" @click="edit(row)">
                      编辑
                    </YdButton>
                    <YdButton v-if="!row.builtin" v-auth="'platform:agent-skill:delete'" size="sm" variant="destructive" @click="remove(row)">
                      删除
                    </YdButton>
                  </div>
                </div>
              </YdCard>
            </template>
          </YdResponsiveEnhancedTable>
        </YdLoadingOverlay>
      </template>

      <template #footer>
        <YdPagination
          v-model:page="pagination.page"
          v-model:size="pagination.size"
          :total="pagination.total"
          @page-change="onPageChange"
          @size-change="onSizeChange"
        />
      </template>
    </YdTablePageLayout>
    <YdFilePicker ref="uploadInput" accept=".zip" label="选择技能压缩包" @select="onZipChange">
      <template #default />
    </YdFilePicker>
    <YdModal v-model="visible" :title="editing ? '编辑技能' : '新建技能'" class="sm:max-w-3xl">
      <div class="skill-form">
        <div class="gap-4 grid sm:grid-cols-2">
          <label class="form-field required">
            <span>技能名称</span>
            <YdInput v-model="form.name" class="w-full" maxlength="60" placeholder="例如：code-reviewer" />
          </label>
          <label class="form-field">
            <span>启用状态</span>
            <YdCheckbox v-model="form.enabled" class="check-line"><span><b>启用技能</b><small>停用后 Agent 不再看到该技能</small></span></YdCheckbox>
          </label>
        </div>
        <label class="form-field required">
          <span>技能描述</span>
          <YdTextarea v-model="form.description" class="w-full" :autosize="{ minRows: 2, maxRows: 4 }" placeholder="写清触发场景，例如：当用户需要代码评审、风格反馈或 PR 审核时使用" />
          <small>模型根据这段描述决定是否加载技能，务必写具体触发场景而非笼统功能</small>
        </label>
        <label class="form-field required">
          <span>技能内容（Markdown）</span>
          <YdTextarea v-model="form.content" class="w-full" input-class="font-mono" :autosize="{ minRows: 10, maxRows: 20 }" placeholder="# 技能标题&#10;&#10;执行步骤…" />
          <small>建议控制在 2k tokens 内，长篇资料放入 references 资源并按相对路径引用</small>
        </label>
        <label class="form-field">
          <span>引用资源（JSON，可选）</span>
          <YdTextarea v-model="resourcesText" class="w-full" input-class="font-mono" :autosize="{ minRows: 4, maxRows: 10 }" placeholder="{ &quot;references/style-guide.md&quot;: &quot;内容…&quot; }" />
          <small>相对路径 → 文本内容；技能内容中以相对路径引用（如 scripts/run.py）</small>
        </label>
      </div>
      <template #footer>
        <YdButton variant="outline" @click="visible = false">
          取消
        </YdButton><YdButton @click="save">
          保存技能
        </YdButton>
      </template>
    </YdModal>
  </div>
</template>

<style scoped>
.skill-form { display: grid; gap: 16px; }
.form-field { display: grid; gap: 7px; color: var(--color-text-2); font-size: 12px; }
.form-field.required > span::after { margin-left: 3px; color: rgb(var(--danger-6)); content: '*'; }
.form-field small { color: var(--color-text-3); font-size: 10px; line-height: 1.5; }
.check-line { display: flex; align-items: start; gap: 8px; padding: 10px; border: 1px solid var(--color-border-2); border-radius: 6px; font-size: 12px; cursor: pointer; }
.check-line span { display: grid; gap: 2px; }
.check-line b { color: var(--color-text-1); font-weight: 500; }
.check-line small { color: var(--color-text-3); font-size: 10px; }
</style>
