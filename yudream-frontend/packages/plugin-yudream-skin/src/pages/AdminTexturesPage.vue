<script setup lang="ts">
import type { TableColumn } from '@fantastic-admin/components'
import type { SkinTexture } from '../types'
import type { SkinPluginModel } from '../composables/useSkinPlugin'
import { computed, reactive, watch } from 'vue'
import { FaButton, FaIcon, FaInput, FaPagination, FaSearchBar, FaSelect, FaTable, FaTag, useFaModal } from '@fantastic-admin/components'
import SkinTexturePreview from '../components/SkinTexturePreview.vue'

const props = defineProps<{
  model: SkinPluginModel
}>()

const modal = useFaModal()
const search = reactive({
  keyword: '',
  type: 'all',
  visibility: 'all',
})
const pagination = reactive({ page: 1, size: 10 })

const typeOptions = [
  { label: '全部类型', value: 'all' },
  { label: 'Steve 皮肤', value: 'default' },
  { label: 'Alex 皮肤', value: 'slim' },
  { label: '披风', value: 'cape' },
]
const visibilityOptions = [
  { label: '全部权限', value: 'all' },
  { label: '公开材质', value: 'public' },
  { label: '私有材质', value: 'private' },
]

const filteredRows = computed(() => {
  const keyword = search.keyword.trim().toLowerCase()
  return props.model.textures.filter((texture) => {
    if (search.type === 'cape' && texture.type !== 'cape') {
      return false
    }
    if (search.type === 'slim' && !(texture.type !== 'cape' && texture.model === 'slim')) {
      return false
    }
    if (search.type === 'default' && !(texture.type !== 'cape' && texture.model !== 'slim')) {
      return false
    }
    if (search.visibility === 'public' && !texture.publicAccess) {
      return false
    }
    if (search.visibility === 'private' && texture.publicAccess) {
      return false
    }
    if (!keyword) {
      return true
    }
    const text = [
      texture.name,
      texture.hash,
      textureKind(texture),
      texture.uploaderId,
      props.model.userName(texture.uploaderId),
    ].join(' ').toLowerCase()
    return text.includes(keyword)
  })
})

const pagedRows = computed(() => {
  const start = (pagination.page - 1) * pagination.size
  return filteredRows.value.slice(start, start + pagination.size)
})

const totalPages = computed(() => Math.max(1, Math.ceil(filteredRows.value.length / pagination.size)))

const tableColumns = computed<TableColumn<SkinTexture>[]>(() => [
  { id: 'preview', header: '预览', width: 112, fixed: 'left' },
  { accessorKey: 'name', header: '材质名称', width: 220, fixed: 'left' },
  { id: 'type', header: '类型', width: 116 },
  { id: 'publicAccess', header: '权限', width: 92, align: 'center' },
  { accessorKey: 'uploaderId', header: '上传人', width: 132 },
  { id: 'size', header: '大小', width: 96 },
  { id: 'uploadedAt', header: '上传时间', width: 152 },
  { id: 'hash', header: 'Hash', width: 190 },
  { id: 'operation', header: '操作', width: 92, align: 'center', fixed: 'right' },
])

watch([() => search.keyword, () => search.type, () => search.visibility], () => {
  pagination.page = 1
})

watch(totalPages, (pages) => {
  if (pagination.page > pages) {
    pagination.page = pages
  }
})

function resetSearch() {
  search.keyword = ''
  search.type = 'all'
  search.visibility = 'all'
  pagination.page = 1
}

function confirmDelete(row: SkinTexture) {
  modal.confirm({
    title: '删除材质',
    content: `确认删除材质「${row.name}」吗？关联衣柜项会同步清理，角色绑定会置空。`,
    onConfirm: () => props.model.deleteAdminTexture(row.hash),
  })
}

function onPageChange(page: number) {
  pagination.page = page
}

function onSizeChange(size: number) {
  pagination.size = size
  pagination.page = 1
}

function textureKind(texture: SkinTexture) {
  if (texture.type === 'cape') {
    return '披风'
  }
  return texture.model === 'slim' ? 'Alex 皮肤' : 'Steve 皮肤'
}

function shortHash(hash?: string) {
  if (!hash) {
    return '-'
  }
  return hash.length > 18 ? `${hash.slice(0, 12)}...${hash.slice(-8)}` : hash
}

function formatSize(size?: number) {
  if (!size) {
    return '-'
  }
  if (size < 1024) {
    return `${size} B`
  }
  if (size < 1024 * 1024) {
    return `${(size / 1024).toFixed(1)} KB`
  }
  return `${(size / 1024 / 1024).toFixed(1)} MB`
}
</script>

<template>
  <div class="skin-admin-table-page">
    <FaTable
      row-key="hash"
      table-root-class="skin-admin-texture-table-root rounded-lg"
      table-class="skin-admin-texture-table"
      column-visibility
      border
      stripe
      :columns="tableColumns"
      :data="pagedRows"
    >
      <template #toolbar>
        <FaSearchBar class="w-full">
          <div class="skin-admin-filter-grid">
            <FaInput v-model="search.keyword" clearable placeholder="名称 / Hash / 上传人" class="skin-admin-filter-grid__keyword" @keydown.enter="pagination.page = 1" @clear="pagination.page = 1" />
            <FaSelect v-model="search.type" :options="typeOptions" class="skin-admin-filter-grid__select" />
            <FaSelect v-model="search.visibility" :options="visibilityOptions" class="skin-admin-filter-grid__select" />
            <div class="skin-admin-filter-grid__actions">
              <FaButton variant="outline" @click="resetSearch">
                重置
              </FaButton>
              <FaButton @click="pagination.page = 1">
                <FaIcon name="i-ri:search-line" />
                筛选
              </FaButton>
            </div>
          </div>
        </FaSearchBar>
      </template>
      <template #cell-preview="{ row }">
        <div class="skin-admin-texture-preview" aria-hidden="true">
          <SkinTexturePreview
            :texture-url="model.textureUrl(row.original.hash)"
            :type="row.original.type"
            :model="row.original.model"
          />
        </div>
      </template>
      <template #cell-name="{ row }">
        <span class="skin-admin-texture-name" :title="row.original.name">{{ row.original.name }}</span>
      </template>
      <template #cell-type="{ row }">
        {{ textureKind(row.original) }}
      </template>
      <template #cell-publicAccess="{ row }">
        <FaTag :variant="row.original.publicAccess ? 'default' : 'secondary'">
          {{ row.original.publicAccess ? '公开' : '私有' }}
        </FaTag>
      </template>
      <template #cell-uploaderId="{ row }">
        {{ model.userName(row.original.uploaderId) }}
      </template>
      <template #cell-size="{ row }">
        {{ formatSize(row.original.size) }}
      </template>
      <template #cell-uploadedAt="{ row }">
        {{ model.dateText(row.original.uploadedAt) }}
      </template>
      <template #cell-hash="{ row }">
        <code class="skin-admin-code" :title="row.original.hash">{{ shortHash(row.original.hash) }}</code>
      </template>
      <template #cell-operation="{ row }">
        <FaButton variant="destructive" size="sm" :loading="model.saving === `admin-texture:${row.original.hash}`" @click="confirmDelete(row.original)">
          删除
        </FaButton>
      </template>
    </FaTable>

    <FaPagination
      v-model:page="pagination.page"
      v-model:size="pagination.size"
      :total="filteredRows.length"
      class="mt-3"
      @page-change="onPageChange"
      @size-change="onSizeChange"
    />
  </div>
</template>
