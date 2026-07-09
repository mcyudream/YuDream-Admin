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
        <span>Task Center</span>
        <h2>任务中心</h2>
      </div>
      <button type="button" :disabled="model.loading" @click="model.loadTaskCenter">
        刷新
      </button>
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

    <ProgressPanel title="可认领任务" subtle="公开任务直接认领；限制候选人的任务只对候选人显示">
      <table class="pp-table">
        <thead>
          <tr>
            <th>项目</th>
            <th>任务</th>
            <th>名额</th>
            <th>截止时间</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="task in model.claimableTasks" :key="task.id">
            <td>{{ model.projectName(task.projectId) }}</td>
            <td>
              <strong>{{ task.title }}</strong>
              <span>{{ task.description || '暂无说明' }}</span>
            </td>
            <td>{{ task.assigneeUserIds.length }} / {{ task.requiredAssigneeCount }}</td>
            <td>{{ model.formatTime(task.dueAt) }}</td>
            <td>
              <button class="pp-primary" type="button" :disabled="model.saving" @click="model.claim(task)">
                认领
              </button>
            </td>
          </tr>
          <tr v-if="!model.claimableTasks.length">
            <td colspan="5">
              <div class="pp-empty">暂无可认领任务</div>
            </td>
          </tr>
        </tbody>
      </table>
    </ProgressPanel>

    <ProgressPanel title="我的任务" subtle="认领或分配给你的任务会出现在这里，可直接进入打卡动作">
      <table class="pp-table">
        <thead>
          <tr>
            <th>项目</th>
            <th>任务</th>
            <th>状态</th>
            <th>截止时间</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="task in model.myTasks" :key="task.id">
            <td>{{ model.projectName(task.projectId) }}</td>
            <td>
              <strong>{{ task.title }}</strong>
              <span>{{ task.description || '暂无说明' }}</span>
            </td>
            <td>{{ task.statusCode }}</td>
            <td>{{ model.formatTime(task.dueAt) }}</td>
            <td>
              <button type="button" @click="model.selectDetail(task)">
                查看打卡
              </button>
              <button v-if="model.canMinecraftCheckIn(task)" type="button" @click="model.minecraftCheckIn(task)">
                MC 打卡
              </button>
            </td>
          </tr>
          <tr v-if="!model.myTasks.length">
            <td colspan="5">
              <div class="pp-empty">你还没有负责的任务</div>
            </td>
          </tr>
        </tbody>
      </table>
    </ProgressPanel>
  </section>
</template>
