<script setup lang="ts">
import type { EntityPeekAction, EntityPeekChip, EntityPeekFact, RelatedEntityRef } from '@yudream/components'
import type { RoleManageItem, RolePayload } from '@/api/modules/system-role'
import { YdEntityPeek } from '@yudream/components'
import apiRole from '@/api/modules/system-role'
import { requestEntityFocus } from './focus'

const props = defineProps<RelatedEntityRef>()

const emit = defineEmits<{ changed: [] }>()

const { auth } = useAppAuth()
const toast = useYdToast()
const modal = useYdModal()
const router = useRouter()

const detail = ref<RoleManageItem>()
const loading = ref(false)
const error = ref('')
const editing = ref(false)
const saving = ref(false)
const form = reactive({
  name: '',
  code: '',
})

/** 角色没有单条详情端点：按 label 检索列表后按 id 定位，结果 30 秒复用 */
let roleFetchedAt = 0
const ROLE_TTL = 30_000
let flatRoles: RoleManageItem[] = []

const canEdit = computed(() => auth('system:role:edit'))
const canDisable = computed(() => auth('system:role:delete'))
// 与用户卡一致：列表给什么名称就一直显示什么，详情只在列表没给时兜底
const name = computed(() => props.label || detail.value?.name || props.entityId)

const status = computed(() => {
  if (!detail.value) {
    return null
  }
  return detail.value.status === 'DEPRECATED'
    ? { label: '已弃用', tone: 'neutral' as const }
    : { label: '启用', tone: 'success' as const }
})

const facts = computed<EntityPeekFact[]>(() => {
  const role = detail.value
  if (!role) {
    return []
  }
  return [
    { label: '角色编码', value: role.code, mono: true },
    { label: '所属部门', value: role.deptName },
    { label: '级别', value: role.level },
    { label: '权限数', value: role.permissionCount },
    { label: '创建时间', value: dateText(role.createTime) },
    { label: '角色 ID', value: role.id, mono: true },
  ]
})

const chips = computed<Array<{ label: string, items: EntityPeekChip[] }>>(() => {
  const role = detail.value
  if (!role) {
    return []
  }
  const items: EntityPeekChip[] = [{ label: role.systemRole ? '系统内置' : '自定义角色' }]
  if (role.systemType) {
    items.push({ label: role.systemType })
  }
  return [{ label: '类型', items }]
})

const actions = computed<EntityPeekAction[]>(() => {
  const role = detail.value
  if (!role || role.systemRole) {
    return []
  }
  return role.status === 'DEPRECATED'
    ? [{ label: '启用', icon: 'morph:circle-check', disabled: !canEdit.value, onClick: () => confirmToggle(role, 'enable') }]
    : [{ label: '弃用', icon: 'morph:ban', danger: true, disabled: !canDisable.value, onClick: () => confirmToggle(role, 'deprecate') }]
})

function dateText(value?: string) {
  return value ? value.replace('T', ' ').slice(0, 19) : ''
}

async function load(force = false) {
  if (!props.entityId) {
    return
  }
  loading.value = true
  error.value = ''
  try {
    if (force || !flatRoles.length || Date.now() - roleFetchedAt > ROLE_TTL) {
      // 角色的详情只能从列表里取：按名称/编码关键词收窄后定位，避免整表翻页
      const keyword = props.label || undefined
      const res = await apiRole.page({ page: 1, size: 200, keyword })
      flatRoles = res.data.records || []
      roleFetchedAt = Date.now()
    }
    const role = flatRoles.find(item => String(item.id) === String(props.entityId))
    if (!role) {
      error.value = '角色不存在或不在当前检索范围'
      return
    }
    detail.value = role
  }
  catch (e) {
    error.value = e instanceof Error ? e.message : '无法加载角色信息'
  }
  finally {
    loading.value = false
  }
}

function startEdit() {
  const role = detail.value
  if (!role) {
    return
  }
  Object.assign(form, { name: role.name || '', code: role.code || '' })
  editing.value = true
}

async function save() {
  const role = detail.value
  if (!role) {
    return
  }
  if (!form.name.trim() || !form.code.trim()) {
    toast.error('角色名称与编码不能为空')
    return
  }
  saving.value = true
  try {
    const payload: RolePayload = {
      name: form.name.trim(),
      code: form.code.trim(),
      deptId: role.deptId,
      level: role.level,
      status: role.status,
      icon: role.icon,
      color: role.color,
      // 权限清单不在卡片里编辑，原样带回避免被清空
      permissions: role.permissions || [],
    }
    await apiRole.update(role.id, payload)
    toast.success('已保存')
    editing.value = false
    await load(true)
    emit('changed')
  }
  finally {
    saving.value = false
  }
}

/** 「完整管理」：移交定位意图并跳到角色管理（权限分配等完整能力在目标页） */
function openFullManage() {
  const id = detail.value?.id || props.entityId
  const keyword = detail.value?.name || props.label
  requestEntityFocus({ id, keyword })
  router.push({ path: '/system/role', query: { keyword: keyword || undefined, focusRole: id } })
}

function confirmToggle(role: RoleManageItem, action: 'enable' | 'deprecate') {
  const enabling = action === 'enable'
  modal.confirm({
    title: '确认信息',
    content: enabling ? `确认启用「${role.name}」吗？` : `确认弃用「${role.name}」吗？`,
    onConfirm: async () => {
      if (enabling) {
        await apiRole.enable(role.id)
      }
      else {
        await apiRole.disable(role.id)
      }
      toast.success(enabling ? '已启用' : '已弃用')
      await load(true)
      emit('changed')
    },
  })
}
</script>

<template>
  <YdEntityPeek
    :title="name"
    :avatar-fallback="name.slice(0, 1)"
    :status="status"
    :facts="facts"
    :chips="chips"
    :loading="loading"
    :error="error"
    :actions="actions"
    :editable="canEdit && !detail?.systemRole"
    :editing="editing"
    :saving="saving"
    :detail-label="canEdit ? '完整管理' : ''"
    side="bottom"
    @open="load()"
    @retry="load(true)"
    @update:editing="value => value ? startEdit() : (editing = false)"
    @save="save"
    @cancel="editing = false"
    @detail="openFullManage"
  >
    <template #trigger>
      <slot>
        <YdButton variant="ghost" class="related-entity" :title="name">
          <span class="related-entity__body">
            <span class="related-entity__name">{{ name }}</span>
            <span v-if="secondary" class="related-entity__secondary">{{ secondary }}</span>
          </span>
        </YdButton>
      </slot>
    </template>

    <template #editor>
      <div class="related-entity__editor">
        <label>
          <span>角色名称</span>
          <YdInput v-model="form.name" class="w-full" />
        </label>
        <label>
          <span>角色编码</span>
          <YdInput v-model="form.code" class="w-full" />
        </label>
      </div>
    </template>
  </YdEntityPeek>
</template>

<style>
@import './related-entity.css';
</style>
