<template>
  <section class="skin-page">
    <div class="skin-command-bar">
      <div>
        <span>皮肤库</span>
        <h2>浏览全站材质</h2>
        <p>选择一个材质查看 3D 效果，保存到衣柜后即可给自己的角色换装。</p>
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
      <SkinPanel title="材质">
        <template #header>
          <FaTag variant="secondary">{{ model.textures.length }}</FaTag>
        </template>
        <div class="skin-card-grid">
          <button
            v-for="texture in model.textures"
            :key="texture.hash"
            type="button"
            class="skin-texture-card"
            :class="{ active: model.selectedTextureHash === texture.hash }"
            @click="model.selectTexture(texture)"
          >
            <span class="skin-texture-card__image">
              <img :src="model.textureUrl(texture.hash)" alt="">
            </span>
            <span class="skin-texture-card__body">
              <strong>{{ texture.name }}</strong>
              <small>{{ texture.type === 'cape' ? '披风' : texture.model === 'slim' ? 'Alex 皮肤' : 'Steve 皮肤' }}</small>
            </span>
          </button>
          <div v-if="!model.textures.length" class="empty-state">暂无材质</div>
        </div>
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

        <div class="skin-action-panel">
          <div>
            <strong>{{ model.selectedTexture?.name || '选择一个材质' }}</strong>
            <span>{{ model.selectedTexture?.hash || '从左侧材质库中选择后再操作' }}</span>
          </div>
          <div class="skin-action-panel__buttons">
            <FaButton
              v-if="model.canUse"
              variant="outline"
              :disabled="!model.selectedTexture"
              :loading="model.saving === 'closet'"
              @click="model.selectedTexture && model.addTextureToCloset(model.selectedTexture)"
            >
              <FaIcon name="i-ri:archive-drawer-line" />
              加入衣柜
            </FaButton>
            <FaButton
              v-if="model.canUse"
              :disabled="!model.selectedTexture || !model.selectedPlayer"
              :loading="model.selectedTexture ? model.saving === `use-texture:${model.selectedTexture.hash}` : false"
              @click="model.useTextureOnSelectedPlayer()"
            >
              <FaIcon name="i-ri:t-shirt-2-line" />
              应用到当前角色
            </FaButton>
          </div>
          <p v-if="model.canUse && !model.selectedPlayer" class="skin-help-text">先在“我的角色”中选择或创建角色，再一键应用材质。</p>
        </div>
      </aside>
    </div>

    <FaModal
      v-model="uploadVisible"
      title="上传材质"
      description="上传 PNG 皮肤或披风，普通用户会自动保存到自己的衣柜。"
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
import { computed, ref } from 'vue'
import { FaButton, FaIcon, FaInput, FaModal, FaSelect, FaSwitch, FaTag } from '@fantastic-admin/components'
import SkinPanel from '../components/SkinPanel.vue'
import SkinPreview from '../components/SkinPreview.vue'

const props = defineProps<{
  model: SkinPluginModel
}>()

const uploadVisible = ref(false)
const textureTypeOptions = [
  { label: 'Steve 皮肤', value: 'steve' },
  { label: 'Alex 皮肤', value: 'alex' },
  { label: '披风', value: 'cape' },
]

const selectedKind = computed(() => {
  const texture = props.model.selectedTexture
  if (!texture) {
    return '-'
  }
  if (texture.type === 'cape') {
    return '披风'
  }
  return texture.model === 'slim' ? 'Alex 皮肤' : 'Steve 皮肤'
})

async function submitUpload() {
  await props.model.uploadTexture()
  uploadVisible.value = false
}
</script>
