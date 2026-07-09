<script setup lang="ts">
import type { ProjectProgressModel } from '../composables/useProjectProgress'
import type { ProjectDeptOption, ProjectUserOption } from '../types'
import { computed, ref, watch } from 'vue'

const props = withDefaults(defineProps<{
  model: ProjectProgressModel
  modelValue: string[]
  title: string
  placeholder?: string
  multiple?: boolean
  allowDepartments?: boolean
}>(), {
  placeholder: '选择人员',
  multiple: true,
  allowDepartments: true,
})

const emit = defineEmits<{
  (event: 'update:modelValue', value: string[]): void
}>()

const open = ref(false)
const keyword = ref('')
const departmentKeyword = ref('')
const selectedDeptId = ref('')
const searchResults = ref<ProjectUserOption[]>([])
const loadingUsers = ref(false)
const loadingDepartments = ref(false)

const selectedUsers = computed(() => props.model.userOptionsForIds(props.modelValue || []))
const selectedSet = computed(() => new Set(props.modelValue || []))
const departments = computed(() => flattenDepartments(props.model.departments || []))
const activeDeptName = computed(() => departments.value.find(item => item.dept.id === selectedDeptId.value)?.dept.name || '全部人员')

watch(() => (props.modelValue || []).join(','), (value) => {
  if (value) {
    void props.model.resolveUsers(props.modelValue)
  }
}, { immediate: true })

async function showPicker() {
  open.value = true
  await Promise.all([
    refreshUsers(),
    refreshDepartments(),
    props.model.resolveUsers(props.modelValue),
  ])
}

async function refreshUsers() {
  loadingUsers.value = true
  try {
    searchResults.value = await props.model.searchUsers(keyword.value, selectedDeptId.value)
  }
  finally {
    loadingUsers.value = false
  }
}

async function refreshDepartments() {
  if (!props.allowDepartments) {
    return
  }
  loadingDepartments.value = true
  try {
    await props.model.loadDepartments(departmentKeyword.value)
  }
  finally {
    loadingDepartments.value = false
  }
}

async function chooseDepartment(deptId: string) {
  selectedDeptId.value = deptId
  await refreshUsers()
}

async function addDepartment(dept: ProjectDeptOption) {
  loadingUsers.value = true
  try {
    const users = await props.model.loadDepartmentUsers(dept.id)
    updateSelection([...props.modelValue, ...users.map(user => user.id)])
  }
  finally {
    loadingUsers.value = false
  }
}

function toggleUser(user: ProjectUserOption) {
  if (!props.multiple) {
    updateSelection([user.id])
    open.value = false
    return
  }
  if (selectedSet.value.has(user.id)) {
    updateSelection(props.modelValue.filter(id => id !== user.id))
    return
  }
  updateSelection([...props.modelValue, user.id])
}

function removeUser(userId: string) {
  updateSelection(props.modelValue.filter(id => id !== userId))
}

function updateSelection(ids: string[]) {
  emit('update:modelValue', Array.from(new Set(ids.filter(Boolean))))
}

function flattenDepartments(items: ProjectDeptOption[], level = 0): Array<{ dept: ProjectDeptOption, level: number }> {
  return items.flatMap(item => [
    { dept: item, level },
    ...flattenDepartments(item.children || [], level + 1),
  ])
}
</script>

<template>
  <div class="pp-picker">
    <div class="pp-picker-field arco-picker-field">
      <a-space wrap>
        <a-tag
          v-for="user in selectedUsers"
          :key="user.id"
          closable
          @close="removeUser(user.id)"
        >
          {{ model.userLabel(user) }}
        </a-tag>
        <span v-if="!selectedUsers.length" class="pp-muted">{{ placeholder }}</span>
      </a-space>
      <a-button type="outline" @click="showPicker">
        {{ selectedUsers.length ? '调整人员' : placeholder }}
      </a-button>
    </div>

    <a-modal
      v-model:visible="open"
      :title="title"
      :width="920"
      :mask-closable="false"
      unmount-on-close
    >
      <div class="pp-picker-summary">
        <span>{{ activeDeptName }}</span>
        <a-tag color="arcoblue">已选 {{ selectedUsers.length }} 人</a-tag>
      </div>

      <div class="pp-picker-layout">
        <aside v-if="allowDepartments" class="pp-dept-pane">
          <a-input-search
            v-model="departmentKeyword"
            placeholder="搜索部门"
            :loading="loadingDepartments"
            @search="refreshDepartments"
            @press-enter="refreshDepartments"
          />
          <a-button
            long
            :type="!selectedDeptId ? 'primary' : 'text'"
            class="pp-dept-button"
            @click="chooseDepartment('')"
          >
            全部人员
          </a-button>
          <a-spin :loading="loadingDepartments">
            <div class="pp-dept-list">
              <div
                v-for="item in departments"
                :key="item.dept.id"
                class="pp-dept-row"
                :style="{ paddingLeft: `${8 + item.level * 16}px` }"
              >
                <a-button
                  :type="selectedDeptId === item.dept.id ? 'primary' : 'text'"
                  @click="chooseDepartment(item.dept.id)"
                >
                  {{ item.dept.name }}
                </a-button>
                <a-button type="text" size="mini" @click="addDepartment(item.dept)">
                  全选
                </a-button>
              </div>
            </div>
          </a-spin>
        </aside>

        <main class="pp-user-pane">
          <a-input-search
            v-model="keyword"
            placeholder="搜索姓名、用户名或邮箱"
            :loading="loadingUsers"
            @search="refreshUsers"
            @press-enter="refreshUsers"
          />
          <a-table
            class="pp-picker-table"
            :data="searchResults"
            :loading="loadingUsers"
            :pagination="false"
            row-key="id"
            size="small"
          >
            <template #columns>
              <a-table-column title="" :width="54">
                <template #cell="{ record }">
                  <a-checkbox :model-value="selectedSet.has(record.id)" @change="toggleUser(record)" />
                </template>
              </a-table-column>
              <a-table-column title="人员">
                <template #cell="{ record }">
                  <strong>{{ model.userLabel(record) }}</strong>
                  <div class="pp-table-sub">{{ model.userMeta(record) || '暂无账号信息' }}</div>
                </template>
              </a-table-column>
              <a-table-column title="部门" :width="220">
                <template #cell="{ record }">
                  <span>{{ record.deptNames?.join(' / ') || '-' }}</span>
                </template>
              </a-table-column>
            </template>
          </a-table>
          <a-empty v-if="!loadingUsers && !searchResults.length" description="没有找到匹配人员" />
        </main>
      </div>

      <template #footer>
        <a-button @click="open = false">完成</a-button>
      </template>
    </a-modal>
  </div>
</template>
