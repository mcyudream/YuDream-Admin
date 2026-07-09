<script setup lang="ts">
import type { TableColumn } from '@fantastic-admin/components'
import type { ProjectProgressModel } from '../composables/useProjectProgress'
import type { ProjectMemberStats } from '../types'
import { computed } from 'vue'
import { FaButton, FaIcon, FaSelect, FaTable } from '@fantastic-admin/components'

const props = defineProps<{
  model: ProjectProgressModel
}>()

const projectOptions = computed(() => props.model.projects.map(project => ({
  label: project.name,
  value: project.id,
})))

const tableColumns = computed<TableColumn<ProjectMemberStats>[]>(() => [
  { id: 'user', header: '成员', width: 260, fixed: 'left' },
  { accessorKey: 'assignedDetails', header: '分配细分', width: 120, align: 'center' },
  { accessorKey: 'completedDetails', header: '已完成', width: 110, align: 'center' },
  { accessorKey: 'pendingAcceptanceDetails', header: '待验收', width: 110, align: 'center' },
  { accessorKey: 'acceptedReviews', header: '通过', width: 96, align: 'center' },
  { accessorKey: 'rejectedReviews', header: '退回', width: 96, align: 'center' },
  { accessorKey: 'checkIns', header: '打卡', width: 96, align: 'center' },
  { id: 'lastActivityAt', header: '最近活动', width: 180 },
])

async function selectProject(projectId: unknown) {
  const value = String(projectId || '')
  await props.model.selectProjectById(value)
  await props.model.loadMemberStats(value)
}

function userFor(userId: string) {
  return props.model.userOptionsForIds([userId])[0]
}
</script>

<template>
  <section class="pp-page">
    <section class="pp-toolbar">
      <div>
        <span>项目成员</span>
        <h2>成员统计</h2>
      </div>
      <div class="pp-actions">
        <FaSelect
          :model-value="model.selectedProjectId"
          :options="projectOptions"
          placeholder="选择项目"
          class="pp-project-select"
          @update:model-value="selectProject"
        />
        <FaButton :loading="model.loading" @click="model.loadMemberStats()">
          <FaIcon name="i-ri:refresh-line" />
          刷新
        </FaButton>
      </div>
    </section>

    <section class="pp-panel">
      <div class="pp-stat-grid">
        <article class="pp-stat-card">
          <span>项目成员</span>
          <strong>{{ model.memberStats.length }}</strong>
        </article>
        <article class="pp-stat-card">
          <span>分配细分</span>
          <strong>{{ model.memberStats.reduce((sum, item) => sum + item.assignedDetails, 0) }}</strong>
        </article>
        <article class="pp-stat-card">
          <span>已完成细分</span>
          <strong>{{ model.memberStats.reduce((sum, item) => sum + item.completedDetails, 0) }}</strong>
        </article>
        <article class="pp-stat-card">
          <span>验收通过</span>
          <strong>{{ model.memberStats.reduce((sum, item) => sum + item.acceptedReviews, 0) }}</strong>
        </article>
        <article class="pp-stat-card">
          <span>退回返工</span>
          <strong>{{ model.memberStats.reduce((sum, item) => sum + item.rejectedReviews, 0) }}</strong>
        </article>
        <article class="pp-stat-card">
          <span>打卡次数</span>
          <strong>{{ model.memberStats.reduce((sum, item) => sum + item.checkIns, 0) }}</strong>
        </article>
      </div>
    </section>

    <section class="pp-panel">
      <header class="pp-panel-head">
        <div>
          <h3>成员表现</h3>
          <span>统计口径：完成按细分当前完成状态，通过/退回按验收处理记录计数</span>
        </div>
      </header>
      <FaTable
        row-key="userId"
        table-root-class="pp-fa-table-root"
        table-class="pp-fa-table"
        border
        stripe
        :columns="tableColumns"
        :data="model.memberStats"
        empty-text="暂无成员统计"
      >
        <template #cell-user="{ row }">
          <strong>{{ model.userLabel(userFor(row.original.userId)) }}</strong>
          <div class="pp-table-sub">{{ model.userMeta(userFor(row.original.userId)) }}</div>
        </template>
        <template #cell-lastActivityAt="{ row }">
          {{ model.formatTime(row.original.lastActivityAt) }}
        </template>
      </FaTable>
    </section>
  </section>
</template>
