<script setup lang="ts">
import type { AgentSkill } from '@/api/modules/platform-agent-skill'
import type { TableColumn } from '@yudream/components'
import type { YdFilePickerInstance } from '@yudream/components'
import apiSkill from '@/api/modules/platform-agent-skill'
import { useMediaQuery } from '@vueuse/core'
import { computed, shallowRef } from 'vue'

const toast = useYdToast()
const modal = useYdModal()
const loading = ref(false)
const visible = ref(false)
const editing = ref<AgentSkill | null>(null)
const rows = ref<AgentSkill[]>([])
const selectedRows = shallowRef<AgentSkill[]>([])
const pagination = reactive({ page: 1, size: 10, total: 0 })
const isCompact = useMediaQuery('(max-width: 640px)')
const paginationLayout = computed(() => isCompact.value ? 'total, pager' : 'total, sizes, ->, pager, jumper')
const keyword = ref('')
const resourcesText = ref('{}')
const uploadInput = ref<YdFilePickerInstance | null>(null)
const form = reactive({ name: '', description: '', content: '', enabled: true })

const columns = computed<TableColumn<AgentSkill>[]>(() => [
  { type: 'selection', fixed: 'left', width: 44 },
  { accessorKey: 'name', header: '名称' },
  { accessorKey: 'description', header: '描述' },
  { id: 'builtin', header: '来源', width: 90 },
  { id: 'enabled', header: '状态', width: 90 },
  { accessorKey: 'updateTime', header: '更新时间', width: 170 },
  { id: 'operation', header: '操作', width: 200 },
])

onMounted(load)
async function load() {
  loading.value = true
  try {
    const res = await apiSkill.page({ ...pagination, keyword: keyword.value || undefined })
    rows.value = res.data.records
    pagination.total = Number(res.data.total || 0)
    selectedRows.value = []
  }
  finally { loading.value = false }
}
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
    <YdPageHeader title="平台技能库" class="mb-0">
      <YdButton v-auth="'platform:agent-skill:create'" @click="create">
        <YdIcon name="morph:plus" /> 新建技能
      </YdButton>
    </YdPageHeader>
    <YdPageMain>
      <section class="skill-section">
        <YdLoadingOverlay :loading="loading"><YdResponsiveTable
          
          row-key="id"
          :data="rows"
          :columns="columns"
          selectable
          multiple
          @selection-change="onSelectionChange"
        >
          <template #toolbar>
            <div class="toolbar">
              <YdInput v-model="keyword" class="w-72" clearable placeholder="名称 / 描述" @keydown.enter="load" />
              <div class="flex-1" />
              <template v-if="selectedRows.length">
                <span class="selected-hint">已选 {{ selectedRows.length }} 项</span>
                <YdButton v-auth="'platform:agent-skill:edit'" size="sm" variant="outline" @click="batchSetEnabled(true)">
                  批量启用
                </YdButton>
                <YdButton v-auth="'platform:agent-skill:edit'" size="sm" variant="outline" @click="batchSetEnabled(false)">
                  批量停用
                </YdButton>
                <YdButton v-auth="'platform:agent-skill:delete'" size="sm" variant="destructive" @click="batchRemove">
                  批量删除
                </YdButton>
              </template>
              <YdButton v-auth="'platform:agent-skill:create'" size="sm" variant="outline" @click="pickZip">
                <YdIcon name="morph:upload" /> 上传压缩包
              </YdButton>
            </div>
          </template>
          <template #cell-builtin="{ row }">
            <YdTag :variant="row.original.builtin ? 'secondary' : 'default'">
              {{ row.original.builtin ? '内置' : '自定义' }}
            </YdTag>
          </template>
          <template #cell-enabled="{ row }">
            <YdTag :variant="row.original.enabled ? 'default' : 'secondary'">
              {{ row.original.enabled ? '启用' : '停用' }}
            </YdTag>
          </template>
          <template #cell-updateTime="{ row }">
            {{ formatTime(row.original.updateTime) }}
          </template>
          <template #cell-operation="{ row }">
            <div class="flex gap-2 justify-center">
              <YdButton v-auth="'platform:agent-skill:edit'" size="sm" variant="outline" @click="confirmToggle(row.original)">
                {{ row.original.enabled ? '停用' : '启用' }}
              </YdButton>
              <YdButton v-auth="'platform:agent-skill:edit'" size="sm" variant="outline" @click="edit(row.original)">
                编辑
              </YdButton>
              <YdButton v-if="!row.original.builtin" v-auth="'platform:agent-skill:delete'" size="sm" variant="destructive" @click="remove(row.original)">
                删除
              </YdButton>
            </div>
          </template>
          <template #card="{ row }">
            <YdCard class="w-full">
              <div class="flex flex-col gap-3">
                <div class="flex items-center justify-between gap-2">
                  <span class="min-w-0 break-words text-base font-semibold">{{ row.name }}</span>
                  <YdTag :variant="row.enabled ? 'default' : 'secondary'">
                    {{ row.enabled ? '启用' : '停用' }}
                  </YdTag>
                </div>
                <div class="flex flex-col gap-1 text-sm">
                  <div class="flex gap-2">
                    <span class="shrink-0 text-secondary-foreground/60">描述</span>
                    <span class="break-all">{{ row.description }}</span>
                  </div>
                  <div class="flex gap-2">
                    <span class="shrink-0 text-secondary-foreground/60">来源</span>
                    <span>{{ row.builtin ? '内置' : '自定义' }}</span>
                  </div>
                  <div class="flex gap-2">
                    <span class="shrink-0 text-secondary-foreground/60">更新</span>
                    <span>{{ formatTime(row.updateTime) }}</span>
                  </div>
                </div>
                <div class="flex flex-wrap gap-2 border-t pt-3">
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
        </YdResponsiveTable></YdLoadingOverlay>
        <YdPagination v-model:page="pagination.page" v-model:size="pagination.size" :total="pagination.total" :layout="paginationLayout" class="mt-3" @page-change="load" @size-change="load" />
      </section>
    </YdPageMain>
    <YdFilePicker ref="uploadInput" accept=".zip" label="选择技能压缩包" @select="onZipChange"><template #default /></YdFilePicker>
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
          <YdTextarea v-model="resourcesText" class="w-full" input-class="font-mono" :autosize="{ minRows: 4, maxRows: 10 }" placeholder='{ "references/style-guide.md": "内容…" }' />
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
.skill-section { display: grid; gap: 16px; }
.toolbar { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; width: 100%; }
.selected-hint { color: var(--color-text-3); font-size: 12px; }
.skill-form { display: grid; gap: 16px; }
.form-field { display: grid; gap: 7px; color: var(--color-text-2); font-size: 12px; }
.form-field.required > span::after { margin-left: 3px; color: rgb(var(--danger-6)); content: '*'; }
.form-field small { color: var(--color-text-3); font-size: 10px; line-height: 1.5; }
.check-line { display: flex; align-items: start; gap: 8px; padding: 10px; border: 1px solid var(--color-border-2); border-radius: 6px; font-size: 12px; cursor: pointer; }
.check-line span { display: grid; gap: 2px; }
.check-line b { color: var(--color-text-1); font-weight: 500; }
.check-line small { color: var(--color-text-3); font-size: 10px; }
</style>
