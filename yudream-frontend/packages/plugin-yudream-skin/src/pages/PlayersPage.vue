<template>
  <section class="skin-page">
    <div class="skin-command-bar">
      <div>
        <span>{{ isManagement ? '角色管理' : '我的角色' }}</span>
        <h2>{{ model.selectedPlayerName || (isManagement ? '管理皮肤角色' : '管理 Minecraft 角色') }}</h2>
        <p>{{ isManagement ? '维护皮肤侧角色、归属用户与角色外观绑定。' : '角色属于当前账号，默认角色会作为皮肤库与衣柜一键应用的目标。' }}</p>
      </div>
    </div>

    <div class="skin-player-layout">
      <aside class="skin-character-card">
        <SkinPreview
          title="当前外观"
          :skin="model.selectedPlayerSkin"
          :cape="model.selectedPlayerCape"
          :slim="model.selectedPlayerSlim"
        >
          <div class="preview-meta">
            <span>皮肤：{{ model.textureName(model.selectedPlayer?.skinHash) }}</span>
            <span>披风：{{ model.textureName(model.selectedPlayer?.capeHash) }}</span>
          </div>
        </SkinPreview>
        <div class="skin-character-card__meta">
          <strong>{{ model.selectedPlayerName || '未选择角色' }}</strong>
          <span>{{ model.selectedPlayer?.uuid || '创建或选择一个角色后开始换装' }}</span>
          <FaTag v-if="model.selectedPlayerName && model.defaultPlayerName === model.selectedPlayerName" variant="secondary">
            默认角色
          </FaTag>
          <FaButton
            v-else-if="model.selectedPlayer"
            variant="outline"
            :loading="model.saving === `default-player:${model.selectedPlayer.name}`"
            @click="model.setDefaultPlayer(model.selectedPlayer)"
          >
            <FaIcon name="i-ri:star-line" />
            设为默认角色
          </FaButton>
        </div>
      </aside>

      <main class="skin-flow">
        <SkinPanel :title="isManagement ? '全部角色' : '角色'">
          <template #header>
            <FaTag variant="secondary">{{ model.players.length }}</FaTag>
          </template>
          <div class="skin-role-list">
            <div
              v-for="player in model.players"
              :key="player.uuid"
              class="skin-role-item"
              :class="{ active: model.selectedPlayerName === player.name, 'is-default': model.defaultPlayerName === player.name }"
            >
              <button type="button" class="skin-role-select" @click="model.selectPlayer(player)">
                <span class="skin-role-meta">
                  <strong>{{ player.name }}</strong>
                  <small>{{ isManagement ? `归属 ${model.userName(player.ownerId)}` : (player.skinHash ? '已配置外观' : '待换装') }}</small>
                </span>
                <span class="skin-role-badges">
                  <FaTag v-if="model.defaultPlayerName === player.name" variant="secondary">默认</FaTag>
                  <FaIcon name="i-ri:arrow-right-s-line" />
                </span>
              </button>
              <FaTooltip :text="model.defaultPlayerName === player.name ? '当前默认角色' : '设为默认角色'" side="top">
                <span class="skin-action-tooltip">
                  <FaButton
                    variant="ghost"
                    size="icon-sm"
                    class="skin-role-default"
                    :disabled="model.defaultPlayerName === player.name"
                    :loading="model.saving === `default-player:${player.name}`"
                    aria-label="设为默认角色"
                    @click="model.setDefaultPlayer(player)"
                  >
                    <FaIcon :name="model.defaultPlayerName === player.name ? 'i-ri:star-fill' : 'i-ri:star-line'" />
                  </FaButton>
                </span>
              </FaTooltip>
            </div>
            <div v-if="!model.players.length" class="empty-state">还没有角色，先创建一个 Minecraft ID。</div>
          </div>

          <div class="skin-inline-form">
            <FaInput v-model="model.playerForm.name" placeholder="输入角色名，例如 Steve" />
            <FaInput v-if="isManagement && model.canManage" v-model="model.playerForm.ownerId" placeholder="系统用户 ID" />
            <FaButton :loading="model.saving === 'player'" @click="model.createPlayer">
              <FaIcon name="i-ri:add-line" />
              创建角色
            </FaButton>
          </div>
        </SkinPanel>

        <SkinPanel title="从衣柜换装">
          <template #header>
            <FaTag variant="secondary">{{ model.closetItems.length }}</FaTag>
          </template>
          <div class="skin-dress-form">
            <label>
              <span>皮肤</span>
              <FaSelect v-model="model.assignForm.skinHash" clearable :options="model.skinTextureOptions" />
            </label>
            <label>
              <span>披风</span>
              <FaSelect v-model="model.assignForm.capeHash" clearable :options="model.capeTextureOptions" />
            </label>
            <FaButton :disabled="!model.selectedPlayer" :loading="model.saving === 'assign'" @click="model.assignTextures">
              <FaIcon name="i-ri:save-3-line" />
              保存外观
            </FaButton>
          </div>
          <p v-if="!model.closetItems.length" class="skin-help-text">衣柜是换装来源，可以先在皮肤库收藏材质，或上传自己的 PNG 材质。</p>
        </SkinPanel>

        <details class="skin-collapse" :open="false">
          <summary>
            <span>角色设置</span>
            <FaIcon name="i-ri:arrow-down-s-line" />
          </summary>
          <div class="skin-inline-form">
            <FaInput v-model="model.playerRenameForm.name" placeholder="新的角色名" />
            <FaButton variant="outline" :loading="model.saving === 'player-rename'" @click="model.renamePlayer">
              <FaIcon name="i-ri:edit-2-line" />
              重命名
            </FaButton>
            <FaButton variant="outline" :loading="model.saving === 'player-delete'" @click="model.deletePlayer">
              <FaIcon name="i-ri:delete-bin-6-line" />
              删除
            </FaButton>
          </div>
        </details>
      </main>
    </div>
  </section>
</template>

<script setup lang="ts">
import type { SkinPluginModel } from '../composables/useSkinPlugin'
import { computed } from 'vue'
import { FaButton, FaIcon, FaInput, FaSelect, FaTag, FaTooltip } from '@fantastic-admin/components'
import SkinPanel from '../components/SkinPanel.vue'
import SkinPreview from '../components/SkinPreview.vue'

const props = withDefaults(defineProps<{
  model: SkinPluginModel
  mode?: 'user' | 'management'
}>(), {
  mode: 'user',
})

const isManagement = computed(() => props.mode === 'management')
</script>
