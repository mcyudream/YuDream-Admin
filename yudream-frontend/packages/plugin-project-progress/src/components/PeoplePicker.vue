<script setup lang="ts">
import type { ProjectProgressModel } from '../composables/useProjectProgress'
import type { ProjectDeptOption, ProjectUserOption } from '../types'
import { FaButton, FaInput, FaModal, FaPagination, FaTag } from '@fantastic-admin/components'
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
const page = ref(1)
const pageSize = ref(10)
const hasNext = ref(false)

const selectedUsers = computed(() => props.model.userOptionsForIds(props.modelValue || []))
const selectedSet = computed(() => new Set(props.modelValue || []))
const departments = computed(() => flattenDepartments(props.model.departments || []))
const activeDeptName = computed(() => departments.value.find(item => item.dept.id === selectedDeptId.value)?.dept.name || '全部人员')
const paginationTotal = computed(() => (page.value - 1) * pageSize.value + searchResults.value.length + (hasNext.value ? 1 : 0))

watch(() => (props.modelValue || []).join(','), (value) => {
  if (value) {
    void props.model.resolveUsers(props.modelValue)
  }
}, { immediate: true })

async function showPicker() {
  open.value = true
  page.value = 1
  await Promise.all([
    refreshUsers(),
    refreshDepartments(),
    props.model.resolveUsers(props.modelValue),
  ])
}

async function refreshUsers(resetPage = false) {
  if (resetPage) {
    page.value = 1
  }
  loadingUsers.value = true
  try {
    const rows = await props.model.searchUsersPage(keyword.value, selectedDeptId.value, page.value, pageSize.value)
    searchResults.value = rows
    hasNext.value = rows.length >= pageSize.value
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
  await refreshUsers(true)
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

async function changePage(nextPage: number) {
  page.value = nextPage
  await refreshUsers()
}

async function changeSize(nextSize: number) {
  pageSize.value = nextSize
  await refreshUsers(true)
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
    <div class="pp-picker-field">
      <div class="pp-chip-list">
        <FaTag
          v-for="user in selectedUsers"
          :key="user.id"
          closable
          @close="removeUser(user.id)"
        >
          {{ model.userLabel(user) }}
        </FaTag>
        <span v-if="!selectedUsers.length" class="pp-muted">{{ placeholder }}</span>
      </div>
      <FaButton variant="outline" @click="showPicker">
        {{ selectedUsers.length ? '调整人员' : placeholder }}
      </FaButton>
    </div>

    <FaModal
      v-model="open"
      :title="title"
      class="max-w-[920px]"
      :close-on-click-overlay="false"
      :show-confirm-button="false"
      show-cancel-button
      cancel-button-text="完成"
    >
      <div class="pp-picker-summary">
        <span>{{ activeDeptName }}</span>
        <FaTag>已选 {{ selectedUsers.length }} 人</FaTag>
      </div>

      <div class="pp-picker-layout">
        <aside v-if="allowDepartments" class="pp-dept-pane">
          <div class="pp-search">
            <FaInput
              v-model="departmentKeyword"
              clearable
              placeholder="搜索部门"
              class="w-full"
              @keyup.enter="refreshDepartments"
            />
            <FaButton :loading="loadingDepartments" @click="refreshDepartments">搜索</FaButton>
          </div>
          <FaButton
            class="w-full justify-start"
            :variant="!selectedDeptId ? 'default' : 'ghost'"
            @click="chooseDepartment('')"
          >
            全部人员
          </FaButton>
          <div class="pp-dept-list">
            <div
              v-for="item in departments"
              :key="item.dept.id"
              class="pp-dept-row"
              :style="{ paddingLeft: `${8 + item.level * 16}px` }"
            >
              <FaButton
                :variant="selectedDeptId === item.dept.id ? 'default' : 'ghost'"
                class="min-w-0 flex-1 justify-start"
                @click="chooseDepartment(item.dept.id)"
              >
                {{ item.dept.name }}
              </FaButton>
              <FaButton variant="ghost" size="sm" @click="addDepartment(item.dept)">
                全选
              </FaButton>
            </div>
          </div>
        </aside>

        <main class="pp-user-pane">
          <div class="pp-search">
            <FaInput
              v-model="keyword"
              clearable
              placeholder="搜索姓名、用户名或邮箱"
              class="w-full"
              @keyup.enter="refreshUsers(true)"
            />
            <FaButton :loading="loadingUsers" @click="refreshUsers(true)">搜索</FaButton>
          </div>

          <div class="pp-table-wrap">
            <table class="pp-table">
              <thead>
                <tr>
                  <th class="pp-select-col"></th>
                  <th>人员</th>
                  <th>部门</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="user in searchResults" :key="user.id" @click="toggleUser(user)">
                  <td class="pp-select-col">
                    <input type="checkbox" :checked="selectedSet.has(user.id)" @click.stop="toggleUser(user)">
                  </td>
                  <td>
                    <strong>{{ model.userLabel(user) }}</strong>
                    <div class="pp-table-sub">{{ model.userMeta(user) || '暂无账号信息' }}</div>
                  </td>
                  <td>{{ user.deptNames?.join(' / ') || '-' }}</td>
                </tr>
                <tr v-if="!loadingUsers && !searchResults.length">
                  <td colspan="3">
                    <div class="pp-empty">没有找到匹配人员</div>
                  </td>
                </tr>
                <tr v-if="loadingUsers">
                  <td colspan="3">
                    <div class="pp-empty">正在加载人员</div>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>

          <FaPagination
            v-model:page="page"
            v-model:size="pageSize"
            :total="paginationTotal"
            :sizes="[10, 20, 50]"
            class="mt-3"
            @page-change="changePage"
            @size-change="changeSize"
          />
        </main>
      </div>
    </FaModal>
  </div>
</template>
