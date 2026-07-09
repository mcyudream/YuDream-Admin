<script setup lang="ts">
import type { ProjectProgressModel } from '../composables/useProjectProgress'
import type { ProjectDeptOption, ProjectUserOption } from '../types'
import { FaButton, FaCheckbox, FaIcon, FaInput, FaModal, FaPagination, FaTag } from '@fantastic-admin/components'
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
const paginationTextTemplates = {
  sizes: (size: number) => `${size} 人/页`,
  jumper: { before: '前往', after: '页' },
}

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

function userAccountText(user: ProjectUserOption) {
  return props.model.userMeta(user) || '暂无账号信息'
}

function userDepartmentText(user: ProjectUserOption) {
  return user.deptNames?.length ? user.deptNames.join(' / ') : '暂无部门'
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
        <FaIcon name="i-ri:user-search-line" />
        {{ selectedUsers.length ? '调整人员' : placeholder }}
      </FaButton>
    </div>

    <FaModal
      v-model="open"
      :title="title"
      class="pp-picker-modal"
      content-class="pp-picker-modal-body"
      :close-on-click-overlay="false"
      :show-confirm-button="false"
      show-cancel-button
      cancel-button-text="完成"
    >
      <div class="pp-picker-summary">
        <div>
          <strong>{{ activeDeptName }}</strong>
          <span>可搜索姓名、用户名、邮箱或按部门筛选</span>
        </div>
        <FaTag>已选 {{ selectedUsers.length }} 人</FaTag>
      </div>

      <div class="pp-picker-layout">
        <aside v-if="allowDepartments" class="pp-dept-pane">
          <div class="pp-pane-title">
            <FaIcon name="i-ri:organization-chart" />
            <span>部门</span>
          </div>
          <div class="pp-search">
            <FaInput
              v-model="departmentKeyword"
              clearable
              placeholder="搜索部门"
              class="w-full"
              @keyup.enter="refreshDepartments"
            />
            <FaButton :loading="loadingDepartments" @click="refreshDepartments">
              搜索
            </FaButton>
          </div>
          <FaButton
            class="pp-dept-all"
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
              :style="{ paddingLeft: `${item.level * 14}px` }"
            >
              <FaButton
                :variant="selectedDeptId === item.dept.id ? 'default' : 'ghost'"
                class="pp-dept-name"
                @click="chooseDepartment(item.dept.id)"
              >
                {{ item.dept.name }}
              </FaButton>
              <FaButton variant="outline" size="sm" @click="addDepartment(item.dept)">
                全选
              </FaButton>
            </div>
          </div>
        </aside>

        <main class="pp-user-pane">
          <div class="pp-pane-title">
            <FaIcon name="i-ri:user-3-line" />
            <span>人员</span>
          </div>
          <div class="pp-search">
            <FaInput
              v-model="keyword"
              clearable
              placeholder="搜索姓名、用户名或邮箱"
              class="w-full"
              @keyup.enter="refreshUsers(true)"
            />
            <FaButton :loading="loadingUsers" @click="refreshUsers(true)">
              搜索
            </FaButton>
          </div>

          <div class="pp-user-list">
            <FaButton
              v-for="user in searchResults"
              :key="user.id"
              variant="ghost"
              class="pp-user-card"
              :class="{ active: selectedSet.has(user.id) }"
              @click="toggleUser(user)"
            >
              <FaCheckbox
                :model-value="selectedSet.has(user.id)"
                @click.stop
                @update:model-value="toggleUser(user)"
              />
              <div class="pp-user-main">
                <strong>{{ model.userLabel(user) }}</strong>
                <span>{{ userAccountText(user) }}</span>
              </div>
              <div class="pp-user-depts">
                {{ userDepartmentText(user) }}
              </div>
            </FaButton>
            <div v-if="!loadingUsers && !searchResults.length" class="pp-empty">
              没有找到匹配人员
            </div>
            <div v-if="loadingUsers" class="pp-empty">
              正在加载人员
            </div>
          </div>

          <FaPagination
            v-model:page="page"
            v-model:size="pageSize"
            :total="paginationTotal"
            :sizes="[10, 20, 50]"
            :text-templates="paginationTextTemplates"
            layout="pager, ->, sizes"
            class="mt-3"
            @page-change="changePage"
            @size-change="changeSize"
          />
        </main>
      </div>
    </FaModal>
  </div>
</template>
