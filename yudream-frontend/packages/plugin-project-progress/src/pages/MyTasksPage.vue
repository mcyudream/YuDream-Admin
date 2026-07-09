<script setup lang="ts">
import type { ProjectProgressModel } from '../composables/useProjectProgress'
import { useRouter } from 'vue-router'

const props = defineProps<{
  model: ProjectProgressModel
}>()

const router = useRouter()

async function goCheckIn(projectId: string) {
  await props.model.selectProjectById(projectId)
  await router.push({ name: 'platform-plugin-project-progress-check-ins' })
}
</script>

<template>
  <section class="pp-page">
    <section class="pp-toolbar">
      <div>
        <span>个人任务</span>
        <h2>我的任务</h2>
      </div>
      <a-button :loading="model.loading" @click="model.loadMyTasks">刷新</a-button>
    </section>

    <section class="pp-panel">
      <header class="pp-panel-head">
        <div>
          <h3>已分配任务</h3>
          <span>完成任务后提交验收，通过后才计入完成进度</span>
        </div>
      </header>
      <a-table :data="model.myTasks" :pagination="false" row-key="id">
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
          <a-table-column title="状态" :width="170">
            <template #cell="{ record }">
              <a-space wrap>
                <a-tag :color="record.pendingAcceptance ? 'orange' : 'arcoblue'">
                  {{ model.detailStatusLabel(record) }}
                </a-tag>
                <a-tag v-if="record.pendingAcceptance" color="orange">待验收</a-tag>
              </a-space>
            </template>
          </a-table-column>
          <a-table-column title="分配方式" :width="150">
            <template #cell="{ record }">
              {{ model.assignmentLabel(record) }}
            </template>
          </a-table-column>
          <a-table-column title="截止时间" :width="170">
            <template #cell="{ record }">
              {{ model.formatTime(record.dueAt) }}
            </template>
          </a-table-column>
          <a-table-column title="操作" :width="300" fixed="right">
            <template #cell="{ record }">
              <a-space>
                <a-button type="text" @click="goCheckIn(record.projectId)">
                  项目打卡
                </a-button>
                <a-button
                  v-if="model.canMinecraftCheckIn(record)"
                  type="text"
                  @click="model.minecraftCheckIn(record.projectId)"
                >
                  MC 打卡
                </a-button>
                <a-button
                  type="primary"
                  :disabled="!model.canSubmitAcceptance(record)"
                  :loading="model.saving"
                  @click="model.submitAcceptance(record)"
                >
                  提交验收
                </a-button>
              </a-space>
            </template>
          </a-table-column>
        </template>
      </a-table>
      <a-empty v-if="!model.myTasks.length" description="暂无分配给你的任务" />
    </section>
  </section>
</template>
