<script setup lang="ts">
import type { ProjectProgressModel } from '../composables/useProjectProgress'
import type { ProjectWorkDetail } from '../types'
import { ref } from 'vue'

const props = defineProps<{
  model: ProjectProgressModel
}>()

const reviewVisible = ref(false)
const reviewingDetail = ref<ProjectWorkDetail | null>(null)
const accepted = ref(true)

function openReview(detail: ProjectWorkDetail, nextAccepted: boolean) {
  reviewingDetail.value = detail
  accepted.value = nextAccepted
  props.model.acceptanceForm.reason = ''
  reviewVisible.value = true
}

async function submitReview() {
  if (!reviewingDetail.value) {
    return
  }
  await props.model.review(reviewingDetail.value, accepted.value)
  reviewVisible.value = false
}

function acceptanceFileNames(detail: ProjectWorkDetail) {
  return detail.acceptanceFiles?.length ? detail.acceptanceFiles.map(file => file.filename).join('、') : '未上传附件'
}
</script>

<template>
  <section class="pp-page">
    <section class="pp-toolbar">
      <div>
        <span>验收处理</span>
        <h2>任务验收</h2>
      </div>
      <a-button :loading="model.loading" @click="model.loadAcceptance">刷新</a-button>
    </section>

    <section class="pp-panel">
      <header class="pp-panel-head">
        <div>
          <h3>待验收任务</h3>
          <span>只有提交验收的任务会出现在这里</span>
        </div>
      </header>
      <a-table :data="model.pendingAcceptance" :loading="model.loading" :pagination="false" row-key="id">
        <template #columns>
          <a-table-column title="项目" :width="180">
            <template #cell="{ record }">
              {{ model.projectName(record.projectId) }}
            </template>
          </a-table-column>
          <a-table-column title="任务">
            <template #cell="{ record }">
              <strong>{{ record.title }}</strong>
              <div class="pp-table-sub">{{ record.description || '暂无说明' }}</div>
            </template>
          </a-table-column>
          <a-table-column title="状态" :width="160">
            <template #cell="{ record }">
              <a-space wrap>
                <a-tag color="orange">{{ model.detailStatusLabel(record) }}</a-tag>
                <a-tag color="orange">待验收</a-tag>
              </a-space>
            </template>
          </a-table-column>
          <a-table-column title="负责人" :width="240">
            <template #cell="{ record }">
              <a-space wrap>
                <a-tag v-for="user in model.userOptionsForIds(record.assigneeUserIds)" :key="user.id">
                  {{ model.userLabel(user) }}
                </a-tag>
              </a-space>
            </template>
          </a-table-column>
          <a-table-column title="验收材料" :width="260">
            <template #cell="{ record }">
              <strong>{{ record.acceptanceSummary || '无说明' }}</strong>
              <div class="pp-table-sub">
                {{ acceptanceFileNames(record) }}
              </div>
            </template>
          </a-table-column>
          <a-table-column title="截止时间" :width="170">
            <template #cell="{ record }">
              {{ model.formatTime(record.dueAt) }}
            </template>
          </a-table-column>
          <a-table-column title="操作" :width="190" fixed="right">
            <template #cell="{ record }">
              <a-space>
                <a-button type="primary" @click="openReview(record, true)">通过</a-button>
                <a-button status="danger" @click="openReview(record, false)">退回</a-button>
              </a-space>
            </template>
          </a-table-column>
        </template>
      </a-table>
      <a-empty v-if="!model.pendingAcceptance.length" description="暂无待验收任务" />
    </section>

    <a-modal
      v-model:visible="reviewVisible"
      :title="accepted ? '验收通过' : '退回返工'"
      :width="560"
      :mask-closable="false"
    >
      <a-form :model="model.acceptanceForm" layout="vertical">
        <a-form-item label="处理说明">
          <a-textarea
            v-model="model.acceptanceForm.reason"
            :auto-size="{ minRows: 4, maxRows: 7 }"
            :placeholder="accepted ? '填写通过说明' : '填写返工原因'"
          />
        </a-form-item>
      </a-form>
      <template #footer>
        <a-space>
          <a-button @click="reviewVisible = false">取消</a-button>
          <a-button
            :type="accepted ? 'primary' : 'outline'"
            :status="accepted ? 'normal' : 'danger'"
            :loading="model.saving"
            @click="submitReview"
          >
            {{ accepted ? '确认通过' : '确认退回' }}
          </a-button>
        </a-space>
      </template>
    </a-modal>
  </section>
</template>
