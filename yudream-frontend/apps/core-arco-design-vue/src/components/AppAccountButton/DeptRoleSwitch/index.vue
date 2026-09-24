<script setup lang="ts">
import type { DeptItem, IdValue, RoleItem } from '@/api/modules/user'
import apiUser from '@/api/modules/user'

defineOptions({
  name: 'DeptRoleSwitch',
})

const appAccountStore = useAppAccountStore()
const toast = useFaToast()

const depts = ref<DeptItem[]>([])
const roles = ref<RoleItem[]>([])
const loading = ref(false)
const switching = ref(false)

const selectedDeptId = ref<IdValue>()
const selectedRoleId = ref<IdValue>()

const deptOptions = computed(() => depts.value.map(item => ({ label: item.name, value: item.id })))
const roleOptions = computed(() => roles.value.map(item => ({ label: item.name, value: item.id })))
// 当前角色不在所选部门内（存量数据的回落角色）时提示继续沿用，避免与页面实际权限不一致
const inheritedRole = computed(() => {
  const role = appAccountStore.currentRole
  if (!role) {
    return null
  }
  return roles.value.some(item => sameId(item.id, role.id)) ? null : role
})

onMounted(load)

watch(selectedDeptId, async (deptId, previous) => {
  if (deptId == null || sameId(deptId, previous) || sameId(deptId, appAccountStore.currentDept?.id)) {
    return
  }
  await switchDept(deptId)
})

watch(selectedRoleId, async (roleId, previous) => {
  if (roleId == null || sameId(roleId, previous) || sameId(roleId, appAccountStore.currentRole?.id)) {
    return
  }
  await switchRole(roleId)
})

async function load() {
  loading.value = true
  try {
    const [deptRes, roleRes] = await Promise.all([apiUser.listDepts(), apiUser.listRoles()])
    depts.value = deptRes.data
    roles.value = roleRes.data
    syncSelection()
  }
  finally {
    loading.value = false
  }
}

async function switchDept(deptId: IdValue) {
  switching.value = true
  try {
    await appAccountStore.switchDept(deptId)
    roles.value = (await apiUser.listRoles()).data
    toast.success('切换成功', { description: `当前部门：${appAccountStore.currentDept?.name}` })
  }
  catch {
    // 失败提示由请求拦截器统一处理，这里只需回退到服务端当前上下文
  }
  finally {
    switching.value = false
    syncSelection()
  }
}

async function switchRole(roleId: IdValue) {
  switching.value = true
  try {
    await appAccountStore.switchRole(roleId)
    toast.success('切换成功', { description: `当前角色：${appAccountStore.currentRole?.name}` })
  }
  catch {
    // 同上
  }
  finally {
    switching.value = false
    syncSelection()
  }
}

function syncSelection() {
  const currentRoleId = appAccountStore.currentRole?.id
  selectedDeptId.value = appAccountStore.currentDept?.id
  selectedRoleId.value = currentRoleId != null && roles.value.some(item => sameId(item.id, currentRoleId))
    ? currentRoleId
    : undefined
}

function sameId(left?: IdValue | null, right?: IdValue | null) {
  return String(left ?? '') === String(right ?? '')
}
</script>

<template>
  <div class="p-6 w-80">
    <h3 class="mb-4 text-lg font-bold">
      切换部门/角色
    </h3>
    <div v-if="loading" class="py-8 text-center text-sm text-muted-foreground">
      加载中...
    </div>
    <div v-else class="space-y-4">
      <div>
        <label class="mb-1 block text-sm">当前部门</label>
        <FaSelect v-model="selectedDeptId" :options="deptOptions" :disabled="switching" placeholder="请选择部门" class="w-full" />
      </div>
      <div>
        <label class="mb-1 block text-sm">当前角色</label>
        <FaSelect
          v-model="selectedRoleId"
          :options="roleOptions"
          :disabled="switching || roleOptions.length === 0"
          :placeholder="roleOptions.length === 0 ? '当前部门未分配角色' : '请选择角色'"
          class="w-full"
        />
        <p class="mt-1 text-xs text-muted-foreground">
          角色随部门划分，切换部门后只能选择该部门的角色
        </p>
        <p v-if="inheritedRole" class="mt-1 text-xs text-warning">
          当前部门未分配角色，继续沿用「{{ inheritedRole.name }}」的权限
        </p>
      </div>
    </div>
  </div>
</template>
