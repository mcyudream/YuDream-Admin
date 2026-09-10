<script setup lang="ts">
import type { TableColumn } from '@yudream/components'
import type { CmsBlock, CmsBlockKind, CmsBlockPayload } from '@/api/modules/platform-cms'
import apiCms from '@/api/modules/platform-cms'

const props = defineProps<{
  visible: boolean
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
  saved: []
}>()

const toast = useFaToast()
const modal = useFaModal()

const loading = ref(false)
const saving = ref(false)
const blocks = ref<CmsBlock[]>([])
const categories = ref<string[]>([])
const keyword = ref('')
const categoryFilter = ref('')
const pagination = reactive({ page: 1, size: 20, total: 0 })
const formVisible = ref(false)
const editingBlock = ref<CmsBlock | null>(null)
const form = reactive<CmsBlockPayload>({
  code: '',
  name: '',
  description: '',
  category: '',
  kind: 'ATOMIC',
  htmlContent: '',
  cssContent: '',
  jsContent: '',
  enabled: true,
})

const kindOptions: { label: string, value: CmsBlockKind }[] = [
  { label: '原子', value: 'ATOMIC' },
  { label: '预制', value: 'PRESET' },
]

const columns: TableColumn<CmsBlock>[] = [
  { accessorKey: 'code', header: '编码', width: 160 },
  { accessorKey: 'name', header: '名称', width: 180 },
  { accessorKey: 'category', header: '分类', width: 120 },
  { id: 'kind', header: '类型', width: 90, align: 'center' },
  { id: 'enabled', header: '启用状态', width: 100, align: 'center' },
  { id: 'operation', header: '操作', width: 180, align: 'center' },
]

const categoryOptions = computed(() => [
  { label: '全部分类', value: '' },
  ...categories.value.map(item => ({ label: item, value: item })),
])

watch(() => props.visible, (visible) => {
  if (visible) {
    void loadBlocks()
    void loadCategories()
  }
})

watch([() => keyword.value, () => categoryFilter.value], () => {
  pagination.page = 1
  void loadBlocks()
})

async function loadBlocks() {
  loading.value = true
  try {
    const res = await apiCms.blockList({
      page: pagination.page,
      size: pagination.size,
      keyword: keyword.value || undefined,
      category: categoryFilter.value || undefined,
      kind: undefined,
    })
    blocks.value = res.data.records
    pagination.total = res.data.total
  }
  catch (error) {
    toast.error(error instanceof Error ? error.message : '区块列表加载失败')
    blocks.value = []
    pagination.total = 0
  }
  finally {
    loading.value = false
  }
}

async function loadCategories() {
  try {
    const res = await apiCms.blockCategories()
    categories.value = res.data || []
  }
  catch {
    categories.value = []
  }
}

function resetForm() {
  Object.assign(form, {
    code: '',
    name: '',
    description: '',
    category: '',
    kind: 'ATOMIC',
    htmlContent: '',
    cssContent: '',
    jsContent: '',
    enabled: true,
  })
  editingBlock.value = null
}

function openCreate() {
  resetForm()
  formVisible.value = true
}

function openEdit(block: CmsBlock) {
  editingBlock.value = block
  Object.assign(form, {
    code: block.code,
    name: block.name,
    description: block.description || '',
    category: block.category || '',
    kind: block.kind,
    htmlContent: block.htmlContent || '',
    cssContent: block.cssContent || '',
    jsContent: block.jsContent || '',
    enabled: block.enabled,
  })
  formVisible.value = true
}

function closeForm() {
  formVisible.value = false
  resetForm()
}

async function saveForm() {
  if (!form.code.trim() || !form.name.trim()) {
    toast.warning('请输入编码和名称')
    return
  }
  saving.value = true
  try {
    if (editingBlock.value) {
      await apiCms.updateBlock(editingBlock.value.id, { ...form })
      toast.success('区块已更新')
    }
    else {
      await apiCms.createBlock({ ...form })
      toast.success('区块已创建')
    }
    closeForm()
    await loadBlocks()
    emit('saved')
  }
  catch (error) {
    toast.error(error instanceof Error ? error.message : '区块保存失败')
  }
  finally {
    saving.value = false
  }
}

function confirmDelete(block: CmsBlock) {
  modal.confirm({
    title: '删除区块',
    content: `确认删除「${block.name}」吗？删除后无法恢复。`,
    onConfirm: async () => {
      try {
        await apiCms.deleteBlock(block.id)
        toast.success('区块已删除')
        await loadBlocks()
        emit('saved')
      }
      catch (error) {
        toast.error(error instanceof Error ? error.message : '区块删除失败')
      }
    },
  })
}

async function toggleEnable(block: CmsBlock) {
  try {
    if (block.enabled) {
      await apiCms.disableBlock(block.id)
      toast.success('已禁用')
    }
    else {
      await apiCms.enableBlock(block.id)
      toast.success('已启用')
    }
    await loadBlocks()
    emit('saved')
  }
  catch (error) {
    toast.error(error instanceof Error ? error.message : '状态更新失败')
  }
}

function kindLabel(kind: CmsBlockKind) {
  return kind === 'PRESET' ? '预制' : '原子'
}

function close() {
  emit('update:visible', false)
}

function onPageChange(page: number) {
  pagination.page = page
  void loadBlocks()
}

function onPageSizeChange(size: number) {
  pagination.size = size
  pagination.page = 1
  void loadBlocks()
}
</script>

<template>
  <FaModal
    :model-value="props.visible"
    title="CMS 区块库"
    class="sm:max-w-[960px]"
    :footer="false"
    :close-on-click-overlay="false"
    :close-on-press-escape="false"
    @update:model-value="emit('update:visible', $event)"
    @close="close"
  >
    <div class="block-library">
      <div class="block-library__toolbar">
        <FaInput
          v-model="keyword"
          placeholder="搜索编码、名称"
          clearable
          class="w-[220px]"
        />
        <FaSelect
          v-model="categoryFilter"
          :options="categoryOptions"
          placeholder="选择分类"
          class="w-[160px]"
        />
        <FaButton @click="openCreate">
          <FaIcon name="i-ri:add-line" />
          新增区块
        </FaButton>
      </div>

      <FaResponsiveTable
        v-loading="loading"
        row-key="id"
        table-root-class="rounded-lg overflow-hidden"
        table-class="min-w-[720px]"
        border
        stripe
        :columns="columns"
        :data="blocks"
      >
        <template #cell-kind="{ row }">
          <FaTag :variant="row.original.kind === 'PRESET' ? 'default' : 'secondary'">
            {{ kindLabel(row.original.kind) }}
          </FaTag>
        </template>
        <template #cell-enabled="{ row }">
          <FaSwitch :model-value="row.original.enabled" @update:model-value="toggleEnable(row.original)" />
        </template>
        <template #cell-operation="{ row }">
          <div class="flex flex-wrap gap-2">
            <FaButton size="sm" variant="outline" @click="openEdit(row.original)">
              编辑
            </FaButton>
            <FaButton size="sm" variant="outline" @click="toggleEnable(row.original)">
              {{ row.original.enabled ? '禁用' : '启用' }}
            </FaButton>
            <FaButton size="sm" variant="destructive" @click="confirmDelete(row.original)">
              删除
            </FaButton>
          </div>
        </template>

        <template #card="{ row }">
          <FaCard class="w-full">
            <div class="flex flex-col gap-3">
              <div class="flex items-center justify-between gap-2">
                <span class="min-w-0 break-words text-base font-semibold">{{ row.name }}</span>
                <FaTag :variant="row.kind === 'PRESET' ? 'default' : 'secondary'">
                  {{ kindLabel(row.kind) }}
                </FaTag>
              </div>
              <div class="flex flex-col gap-1 text-sm">
                <div class="flex gap-2">
                  <span class="shrink-0 text-secondary-foreground/60">编码</span>
                  <span class="break-all font-mono">{{ row.code }}</span>
                </div>
                <div v-if="row.category" class="flex gap-2">
                  <span class="shrink-0 text-secondary-foreground/60">分类</span>
                  <span>{{ row.category }}</span>
                </div>
                <div v-if="row.description" class="flex gap-2">
                  <span class="shrink-0 text-secondary-foreground/60">描述</span>
                  <span class="break-all">{{ row.description }}</span>
                </div>
              </div>
              <div class="flex items-center gap-2">
                <span class="text-sm text-secondary-foreground/60">启用</span>
                <FaSwitch :model-value="row.enabled" @update:model-value="toggleEnable(row)" />
              </div>
              <div class="flex flex-wrap gap-2 border-t pt-3">
                <FaButton size="sm" variant="outline" @click="openEdit(row)">
                  编辑
                </FaButton>
                <FaButton size="sm" variant="outline" @click="toggleEnable(row)">
                  {{ row.enabled ? '禁用' : '启用' }}
                </FaButton>
                <FaButton size="sm" variant="destructive" @click="confirmDelete(row)">
                  删除
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
        @size-change="onPageSizeChange"
      />
    </div>

    <FaModal
      v-model="formVisible"
      :title="editingBlock ? '编辑区块' : '新增区块'"
      class="sm:max-w-[720px]"
      :close-on-click-overlay="false"
      @close="closeForm"
    >
      <a-form :model="form" layout="vertical">
        <div class="block-form-grid">
          <a-form-item label="编码" required>
            <FaInput v-model="form.code" placeholder="block-hero-center" :disabled="Boolean(editingBlock)" />
          </a-form-item>
          <a-form-item label="名称" required>
            <FaInput v-model="form.name" placeholder="区块名称" />
          </a-form-item>
          <a-form-item label="分类">
            <FaInput v-model="form.category" placeholder="预制" />
          </a-form-item>
          <a-form-item label="类型">
            <FaSelect v-model="form.kind" :options="kindOptions" />
          </a-form-item>
          <a-form-item label="启用" class="block-form-switch">
            <FaSwitch v-model="form.enabled" />
          </a-form-item>
          <a-form-item label="描述" class="block-form-span-2">
            <FaTextarea v-model="form.description" rows="2" placeholder="可选描述" />
          </a-form-item>
          <a-form-item label="HTML 内容" class="block-form-span-2">
            <FaTextarea v-model="form.htmlContent" rows="6" placeholder="<section>...</section>" />
          </a-form-item>
          <a-form-item label="CSS 内容" class="block-form-span-2">
            <FaTextarea v-model="form.cssContent" rows="4" placeholder="可选 CSS" />
          </a-form-item>
          <a-form-item label="JS 内容" class="block-form-span-2">
            <FaTextarea v-model="form.jsContent" rows="3" placeholder="可选 JS" />
          </a-form-item>
        </div>
      </a-form>
      <template #footer>
        <div class="flex justify-end gap-2">
          <FaButton variant="outline" @click="closeForm">取消</FaButton>
          <FaButton :loading="saving" @click="saveForm">
            保存
          </FaButton>
        </div>
      </template>
    </FaModal>
  </FaModal>
</template>

<style scoped>
.block-library {
  display: grid;
  gap: 16px;
}

.block-library__toolbar {
  display: flex;
  gap: 12px;
  align-items: center;
  flex-wrap: wrap;
}

.block-form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 16px;
}

.block-form-span-2 {
  grid-column: span 2;
}

.block-form-switch :deep(.arco-form-item-content) {
  min-height: 32px;
  display: flex;
  align-items: center;
}

@media (max-width: 640px) {
  .block-form-grid {
    grid-template-columns: 1fr;
  }

  .block-form-span-2 {
    grid-column: span 1;
  }
}
</style>
