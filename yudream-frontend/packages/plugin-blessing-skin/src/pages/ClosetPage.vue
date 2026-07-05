<template>
  <section class="skin-page">
    <div class="skin-command-bar">
      <div>
        <span>我的衣柜</span>
        <h2>{{ model.selectedClosetItem?.itemName || '收藏的材质' }}</h2>
        <p>衣柜只展示当前账号可使用的材质，选中后可以预览、重命名或直接应用到角色。</p>
      </div>
      <FaButton variant="outline" :loading="model.loading" @click="model.load">
        <FaIcon name="i-ri:refresh-line" />
        刷新
      </FaButton>
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
            <span class="skin-texture-card__image">
              <img :src="model.textureUrl(item.textureHash)" alt="">
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
            <span>{{ model.selectedClosetTexture?.type === 'cape' ? '披风' : '皮肤' }}</span>
          </div>
        </SkinPreview>

        <div class="skin-action-panel">
          <div>
            <strong>{{ model.selectedClosetItem?.itemName || '选择衣柜项' }}</strong>
            <span>{{ model.selectedClosetTexture?.hash || '从左侧衣柜中选择后再操作' }}</span>
          </div>
          <div class="skin-inline-form single">
            <FaInput v-model="model.closetRenameForm.itemName" placeholder="显示名称" />
            <FaButton variant="outline" :disabled="!model.selectedClosetItem" :loading="model.saving.startsWith('closet-rename')" @click="model.renameClosetItem()">
              <FaIcon name="i-ri:edit-2-line" />
              保存名称
            </FaButton>
          </div>
          <div class="skin-action-panel__buttons">
            <FaButton :disabled="!model.selectedClosetItem || !model.selectedPlayer" @click="model.useClosetItemOnSelectedPlayer()">
              <FaIcon name="i-ri:t-shirt-2-line" />
              应用到当前角色
            </FaButton>
            <FaButton
              variant="outline"
              :disabled="!model.selectedClosetItem"
              :loading="model.selectedClosetItem ? model.saving === `closet:${model.selectedClosetItem.id}` : false"
              @click="model.selectedClosetItem && model.deleteClosetItem(model.selectedClosetItem)"
            >
              <FaIcon name="i-ri:delete-bin-6-line" />
              移出衣柜
            </FaButton>
          </div>
          <p v-if="!model.selectedPlayer" class="skin-help-text">先选择一个角色后，可以从这里直接应用衣柜材质。</p>
        </div>
      </aside>
    </div>
  </section>
</template>

<script setup lang="ts">
import type { SkinPluginModel } from '../composables/useSkinPlugin'
import { FaButton, FaIcon, FaInput, FaTag } from '@fantastic-admin/components'
import SkinPanel from '../components/SkinPanel.vue'
import SkinPreview from '../components/SkinPreview.vue'

defineProps<{
  model: SkinPluginModel
}>()
</script>
