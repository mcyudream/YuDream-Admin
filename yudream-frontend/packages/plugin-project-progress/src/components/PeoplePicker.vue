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

watch(() => props.modelValue.join(','), value => {
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

function closePicker() {
  open.value = false
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
    closePicker()
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
    <div class="pp-picker-field">
      <div class="pp-chip-list">
        <span v-if="!selectedUsers.length" class="pp-muted">{{ placeholder }}</span>
        <span v-for="user in selectedUsers" :key="user.id" class="pp-chip">
          {{ model.userLabel(user) }}
          <button type="button" title="移除" @click="removeUser(user.id)">×</button>
        </span>
      </div>
      <button type="button" @click="showPicker">
        {{ selectedUsers.length ? '调整人员' : placeholder }}
      </button>
    </div>

    <div v-if="open" class="pp-modal-mask" @click.self="closePicker">
      <section class="pp-modal">
        <header class="pp-modal-head">
          <div>
            <h3>{{ title }}</h3>
            <span>{{ activeDeptName }} · 已选 {{ selectedUsers.length }} 人</span>
          </div>
          <button type="button" title="关闭" @click="closePicker">×</button>
        </header>

        <div class="pp-picker-layout">
          <aside v-if="allowDepartments" class="pp-dept-pane">
            <div class="pp-search">
              <input
                v-model="departmentKeyword"
                placeholder="搜索部门"
                @keydown.enter.prevent="refreshDepartments"
              >
              <button type="button" :disabled="loadingDepartments" @click="refreshDepartments">搜索</button>
            </div>
            <button
              type="button"
              class="pp-dept-item"
              :class="{ active: !selectedDeptId }"
              @click="chooseDepartment('')"
            >
              全部人员
            </button>
            <button
              v-for="item in departments"
              :key="item.dept.id"
              type="button"
              class="pp-dept-item"
              :class="{ active: selectedDeptId === item.dept.id }"
              :style="{ paddingLeft: `${12 + item.level * 16}px` }"
              @click="chooseDepartment(item.dept.id)"
            >
              <span>{{ item.dept.name }}</span>
              <small @click.stop="addDepartment(item.dept)">全选</small>
            </button>
          </aside>

          <main class="pp-user-pane">
            <div class="pp-search">
              <input
                v-model="keyword"
                placeholder="搜索姓名、用户名、邮箱，或粘贴旧用户 ID"
                @keydown.enter.prevent="refreshUsers"
              >
              <button type="button" :disabled="loadingUsers" @click="refreshUsers">搜索</button>
            </div>

            <div class="pp-user-results">
              <button
                v-for="user in searchResults"
                :key="user.id"
                type="button"
                class="pp-user-option"
                :class="{ active: selectedSet.has(user.id) }"
                @click="toggleUser(user)"
              >
                <strong>{{ model.userLabel(user) }}</strong>
                <span>{{ model.userMeta(user) || '暂无账号信息' }}</span>
              </button>
              <div v-if="!searchResults.length" class="pp-empty">
                {{ loadingUsers ? '正在加载人员' : '没有找到匹配人员' }}
              </div>
            </div>
          </main>
        </div>

        <footer class="pp-modal-foot">
          <button type="button" @click="closePicker">完成</button>
        </footer>
      </section>
    </div>
  </div>
</template>
