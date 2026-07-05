<template>
  <section class="skin-page skin-dashboard">
    <div class="skin-dashboard-hero">
      <div class="skin-dashboard-hero__copy">
        <span>我的皮肤站</span>
        <h2>{{ heroTitle }}</h2>
        <p>{{ heroDescription }}</p>
      </div>
      <div class="skin-dashboard-hero__actions">
        <FaButton
          variant="outline"
          class="skin-icon-button"
          title="刷新数据"
          :loading="model.loading"
          @click="model.load"
        >
          <FaIcon name="i-ri:refresh-line" />
        </FaButton>
        <a class="skin-primary-link" href="/platform/plugins/blessing-skin/players">
          <FaIcon name="i-ri:gamepad-line" />
          管理角色
        </a>
      </div>
    </div>

    <div class="skin-dashboard-stats">
      <div>
        <span>我的角色</span>
        <strong>{{ model.players.length }}</strong>
      </div>
      <div>
        <span>衣柜材质</span>
        <strong>{{ model.closetItems.length }}</strong>
      </div>
      <div>
        <span>可用材质</span>
        <strong>{{ model.textures.length }}</strong>
      </div>
    </div>

    <div class="skin-dashboard-layout">
      <section class="skin-current-card">
        <div class="skin-current-card__preview">
          <SkinPreview
            :skin="model.selectedPlayerSkin"
            :cape="model.selectedPlayerCape"
            :slim="model.selectedPlayerSlim"
            :auto-rotate="false"
            compact
          />
        </div>
        <div class="skin-current-card__body">
          <span>当前角色</span>
          <h3>{{ model.selectedPlayer?.name || '还没有角色' }}</h3>
          <p>{{ currentPlayerDescription }}</p>
          <div class="skin-current-card__actions">
            <a class="skin-secondary-link" href="/platform/plugins/blessing-skin/players">
              <FaIcon name="i-ri:edit-2-line" />
              角色管理
            </a>
            <a class="skin-secondary-link" href="/platform/plugins/blessing-skin/closet">
              <FaIcon name="i-ri:archive-drawer-line" />
              打开衣柜
            </a>
          </div>
        </div>
      </section>

      <aside class="skin-dashboard-side">
        <SkinPanel title="最近衣柜">
          <div class="skin-mini-list">
            <a
              v-for="item in model.closetItems.slice(0, 4)"
              :key="item.id"
              class="skin-mini-row"
              href="/platform/plugins/blessing-skin/closet"
            >
              <img :src="model.textureUrl(item.textureHash)" alt="">
              <span>
                <strong>{{ item.itemName || model.textureName(item.textureHash) }}</strong>
                <small>{{ model.textureName(item.textureHash) }}</small>
              </span>
              <FaIcon name="i-ri:arrow-right-s-line" />
            </a>
            <div v-if="!model.closetItems.length" class="empty-state compact">
              衣柜还是空的，先去皮肤库收藏或上传材质。
            </div>
          </div>
        </SkinPanel>

        <SkinPanel title="下一步">
          <div class="skin-next-actions">
            <a v-if="!model.players.length" href="/platform/plugins/blessing-skin/players">
              <FaIcon name="i-ri:add-circle-line" />
              创建你的第一个角色
            </a>
            <a v-if="model.players.length && !model.closetItems.length" href="/platform/plugins/blessing-skin/textures">
              <FaIcon name="i-ri:t-shirt-2-line" />
              去皮肤库添加材质
            </a>
            <a v-if="model.players.length && model.closetItems.length" href="/platform/plugins/blessing-skin/players">
              <FaIcon name="i-ri:magic-line" />
              给当前角色换装
            </a>
            <a v-if="model.canManage" href="/platform/plugins/blessing-skin/system">
              <FaIcon name="i-ri:settings-3-line" />
              皮肤站管理
            </a>
          </div>
        </SkinPanel>
      </aside>
    </div>
  </section>
</template>

<script setup lang="ts">
import type { SkinPluginModel } from '../composables/useSkinPlugin'
import { computed } from 'vue'
import { FaButton, FaIcon } from '@fantastic-admin/components'
import SkinPanel from '../components/SkinPanel.vue'
import SkinPreview from '../components/SkinPreview.vue'

const props = defineProps<{
  model: SkinPluginModel
}>()

const heroTitle = computed(() => {
  if (props.model.selectedPlayer?.name) {
    return `${props.model.selectedPlayer.name} 的外观已就绪`
  }
  return `欢迎回来，${props.model.accountName || '玩家'}`
})

const heroDescription = computed(() => {
  if (!props.model.players.length) {
    return '先创建一个 Minecraft 角色，再从皮肤库或衣柜里选择外观。'
  }
  if (!props.model.closetItems.length) {
    return '角色已经准备好，下一步可以把喜欢的皮肤保存到衣柜。'
  }
  return '从衣柜选择皮肤或披风，保存后即可同步到当前角色。'
})

const currentPlayerDescription = computed(() => {
  const player = props.model.selectedPlayer
  if (!player) {
    return '创建角色后，这里会展示当前外观和快速换装入口。'
  }
  if (!player.skinHash && !player.capeHash) {
    return '这个角色还没有绑定外观，可以从衣柜里选择皮肤或披风。'
  }
  const skin = props.model.textureName(player.skinHash)
  const cape = props.model.textureName(player.capeHash)
  return `皮肤：${skin} / 披风：${cape}`
})
</script>
