<script setup lang="ts">
import type { ActionMenuItem, TableColumn, YdTablePickerQuery, YdTablePickerResult } from '@yudream/components'
import type { RoleManageItem } from '@/api/modules/system-role'
import type { WikiAclAction, WikiMemberSubjectType, WikiSpaceMember } from '@/api/modules/platform-wiki'
import type { UserPickerRow } from '@/api/modules/system-user'
import { YdAlert, YdButton, YdCheckboxGroup, YdEmpty, YdIcon, YdLoading, YdModal, YdPagination, YdScrollArea, YdSegmented, YdTablePicker, YdTag } from '@yudream/components'
import { computed, inject, onMounted, ref } from 'vue'
import { createWikiSpaceMember, deleteWikiSpaceMember, enableWikiRestrictedAcl, fetchWikiSpaceMembers, updateWikiSpaceMember } from '@/api/modules/platform-wiki'
import apiRole from '@/api/modules/system-role'
import apiUser from '@/api/modules/system-user'
import { toBackendAssetUrl } from '@/utils/backend-url'
import { aclModeMeta, wikiWorkbenchKey } from '../wiki-utils'

/**
 * 空间成员授权：实例 ACL 的唯一维护入口。
 *
 * 两段事实必须说清楚，不能只给一张表：
 * - LEGACY（全局权限）下成员表不生效，授权只是「待生效」的准备工作；
 * - 启用受限访问（RESTRICTED）后未授权用户立即不可见，且后端保证至少保留一名可管理成员。
 * 动作按 MANAGE > EDIT > COMMENT > READ 语义包含，勾选高阶即覆盖低阶。
 */
const store = inject(wikiWorkbenchKey)!
const toast = useYdToast()
const modal = useYdModal()
const { auth } = useAppAuth()

const canManage = computed(() => auth('platform:wiki:member:manage'))
const canDelete = computed(() => auth('platform:wiki:member:delete'))

const members = ref<WikiSpaceMember[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const loading = ref(false)
const loadError = ref('')
const mode = computed(() => aclModeMeta(store.space.value?.aclMode))
const managerMissing = computed(() => mode.value.key === 'RESTRICTED'
  && members.value.length > 0
  && !members.value.some(member => member.actions.includes('MANAGE')))

const ACTION_ORDER: WikiAclAction[] = ['READ', 'COMMENT', 'EDIT', 'MANAGE']
const ACTION_META: Record<WikiAclAction, { label: string, description: string }> = {
  READ: { label: '读取', description: '查看目录、页面与治理数据' },
  COMMENT: { label: '评论', description: '在已发布页面上发表与维护本人评论（同时具备读取）' },
  EDIT: { label: '编辑', description: '维护页面、资料、摄入与发布（同时具备评论）' },
  MANAGE: { label: '管理', description: '成员、封面、设置与审核（同时具备编辑，权限最高）' },
}
const actionOptions = ACTION_ORDER.map(value => ({ value, label: ACTION_META[value].label, description: ACTION_META[value].description }))
const actionRank = (action: WikiAclAction) => ACTION_ORDER.indexOf(action)
/** 授权集合按语义包含归一：勾了管理就不再逐条要求编辑/评论/读取 */
function normalizeActions(actions: WikiAclAction[]): WikiAclAction[] {
  return [...actions].sort((a, b) => actionRank(a) - actionRank(b))
}
/** 展示时给出「显式授予」与「被高阶覆盖」两层，避免行里堆四个标签还看不出重点 */
function grantSummary(member: WikiSpaceMember) {
  const explicit = normalizeActions(member.actions)
  const highest = explicit[explicit.length - 1]
  const implied = highest ? ACTION_ORDER.filter(action => actionRank(action) < actionRank(highest) && !explicit.includes(action)) : []
  return { explicit, highest, implied }
}

const memberColumns: TableColumn<WikiSpaceMember>[] = [
  { id: 'subject', header: '主体', minWidth: 260 },
  // 列 id 不能叫 actions：那是表格内置操作列的保留 id，撞名会让行尾菜单渲染不出来
  { id: 'grants', header: '动作', minWidth: 220 },
  { id: 'createdBy', header: '授权人', width: 160 },
]
const memberActions: ActionMenuItem<WikiSpaceMember>[] = [
  { label: '调整授权', icon: 'morph:sliders-horizontal', visible: () => canManage.value, onClick: member => openEdit(member) },
  { label: '移除', icon: 'morph:user-minus', danger: true, visible: () => canDelete.value, onClick: member => removeMember(member) },
]

async function load() {
  if (!store.spaceId.value) {
    members.value = []
    total.value = 0
    return
  }
  loading.value = true
  loadError.value = ''
  try {
    const res = await fetchWikiSpaceMembers(store.spaceId.value, { page: page.value, size: size.value })
    members.value = res.data?.records || []
    total.value = res.data?.total || 0
  }
  catch (exception) {
    members.value = []
    total.value = 0
    loadError.value = exception instanceof Error ? exception.message : '成员加载失败'
  }
  finally {
    loading.value = false
  }
}

// —— 新增成员 ——
const createVisible = ref(false)
const createSaving = ref(false)
const subjectType = ref<WikiMemberSubjectType>('USER')
const createActions = ref<WikiAclAction[]>(['READ'])
/** YdTablePicker 的模型是行键（雪花 ID 字符串）数组；名字另存标签表，供提交后回显 */
const userSelection = ref<string[]>([])
const roleSelection = ref<string[]>([])
const subjectLabels = ref<Record<string, string>>({})
const subjectTypeOptions = [{ label: '用户', value: 'USER' }, { label: '角色', value: 'ROLE' }]
const userColumns: TableColumn<UserPickerRow>[] = [
  { accessorKey: 'nickname', header: '昵称' },
  { accessorKey: 'username', header: '用户名' },
]
const roleColumns: TableColumn<RoleManageItem>[] = [
  { accessorKey: 'name', header: '角色' },
  { accessorKey: 'deptName', header: '部门' },
  { accessorKey: 'code', header: '编码' },
]
const pickedSubjectId = computed(() => (subjectType.value === 'USER' ? userSelection.value : roleSelection.value)[0] || '')
const pickedSubjectName = computed(() => subjectLabels.value[pickedSubjectId.value] || pickedSubjectId.value)

async function fetchUserOptions(query: YdTablePickerQuery): Promise<YdTablePickerResult<UserPickerRow>> {
  const result = (await apiUser.page({ page: query.page, size: query.size, keyword: query.keyword })).data
  const list = (result?.records || []).map((user): UserPickerRow => {
    const nickname = user.nickname || user.username
    subjectLabels.value[user.id] = `${nickname}（${user.username}）`
    return {
      id: user.id,
      username: user.username,
      nickname,
      avatar: user.avatar ? toBackendAssetUrl(user.avatar) : undefined,
    }
  })
  return { list, total: result?.total || 0 }
}
async function fetchRoleOptions(query: YdTablePickerQuery): Promise<YdTablePickerResult<RoleManageItem>> {
  const result = (await apiRole.page({ page: query.page, size: query.size, keyword: query.keyword })).data
  const list = result?.records || []
  for (const role of list) {
    subjectLabels.value[role.id] = role.deptName ? `${role.name}（${role.deptName}）` : role.name
  }
  return { list, total: result?.total || 0 }
}

function openCreate() {
  subjectType.value = 'USER'
  createActions.value = ['READ']
  userSelection.value = []
  roleSelection.value = []
  createVisible.value = true
}

async function submitCreate() {
  if (!pickedSubjectId.value) {
    toast.warning('请先选择授权主体')
    return
  }
  if (!createActions.value.length) {
    toast.warning('至少授予一个动作')
    return
  }
  createSaving.value = true
  try {
    await createWikiSpaceMember(store.spaceId.value, {
      subjectType: subjectType.value,
      subjectId: pickedSubjectId.value,
      actions: normalizeActions(createActions.value),
    })
    toast.success(`已授权「${pickedSubjectName.value}」`)
    createVisible.value = false
    page.value = 1
    await load()
  }
  finally {
    createSaving.value = false
  }
}

// —— 调整授权（CAS：回传成员当前版本）——
const editVisible = ref(false)
const editSaving = ref(false)
const editTarget = ref<WikiSpaceMember | null>(null)
const editActions = ref<WikiAclAction[]>([])

function openEdit(member: WikiSpaceMember) {
  editTarget.value = member
  editActions.value = [...member.actions]
  editVisible.value = true
}

async function submitEdit() {
  const member = editTarget.value
  if (!member) {
    return
  }
  if (!editActions.value.length) {
    toast.warning('至少保留一个动作')
    return
  }
  editSaving.value = true
  try {
    await updateWikiSpaceMember(store.spaceId.value, member.id, {
      actions: normalizeActions(editActions.value),
      expectedVersion: member.version,
    })
    toast.success(`已更新「${member.subjectName || member.subjectId}」的授权`)
    editVisible.value = false
    await load()
  }
  finally {
    editSaving.value = false
  }
}

function removeMember(member: WikiSpaceMember) {
  const name = member.subjectName || member.subjectId
  modal.confirm({
    title: '移除空间成员',
    content: `移除「${name}」的授权后，${mode.value.key === 'RESTRICTED' ? '该主体将无法再访问本知识库（受限空间必须保留至少一名可管理成员）。' : '该授权记录会删除（全局权限模式下的访问不受影响）。'}`,
    onConfirm: async () => {
      await deleteWikiSpaceMember(store.spaceId.value, member.id)
      toast.success(`已移除「${name}」`)
      if (members.value.length === 1 && page.value > 1) {
        page.value -= 1
      }
      await load()
    },
  })
}

function enableRestricted() {
  modal.confirm({
    title: '启用空间成员授权',
    content: '启用后只有下表中的成员可以访问本知识库（未授权用户立即不可见，fail closed）。既有成员授权保留；若表中没有可管理成员，当前管理员会先获得管理动作。',
    onConfirm: async () => {
      await enableWikiRestrictedAcl(store.spaceId.value)
      toast.success('已启用空间成员授权')
      await store.reloadSpaces()
      await load()
    },
  })
}

/** 每页条数变化时回到第一页：尺寸事件本身不触发 pageChange，需要显式重载 */
function onSizeChange() {
  if (page.value !== 1) {
    page.value = 1
  }
  void load()
}

onMounted(load)
defineExpose({ reload: load })
</script>

<template>
  <div class="mem-panel">
    <YdScrollArea class="mem-scroll">
      <div class="mem-inner">
        <header class="mem-head">
          <div class="mem-head__text">
            <h2 class="mem-head__title">
              空间成员授权
            </h2>
            <p class="mem-head__desc">
              {{ mode.key === 'RESTRICTED'
                ? '本知识库只对下表成员开放：未授权用户看不到目录、页面与治理数据。'
                : '当前处于全局权限模式：下表授权暂不生效，启用限额后按成员开放访问。' }}
            </p>
          </div>
          <div class="mem-head__actions">
            <YdButton size="sm" variant="outline" :loading="loading" @click="load">
              <YdIcon name="morph:refresh-cw" /> 刷新
            </YdButton>
            <YdButton v-if="mode.key !== 'RESTRICTED'" size="sm" :disabled="!canManage" @click="enableRestricted">
              <YdIcon name="morph:shield-check" /> 启用空间成员授权
            </YdButton>
            <YdButton size="sm" :disabled="!canManage" @click="openCreate">
              <YdIcon name="morph:user-plus" /> 添加成员
            </YdButton>
          </div>
        </header>

        <section class="mem-facts" aria-label="访问模式">
          <div class="mem-fact">
            <span class="mem-fact__label">访问模式</span>
            <span class="mem-fact__value">
              <span class="mem-mode" :class="`mem-mode--${mode.key.toLowerCase()}`">{{ mode.label }}</span>
            </span>
            <span class="mem-fact__meta">{{ mode.description }}</span>
          </div>
          <div class="mem-fact">
            <span class="mem-fact__label">已授权成员</span>
            <span class="mem-fact__value">{{ total }}</span>
            <span class="mem-fact__meta">动作按「管理 &gt; 编辑 &gt; 评论 &gt; 读取」包含，高阶覆盖低阶</span>
          </div>
        </section>

        <p v-if="!canManage" class="mem-note">
          <YdIcon name="morph:lock" /> 你当前没有成员管理权限（platform:wiki:member:manage），只能查看已授权成员；需要变更请联系管理员。
        </p>

        <YdAlert
          v-if="managerMissing"
          variant="destructive"
          title="受限空间缺少可管理成员"
          description="受限空间至少要有一名具备「管理」动作的成员，否则无人能再维护授权。请尽快为一名成员授予管理动作。"
        />

        <div v-if="loadError" class="mem-error">
          <YdAlert variant="destructive" title="成员加载失败" :description="loadError" />
          <YdButton variant="outline" size="sm" :loading="loading" @click="load">
            <YdIcon name="morph:rotate-cw" /> 重新加载
          </YdButton>
        </div>

        <div v-if="loading && !members.length" class="mem-loading">
          <YdLoading :size="32" text="正在加载空间成员…" />
        </div>
        <YdEmpty
          v-else-if="!members.length && !loadError"
          icon="morph:users"
          title="尚无空间成员授权"
          :description="mode.key === 'RESTRICTED'
            ? '受限空间目前没有成员，所有用户都看不到这个知识库；请立即添加成员。'
            : '可以先添加成员并配好动作，再启用空间成员授权。'"
        />
        <template v-else-if="members.length">
          <YdResponsiveEnhancedTable
            row-key="id"
            stripe
            border
            column-visibility
            table-class="min-w-[720px]"
            :columns="memberColumns"
            :actions="memberActions"
            :data="members"
          >
            <template #cell-subject="{ row }">
              <div class="mem-subject">
                <span class="mem-subject__name">
                  {{ row.original.subjectName || row.original.subjectId }}
                  <YdTag :variant="row.original.subjectType === 'ROLE' ? 'secondary' : 'outline'">
                    {{ row.original.subjectType === 'ROLE' ? '角色' : '用户' }}
                  </YdTag>
                </span>
                <span class="mem-subject__id">ID {{ row.original.subjectId }}</span>
              </div>
            </template>
            <template #cell-grants="{ row }">
              <div class="mem-grants">
                <span class="mem-grants__tags">
                  <YdTag v-for="action in grantSummary(row.original).explicit" :key="action" :variant="action === 'MANAGE' ? 'default' : 'secondary'">
                    {{ ACTION_META[action].label }}
                  </YdTag>
                </span>
                <span v-if="grantSummary(row.original).implied.length" class="mem-grants__implied">
                  含 {{ grantSummary(row.original).implied.map(action => ACTION_META[action].label).join('、') }}
                </span>
              </div>
            </template>
            <template #cell-createdBy="{ row }">
              <span class="mem-subject__id">{{ row.original.createdByUserId || '-' }}</span>
            </template>
          </YdResponsiveEnhancedTable>
          <YdPagination
            v-if="total > size"
            v-model:page="page"
            v-model:size="size"
            :total="total"
            class="mem-pagination"
            @page-change="load"
            @size-change="onSizeChange"
          />
        </template>
      </div>
    </YdScrollArea>

    <YdModal
      v-model="createVisible"
      title="添加空间成员"
      class="w-[min(720px,92vw)]"
      show-cancel-button
      :confirm-button-loading="createSaving"
      @confirm="submitCreate"
    >
      <div class="mem-form">
        <div class="mem-form__row">
          <span class="mem-form__label">授权主体</span>
          <YdSegmented v-model="subjectType" :options="subjectTypeOptions" size="sm" aria-label="授权主体类型" />
        </div>
        <YdTablePicker
          v-if="subjectType === 'USER'"
          v-model="userSelection"
          :columns="userColumns"
          :fetcher="fetchUserOptions"
          row-key="id"
          label-key="nickname"
          :multiple="false"
          title="选择用户"
          placeholder="搜索并选择用户"
        />
        <YdTablePicker
          v-else
          v-model="roleSelection"
          :columns="roleColumns"
          :fetcher="fetchRoleOptions"
          row-key="id"
          label-key="name"
          :multiple="false"
          title="选择角色"
          placeholder="搜索并选择角色"
        />
        <div class="mem-form__row mem-form__row--stack">
          <span class="mem-form__label">授予动作</span>
          <YdCheckboxGroup v-model="createActions" :options="actionOptions" />
          <p class="mem-form__hint">
            动作按包含关系生效：授予「管理」后编辑、评论、读取自动具备，不必逐条勾选。
          </p>
        </div>
      </div>
    </YdModal>

    <YdModal
      v-model="editVisible"
      :title="`调整授权：${editTarget?.subjectName || editTarget?.subjectId || ''}`"
      class="w-[min(640px,92vw)]"
      show-cancel-button
      :confirm-button-loading="editSaving"
      @confirm="submitEdit"
    >
      <div class="mem-form">
        <YdCheckboxGroup v-model="editActions" :options="actionOptions" />
        <p class="mem-form__hint">
          提交时携带成员当前版本（v{{ editTarget?.version }}）做 CAS 校验：若期间被他人改动，保存会被拒绝并提示刷新。
        </p>
      </div>
    </YdModal>
  </div>
</template>

<style scoped>
.mem-panel {
  height: 100%;
  min-height: 0;
  background: var(--color-fill-1);
}

.mem-scroll {
  height: 100%;
}

.mem-inner {
  display: grid;
  gap: 14px;
  padding: 16px;
}

.mem-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.mem-head__title {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: var(--color-text-1);
}

.mem-head__desc {
  margin: 4px 0 0;
  max-width: 68ch;
  font-size: 13px;
  line-height: 1.6;
  color: var(--color-text-3);
}

.mem-head__actions {
  display: flex;
  flex: none;
  align-items: center;
  gap: 8px;
}

.mem-facts {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: 10px;
}

.mem-fact {
  display: grid;
  gap: 4px;
  padding: 12px 14px;
  background: var(--color-bg-2);
  border-radius: var(--border-radius-medium, 6px);
}

.mem-fact__label {
  font-size: 12px;
  color: var(--color-text-3);
}

.mem-fact__value {
  font-size: 20px;
  font-weight: 600;
  color: var(--color-text-1);
}

.mem-fact__meta {
  font-size: 12px;
  line-height: 1.6;
  color: var(--color-text-3);
}

.mem-mode {
  display: inline-flex;
  align-items: center;
  padding: 2px 10px;
  font-size: 13px;
  font-weight: 600;
  border-radius: 999px;
}

.mem-mode--legacy {
  color: var(--color-text-2);
  background: var(--color-fill-3);
}

.mem-mode--restricted {
  color: rgb(var(--success-6));
  background: color-mix(in srgb, rgb(var(--success-6)) 14%, transparent);
}

.mem-note {
  display: flex;
  gap: 6px;
  align-items: center;
  margin: 0;
  font-size: 12px;
  color: var(--color-text-3);
}

.mem-error {
  display: grid;
  gap: 8px;
}

.mem-loading {
  display: flex;
  justify-content: center;
  padding: 56px 0;
}

.mem-pagination {
  display: flex;
  justify-content: flex-end;
}

.mem-subject {
  display: grid;
  gap: 2px;
  min-width: 0;
}

.mem-subject__name {
  display: flex;
  gap: 6px;
  align-items: center;
  font-weight: 600;
  color: var(--color-text-1);
}

.mem-subject__id {
  overflow: hidden;
  font-size: 12px;
  color: var(--color-text-3);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.mem-grants {
  display: grid;
  gap: 4px;
}

.mem-grants__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.mem-grants__implied {
  font-size: 12px;
  color: var(--color-text-3);
}

.mem-form {
  display: grid;
  gap: 14px;
}

.mem-form__row {
  display: grid;
  gap: 6px;
}

.mem-form__row--stack {
  gap: 8px;
}

.mem-form__label {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-2);
}

.mem-form__hint {
  margin: 0;
  font-size: 12px;
  line-height: 1.6;
  color: var(--color-text-3);
}
</style>
