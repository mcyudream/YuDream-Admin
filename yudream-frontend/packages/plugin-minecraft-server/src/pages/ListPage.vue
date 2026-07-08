<template>
  <section class="mc-page">
    <section class="mc-hero">
      <div>
        <h2>MC 服务器</h2>
        <p>查看服务器在线状态、线路地址和当前周目。</p>
      </div>
      <div class="mc-actions">
        <a v-if="model.canManage" class="mc-button" href="/platform/plugins/minecraft-server/admin">
          <FaIcon name="i-ri:settings-3-line" />
          管理
        </a>
      </div>
    </section>

    <div class="mc-server-grid">
      <article v-for="server in model.servers" :key="server.id" class="mc-server-card">
        <header>
          <div>
            <h3>{{ server.name }}</h3>
            <span>{{ server.currentSeason?.name || '暂无周目' }}</span>
          </div>
          <StatusPill :status="server.status?.status" />
        </header>
        <div class="mc-online">
          <strong>{{ server.status?.onlinePlayers ?? 0 }}</strong>
          <span>/ {{ server.status?.maxPlayers ?? 0 }} 在线</span>
        </div>
        <div class="mc-line-list">
          <div v-for="endpoint in server.endpoints" :key="endpoint.id || endpoint.host" class="mc-line">
            <span>{{ endpoint.name }}</span>
            <code>{{ model.endpointAddress(endpoint) }}</code>
          </div>
        </div>
        <div class="mc-card-actions">
          <a class="mc-button primary" :href="`/platform/plugins/minecraft-server/detail?id=${encodeURIComponent(server.id)}`">
            <FaIcon name="i-ri:file-info-line" />
            详情
          </a>
        </div>
      </article>
      <McPanel v-if="!model.servers.length" title="暂无服务器" eyebrow="Empty">
        <div class="mc-empty">管理员还没有发布服务器。</div>
      </McPanel>
    </div>
  </section>
</template>

<script setup lang="ts">
import { FaIcon } from '@fantastic-admin/components'
import McPanel from '../components/McPanel.vue'
import StatusPill from '../components/StatusPill.vue'
import type { MinecraftServerPluginModel } from '../composables/useMinecraftServerPlugin'

defineProps<{
  model: MinecraftServerPluginModel
}>()
</script>
