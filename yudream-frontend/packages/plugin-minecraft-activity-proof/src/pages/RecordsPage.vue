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
        <span>History</span>
        <h2>活动证明记录</h2>
      </div>
      <div class="proof-actions">
        <button type="button" @click="model.loadRecords">
          <span class="i-ri:refresh-line" />
          刷新
        </button>
      </div>
    </section>

    <ProofPanel title="导出记录" eyebrow="Records">
      <div class="proof-table-wrap">
        <table class="proof-table records">
          <thead>
            <tr>
              <th>文件</th>
              <th>活动</th>
              <th>参与</th>
              <th>盖章 PDF</th>
              <th>生成时间</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in model.exports" :key="row.id">
              <td class="proof-main-cell">
                <strong>{{ row.outputFilename }}</strong>
                <span>{{ row.serverName || row.serverId }}</span>
              </td>
              <td>{{ row.activityName || '-' }}</td>
              <td>
                {{ row.participantCount }} 人
                <span v-if="row.unmatchedCount"> / {{ row.unmatchedCount }} 未匹配</span>
              </td>
              <td class="proof-main-cell">
                <strong>{{ row.stampedPdfReady ? row.stampedPdfFilename : '未上传' }}</strong>
                <span v-if="row.stampedPdfReady">
                  {{ model.formatFileSize(row.stampedPdfSize) }} / {{ model.formatTime(row.stampedPdfUploadedAt) }}
                </span>
              </td>
              <td>{{ model.formatTime(row.generatedAt) }}</td>
              <td>
                <div class="proof-row-actions">
                  <button type="button" @click="model.openDownload(row)">下载 Word</button>
                  <button v-if="row.stampedPdfReady" type="button" @click="model.openStampedPdf(row)">下载 PDF</button>
                  <label class="proof-upload">
                    <span>{{ row.stampedPdfReady ? '替换 PDF' : '上传 PDF' }}</span>
                    <input type="file" accept="application/pdf" @change="model.uploadStampedPdf(row, $event)">
                  </label>
                  <button type="button" class="danger" @click="model.deleteExportRecord(row)">删除</button>
                </div>
              </td>
            </tr>
            <tr v-if="!model.exports.length">
              <td colspan="6">
                <div class="proof-empty">暂无导出记录</div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </ProofPanel>
  </section>
</template>
