<template>
  <section class="mc-page">
    <section class="mc-hero">
      <div>
        <h2>{{ server?.name || '服务器详情' }}</h2>
        <p>{{ server?.currentSeason?.name || '当前周目未设置' }}</p>
      </div>
      <div class="mc-actions">
        <FaButton :loading="model.saving" variant="outline" @click="model.refreshStatus()">
          <FaIcon name="i-ri:radar-line" />
          刷新状态
        </FaButton>
        <a class="mc-button" href="/platform/plugins/minecraft-server">
          <FaIcon name="i-ri:list-check" />
          列表
        </a>
      </div>
    </section>

    <div v-if="server" class="mc-detail-layout">
      <McPanel title="在线状态" eyebrow="Status">
        <div class="mc-status-board">
          <StatusPill :status="server.status?.status" />
          <strong>{{ server.status?.onlinePlayers ?? 0 }} / {{ server.status?.maxPlayers ?? 0 }}</strong>
          <span>最后检查：{{ model.formatTime(server.status?.checkedAt) }}</span>
        </div>
        <div class="mc-line-list">
          <div v-for="endpoint in server.endpoints" :key="endpoint.id || endpoint.host" class="mc-line detail">
            <div>
              <strong>{{ endpoint.name }}</strong>
              <code>{{ endpoint.host }}:{{ endpoint.port }}</code>
            </div>
            <StatusPill :status="endpointStatus(endpoint.id)?.status" />
            <span>{{ endpoint.edition }}</span>
            <span>{{ endpointStatus(endpoint.id)?.ping ?? '-' }} ms</span>
          </div>
        </div>
      </McPanel>

      <McPanel title="服务器说明" eyebrow="Markdown">
        <MarkdownPreview :content="server.descriptionMarkdown" />
      </McPanel>

      <McPanel title="我的操作记录" eyebrow="Records">
        <div class="mc-table-wrap">
          <table class="mc-table">
            <thead>
              <tr>
                <th>时间</th>
                <th>类型</th>
                <th>币种</th>
                <th>金额</th>
                <th>备注</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="record in model.records" :key="record.id">
                <td>{{ model.formatTime(record.createdAt) }}</td>
                <td>{{ record.source || record.type }}</td>
                <td>{{ record.assetCode }}</td>
                <td class="amount-cell">{{ model.formatAmount(record.amount) }}</td>
                <td class="wrap-cell">{{ record.remark || record.businessNo || '-' }}</td>
              </tr>
              <tr v-if="!model.records.length">
                <td colspan="5"><div class="mc-empty compact">暂无操作记录</div></td>
              </tr>
            </tbody>
          </table>
        </div>
      </McPanel>
    </div>
    <McPanel v-else title="未找到服务器" eyebrow="Empty">
      <div class="mc-empty">请从服务器列表重新进入。</div>
    </McPanel>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { FaButton, FaIcon } from '@fantastic-admin/components'
import MarkdownPreview from '../components/MarkdownPreview.vue'
import McPanel from '../components/McPanel.vue'
import StatusPill from '../components/StatusPill.vue'
import type { MinecraftServerPluginModel } from '../composables/useMinecraftServerPlugin'

const props = defineProps<{
  model: MinecraftServerPluginModel
}>()

const server = computed(() => props.model.selectedServer)

function endpointStatus(endpointId?: string) {
  return server.value?.status?.endpoints.find(item => item.endpointId === endpointId)
}
</script>
