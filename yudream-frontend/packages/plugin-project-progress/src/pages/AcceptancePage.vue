<script setup lang="ts">
import type { ProjectProgressModel } from '../composables/useProjectProgress'
import { computed } from 'vue'
import ProgressPanel from '../components/ProgressPanel.vue'

const props = defineProps<{
  model: ProjectProgressModel
}>()

const selectedDetail = computed(() => props.model.pendingAcceptance.find(item => item.id === props.model.selectedAcceptanceId) || null)
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
            <span>
              {{ model.statusLabel(detail.statusCode) }} ·
              {{ detail.assigneeUserIds.length ? model.userOptionsForIds(detail.assigneeUserIds).map(model.userLabel).join('、') : '未分配' }}
            </span>
          </button>
          <div v-if="!model.pendingAcceptance.length" class="pp-empty">暂无待验收任务</div>
        </div>
      </ProgressPanel>

      <ProgressPanel title="验收处理" :subtle="selectedDetail?.title || '请选择任务'">
        <form class="pp-form" @submit.prevent>
          <label>
            <span>处理说明</span>
            <textarea v-model="model.acceptanceForm.reason" rows="5" placeholder="通过说明或返工原因" />
          </label>
          <div class="pp-actions">
            <button
              class="pp-primary"
              type="button"
              :disabled="!selectedDetail"
              @click="selectedDetail && model.review(selectedDetail, true)"
            >
              通过
            </button>
            <button
              type="button"
              :disabled="!selectedDetail"
              @click="selectedDetail && model.review(selectedDetail, false)"
            >
              退回返工
            </button>
          </div>
        </form>
      </ProgressPanel>
    </section>
  </section>
</template>
