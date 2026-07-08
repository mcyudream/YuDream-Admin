<template>
  <section class="student-info-page">
    <section class="student-info-hero">
      <div>
        <span>我的学生信息</span>
        <h2>{{ model.accountName }}</h2>
      </div>
      <div class="student-info-badges">
        <span :class="['student-info-tag', model.profileReady ? 'success' : 'muted']">
          {{ model.profileStatus(model.profile) }}
        </span>
        <a v-if="model.canManage" class="student-info-link-button" href="/platform/plugins/yudream-student-info/admin/profiles">
          <FaIcon name="i-ri:list-check-3-line" />
          管理
        </a>
      </div>
    </section>

    <div class="student-info-layout">
      <StudentInfoPanel title="学籍信息" eyebrow="Profile">
        <form class="student-info-form" @submit.prevent="model.saveMine">
          <label>
            <span>学号</span>
            <input v-model="model.profileForm.studentNo" autocomplete="off" maxlength="64">
          </label>
          <label>
            <span>班级</span>
            <input v-model="model.profileForm.className" autocomplete="off" maxlength="80">
          </label>
          <label>
            <span>学院</span>
            <input v-model="model.profileForm.college" autocomplete="off" maxlength="80">
          </label>
          <div class="student-info-actions">
            <FaButton :loading="model.saving" type="submit">
              <FaIcon name="i-ri:save-3-line" />
              保存
            </FaButton>
          </div>
        </form>
      </StudentInfoPanel>

      <StudentInfoPanel title="当前记录" eyebrow="Snapshot">
        <dl class="student-info-detail">
          <div>
            <dt>学号</dt>
            <dd>{{ model.profile?.studentNo || '-' }}</dd>
          </div>
          <div>
            <dt>班级</dt>
            <dd>{{ model.profile?.className || '-' }}</dd>
          </div>
          <div>
            <dt>学院</dt>
            <dd>{{ model.profile?.college || '-' }}</dd>
          </div>
          <div>
            <dt>更新时间</dt>
            <dd>{{ model.formatTime(model.profile?.updatedAt) }}</dd>
          </div>
        </dl>
      </StudentInfoPanel>
    </div>
  </section>
</template>

<script setup lang="ts">
import type { StudentInfoPluginModel } from '../composables/useStudentInfoPlugin'
import { FaButton, FaIcon } from '@fantastic-admin/components'
import StudentInfoPanel from '../components/StudentInfoPanel.vue'

defineProps<{
  model: StudentInfoPluginModel
}>()
</script>
