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
        <span>任务认领</span>
        <h2>任务中心</h2>
      </div>
      <a-button :loading="model.loading" @click="model.loadTaskCenter">刷新</a-button>
    </section>

    <div class="pp-metrics">
      <article>
        <span>可认领</span>
        <strong>{{ model.claimableTasks.length }}</strong>
      </article>
      <article>
        <span>我的任务</span>
        <strong>{{ model.myTasks.length }}</strong>
      </article>
      <article>
        <span>Minecraft 联动</span>
        <strong>{{ model.status?.minecraftReady ? '已启用' : '未启用' }}</strong>
      </article>
      <article>
        <span>邮件通知</span>
        <strong>{{ model.status?.mailReady ? '可用' : '未配置' }}</strong>
      </article>
    </div>

    <section class="pp-panel">
      <header class="pp-panel-head">
        <div>
          <h3>可认领任务</h3>
          <span>公开任务可直接认领，指定候选人的任务只会对候选人显示</span>
        </div>
      </header>
      <a-table :data="model.claimableTasks" :pagination="false" row-key="id">
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
          <a-table-column title="名额" :width="110">
            <template #cell="{ record }">
              {{ record.assigneeUserIds.length }} / {{ record.requiredAssigneeCount }}
            </template>
          </a-table-column>
          <a-table-column title="截止时间" :width="170">
            <template #cell="{ record }">
              {{ model.formatTime(record.dueAt) }}
            </template>
          </a-table-column>
          <a-table-column title="操作" :width="120">
            <template #cell="{ record }">
              <a-button type="primary" :loading="model.saving" @click="model.claim(record)">
                认领
              </a-button>
            </template>
          </a-table-column>
        </template>
      </a-table>
      <a-empty v-if="!model.claimableTasks.length" description="暂无可认领任务" />
    </section>

    <section class="pp-panel">
      <header class="pp-panel-head">
        <div>
          <h3>我的任务</h3>
          <span>打卡按项目记录，任务完成必须先提交验收</span>
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
          <a-table-column title="截止时间" :width="170">
            <template #cell="{ record }">
              {{ model.formatTime(record.dueAt) }}
            </template>
          </a-table-column>
          <a-table-column title="操作" :width="280" fixed="right">
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
      <a-empty v-if="!model.myTasks.length" description="你还没有负责的任务" />
    </section>
  </section>
</template>
