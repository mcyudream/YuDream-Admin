<script setup lang="ts">
import type { EntityPeekAction, EntityPeekChip, EntityPeekFact, RelatedEntityRef } from '@yudream/components'
import type { DeptManageItem, DeptPayload } from '@/api/modules/system-dept'
import { YdEntityPeek } from '@yudream/components'
import apiDept from '@/api/modules/system-dept'
import { requestEntityFocus } from './focus'

const props = defineProps<RelatedEntityRef>()

const emit = defineEmits<{ changed: [] }>()

const { auth } = useAppAuth()
const toast = useYdToast()
const modal = useYdModal()
const router = useRouter()

const detail = ref<DeptManageItem>()
const loading = ref(false)
const error = ref('')
const editing = ref(false)
const saving = ref(false)
const form = reactive({
  name: '',
  description: '',
  phone: '',
  sortOrder: 0,
})

/** 部门没有单条详情端点：取一次树后扁平缓存（30 秒），按 id 定位并解析上级名称 */
let flatDepts: DeptManageItem[] = []
let treeFetchedAt = 0
const TREE_TTL = 30_000

const canEdit = computed(() => auth('system:dept:edit'))
const canDisable = computed(() => auth('system:dept:delete'))
// 与用户卡一致：列表给什么名称就一直显示什么，详情只在列表没给时兜底
const name = computed(() => props.label || detail.value?.name || props.entityId)
const avatar = computed(() => detail.value?.avatar || props.avatar || '')

const status = computed(() => {
  if (!detail.value) {
    return null
  }
  return detail.value.status === 'DEPRECATED'
    ? { label: '已弃用', tone: 'neutral' as const }
    : { label: '启用', tone: 'success' as const }
})

const facts = computed<EntityPeekFact[]>(() => {
  const node = detail.value
  if (!node) {
    return []
  }
  return [
    { label: '上级部门', value: parentName(node) },
    { label: '负责人', value: node.leaderName },
    { label: '联系电话', value: node.phone, mono: true },
    { label: '排序', value: node.sortOrder ?? 0 },
    { label: '子部门', value: `${node.children?.length ?? 0} 个` },
    { label: '部门 ID', value: node.id, mono: true },
  ]
})

const chips = computed<Array<{ label: string, items: EntityPeekChip[] }>>(() => {
  const node = detail.value
  if (!node) {
    return []
  }
  const items: EntityPeekChip[] = [{ label: node.systemDept ? '系统内置' : '自定义部门' }]
  if (node.deptType) {
    items.push({ label: node.deptType })
  }
  return [{ label: '类型', items }]
})

const actions = computed<EntityPeekAction[]>(() => {
  const node = detail.value
  if (!node || node.systemDept) {
    return []
  }
  return node.status === 'DEPRECATED'
    ? [{ label: '启用', icon: 'morph:circle-check', disabled: !canEdit.value, onClick: () => confirmToggle(node, 'enable') }]
    : [{ label: '弃用', icon: 'morph:ban', danger: true, disabled: !canDisable.value, onClick: () => confirmToggle(node, 'deprecate') }]
})

function parentName(node: DeptManageItem) {
  if (!node.parentId) {
    return '顶级部门'
  }
  return flatDepts.find(item => String(item.id) === String(node.parentId))?.name || String(node.parentId)
}

async function load(force = false) {
  if (!props.entityId) {
    return
  }
  loading.value = true
  error.value = ''
  try {
    if (force || !flatDepts.length || Date.now() - treeFetchedAt > TREE_TTL) {
      const res = await apiDept.tree()
      const flat: DeptManageItem[] = []
      const walk = (nodes: DeptManageItem[]) => {
        for (const node of nodes || []) {
          flat.push(node)
          walk(node.children || [])
        }
      }
      walk(res.data || [])
      flatDepts = flat
      treeFetchedAt = Date.now()
    }
    const node = flatDepts.find(item => String(item.id) === String(props.entityId))
    if (!node) {
      error.value = '部门不存在或已删除'
      return
    }
    detail.value = node
  }
  catch (e) {
    error.value = e instanceof Error ? e.message : '无法加载部门信息'
  }
  finally {
    loading.value = false
  }
}

function startEdit() {
  const node = detail.value
  if (!node) {
    return
  }
  Object.assign(form, {
    name: node.name || '',
    description: node.description || '',
    phone: node.phone || '',
    sortOrder: node.sortOrder ?? 0,
  })
  editing.value = true
}

async function save() {
  const node = detail.value
  if (!node) {
    return
  }
  if (!form.name.trim()) {
    toast.error('部门名称不能为空')
    return
  }
  saving.value = true
  try {
    const payload: DeptPayload = {
      name: form.name.trim(),
      description: form.description.trim(),
      phone: form.phone.trim(),
      sortOrder: form.sortOrder,
      parentId: node.parentId,
      color: node.color,
      leaderId: node.leaderId,
      status: node.status,
    }
    await apiDept.update(node.id, payload)
    toast.success('已保存')
    editing.value = false
    await load(true)
    emit('changed')
  }
  finally {
    saving.value = false
  }
}

/** 「完整管理」：移交定位意图并跳到部门管理（保留部门色/图标等自定义视觉在目标页） */
function openFullManage() {
  const id = detail.value?.id || props.entityId
  const keyword = detail.value?.name || props.label
  requestEntityFocus({ id, keyword })
  router.push({ path: '/system/dept', query: { keyword: keyword || undefined, focusDept: id } })
}

function confirmToggle(node: DeptManageItem, action: 'enable' | 'deprecate') {
  const enabling = action === 'enable'
  modal.confirm({
    title: '确认信息',
    content: enabling ? `确认启用「${node.name}」吗？` : `确认弃用「${node.name}」吗？`,
    onConfirm: async () => {
      if (enabling) {
        await apiDept.enable(node.id)
      }
      else {
        await apiDept.disable(node.id)
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
    :avatar="avatar"
    :avatar-fallback="name.slice(0, 2)"
    :status="status"
    :facts="facts"
    :chips="chips"
    :loading="loading"
    :error="error"
    :actions="actions"
    :editable="canEdit && !detail?.systemDept"
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
          <YdAvatar :src="avatar" :fallback="name.slice(0, 2)" :alt="name" class="shrink-0 size-6" />
          <span v-if="!avatarOnly" class="related-entity__body">
            <span class="related-entity__name">{{ name }}</span>
            <span v-if="secondary" class="related-entity__secondary">{{ secondary }}</span>
          </span>
        </YdButton>
      </slot>
    </template>

    <template #editor>
      <div class="related-entity__editor">
        <label>
          <span>部门名称</span>
          <YdInput v-model="form.name" class="w-full" />
        </label>
        <label>
          <span>联系电话</span>
          <YdInput v-model="form.phone" class="w-full" />
        </label>
        <label class="related-entity__wide">
          <span>描述</span>
          <YdInput v-model="form.description" class="w-full" />
        </label>
        <label>
          <span>排序</span>
          <YdInput v-model="form.sortOrder" type="number" class="w-full" />
        </label>
      </div>
    </template>
  </YdEntityPeek>
</template>

<style scoped>
@import './related-entity.css';
</style>
