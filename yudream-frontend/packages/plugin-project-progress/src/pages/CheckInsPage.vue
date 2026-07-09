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
        <span>Check In</span>
        <h2>任务打卡</h2>
      </div>
      <button type="button" @click="model.loadMyTasks">加载我的任务</button>
    </section>

    <section class="pp-grid">
      <ProgressPanel title="提交打卡" :subtle="model.selectedDetail?.title || '请选择任务'">
        <form class="pp-form" @submit.prevent="model.submitCheckIn">
          <label>
            <span>工作细节</span>
            <select v-model="model.selectedDetailId" @change="model.selectedDetail && model.selectDetail(model.selectedDetail)">
              <option value="">请选择</option>
              <option v-for="task in model.myTasks" :key="task.id" :value="task.id">
                {{ task.title }}
              </option>
            </select>
          </label>
          <label>
            <span>打卡类型</span>
            <select v-model="model.checkInForm.type">
              <option value="IMAGE">图片</option>
              <option value="FILE">文件</option>
              <option value="LOCATION">定位</option>
            </select>
          </label>
          <label>
            <span>说明</span>
            <textarea v-model="model.checkInForm.summary" rows="3" />
          </label>
          <label v-if="model.checkInForm.type !== 'LOCATION'">
            <span>文件</span>
            <input type="file" @change="model.evidenceFile = ($event.target as HTMLInputElement).files?.[0] || null">
          </label>
          <template v-if="model.checkInForm.type === 'LOCATION'">
            <label>
              <span>地址</span>
              <input v-model="model.checkInForm.address" autocomplete="off">
            </label>
            <div class="pp-form-row">
              <label>
                <span>纬度</span>
                <input v-model="model.checkInForm.latitude" autocomplete="off">
              </label>
              <label>
                <span>经度</span>
                <input v-model="model.checkInForm.longitude" autocomplete="off">
              </label>
            </div>
          </template>
          <button class="pp-primary" type="submit" :disabled="model.saving">提交打卡</button>
        </form>
      </ProgressPanel>

      <ProgressPanel title="打卡记录">
        <div class="pp-feed">
          <article v-for="record in model.checkIns" :key="record.id">
            <strong>{{ record.type }} · {{ record.summary || '无说明' }}</strong>
            <span>{{ model.formatTime(record.createdAt) }}</span>
            <small v-if="record.minecraft">有效在线 {{ model.minutes(record.minecraft.effectiveOnlineMillis) }}</small>
            <small v-if="record.location">{{ record.location.address }}</small>
          </article>
          <div v-if="!model.checkIns.length" class="pp-empty">暂无打卡记录</div>
        </div>
      </ProgressPanel>
    </section>
  </section>
</template>
