<script setup lang="ts">
import type { ProjectProgressModel } from '../composables/useProjectProgress'
import ProgressPanel from '../components/ProgressPanel.vue'

defineProps<{
  model: ProjectProgressModel
}>()
</script>

<template>
  <section class="pp-page">
    <section class="pp-toolbar">
      <div>
        <span>Acceptance</span>
        <h2>任务验收</h2>
      </div>
      <button type="button" @click="model.loadAcceptance">刷新</button>
    </section>

    <section class="pp-grid">
      <ProgressPanel title="待验收任务">
        <div class="pp-list">
          <button
            v-for="detail in model.pendingAcceptance"
            :key="detail.id"
            type="button"
            :class="{ active: detail.id === model.selectedAcceptanceId }"
            @click="model.selectedAcceptanceId = detail.id"
          >
            <strong>{{ detail.title }}</strong>
            <span>{{ detail.statusCode }} · {{ detail.assigneeUserIds.join(', ') || '未分配' }}</span>
          </button>
          <div v-if="!model.pendingAcceptance.length" class="pp-empty">暂无待验收任务</div>
        </div>
      </ProgressPanel>

      <ProgressPanel title="验收处理">
        <form class="pp-form" @submit.prevent>
          <label>
            <span>处理说明</span>
            <textarea v-model="model.acceptanceForm.reason" rows="5" />
          </label>
          <label>
            <span>目标状态（可选）</span>
            <input v-model="model.acceptanceForm.toStatusCode" autocomplete="off">
          </label>
          <div class="pp-actions">
            <button
              class="pp-primary"
              type="button"
              :disabled="!model.selectedAcceptanceId"
              @click="model.review(model.pendingAcceptance.find(item => item.id === model.selectedAcceptanceId)!, true)"
            >
              通过
            </button>
            <button
              type="button"
              :disabled="!model.selectedAcceptanceId"
              @click="model.review(model.pendingAcceptance.find(item => item.id === model.selectedAcceptanceId)!, false)"
            >
              退回返工
            </button>
          </div>
        </form>
      </ProgressPanel>
    </section>
  </section>
</template>
