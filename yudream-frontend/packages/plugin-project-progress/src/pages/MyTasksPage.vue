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
        <span>My Work</span>
        <h2>我的任务</h2>
      </div>
      <button type="button" @click="model.loadMyTasks">刷新</button>
    </section>

    <ProgressPanel title="已分配任务" subtle="选择任务后可在打卡页提交证据">
      <table class="pp-table">
        <thead>
          <tr>
            <th>任务</th>
            <th>状态</th>
            <th>分配方式</th>
            <th>截止时间</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="task in model.myTasks" :key="task.id">
            <td>
              <strong>{{ task.title }}</strong>
              <span>{{ task.description || '暂无说明' }}</span>
            </td>
            <td>{{ model.statusLabel(task.statusCode) }}</td>
            <td>{{ model.assignmentLabel(task) }}</td>
            <td>{{ model.formatTime(task.dueAt) }}</td>
            <td>
              <button type="button" @click="model.selectDetail(task)">查看打卡</button>
              <button v-if="model.canMinecraftCheckIn(task)" type="button" @click="model.minecraftCheckIn(task)">MC 打卡</button>
            </td>
          </tr>
          <tr v-if="!model.myTasks.length">
            <td colspan="5">
              <div class="pp-empty">暂无分配给你的任务</div>
            </td>
          </tr>
        </tbody>
      </table>
    </ProgressPanel>
  </section>
</template>
