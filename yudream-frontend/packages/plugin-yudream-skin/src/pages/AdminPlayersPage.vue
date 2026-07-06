<script setup lang="ts">
import type { TableColumn } from '@fantastic-admin/components'
import type { SkinPlayer } from '../types'
import type { SkinPluginModel } from '../composables/useSkinPlugin'
import { computed, reactive, ref, watch } from 'vue'
import { FaButton, FaIcon, FaInput, FaModal, FaPagination, FaSearchBar, FaSelect, FaTable, FaTag, useFaModal } from '@fantastic-admin/components'

const props = defineProps<{
  model: SkinPluginModel
}>()

const modal = useFaModal()
const search = reactive({
  keyword: '',
  ownerId: '',
  status: 'all',
})
const pagination = reactive({ page: 1, size: 10 })
const formVisible = ref(false)
const editing = ref<SkinPlayer | null>(null)
const form = reactive({
  name: '',
  ownerId: '',
  skinHash: '',
  capeHash: '',
})

const statusOptions = [
  { label: '全部状态', value: 'all' },
  { label: '已配置外观', value: 'configured' },
  { label: '待换装', value: 'empty' },
]

const skinOptions = computed(() => props.model.textures
  .filter(texture => texture.type !== 'cape')
  .map(texture => ({ label: `${texture.name} (${shortHash(texture.hash)})`, value: texture.hash })))

const capeOptions = computed(() => props.model.textures
  .filter(texture => texture.type === 'cape')
  .map(texture => ({ label: `${texture.name} (${shortHash(texture.hash)})`, value: texture.hash })))

const filteredRows = computed(() => {
  const keyword = search.keyword.trim().toLowerCase()
  const ownerKeyword = search.ownerId.trim().toLowerCase()
  return props.model.players.filter((player) => {
    if (ownerKeyword && !ownerSearchText(player).toLowerCase().includes(ownerKeyword)) {
      return false
    }
    if (search.status === 'configured' && !player.skinHash && !player.capeHash) {
      return false
    }
    if (search.status === 'empty' && (player.skinHash || player.capeHash)) {
      return false
    }
    if (!keyword) {
      return true
    }
    const text = [
      player.name,
      player.uuid,
      player.ownerId,
      ownerSearchText(player),
      props.model.textureName(player.skinHash),
      props.model.textureName(player.capeHash),
    ].join(' ').toLowerCase()
    return text.includes(keyword)
  })
})

const pagedRows = computed(() => {
  const start = (pagination.page - 1) * pagination.size
  return filteredRows.value.slice(start, start + pagination.size)
})

const totalPages = computed(() => Math.max(1, Math.ceil(filteredRows.value.length / pagination.size)))

const tableColumns = computed<TableColumn<SkinPlayer>[]>(() => [
  { accessorKey: 'name', header: '角色名', width: 150, fixed: 'left' },
  { accessorKey: 'ownerId', header: '系统用户', width: 220 },
  { id: 'skin', header: '皮肤', width: 180 },
  { id: 'cape', header: '披风', width: 180 },
  { id: 'status', header: '状态', width: 96, align: 'center' },
  { id: 'lastModified', header: '更新时间', width: 150 },
  { id: 'uuid', header: 'UUID', width: 180 },
  { id: 'operation', header: '操作', width: 142, align: 'center', fixed: 'right' },
])

watch([() => search.keyword, () => search.ownerId, () => search.status], () => {
  pagination.page = 1
})

watch(totalPages, (pages) => {
  if (pagination.page > pages) {
    pagination.page = pages
  }
})

function resetSearch() {
  search.keyword = ''
  search.ownerId = ''
  search.status = 'all'
  pagination.page = 1
}

function ownerText(player: SkinPlayer) {
  return player.ownerName || player.ownerUsername || player.ownerEmail || props.model.userName(player.ownerId)
}

function ownerSubText(player: SkinPlayer) {
  const parts = [
    player.ownerUsername ? `@${player.ownerUsername}` : '',
    player.ownerEmail || '',
    `ID ${player.ownerId}`,
  ].filter(Boolean)
  return parts.join(' / ')
}

function ownerSearchText(player: SkinPlayer) {
  return [
    player.ownerId,
    player.ownerName,
    player.ownerUsername,
    player.ownerEmail,
    props.model.userName(player.ownerId),
  ].filter(Boolean).join(' ')
}

function openCreate() {
  editing.value = null
  Object.assign(form, {
    name: '',
    ownerId: props.model.currentUserId || '',
    skinHash: '',
    capeHash: '',
  })
  formVisible.value = true
}

function openEdit(row: SkinPlayer) {
  editing.value = row
  Object.assign(form, {
    name: row.name,
    ownerId: row.ownerId,
    skinHash: row.skinHash || '',
    capeHash: row.capeHash || '',
  })
  formVisible.value = true
}

async function saveForm() {
  const name = form.name.trim()
  const ownerId = form.ownerId.trim()
  if (!name || !ownerId) {
    return
  }
  let targetName = name
  if (editing.value) {
    targetName = editing.value.name
    if (name !== editing.value.name) {
      const renamed = await props.model.renameAdminPlayer(editing.value.name, { name })
      targetName = renamed.name
    }
    if (form.skinHash !== (editing.value.skinHash || '') || form.capeHash !== (editing.value.capeHash || '')) {
      await props.model.assignAdminTextures(targetName, { skinHash: form.skinHash, capeHash: form.capeHash })
    }
  }
  else {
    const created = await props.model.createAdminPlayer({ name, ownerId })
    if (form.skinHash || form.capeHash) {
      await props.model.assignAdminTextures(created.name, { skinHash: form.skinHash, capeHash: form.capeHash })
    }
  }
  formVisible.value = false
}

function confirmDelete(row: SkinPlayer) {
  modal.confirm({
    title: '删除角色',
    content: `确认删除角色「${row.name}」吗？`,
    onConfirm: () => props.model.deleteAdminPlayer(row.name),
  })
}

function onPageChange(page: number) {
  pagination.page = page
}

function onSizeChange(size: number) {
  pagination.size = size
  pagination.page = 1
}

function shortHash(hash?: string) {
  if (!hash) {
    return '-'
  }
  return hash.length > 18 ? `${hash.slice(0, 10)}...${hash.slice(-6)}` : hash
}
</script>

<template>
  <div class="skin-admin-table-page">
    <FaSearchBar>
      <template #default="{ fold, toggle }">
        <div class="skin-admin-filter-grid">
          <FaInput v-model="search.keyword" clearable placeholder="角色名 / UUID / 材质 / 用户" class="skin-admin-filter-grid__keyword" @keydown.enter="pagination.page = 1" @clear="pagination.page = 1" />
          <FaInput v-model="search.ownerId" clearable placeholder="系统用户 ID / 昵称 / 用户名" class="skin-admin-filter-grid__select" @keydown.enter="pagination.page = 1" @clear="pagination.page = 1" />
          <FaSelect v-if="!fold" v-model="search.status" :options="statusOptions" class="skin-admin-filter-grid__select" />
          <div class="skin-admin-filter-grid__actions">
            <FaButton variant="outline" @click="resetSearch">
              重置
            </FaButton>
            <FaButton @click="pagination.page = 1">
              <FaIcon name="i-ri:search-line" />
              筛选
            </FaButton>
            <FaButton variant="ghost" @click="toggle">
              {{ fold ? '展开' : '收起' }}
              <FaIcon :name="fold ? 'i-ep:caret-bottom' : 'i-ep:caret-top'" />
            </FaButton>
            <FaButton @click="openCreate">
              <FaIcon name="i-ri:add-line" />
              新增角色
            </FaButton>
          </div>
        </div>
      </template>
    </FaSearchBar>

    <div class="mx--4 my-3 border-t border-t-dashed" />

    <FaTable
      row-key="uuid"
      table-root-class="skin-admin-player-table-root rounded-lg"
      table-class="skin-admin-player-table"
      column-visibility
      border
      stripe
      :columns="tableColumns"
      :data="pagedRows"
    >
      <template #cell-name="{ row }">
        <span class="skin-admin-texture-name" :title="row.original.name">{{ row.original.name }}</span>
      </template>
      <template #cell-ownerId="{ row }">
        <span class="skin-admin-owner">
          <strong :title="ownerText(row.original)">{{ ownerText(row.original) }}</strong>
          <small :title="ownerSubText(row.original)">{{ ownerSubText(row.original) }}</small>
        </span>
      </template>
      <template #cell-skin="{ row }">
        <span class="skin-admin-texture-name" :title="model.textureName(row.original.skinHash)">{{ model.textureName(row.original.skinHash) }}</span>
      </template>
      <template #cell-cape="{ row }">
        <span class="skin-admin-texture-name" :title="model.textureName(row.original.capeHash)">{{ model.textureName(row.original.capeHash) }}</span>
      </template>
      <template #cell-status="{ row }">
        <FaTag :variant="row.original.skinHash || row.original.capeHash ? 'default' : 'secondary'">
          {{ row.original.skinHash || row.original.capeHash ? '已配置' : '待换装' }}
        </FaTag>
      </template>
      <template #cell-lastModified="{ row }">
        {{ model.dateText(row.original.lastModified) }}
      </template>
      <template #cell-uuid="{ row }">
        <code class="skin-admin-code" :title="row.original.uuid">{{ row.original.uuid }}</code>
      </template>
      <template #cell-operation="{ row }">
        <div class="flex-center gap-2">
          <FaButton variant="outline" size="sm" @click="openEdit(row.original)">
            编辑
          </FaButton>
          <FaButton variant="destructive" size="sm" @click="confirmDelete(row.original)">
            删除
          </FaButton>
        </div>
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

    <FaModal v-model="formVisible" :title="editing ? '编辑角色' : '新增角色'" show-cancel-button class="sm:max-w-2xl" @confirm="saveForm">
      <div class="skin-admin-form">
        <label>
          <span>角色名</span>
          <FaInput v-model="form.name" class="w-full" />
        </label>
        <label>
          <span>系统用户 ID</span>
          <FaInput v-model="form.ownerId" :disabled="!!editing" class="w-full" />
        </label>
        <label>
          <span>皮肤</span>
          <FaSelect v-model="form.skinHash" clearable :options="skinOptions" class="w-full" />
        </label>
        <label>
          <span>披风</span>
          <FaSelect v-model="form.capeHash" clearable :options="capeOptions" class="w-full" />
        </label>
      </div>
    </FaModal>
  </div>
</template>
