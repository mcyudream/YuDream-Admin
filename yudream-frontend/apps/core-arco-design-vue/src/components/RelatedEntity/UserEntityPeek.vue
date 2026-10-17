<script setup lang="ts">
import type { EntityPeekAction, EntityPeekFact } from '@yudream/components'
import type { RelatedEntityRef } from './registry'
import type { UserManageItem } from '@/api/modules/system-user'
import { YdEntityPeek } from '@yudream/components'
import apiUser from '@/api/modules/system-user'

const props = defineProps<RelatedEntityRef>()

const emit = defineEmits<{ changed: [] }>()

/** 详情请求超时（毫秒）：超过后按失败处理，避免卡片停在加载态 */
const DETAIL_TIMEOUT = 10_000

const { auth } = useAppAuth()
const toast = useYdToast()
const modal = useYdModal()
const router = useRouter()

const detail = ref<UserManageItem>()
const loading = ref(false)
const error = ref('')
const editing = ref(false)
const saving = ref(false)
const form = reactive({
  username: '',
  nickname: '',
  email: '',
  phone: '',
  qq: '',
  emailVerified: false,
})

const canEdit = computed(() => auth('system:user:edit'))
const canDisable = computed(() => auth('system:user:delete'))
const canView = computed(() => auth('system:user:view'))

/** 卡片内容以详情为准，列表回退值只在加载完成前显示 */
const name = computed(() => detail.value?.username || props.label || props.entityId)
const subtitle = computed(() => detail.value?.email || detail.value?.nickname || '')
const avatar = computed(() => detail.value?.avatar || props.avatar || '')

const status = computed(() => {
  if (!detail.value) {
    return null
  }
  return detail.value.status === 'DISABLED'
    ? { label: '停用', tone: 'neutral' as const }
    : { label: '启用', tone: 'success' as const }
})

const facts = computed<EntityPeekFact[]>(() => {
  const item = detail.value
  if (!item) {
    return []
  }
  return [
    { label: '邮箱', value: item.email },
    { label: '邮箱验证', value: item.emailVerified ? '已验证' : '未验证', tone: item.emailVerified ? 'success' : 'warning' },
    { label: '手机', value: item.phone, mono: true },
    { label: 'QQ', value: item.qq, mono: true },
    { label: '注册时间', value: dateText(item.createTime) },
    { label: '用户 ID', value: item.id, mono: true },
  ]
})

const chipGroups = computed<Array<{ label: string, items: Array<{ label: string }> }>>(() => {
  const item = detail.value
  if (!item) {
    return []
  }
  return [
    { label: '角色', items: (item.roleNames || []).map(roleName => ({ label: roleName })) },
    { label: '部门', items: (item.deptNames || []).map(deptName => ({ label: deptName })) },
  ]
})

const actions = computed<EntityPeekAction[]>(() => {
  const item = detail.value
  if (!item) {
    return []
  }
  const list: EntityPeekAction[] = []
  if (item.status === 'DISABLED') {
    list.push({ label: '启用', icon: 'morph:circle-check', disabled: !canEdit.value, onClick: () => confirmToggle(item, 'enable') })
  }
  else {
    list.push({ label: '停用', icon: 'morph:ban', danger: true, disabled: !canDisable.value, onClick: () => confirmToggle(item, 'disable') })
  }
  return list
})

function dateText(value?: string) {
  return value ? value.replace('T', ' ').slice(0, 19) : ''
}

/** 打开卡片即取详情；缓存命中先填，保证同一用户多行来回点也不重复请求 */
async function load(force = false) {
  if (!props.entityId || (!canView.value && !canEdit.value)) {
    return
  }
  const cached = force ? undefined : readUserCache(props.entityId)
  if (cached) {
    detail.value = cached
    return
  }
  loading.value = true
  error.value = ''
  try {
    const res = await withTimeout(apiUser.detail(props.entityId), DETAIL_TIMEOUT)
    detail.value = res.data
    writeUserCache(props.entityId, res.data)
  }
  catch (e) {
    error.value = e instanceof Error ? e.message : '无法加载用户信息'
  }
  finally {
    loading.value = false
  }
}

/** 详情请求挂住时不能让卡片一直转圈：超时报错并可重开重试 */
function withTimeout<T>(promise: Promise<T>, ms: number) {
  return new Promise<T>((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error('加载用户信息超时，请重试')), ms)
    promise.then(
      (value) => {
        clearTimeout(timer)
        resolve(value)
      },
      (err) => {
        clearTimeout(timer)
        reject(err)
      },
    )
  })
}

function startEdit() {
  const item = detail.value
  if (!item) {
    return
  }
  Object.assign(form, {
    username: item.username || '',
    nickname: item.nickname || '',
    email: item.email || '',
    phone: item.phone || '',
    qq: item.qq || '',
    emailVerified: !!item.emailVerified,
  })
  editing.value = true
}

async function save() {
  if (!form.username.trim()) {
    toast.error('用户名不能为空')
    return
  }
  saving.value = true
  try {
    await apiUser.update(props.entityId, {
      username: form.username.trim(),
      nickname: form.nickname.trim(),
      email: form.email.trim(),
      phone: form.phone.trim(),
      qq: form.qq.trim(),
      emailVerified: form.emailVerified,
    })
    toast.success('已保存')
    editing.value = false
    detailCache.delete(props.entityId)
    await load(true)
    emit('changed')
  }
  finally {
    saving.value = false
  }
}

function confirmToggle(item: UserManageItem, action: 'enable' | 'disable') {
  const enabling = action === 'enable'
  modal.confirm({
    title: '确认信息',
    content: enabling ? `确认启用「${item.username}」吗？` : `确认停用「${item.username}」吗？`,
    onConfirm: async () => {
      if (enabling) {
        await apiUser.enable(item.id)
      }
      else {
        await apiUser.disable(item.id)
      }
      toast.success(enabling ? '已启用' : '已停用')
      detailCache.delete(item.id)
      await load(true)
      emit('changed')
    },
  })
}
</script>

<script lang="ts">
/** 详情缓存：同一列表里大量行指向同一用户，30 秒内复用同一次详情请求 */
const detailCache = new Map<string, { at: number, data: UserManageItem }>()
const CACHE_TTL = 30_000

function readUserCache(id: string) {
  const hit = detailCache.get(id)
  return hit && Date.now() - hit.at < CACHE_TTL ? hit.data : undefined
}

function writeUserCache(id: string, data: UserManageItem) {
  detailCache.set(id, { at: Date.now(), data })
}
</script>

<template>
  <YdEntityPeek
    :title="name"
    :subtitle="subtitle"
    :avatar="avatar"
    :avatar-fallback="name.slice(0, 1)"
    :status="status"
    :facts="facts"
    :chips="chipGroups"
    :loading="loading"
    :error="error"
    :actions="actions"
    :editable="canEdit"
    :editing="editing"
    :saving="saving"
    :detail-label="canView ? '完整管理' : ''"
    side="bottom"
    @open="load()"
    @retry="load(true)"
    @update:editing="value => value ? startEdit() : (editing = false)"
    @save="save"
    @cancel="editing = false"
    @detail="router.push('/system/user')"
  >
    <template #trigger>
      <YdButton variant="ghost" class="related-entity" :title="name">
        <YdAvatar :src="avatar" :fallback="name.slice(0, 2)" :alt="name" class="shrink-0 size-6" />
        <span v-if="!avatarOnly" class="related-entity__body">
          <span class="related-entity__name">{{ name }}</span>
          <span v-if="secondary" class="related-entity__secondary">{{ secondary }}</span>
        </span>
      </YdButton>
    </template>

    <template #editor>
      <div class="related-entity__editor">
        <label>
          <span>用户名</span>
          <YdInput v-model="form.username" class="w-full" />
        </label>
        <label>
          <span>昵称</span>
          <YdInput v-model="form.nickname" class="w-full" />
        </label>
        <label>
          <span>邮箱</span>
          <YdInput v-model="form.email" class="w-full" />
        </label>
        <label>
          <span>手机</span>
          <YdInput v-model="form.phone" class="w-full" />
        </label>
        <label>
          <span>QQ</span>
          <YdInput v-model="form.qq" class="w-full" />
        </label>
        <label class="related-entity__switch">
          <span>邮箱已验证</span>
          <YdSwitch v-model="form.emailVerified" />
        </label>
      </div>
    </template>
  </YdEntityPeek>
</template>

<style scoped>
.related-entity {
  display: inline-flex;
  gap: 0.4rem;
  align-items: center;
  max-width: 100%;
  height: auto;
  padding: 0.15rem 0.3rem;
  font: inherit;
  color: inherit;
  border-radius: var(--yd-control-radius, 6px);
}

.related-entity__body {
  display: grid;
  min-width: 0;
  text-align: left;
}

.related-entity__name {
  overflow: hidden;
  font-size: 0.875rem;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.related-entity__secondary {
  overflow: hidden;
  font-size: 0.72rem;
  color: var(--color-text-3);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.related-entity__editor {
  display: grid;
  gap: 0.5rem;
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.related-entity__editor label {
  display: grid;
  gap: 0.15rem;
  min-width: 0;
}

.related-entity__editor label > span {
  font-size: 0.7rem;
  color: var(--color-text-3);
}

.related-entity__switch {
  grid-column: span 2;
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
}
</style>
