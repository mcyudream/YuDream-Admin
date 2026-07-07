<script setup lang="ts">
import type { TableColumn } from '@fantastic-admin/components'
import type { DeptManageItem } from '@/api/modules/system-dept'
import type { OptionItem } from '@/api/modules/system-role'
import type { IdValue, UserCreatePayload, UserDeptAssign, UserManageItem, UserStatus, UserUpdatePayload } from '@/api/modules/system-user'
import apiDept from '@/api/modules/system-dept'
import apiExcel from '@/api/modules/system-excel'
import apiRole from '@/api/modules/system-role'
import apiUser from '@/api/modules/system-user'
import { excelForm, importResultMessage, pickExcelFile, saveExcelResponse } from '@/utils/excel'

const modal = useFaModal()
const toast = useFaToast()
const { auth } = useAppAuth()
const appAccountStore = useAppAccountStore()

type EmailVerifiedFilter = 'true' | 'false'

const loading = ref(false)
const rows = ref<UserManageItem[]>([])
const roles = ref<OptionItem[]>([])
const depts = ref<DeptManageItem[]>([])
const pagination = reactive({ page: 1, size: 10, total: 0 })
const search = reactive<{ keyword: string; roleId?: IdValue; deptId?: IdValue; emailVerified?: EmailVerifiedFilter; status?: UserStatus }>({
  keyword: '',
})

const formVisible = ref(false)
const assignVisible = ref(false)
const assignMode = ref<'roles' | 'depts'>('roles')
const editing = ref<UserManageItem | null>(null)
const selectedRoleIds = ref<IdValue[]>([])
const selectedDeptIds = ref<IdValue[]>([])
const defaultDeptId = ref<IdValue>()

const form = reactive<UserCreatePayload & UserUpdatePayload>({
  username: '',
  nickname: '',
  email: '',
  phone: '',
  qq: '',
  password: '',
  emailVerified: false,
  roleIds: [],
  depts: [],
})

const statusOptions = [
  { label: '启用', value: 'ACTIVE' },
  { label: '停用', value: 'DISABLED' },
]
const emailVerifiedOptions = [
  { label: '已验证', value: 'true' },
  { label: '未验证', value: 'false' },
]
const deptOptions = computed(() => flattenDepts(depts.value).map(item => ({ label: item.name, value: item.id })))
const roleOptions = computed(() => roles.value.map(item => ({
  label: item.deptName ? `${item.deptName} / ${item.label}` : item.label,
  value: item.id,
})))
const defaultDeptOptions = computed(() => selectedDeptIds.value.map(id => ({
  label: deptOptions.value.find(item => sameId(item.value, id))?.label || String(id),
  value: id,
})))
const selectedDeptRoleSections = computed(() => selectedDeptIds.value.map(id => ({
  deptId: id,
  deptName: deptOptions.value.find(item => sameId(item.value, id))?.label || String(id),
  roleOptions: roleOptionsByDept(id),
})))
const canAssignRole = computed(() => auth('system:user:assign-role'))
const canAssignDept = computed(() => auth('system:user:assign-dept'))
const canImpersonate = computed(() => auth('system:user:impersonate'))

const tableColumns = computed<TableColumn<UserManageItem>[]>(() => [
  { accessorKey: 'username', header: '用户名', width: 140, fixed: 'left' },
  { accessorKey: 'nickname', header: '昵称', width: 140 },
  { accessorKey: 'email', header: '邮箱', width: 220 },
  { id: 'deptNames', header: '部门', width: 220 },
  { id: 'roleNames', header: '角色', width: 220 },
  { id: 'emailVerified', header: '邮箱验证', width: 100, align: 'center' },
  { id: 'status', header: '状态', width: 90, align: 'center' },
  { id: 'operation', header: '操作', width: 380, align: 'center', fixed: 'right' },
])

onMounted(async () => {
  await Promise.all([loadOptions(), loadUsers()])
})

watch([selectedDeptIds, roles], () => {
  normalizeDefaultDept()
  pruneSelectedRoleIdsByDepts()
})

async function loadOptions() {
  const [roleRes, deptRes] = await Promise.all([apiRole.options(), apiDept.tree()])
  roles.value = roleRes.data
  depts.value = deptRes.data
}

async function loadUsers() {
  loading.value = true
  try {
    const res = await apiUser.page({
      page: pagination.page,
      size: pagination.size,
      keyword: search.keyword || undefined,
      roleId: search.roleId,
      deptId: search.deptId,
      emailVerified: resolveEmailVerifiedFilter(),
      status: search.status,
    })
    rows.value = res.data.records
    pagination.total = res.data.total
  }
  finally {
    loading.value = false
  }
}

function resetSearch() {
  search.keyword = ''
  search.roleId = undefined
  search.deptId = undefined
  search.emailVerified = undefined
  search.status = undefined
  pagination.page = 1
  loadUsers()
}

function resolveEmailVerifiedFilter() {
  if (search.emailVerified === 'true') {
    return true
  }
  if (search.emailVerified === 'false') {
    return false
  }
  return undefined
}

function openCreate() {
  editing.value = null
  Object.assign(form, {
    username: '',
    nickname: '',
    email: '',
    phone: '',
    qq: '',
    password: '',
    emailVerified: false,
    roleIds: [],
    depts: [],
  })
  selectedRoleIds.value = []
  selectedDeptIds.value = []
  defaultDeptId.value = undefined
  formVisible.value = true
}

function openEdit(row: UserManageItem) {
  editing.value = row
  Object.assign(form, {
    username: row.username,
    nickname: row.nickname,
    email: row.email,
    phone: row.phone,
    qq: row.qq,
    password: '',
    emailVerified: row.emailVerified,
    roleIds: row.roleIds,
    depts: [],
  })
  selectedRoleIds.value = [...row.roleIds]
  selectedDeptIds.value = [...row.deptIds]
  defaultDeptId.value = row.defaultDeptId ?? row.deptIds[0]
  normalizeDefaultDept()
  formVisible.value = true
}

async function saveForm() {
  if (editing.value) {
    await apiUser.update(editing.value.id, {
      nickname: form.nickname,
      email: form.email,
      phone: form.phone,
      qq: form.qq,
      emailVerified: form.emailVerified,
    })
    const deptsPayload = canAssignDept.value ? buildDeptPayload() : []
    if (canAssignDept.value && !sameDeptAssigns(deptsPayload, editing.value)) {
      await apiUser.assignDepts(editing.value.id, deptsPayload)
    }
    if (canAssignRole.value && !sameIdArray(selectedRoleIds.value, editing.value.roleIds)) {
      await apiUser.assignRoles(editing.value.id, selectedRoleIds.value)
    }
  }
  else {
    const deptsPayload = buildDeptPayload()
    await apiUser.create({ ...form, roleIds: selectedRoleIds.value, depts: deptsPayload })
  }
  toast.success(editing.value ? '编辑成功' : '新增成功')
  formVisible.value = false
  await loadUsers()
}

function openAssign(row: UserManageItem, mode: 'roles' | 'depts') {
  editing.value = row
  assignMode.value = mode
  selectedRoleIds.value = [...row.roleIds]
  selectedDeptIds.value = [...row.deptIds]
  defaultDeptId.value = row.defaultDeptId ?? row.deptIds[0]
  assignVisible.value = true
}

async function saveAssign() {
  if (!editing.value) {
    return
  }
  if (!canAssignDept.value && !canAssignRole.value) {
    toast.warning('没有分配部门角色权限')
    return
  }
  if (canAssignDept.value) {
    await apiUser.assignDepts(editing.value.id, buildDeptPayload())
  }
  if (canAssignRole.value) {
    await apiUser.assignRoles(editing.value.id, selectedRoleIds.value)
  }
  toast.success('分配成功')
  assignVisible.value = false
  await loadUsers()
}

function canImpersonateRow(row: UserManageItem) {
  return canImpersonate.value && row.status === 'ACTIVE' && row.username !== appAccountStore.account && !appAccountStore.isImpersonating
}

function confirmImpersonate(row: UserManageItem) {
  if (!canImpersonateRow(row)) {
    toast.warning('当前用户不可伪装访问')
    return
  }
  modal.confirm({
    title: '伪装访问',
    content: `确认以「${row.nickname || row.username}」身份访问系统吗？当前管理员会话会被临时保存，可在右上角退出伪装。`,
    onConfirm: async () => {
      const res = await apiUser.impersonate(row.id)
      await appAccountStore.impersonate(res.data)
    },
  })
}

function confirmDisable(row: UserManageItem) {
  modal.confirm({
    title: '确认信息',
    content: `确认停用「${row.username}」吗？`,
    onConfirm: async () => {
      await apiUser.disable(row.id)
      toast.success('停用成功')
      await loadUsers()
    },
  })
}

function confirmEnable(row: UserManageItem) {
  modal.confirm({
    title: '确认信息',
    content: `确认启用「${row.username}」吗？`,
    onConfirm: async () => {
      await apiUser.enable(row.id)
      toast.success('启用成功')
      await loadUsers()
    },
  })
}

function onPageChange(page: number) {
  pagination.page = page
  loadUsers()
}

function onSizeChange(size: number) {
  pagination.size = size
  pagination.page = 1
  loadUsers()
}

function buildDeptPayload(): UserDeptAssign[] {
  normalizeDefaultDept()
  return selectedDeptIds.value.map(id => ({ deptId: id, defaultDept: sameId(id, defaultDeptId.value) }))
}

function sameIdArray(left: IdValue[], right: IdValue[]) {
  const sortedLeft = left.map(String).sort()
  const sortedRight = right.map(String).sort()
  return sortedLeft.length === sortedRight.length && sortedLeft.every((item, index) => item === sortedRight[index])
}

function sameId(left?: IdValue, right?: IdValue) {
  return String(left ?? '') === String(right ?? '')
}

function sameDeptAssigns(payload: UserDeptAssign[], row: UserManageItem) {
  return sameIdArray(payload.map(item => item.deptId), row.deptIds) && sameId(defaultDeptId.value, row.defaultDeptId ?? row.deptIds[0])
}

function roleOptionsByDept(deptId: IdValue) {
  return roles.value
    .filter(role => sameId(role.deptId, deptId))
    .map(role => ({ label: role.label, value: role.id }))
}

function roleDeptId(roleId: IdValue) {
  return roles.value.find(role => sameId(role.id, roleId))?.deptId
}

function getDeptRoleIds(deptId: IdValue) {
  return selectedRoleIds.value.filter(roleId => sameId(roleDeptId(roleId), deptId))
}

function setDeptRoleIds(deptId: IdValue, roleIds: IdValue[]) {
  const otherDeptRoleIds = selectedRoleIds.value.filter(roleId => !sameId(roleDeptId(roleId), deptId))
  selectedRoleIds.value = [...otherDeptRoleIds, ...roleIds]
}

function pruneSelectedRoleIdsByDepts() {
  selectedRoleIds.value = selectedRoleIds.value.filter((roleId) => {
    const deptId = roleDeptId(roleId)
    return deptId != null && selectedDeptIds.value.some(selectedDeptId => sameId(selectedDeptId, deptId))
  })
}

function normalizeDefaultDept() {
  if (!selectedDeptIds.value.length) {
    defaultDeptId.value = undefined
    return
  }
  if (!defaultDeptId.value || !selectedDeptIds.value.some(id => sameId(id, defaultDeptId.value))) {
    defaultDeptId.value = selectedDeptIds.value[0]
  }
}

function flattenDepts(items: DeptManageItem[]): DeptManageItem[] {
  return items.flatMap(item => [item, ...flattenDepts(item.children || [])])
}

async function exportUsers() {
  const res = await apiExcel.exportUsers({
    keyword: search.keyword || undefined,
    roleId: search.roleId,
    deptId: search.deptId,
    emailVerified: resolveEmailVerifiedFilter(),
    status: search.status,
  })
  saveExcelResponse(res, '用户管理.xlsx')
}

async function downloadUserTemplate() {
  const res = await apiExcel.userTemplate()
  saveExcelResponse(res, '用户导入模板.xlsx')
}

function importUsers() {
  pickExcelFile(async (file) => {
    const res = await apiExcel.importUsers(excelForm(file))
    toast.success(importResultMessage(res.data))
    await loadUsers()
  })
}
</script>

<template>
  <div>
    <FaPageHeader title="用户管理" class="mb-0">
      <FaButton v-auth="'system:user:export'" variant="outline" @click="exportUsers">
        <FaIcon name="i-ri:file-excel-2-line" />
        导出
      </FaButton>
      <FaButton v-auth="'system:user:import'" variant="outline" @click="downloadUserTemplate">
        <FaIcon name="i-ri:download-2-line" />
        模板
      </FaButton>
      <FaButton v-auth="'system:user:import'" variant="outline" @click="importUsers">
        <FaIcon name="i-ri:upload-2-line" />
        导入
      </FaButton>
      <FaButton v-auth="'system:user:create'" @click="openCreate">
        <FaIcon name="i-ri:user-add-line" />
        新增用户
      </FaButton>
    </FaPageHeader>

    <FaPageMain>
      <FaSearchBar>
        <template #default="{ fold, toggle }">
          <div class="gap-3 grid grid-cols-1 md:grid-cols-[repeat(auto-fit,minmax(260px,1fr))]">
            <FaInput v-model="search.keyword" clearable placeholder="用户名 / 昵称 / 邮箱" class="w-full" @keydown.enter="loadUsers" @clear="loadUsers" />
            <FaSelect v-model="search.deptId" :options="deptOptions" placeholder="部门" class="w-full" />
            <FaSelect v-show="!fold" v-model="search.roleId" :options="roleOptions" placeholder="角色" class="w-full" />
            <FaSelect v-show="!fold" v-model="search.emailVerified" :options="emailVerifiedOptions" placeholder="邮箱验证" class="w-full" />
            <FaSelect v-show="!fold" v-model="search.status" :options="statusOptions" placeholder="状态" class="w-full" />
            <div class="flex gap-2 col-end--1 justify-end">
              <FaButton variant="outline" @click="resetSearch">
                重置
              </FaButton>
              <FaButton @click="loadUsers">
                <FaIcon name="i-ri:search-line" />
                筛选
              </FaButton>
              <FaButton variant="ghost" @click="toggle">
                {{ fold ? '展开' : '收起' }}
                <FaIcon :name="fold ? 'i-ep:caret-bottom' : 'i-ep:caret-top'" />
              </FaButton>
            </div>
          </div>
        </template>
      </FaSearchBar>

      <div class="mx--4 my-3 border-t border-t-dashed" />

      <FaTable
        v-loading="loading"
        row-key="id"
        table-root-class="rounded-lg overflow-hidden"
        table-class="min-w-[1280px]"
        column-visibility
        border
        stripe
        :columns="tableColumns"
        :data="rows"
      >
        <template #cell-deptNames="{ row }">
          <div v-if="row.original.deptIds?.length" class="flex flex-wrap gap-1">
            <FaTag v-for="(deptId, index) in row.original.deptIds" :key="deptId" :variant="sameId(deptId, row.original.defaultDeptId) ? 'default' : 'secondary'">
              {{ row.original.deptNames?.[index] || deptId }}{{ sameId(deptId, row.original.defaultDeptId) ? '（默认）' : '' }}
            </FaTag>
          </div>
          <span v-else>-</span>
        </template>
        <template #cell-roleNames="{ row }">
          {{ row.original.roleNames?.join('、') || '-' }}
        </template>
        <template #cell-emailVerified="{ row }">
          <FaTag :variant="row.original.emailVerified ? 'default' : 'secondary'">
            {{ row.original.emailVerified ? '已验证' : '未验证' }}
          </FaTag>
        </template>
        <template #cell-status="{ row }">
          <FaTag :variant="row.original.status === 'ACTIVE' ? 'default' : 'secondary'">
            {{ row.original.status === 'ACTIVE' ? '启用' : '停用' }}
          </FaTag>
        </template>
        <template #cell-operation="{ row }">
          <div class="flex-center gap-2">
            <FaButton v-auth="'system:user:edit'" variant="outline" size="sm" @click="openEdit(row.original)">
              编辑
            </FaButton>
            <FaButton v-if="canAssignDept || canAssignRole" variant="outline" size="sm" @click="openAssign(row.original, 'depts')">
              部门角色
            </FaButton>
            <FaButton v-auth="'system:user:impersonate'" variant="outline" size="sm" :disabled="!canImpersonateRow(row.original)" @click="confirmImpersonate(row.original)">
              伪装
            </FaButton>
            <FaButton v-if="row.original.status === 'DISABLED'" v-auth="'system:user:edit'" variant="outline" size="sm" @click="confirmEnable(row.original)">
              启用
            </FaButton>
            <FaButton v-else v-auth="'system:user:delete'" variant="destructive" size="sm" @click="confirmDisable(row.original)">
              停用
            </FaButton>
          </div>
        </template>
      </FaTable>

      <FaPagination
        v-model:page="pagination.page"
        v-model:size="pagination.size"
        :total="pagination.total"
        class="mt-3"
        @page-change="onPageChange"
        @size-change="onSizeChange"
      />
    </FaPageMain>

    <FaModal v-model="formVisible" :title="editing ? '编辑用户' : '新增用户'" show-cancel-button class="sm:max-w-3xl" @confirm="saveForm">
      <a-form :model="form" layout="vertical">
        <a-grid :cols="2" :col-gap="16">
          <a-grid-item v-if="!editing">
            <a-form-item label="用户名" required>
              <FaInput v-model="form.username" class="w-full" />
            </a-form-item>
          </a-grid-item>
          <a-grid-item>
            <a-form-item label="昵称">
              <FaInput v-model="form.nickname" class="w-full" />
            </a-form-item>
          </a-grid-item>
          <a-grid-item>
            <a-form-item label="邮箱" required>
              <FaInput v-model="form.email" class="w-full" />
            </a-form-item>
          </a-grid-item>
          <a-grid-item>
            <a-form-item label="手机号">
              <FaInput v-model="form.phone" class="w-full" />
            </a-form-item>
          </a-grid-item>
          <a-grid-item>
            <a-form-item label="QQ">
              <FaInput v-model="form.qq" class="w-full" />
            </a-form-item>
          </a-grid-item>
          <a-grid-item v-if="!editing">
            <a-form-item label="密码" required>
              <FaInput v-model="form.password" type="password" class="w-full" />
            </a-form-item>
          </a-grid-item>
          <a-grid-item>
            <a-form-item label="邮箱状态">
              <div class="h-9 flex items-center gap-3">
                <FaSwitch v-model="form.emailVerified" />
                <span class="text-sm text-muted-foreground">{{ form.emailVerified ? '已验证' : '未验证' }}</span>
              </div>
            </a-form-item>
          </a-grid-item>
        </a-grid>
        <a-form-item v-auth="'system:user:assign-dept'" label="所属部门">
          <FaSelect v-model="selectedDeptIds" multiple :options="deptOptions" class="w-full" />
        </a-form-item>
        <a-form-item v-auth="'system:user:assign-dept'" label="默认部门">
          <FaSelect v-model="defaultDeptId" :disabled="!selectedDeptIds.length" :options="defaultDeptOptions" class="w-full" />
        </a-form-item>
        <a-form-item v-auth="'system:user:assign-role'" label="部门角色">
          <div v-if="selectedDeptRoleSections.length" class="space-y-3 w-full">
            <div v-for="section in selectedDeptRoleSections" :key="section.deptId" class="rounded-md border p-3">
              <div class="mb-2 flex items-center gap-2 text-sm font-medium">
                <FaIcon name="i-ri:organization-chart" />
                {{ section.deptName }}
              </div>
              <FaSelect
                v-if="section.roleOptions.length"
                :model-value="getDeptRoleIds(section.deptId)"
                multiple
                :options="section.roleOptions"
                class="w-full"
                @update:model-value="setDeptRoleIds(section.deptId, $event as IdValue[])"
              />
              <div v-else class="text-sm text-muted-foreground">
                该部门暂无可分配角色
              </div>
            </div>
          </div>
          <div v-else class="text-sm text-muted-foreground">
            请先选择所属部门
          </div>
        </a-form-item>
      </a-form>
    </FaModal>

    <FaModal v-model="assignVisible" title="分配部门角色" show-cancel-button class="sm:max-w-2xl" @confirm="saveAssign">
      <a-form :model="{ selectedDeptIds, defaultDeptId }" layout="vertical">
        <a-form-item v-auth="'system:user:assign-dept'" label="部门">
          <FaSelect v-model="selectedDeptIds" multiple :options="deptOptions" class="w-full" />
        </a-form-item>
        <a-form-item v-auth="'system:user:assign-dept'" label="默认部门">
          <FaSelect v-model="defaultDeptId" :disabled="!selectedDeptIds.length" :options="defaultDeptOptions" class="w-full" />
        </a-form-item>
        <a-form-item v-auth="'system:user:assign-role'" label="部门角色">
          <div v-if="selectedDeptRoleSections.length" class="space-y-3 w-full">
            <div v-for="section in selectedDeptRoleSections" :key="section.deptId" class="rounded-md border p-3">
              <div class="mb-2 flex items-center gap-2 text-sm font-medium">
                <FaIcon name="i-ri:organization-chart" />
                {{ section.deptName }}
              </div>
              <FaSelect
                v-if="section.roleOptions.length"
                :model-value="getDeptRoleIds(section.deptId)"
                multiple
                :options="section.roleOptions"
                class="w-full"
                @update:model-value="setDeptRoleIds(section.deptId, $event as IdValue[])"
              />
              <div v-else class="text-sm text-muted-foreground">
                该部门暂无可分配角色
              </div>
            </div>
          </div>
          <div v-else class="text-sm text-muted-foreground">
            请先选择所属部门
          </div>
        </a-form-item>
      </a-form>
    </FaModal>
  </div>
</template>
