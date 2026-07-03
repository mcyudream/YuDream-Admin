<script setup lang="ts">
import type { DeptItem, IdValue, RoleItem } from '@/api/modules/user'
import apiUser from '@/api/modules/user'

defineOptions({
  name: 'DeptRoleSwitch',
})

const appAccountStore = useAppAccountStore()

const depts = ref<DeptItem[]>([])
const roles = ref<RoleItem[]>([])
const loading = ref(false)

onMounted(() => {
  load()
})

async function load() {
  loading.value = true
  try {
    const [deptRes, roleRes] = await Promise.all([
      apiUser.listDepts(),
      apiUser.listRoles(),
    ])
    depts.value = deptRes.data
    roles.value = roleRes.data
  }
  finally {
    loading.value = false
  }
}

async function handleSwitchDept(id: IdValue) {
  if (sameId(id, appAccountStore.currentDept?.id)) {
    return
  }
  await appAccountStore.switchDept(id)
  useFaToast().success('切换成功', { description: `当前部门：${appAccountStore.currentDept?.name}` })
}

async function handleSwitchRole(id: IdValue) {
  if (sameId(id, appAccountStore.currentRole?.id)) {
    return
  }
  await appAccountStore.switchRole(id)
  useFaToast().success('切换成功', { description: `当前角色：${appAccountStore.currentRole?.name}` })
}

function sameId(left?: IdValue, right?: IdValue) {
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
        <select
          :value="appAccountStore.currentDept?.id"
          class="w-full rounded-md border bg-background px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-primary"
          @change="handleSwitchDept(($event.target as HTMLSelectElement).value)"
        >
          <option v-for="item in depts" :key="item.id" :value="item.id">
            {{ item.name }}
          </option>
        </select>
      </div>
      <div>
        <label class="mb-1 block text-sm">当前角色</label>
        <select
          :value="appAccountStore.currentRole?.id"
          class="w-full rounded-md border bg-background px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-primary"
          @change="handleSwitchRole(($event.target as HTMLSelectElement).value)"
        >
          <option v-for="item in roles" :key="item.id" :value="item.id">
            {{ item.name }}
          </option>
        </select>
      </div>
    </div>
  </div>
</template>
