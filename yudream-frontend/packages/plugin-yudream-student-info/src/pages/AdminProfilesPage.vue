<template>
  <section class="student-info-page">
    <section class="student-info-hero">
      <div>
        <span>学生档案管理</span>
        <h2>{{ model.summary?.profileCount ?? 0 }} 条档案</h2>
      </div>
      <div class="student-info-badges">
        <FaButton variant="outline" @click="model.resetAdminForm">
          <FaIcon name="i-ri:add-line" />
          新建
        </FaButton>
      </div>
    </section>

    <StudentInfoPanel title="筛选" eyebrow="Filter">
      <form class="student-info-filter" @submit.prevent="model.applyFilters">
        <label>
          <span>关键词</span>
          <input v-model="model.filters.keyword" autocomplete="off">
        </label>
        <label>
          <span>学院</span>
          <input v-model="model.filters.college" autocomplete="off">
        </label>
        <label>
          <span>班级</span>
          <input v-model="model.filters.className" autocomplete="off">
        </label>
        <div class="student-info-actions student-info-filter-actions">
          <FaButton variant="outline" type="button" @click="model.resetFilters">
            重置
          </FaButton>
          <FaButton type="submit">
            <FaIcon name="i-ri:search-line" />
            查询
          </FaButton>
        </div>
      </form>
    </StudentInfoPanel>

    <div class="student-info-layout wide-left">
      <StudentInfoPanel title="档案列表" eyebrow="Profiles">
        <div class="student-info-table-wrap">
          <table class="student-info-table">
            <thead>
              <tr>
                <th>用户</th>
                <th>姓名</th>
                <th>学号</th>
                <th>班级</th>
                <th>学院</th>
                <th>更新时间</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in model.profiles" :key="row.userId">
                <td class="wrap-cell">
                  <strong>{{ model.displayName(row) }}</strong>
                  <span>ID {{ row.userId }}{{ row.email ? ` / ${row.email}` : '' }}</span>
                </td>
                <td class="wrap-cell">{{ row.studentName || '-' }}</td>
                <td>{{ row.studentNo }}</td>
                <td class="wrap-cell">{{ row.className }}</td>
                <td class="wrap-cell">{{ row.college }}</td>
                <td>{{ model.formatTime(row.updatedAt) }}</td>
                <td>
                  <div class="student-info-row-actions">
                    <FaButton size="sm" variant="outline" @click="model.editProfile(row)">
                      编辑
                    </FaButton>
                    <FaButton size="sm" variant="destructive" :loading="model.saving" @click="model.deleteProfile(row)">
                      删除
                    </FaButton>
                  </div>
                </td>
              </tr>
              <tr v-if="!model.profiles.length">
                <td colspan="7">
                  <div class="student-info-empty">暂无学生档案</div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <div class="student-info-pagination">
          <FaButton size="sm" variant="outline" :disabled="model.pager.page <= 1" @click="model.prevPage">
            <FaIcon name="i-ri:arrow-left-s-line" />
            上一页
          </FaButton>
          <span>第 {{ model.pager.page }} 页</span>
          <FaButton size="sm" variant="outline" :disabled="!model.pager.hasNext" @click="model.nextPage">
            下一页
            <FaIcon name="i-ri:arrow-right-s-line" />
          </FaButton>
        </div>
      </StudentInfoPanel>

      <StudentInfoPanel :title="model.editingUserId ? '编辑档案' : '新建档案'" eyebrow="Form">
        <form class="student-info-form" @submit.prevent="model.saveAdmin">
          <label>
            <span>用户 ID</span>
            <input v-model="model.adminForm.userId" :disabled="!!model.editingUserId" autocomplete="off">
          </label>
          <label>
            <span>姓名</span>
            <input v-model="model.adminForm.studentName" autocomplete="off" maxlength="40">
          </label>
          <label>
            <span>学号</span>
            <input v-model="model.adminForm.studentNo" autocomplete="off" maxlength="64">
          </label>
          <label>
            <span>班级</span>
            <input v-model="model.adminForm.className" autocomplete="off" maxlength="80">
          </label>
          <label>
            <span>学院</span>
            <input v-model="model.adminForm.college" autocomplete="off" maxlength="80">
          </label>
          <div class="student-info-actions">
            <FaButton :loading="model.saving" type="submit">
              <FaIcon name="i-ri:save-3-line" />
              保存
            </FaButton>
            <FaButton variant="outline" type="button" @click="model.resetAdminForm">
              清空
            </FaButton>
          </div>
        </form>
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
