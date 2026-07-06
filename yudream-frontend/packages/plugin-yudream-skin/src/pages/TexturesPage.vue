<template>
  <section class="skin-page">
    <div class="skin-command-bar">
      <div>
        <span>{{ isManagement ? '材质管理' : '皮肤库' }}</span>
        <h2>{{ isManagement ? '管理全站材质' : '浏览全站材质' }}</h2>
        <p>{{ isManagement ? '维护皮肤、披风材质与公开状态，上传后的材质可用于角色外观。' : '选择一个材质查看 3D 效果，保存到衣柜后即可给默认角色或当前角色换装。' }}</p>
      </div>
      <div class="skin-command-actions">
        <FaButton variant="outline" :loading="model.loading" @click="model.load">
          <FaIcon name="i-ri:refresh-line" />
          刷新
        </FaButton>
        <FaButton v-if="model.canUse" @click="uploadVisible = true">
          <FaIcon name="i-ri:upload-cloud-2-line" />
          上传材质
        </FaButton>
      </div>
    </div>

    <div class="skin-library-layout">
      <SkinPanel :title="isManagement ? '材质管理' : '材质'">
        <template #header>
          <FaTag variant="secondary">{{ filteredTextures.length }}/{{ model.textures.length }}</FaTag>
        </template>

        <div class="skin-filter-bar">
          <FaInput
            v-model="keyword"
            type="search"
            clearable
            class="skin-filter-keyword"
            data-testid="skin-texture-keyword"
            placeholder="搜索名称、Hash 或上传人"
            @clear="keyword = ''"
          />
          <FaSelect v-model="typeFilter" class="skin-filter-select" data-testid="skin-texture-type" :options="typeFilterOptions" />
          <FaSelect v-model="ownerFilter" class="skin-filter-select" data-testid="skin-texture-source" :options="ownerFilterOptions" />
          <FaSelect v-model="sortType" class="skin-filter-select" data-testid="skin-texture-sort" :options="sortOptions" />
          <FaButton v-if="activeFilterCount" variant="outline" class="skin-filter-reset" @click="resetFilters">
            <FaIcon name="i-ri:filter-off-line" />
            清空
          </FaButton>
        </div>

        <div class="skin-card-grid">
          <button
            v-for="texture in pagedTextures"
            :key="texture.hash"
            type="button"
            class="skin-texture-card"
            :class="{ active: model.selectedTextureHash === texture.hash }"
            @click="model.selectTexture(texture)"
          >
            <span class="skin-texture-card__image render">
              <SkinTexturePreview
                :texture-url="model.textureUrl(texture.hash)"
                :type="texture.type"
                :model="texture.model"
              />
            </span>
            <span class="skin-texture-card__body">
              <strong>{{ texture.name }}</strong>
              <small>{{ textureKind(texture) }} / {{ texture.publicAccess ? '公开' : '私有' }}</small>
              <span class="skin-texture-card__meta">
                <span>
                  <FaIcon name="i-ri:user-3-line" />
                  {{ model.userName(texture.uploaderId) }}
                </span>
                <span>
                  <FaIcon name="i-ri:hashtag" />
                  {{ shortHash(texture.hash) }}
                </span>
              </span>
            </span>
          </button>
          <div v-if="!filteredTextures.length" class="empty-state">没有匹配的材质</div>
        </div>
        <FaPagination
          v-if="filteredTextures.length"
          v-model:page="texturePagination.page"
          v-model:size="texturePagination.size"
          :total="filteredTextures.length"
          :sizes="texturePageSizes"
          class="skin-list-pagination"
          layout="total, sizes, ->, pager"
          @size-change="onTexturePageSizeChange"
        />
      </SkinPanel>

      <aside class="skin-inspector">
        <SkinPreview
          title="材质预览"
          :skin="model.selectedTextureSkin"
          :cape="model.selectedTextureCape"
          :slim="model.selectedTextureSlim"
        >
          <div class="preview-meta">
            <span>{{ model.selectedTexture?.name || '未选择材质' }}</span>
            <span>{{ selectedKind }}</span>
          </div>
        </SkinPreview>

        <div class="skin-action-panel skin-detail-panel">
          <div class="skin-detail-head">
            <div class="skin-detail-title">
              <strong>{{ selectedTitle }}</strong>
              <span>{{ selectedSubtitle }}</span>
            </div>
            <div v-if="model.canUse" class="skin-detail-actions">
              <FaTooltip text="加入衣柜" side="top">
                <span class="skin-action-tooltip">
                  <FaButton
                    variant="outline"
                    size="icon-sm"
                    class="skin-action-icon"
                    :disabled="!model.selectedTexture"
                    :loading="model.saving === 'closet'"
                    aria-label="加入衣柜"
                    @click="model.selectedTexture && model.addTextureToCloset(model.selectedTexture)"
                  >
                    <FaIcon name="i-ri:archive-drawer-line" />
                  </FaButton>
                </span>
              </FaTooltip>
              <FaTooltip text="应用到当前角色" side="top">
                <span class="skin-action-tooltip">
                  <FaButton
                    size="icon-sm"
                    class="skin-action-icon"
                    :disabled="!model.selectedTexture || !model.selectedPlayer"
                    :loading="model.selectedTexture ? model.saving === `use-texture:${model.selectedTexture.hash}` : false"
                    aria-label="应用到当前角色"
                    @click="model.useTextureOnSelectedPlayer()"
                  >
                    <FaIcon name="i-ri:t-shirt-2-line" />
                  </FaButton>
                </span>
              </FaTooltip>
            </div>
          </div>

          <div class="skin-detail-meta">
            <div>
              <span>类型</span>
              <strong>{{ selectedKind }}</strong>
            </div>
            <div>
              <span>上传人</span>
              <strong>{{ selectedUploader }}</strong>
            </div>
            <div>
              <span>权限</span>
              <strong>{{ selectedVisibility }}</strong>
            </div>
            <div>
              <span>大小</span>
              <strong>{{ selectedSize }}</strong>
            </div>
            <div>
              <span>上传时间</span>
              <strong>{{ selectedUploadedAt }}</strong>
            </div>
            <div>
              <span>材质 Hash</span>
              <code class="skin-hash" :title="selectedHash">{{ selectedHashShort }}</code>
            </div>
          </div>
          <p v-if="!model.selectedTexture" class="skin-help-text">从左侧材质库中选择后再操作。</p>
          <p v-if="model.canUse && !model.selectedPlayer" class="skin-help-text">先在“{{ isManagement ? '角色管理' : '我的角色' }}”中选择或设定默认角色，再一键应用材质。</p>
        </div>
      </aside>
    </div>

    <FaModal
      v-model="uploadVisible"
      title="上传材质"
      :description="isManagement ? '上传 PNG 皮肤或披风，并设置是否公开到皮肤库。' : '上传 PNG 皮肤或披风，普通用户会自动保存到自己的衣柜。'"
      show-cancel-button
      class="sm:max-w-3xl"
      :confirm-loading="model.saving === 'texture'"
      @confirm="submitUpload"
    >
      <div class="skin-upload-form">
        <label>
          <span>名称</span>
          <FaInput v-model="model.textureForm.name" placeholder="例如：冬季外套 Steve" />
        </label>
        <label>
          <span>类型</span>
          <FaSelect v-model="model.textureForm.type" :options="textureTypeOptions" />
        </label>
        <label>
          <span>PNG 文件</span>
          <input class="file-input" type="file" accept="image/png" @change="model.handleTextureFile">
        </label>
        <label class="switch-row">
          <span>公开到皮肤库</span>
          <FaSwitch v-model="model.textureForm.publicAccess" :disabled="!model.settings.allowPublicUpload" />
        </label>
      </div>
    </FaModal>
  </section>
</template>

<script setup lang="ts">
import type { SkinPluginModel } from '../composables/useSkinPlugin'
import type { SkinTexture } from '../types'
import { computed, reactive, ref, watch } from 'vue'
import { FaButton, FaIcon, FaInput, FaModal, FaPagination, FaSelect, FaSwitch, FaTag, FaTooltip } from '@fantastic-admin/components'
import SkinPanel from '../components/SkinPanel.vue'
import SkinPreview from '../components/SkinPreview.vue'
import SkinTexturePreview from '../components/SkinTexturePreview.vue'

const props = defineProps<{
  model: SkinPluginModel
  mode?: 'user' | 'management'
}>()

const uploadVisible = ref(false)
const isManagement = computed(() => props.mode === 'management')
const keyword = ref('')
const typeFilter = ref('all')
const ownerFilter = ref('all')
const sortType = ref('new')
const texturePageSizes = [12, 24, 48, 96]
const texturePagination = reactive({ page: 1, size: 12 })
const textureTypeOptions = [
  { label: 'Steve 皮肤', value: 'steve' },
  { label: 'Alex 皮肤', value: 'alex' },
  { label: '披风', value: 'cape' },
]
const typeFilterOptions = [
  { label: '全部类型', value: 'all' },
  { label: 'Steve 皮肤', value: 'default' },
  { label: 'Alex 皮肤', value: 'slim' },
  { label: '披风', value: 'cape' },
]
const ownerFilterOptions = [
  { label: '全部来源', value: 'all' },
  { label: '我上传的', value: 'mine' },
  { label: '公开材质', value: 'public' },
]
const sortOptions = [
  { label: '最近上传', value: 'new' },
  { label: '最早上传', value: 'old' },
  { label: '名称排序', value: 'name' },
]
const activeFilterCount = computed(() => [
  keyword.value.trim(),
  typeFilter.value !== 'all',
  ownerFilter.value !== 'all',
].filter(Boolean).length)

const filteredTextures = computed(() => {
  const words = keyword.value.trim().toLowerCase().split(/\s+/).filter(Boolean)
  return props.model.textures
    .filter((texture) => {
      if (typeFilter.value === 'cape' && texture.type !== 'cape') {
        return false
      }
      if (typeFilter.value === 'slim' && !(texture.type !== 'cape' && texture.model === 'slim')) {
        return false
      }
      if (typeFilter.value === 'default' && !(texture.type !== 'cape' && texture.model !== 'slim')) {
        return false
      }
      if (ownerFilter.value === 'mine' && texture.uploaderId !== props.model.currentUserId) {
        return false
      }
      if (ownerFilter.value === 'public' && !texture.publicAccess) {
        return false
      }
      if (!words.length) {
        return true
      }
      const searchText = [
        texture.name,
        texture.hash,
        textureKind(texture),
        props.model.userName(texture.uploaderId),
      ].join(' ').toLowerCase()
      return words.every(word => searchText.includes(word))
    })
    .slice()
    .sort((left, right) => {
      if (sortType.value === 'name') {
        return left.name.localeCompare(right.name)
      }
      const leftTime = left.uploadedAt || 0
      const rightTime = right.uploadedAt || 0
      return sortType.value === 'old' ? leftTime - rightTime : rightTime - leftTime
    })
})

const pagedTextures = computed(() => {
  const start = (texturePagination.page - 1) * texturePagination.size
  return filteredTextures.value.slice(start, start + texturePagination.size)
})

const textureTotalPages = computed(() => {
  return Math.max(1, Math.ceil(filteredTextures.value.length / texturePagination.size))
})

watch([keyword, typeFilter, ownerFilter, sortType], () => {
  texturePagination.page = 1
})

watch(textureTotalPages, (totalPages) => {
  if (texturePagination.page > totalPages) {
    texturePagination.page = totalPages
  }
})

const selectedKind = computed(() => {
  const texture = props.model.selectedTexture
  return texture ? textureKind(texture) : '-'
})
const selectedTitle = computed(() => {
  return props.model.selectedTexture?.name || '选择一个材质'
})
const selectedSubtitle = computed(() => {
  return props.model.selectedTexture ? selectedKind.value : '从左侧材质库中选择后再操作'
})
const selectedHash = computed(() => {
  return props.model.selectedTexture?.hash || ''
})
const selectedHashShort = computed(() => {
  return shortenHash(selectedHash.value)
})
const selectedUploader = computed(() => {
  return props.model.userName(props.model.selectedTexture?.uploaderId)
})
const selectedVisibility = computed(() => {
  const texture = props.model.selectedTexture
  if (!texture) {
    return '-'
  }
  return texture.publicAccess ? '公开材质' : '私有材质'
})
const selectedUploadedAt = computed(() => {
  return props.model.dateText(props.model.selectedTexture?.uploadedAt)
})
const selectedSize = computed(() => {
  return formatSize(props.model.selectedTexture?.size)
})

function textureKind(texture: SkinTexture) {
  if (texture.type === 'cape') {
    return '披风'
  }
  return texture.model === 'slim' ? 'Alex 皮肤' : 'Steve 皮肤'
}

function shortHash(hash?: string) {
  return shortenHash(hash, 10)
}

function shortenHash(hash?: string, head = 10) {
  if (!hash) {
    return '-'
  }
  return hash.length > 18 ? `${hash.slice(0, head)}...${hash.slice(-6)}` : hash
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

function resetFilters() {
  keyword.value = ''
  typeFilter.value = 'all'
  ownerFilter.value = 'all'
}

function onTexturePageSizeChange() {
  texturePagination.page = 1
}

async function submitUpload() {
  await props.model.uploadTexture()
  uploadVisible.value = false
}
</script>
