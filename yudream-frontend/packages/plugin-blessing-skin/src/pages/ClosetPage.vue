<template>
  <section class="skin-page">
    <div class="skin-command-bar">
      <div>
        <span>我的衣柜</span>
        <h2>{{ model.selectedClosetItem?.itemName || '收藏的材质' }}</h2>
        <p>衣柜只展示当前账号可使用的材质，选中后可以预览、重命名或应用到角色。</p>
      </div>
      <FaTooltip text="刷新衣柜" side="bottom">
        <FaButton variant="outline" size="icon" :loading="model.loading" aria-label="刷新衣柜" @click="model.load">
          <FaIcon name="i-ri:refresh-line" />
        </FaButton>
      </FaTooltip>
    </div>

    <div class="skin-library-layout">
      <SkinPanel title="衣柜">
        <template #header>
          <FaTag variant="secondary">{{ model.closetItems.length }}</FaTag>
        </template>
        <div class="skin-card-grid closet">
          <button
            v-for="item in model.closetItems"
            :key="item.id"
            type="button"
            class="skin-texture-card"
            :class="{ active: model.selectedClosetId === item.id }"
            @click="model.selectClosetItem(item)"
          >
            <span class="skin-texture-card__image render">
              <SkinTexturePreview
                :texture-url="model.textureUrl(item.textureHash)"
                :type="closetTexture(item)?.type"
                :model="closetTexture(item)?.model"
              />
            </span>
            <span class="skin-texture-card__body">
              <strong>{{ item.itemName || model.textureName(item.textureHash) }}</strong>
              <small>{{ model.textureName(item.textureHash) }}</small>
            </span>
          </button>
          <div v-if="!model.closetItems.length" class="empty-state">衣柜还是空的，可以先到皮肤库收藏或上传材质。</div>
        </div>
      </SkinPanel>

      <aside class="skin-inspector">
        <SkinPreview
          title="衣柜预览"
          :skin="model.selectedClosetSkin"
          :cape="model.selectedClosetCape"
          :slim="model.selectedClosetSlim"
        >
          <div class="preview-meta">
            <span>{{ model.selectedClosetTexture?.name || '未选择衣柜项' }}</span>
            <span>{{ selectedTextureKind }}</span>
          </div>
        </SkinPreview>

        <div class="skin-action-panel skin-detail-panel">
          <div class="skin-detail-head">
            <div class="skin-detail-title">
              <strong>{{ selectedTitle }}</strong>
              <span>{{ selectedTextureName }}</span>
            </div>
            <div class="skin-detail-actions">
              <FaTooltip text="重命名" side="top">
                <span class="skin-action-tooltip">
                  <FaButton
                    variant="outline"
                    size="icon-sm"
                    class="skin-action-icon"
                    :disabled="!model.selectedClosetItem"
                    aria-label="重命名"
                    @click="openRename"
                  >
                    <FaIcon name="i-ri:edit-2-line" />
                  </FaButton>
                </span>
              </FaTooltip>
              <FaTooltip text="应用到当前角色" side="top">
                <span class="skin-action-tooltip">
                  <FaButton
                    size="icon-sm"
                    class="skin-action-icon"
                    :disabled="!model.selectedClosetItem || !model.selectedPlayer"
                    :loading="useLoading"
                    aria-label="应用到当前角色"
                    @click="model.useClosetItemOnSelectedPlayer()"
                  >
                    <FaIcon name="i-ri:t-shirt-2-line" />
                  </FaButton>
                </span>
              </FaTooltip>
              <FaTooltip text="移出衣柜" side="top">
                <span class="skin-action-tooltip">
                  <FaButton
                    variant="outline"
                    size="icon-sm"
                    class="skin-action-icon danger"
                    :disabled="!model.selectedClosetItem"
                    :loading="removeLoading"
                    aria-label="移出衣柜"
                    @click="model.selectedClosetItem && model.deleteClosetItem(model.selectedClosetItem)"
                  >
                    <FaIcon name="i-ri:delete-bin-6-line" />
                  </FaButton>
                </span>
              </FaTooltip>
            </div>
          </div>

          <div class="skin-detail-meta">
            <div>
              <span>类型</span>
              <strong>{{ selectedTextureKind }}</strong>
            </div>
            <div>
              <span>上传人</span>
              <strong>{{ selectedUploader }}</strong>
            </div>
            <div>
              <span>上传时间</span>
              <strong>{{ selectedUploadedAt }}</strong>
            </div>
            <div>
              <span>材质 Hash</span>
              <code class="skin-hash">{{ selectedHashShort }}</code>
            </div>
          </div>

          <p v-if="!model.selectedClosetItem" class="skin-help-text">从左侧衣柜选择一件外观后再操作。</p>
          <p v-else-if="!model.selectedPlayer" class="skin-help-text">先选择一个角色，就可以直接应用这件外观。</p>
        </div>
      </aside>
    </div>

    <FaModal
      v-model="renameVisible"
      title="重命名衣柜项"
      show-cancel-button
      class="sm:max-w-md"
      :confirm-loading="renameLoading"
      @confirm="submitRename"
    >
      <div class="skin-rename-form">
        <label>
          <span>显示名称</span>
          <FaInput v-model="model.closetRenameForm.itemName" placeholder="例如：夏日 Steve" />
        </label>
      </div>
    </FaModal>
  </section>
</template>

<script setup lang="ts">
import type { SkinPluginModel } from '../composables/useSkinPlugin'
import type { SkinClosetItem } from '../types'
import { computed, ref } from 'vue'
import { FaButton, FaIcon, FaInput, FaModal, FaTag, FaTooltip } from '@fantastic-admin/components'
import SkinPanel from '../components/SkinPanel.vue'
import SkinPreview from '../components/SkinPreview.vue'
import SkinTexturePreview from '../components/SkinTexturePreview.vue'

const props = defineProps<{
  model: SkinPluginModel
}>()

const renameVisible = ref(false)

const selectedTitle = computed(() => {
  return props.model.selectedClosetItem?.itemName || '选择衣柜项'
})
const selectedTextureName = computed(() => {
  return props.model.selectedClosetTexture?.name || '从左侧衣柜中选择后再操作'
})
const selectedHash = computed(() => {
  return props.model.selectedClosetTexture?.hash || props.model.selectedClosetItem?.textureHash || ''
})
const selectedHashShort = computed(() => {
  if (!selectedHash.value) {
    return '-'
  }
  return selectedHash.value.length > 18 ? `${selectedHash.value.slice(0, 10)}...${selectedHash.value.slice(-6)}` : selectedHash.value
})
const selectedTextureKind = computed(() => {
  const texture = props.model.selectedClosetTexture
  if (!texture) {
    return '-'
  }
  if (texture.type === 'cape') {
    return '披风'
  }
  return texture.model === 'slim' ? 'Alex 皮肤' : 'Steve 皮肤'
})
const selectedUploader = computed(() => {
  return props.model.userName(props.model.selectedClosetTexture?.uploaderId)
})
const selectedUploadedAt = computed(() => {
  return props.model.dateText(props.model.selectedClosetTexture?.uploadedAt)
})
const renameLoading = computed(() => {
  return props.model.saving.startsWith('closet-rename')
})
const removeLoading = computed(() => {
  return props.model.selectedClosetItem ? props.model.saving === `closet:${props.model.selectedClosetItem.id}` : false
})
const useLoading = computed(() => {
  return props.model.selectedClosetTexture ? props.model.saving === `use-texture:${props.model.selectedClosetTexture.hash}` : false
})

function openRename() {
  if (!props.model.selectedClosetItem) {
    return
  }
  props.model.closetRenameForm.itemName = props.model.selectedClosetItem.itemName || props.model.textureName(props.model.selectedClosetItem.textureHash)
  renameVisible.value = true
}

function closetTexture(item: SkinClosetItem) {
  return props.model.textures.find(texture => texture.hash === item.textureHash)
}

async function submitRename() {
  if (!props.model.selectedClosetItem || !props.model.closetRenameForm.itemName.trim()) {
    await props.model.renameClosetItem()
    return
  }
  props.model.closetRenameForm.itemName = props.model.closetRenameForm.itemName.trim()
  await props.model.renameClosetItem()
  renameVisible.value = false
}
</script>
