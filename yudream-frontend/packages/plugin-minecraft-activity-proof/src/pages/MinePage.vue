<script setup lang="ts">
import type { ActivityProofModel } from '../composables/useActivityProof'
import ProofPanel from '../components/ProofPanel.vue'

defineProps<{
  model: ActivityProofModel
}>()
</script>

<template>
  <section class="proof-page">
    <section class="proof-toolbar">
      <div>
        <span>Stamped</span>
        <h2>我的活动证明</h2>
      </div>
      <div class="proof-actions">
        <button type="button" @click="model.loadMine">
          <span class="i-ri:refresh-line" />
          刷新
        </button>
      </div>
    </section>

    <ProofPanel title="可下载证明" eyebrow="PDF">
      <div class="proof-list records">
        <article v-for="row in model.myExports" :key="row.id">
          <div>
            <strong>{{ row.activityName || row.stampedPdfFilename }}</strong>
            <span>{{ row.serverName || row.serverId }} / {{ model.formatTime(row.stampedPdfUploadedAt) }}</span>
          </div>
          <button type="button" @click="model.openStampedPdf(row)">下载 PDF</button>
        </article>
        <div v-if="!model.myExports.length" class="proof-empty">暂无已盖章活动证明</div>
      </div>
    </ProofPanel>
  </section>
</template>
