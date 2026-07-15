<script setup lang="ts">
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

const columns = [
  { title: '编码', dataIndex: 'code', ellipsis: true, tooltip: true },
  { title: '名称', dataIndex: 'name', ellipsis: true, tooltip: true },
  { title: '分类', dataIndex: 'category' },
  { title: '类型', slotName: 'kind' },
  { title: '启用状态', slotName: 'enabled', width: 100 },
  { title: '操作', slotName: 'operations', width: 180 },
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
  <a-modal
    :visible="props.visible"
    :width="960"
    :footer="false"
    :mask-closable="false"
    :esc-to-close="false"
    title="CMS 区块库"
    @cancel="close"
  >
    <div class="block-library">
      <div class="block-library__toolbar">
        <a-input
          v-model="keyword"
          placeholder="搜索编码、名称"
          allow-clear
          style="width: 220px"
          @press-enter="loadBlocks"
          @clear="loadBlocks"
        />
        <a-select
          v-model="categoryFilter"
          :options="categoryOptions"
          placeholder="选择分类"
          allow-clear
          style="width: 160px"
          @change="loadBlocks"
        />
        <a-button type="primary" @click="openCreate">
          <template #icon>
            <FaIcon name="i-ri:add-line" />
          </template>
          新增区块
        </a-button>
      </div>

      <a-table
        :columns="columns"
        :data="blocks"
        :loading="loading"
        :pagination="{
          total: pagination.total,
          current: pagination.page,
          pageSize: pagination.size,
          showTotal: true,
          showJumper: true,
        }"
        size="medium"
        @page-change="onPageChange"
        @page-size-change="onPageSizeChange"
      >
        <template #kind="{ record }">
          <a-tag :color="record.kind === 'PRESET' ? 'blue' : 'green'">
            {{ kindLabel(record.kind) }}
          </a-tag>
        </template>
        <template #enabled="{ record }">
          <a-switch :model-value="record.enabled" size="small" @change="toggleEnable(record)" />
        </template>
        <template #operations="{ record }">
          <a-space>
            <a-button type="text" size="small" @click="openEdit(record)">
              编辑
            </a-button>
            <a-button type="text" size="small" @click="toggleEnable(record)">
              {{ record.enabled ? '禁用' : '启用' }}
            </a-button>
            <a-button type="text" size="small" status="danger" @click="confirmDelete(record)">
              删除
            </a-button>
          </a-space>
        </template>
      </a-table>
    </div>

    <a-modal
      v-model:visible="formVisible"
      :title="editingBlock ? '编辑区块' : '新增区块'"
      :width="720"
      :mask-closable="false"
      @cancel="closeForm"
    >
      <a-form :model="form" layout="vertical">
        <div class="block-form-grid">
          <a-form-item label="编码" required>
            <a-input v-model="form.code" placeholder="block-hero-center" :disabled="Boolean(editingBlock)" />
          </a-form-item>
          <a-form-item label="名称" required>
            <a-input v-model="form.name" placeholder="区块名称" />
          </a-form-item>
          <a-form-item label="分类">
            <a-input v-model="form.category" placeholder="预制" />
          </a-form-item>
          <a-form-item label="类型">
            <a-select v-model="form.kind" :options="kindOptions" />
          </a-form-item>
          <a-form-item label="启用" class="block-form-switch">
            <a-switch v-model="form.enabled" />
          </a-form-item>
          <a-form-item label="描述" class="block-form-span-2">
            <a-textarea v-model="form.description" :auto-size="{ minRows: 2, maxRows: 4 }" placeholder="可选描述" />
          </a-form-item>
          <a-form-item label="HTML 内容" class="block-form-span-2">
            <a-textarea v-model="form.htmlContent" :auto-size="{ minRows: 6, maxRows: 12 }" placeholder="<section>...</section>" />
          </a-form-item>
          <a-form-item label="CSS 内容" class="block-form-span-2">
            <a-textarea v-model="form.cssContent" :auto-size="{ minRows: 4, maxRows: 8 }" placeholder="可选 CSS" />
          </a-form-item>
          <a-form-item label="JS 内容" class="block-form-span-2">
            <a-textarea v-model="form.jsContent" :auto-size="{ minRows: 3, maxRows: 6 }" placeholder="可选 JS" />
          </a-form-item>
        </div>
      </a-form>
      <template #footer>
        <a-space>
          <a-button @click="closeForm">取消</a-button>
          <a-button type="primary" :loading="saving" @click="saveForm">
            保存
          </a-button>
        </a-space>
      </template>
    </a-modal>
  </a-modal>
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
