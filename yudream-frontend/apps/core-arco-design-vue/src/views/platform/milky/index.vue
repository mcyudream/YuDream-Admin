<script setup lang="ts">
import type { ActionMenuItem, StatusColumn, TableColumn, YdTablePageAction } from '@yudream/components'
import type { MentionBindingCode, MilkyConnection, MilkyConnectionPayload } from '@/api/modules/platform-milky'
import { useTablePage } from '@yudream/components'
import {
  ALL_OFFICIAL_INTENTS,
  OFFICIAL_QQ_BOT_INTENT_GROUPS,
  OFFICIAL_QQ_BOT_INTENTS,
  officialIntentSelected,
  RECOMMENDED_OFFICIAL_INTENTS,
  selectedOfficialIntentCount,
  setOfficialIntentGroup,
  toggleOfficialIntent,
} from '@/api/modules/official-qqbot-intent-catalog'
import apiMilky from '@/api/modules/platform-milky'
import { fetchSearchConfig } from '@/api/modules/system-search-config'
import MilkyChatWorkspace from './components/MilkyChatWorkspace.vue'

const toast = useYdToast()
const { auth } = useAppAuth()

const formVisible = ref(false)
const editing = ref<MilkyConnection | null>(null)
const chatConnection = ref<MilkyConnection | null>(null)
const chatVisible = ref(false)
const mentionBindVisible = ref(false)
const mentionBindConnection = ref<MilkyConnection | null>(null)
const mentionCode = ref<MentionBindingCode | null>(null)
const form = reactive<MilkyConnectionPayload>({ name: '', protocol: 'milky', baseUrl: 'http://127.0.0.1:3010', token: '', appId: '', appSecret: '', sandbox: false, intents: RECOMMENDED_OFFICIAL_INTENTS, commandMenuImageMode: 'base64', commandMenuPublicBaseUrl: '' })

/** 列表：连接是 Mongo 持久化集合，分页由后端切片；搜索字段来自 MilkyConnectionPageQuery 的 @Searchable */
const {
  loading,
  rows,
  pagination,
  searchQuery,
  searchConfig,
  load,
  search,
  resetSearch,
  onPageChange,
  onSizeChange,
} = useTablePage<MilkyConnection>({
  queryClass: 'online.yudream.base.application.platform.milky.query.MilkyConnectionPageQuery',
  loadApi: async (query) => {
    const res = await apiMilky.page({
      page: query.page,
      size: query.size,
      keyword: query.keyword || undefined,
    })
    return {
      records: res.data.records,
      total: res.data.total,
      page: res.data.page,
      size: res.data.size,
    }
  },
  loadSearchConfig: queryClass => fetchSearchConfig(queryClass).then(res => res.data),
})

/** 列：连接名称是行标识固定左侧；更新时间是审计字段，可勾选但默认不展示 */
const columns: TableColumn<MilkyConnection>[] = [
  { accessorKey: 'name', header: '连接名称', minWidth: 180, fixed: 'left' },
  { id: 'protocol', header: '协议', width: 120, align: 'center' },
  { accessorKey: 'baseUrl', header: '服务地址', minWidth: 260 },
  { accessorKey: 'updateTime', header: '更新时间', width: 180, defaultHidden: true },
]

/** 状态：enabled 是布尔字段，键走字符串，语义色胶囊在桌面与卡片共用 */
const statusColumn: StatusColumn<MilkyConnection> = {
  statusKey: 'enabled',
  header: '状态',
  width: 90,
  statusMap: {
    true: { label: '已启用', variant: 'default', tone: 'success' },
    false: { label: '已停用', variant: 'secondary', tone: 'neutral' },
  },
}

/** 行操作统一收进表格内置操作列（桌面与移动卡片共用），原按钮的启用状态依赖保留 */
const rowActions: ActionMenuItem<MilkyConnection>[] = [
  { label: '编辑', icon: 'morph:pencil', onClick: openEdit },
  { label: '测试连接', icon: 'morph:radar', onClick: test },
  { label: '回填 @ 身份', icon: 'morph:at-sign', visible: row => row.protocol === 'official', onClick: openMentionBind },
  { label: '启用', icon: 'morph:circle-play', visible: row => !row.enabled, onClick: toggle },
  { label: '停用', icon: 'morph:circle-pause', visible: row => row.enabled, onClick: toggle },
  { label: 'WebQQ', icon: 'morph:message-circle', disabled: row => !row.enabled, onClick: openChat },
]

/** 页头操作：新增连接沿用原 v-auth 语义，仅有配置权限时出现 */
const pageActions = computed<YdTablePageAction[]>(() => [
  ...(auth('platform:milky:config')
    ? [{ label: '新增连接', icon: 'morph:plus', onClick: openCreate }]
    : []),
])

function onProtocolChange(value: string | number | bigint | Record<string, any> | null | undefined) {
  if (editing.value || typeof value !== 'string' || (value !== 'milky' && value !== 'official')) {
    return
  }
  Object.assign(form, emptyForm(value), { name: form.name })
}

function onSandboxChange(value?: boolean) {
  form.baseUrl = value ? 'https://sandbox.api.bot.qq.com' : 'https://api.bot.qq.com'
}

const officialIntentCount = computed(() => selectedOfficialIntentCount(form.intents))
const officialIntentTotal = computed(() => OFFICIAL_QQ_BOT_INTENTS.length)

function isOfficialIntentChecked(bit: number) {
  return officialIntentSelected(form.intents, bit)
}

function groupIntentCheckedState(bits: number[]): boolean | 'indeterminate' {
  const selected = bits.filter(bit => officialIntentSelected(form.intents, bit)).length
  if (selected === 0) {
    return false
  }
  if (selected === bits.length) {
    return true
  }
  return 'indeterminate'
}

function toggleOfficialIntentBit(bit: number, checked: boolean | 'indeterminate' | null | undefined) {
  form.intents = toggleOfficialIntent(form.intents, bit, checked === true)
}

function toggleOfficialIntentGroupBits(bits: number[], checked: boolean | 'indeterminate' | null | undefined) {
  form.intents = setOfficialIntentGroup(form.intents, bits, checked === true ? bits : [])
}

function selectRecommendedOfficialIntents() {
  form.intents = RECOMMENDED_OFFICIAL_INTENTS
}

function selectAllOfficialIntents() {
  form.intents = ALL_OFFICIAL_INTENTS
}

function emptyForm(protocol: MilkyConnectionPayload['protocol'] = 'milky'): MilkyConnectionPayload {
  return {
    name: '',
    protocol,
    baseUrl: protocol === 'official' ? 'https://api.bot.qq.com' : 'http://127.0.0.1:3010',
    token: '',
    appId: '',
    appSecret: '',
    sandbox: false,
    intents: protocol === 'official' ? RECOMMENDED_OFFICIAL_INTENTS : undefined,
    commandMenuImageMode: 'base64',
    commandMenuPublicBaseUrl: '',
  }
}

function openCreate() {
  editing.value = null
  Object.assign(form, emptyForm())
  formVisible.value = true
}

function openEdit(connection: MilkyConnection) {
  editing.value = connection
  Object.assign(form, {
    name: connection.name,
    protocol: connection.protocol || 'milky',
    baseUrl: connection.baseUrl,
    token: '',
    appId: connection.appId || '',
    appSecret: '',
    sandbox: !!connection.sandbox,
    intents: connection.intents ?? RECOMMENDED_OFFICIAL_INTENTS,
    commandMenuImageMode: connection.commandMenuImageMode || 'base64',
    commandMenuPublicBaseUrl: connection.commandMenuPublicBaseUrl || '',
  })
  formVisible.value = true
}

async function save() {
  if (editing.value) {
    await apiMilky.update(editing.value.id, form)
    toast.success('Milky 连接已更新')
  }
  else {
    await apiMilky.create(form)
    toast.success('Milky 连接已创建')
  }
  formVisible.value = false
  await load()
}

async function toggle(connection: MilkyConnection) {
  if (connection.enabled) {
    await apiMilky.disable(connection.id)
    toast.success('连接已停用')
  }
  else {
    await apiMilky.enable(connection.id)
    toast.success('连接已启用')
  }
  await load()
}

async function test(connection: MilkyConnection) {
  const result = await apiMilky.test(connection.id)
  const name = String(result.data.nickname ?? result.data.user_name ?? result.data.user_id ?? '')
  toast.success(name ? `连接成功：${name}` : '连接测试成功')
}

function openChat(connection: MilkyConnection) {
  chatConnection.value = connection
  chatVisible.value = true
}

async function openMentionBind(connection: MilkyConnection) {
  const result = await apiMilky.issueMentionBindingCode(connection.id)
  mentionBindConnection.value = connection
  mentionCode.value = result.data
  mentionBindVisible.value = true
}

function dateText(value?: string) {
  return value ? value.replace('T', ' ').slice(0, 19) : '-'
}
</script>

<template>
  <div>
    <YdTablePageLayout
      title="QQ 消息平台"
      :actions="pageActions"
      :actions-threshold="3"
    >
      <!-- 连接总数是只读情报，按规范放页头而不占操作位 -->
      <template #page-title>
        <span class="text-sm text-muted-foreground">共 {{ pagination.total }} 个连接</span>
      </template>

      <template #search="{ fold }">
        <YdDynamicSearchForm
          v-model="searchQuery"
          :config="searchConfig"
          :fold="fold"
          @search="search"
          @reset="resetSearch"
        />
      </template>

      <template #table>
        <YdLoadingOverlay :loading="loading">
          <YdResponsiveEnhancedTable
            row-key="id"
            table-root-class="rounded-lg overflow-hidden"
            table-class="min-w-[880px]"
            column-visibility
            stripe
            border
            :status-column="statusColumn"
            :actions="rowActions"
            :columns="columns"
            :data="rows"
          >
            <template #cell-name="{ row }">
              <span class="font-medium break-all">{{ row.original.name }}</span>
            </template>
            <template #cell-protocol="{ row }">
              <YdTag variant="secondary">
                {{ row.original.protocol === 'official' ? '官方机器人' : 'Milky' }}
              </YdTag>
            </template>
            <template #cell-baseUrl="{ row }">
              <span v-if="row.original.baseUrl" class="min-w-0 whitespace-normal break-all">{{ row.original.baseUrl }}</span>
              <span v-else class="text-muted-foreground/40">-</span>
            </template>
            <template #cell-updateTime="{ row }">
              <span v-if="row.original.updateTime">{{ dateText(row.original.updateTime) }}</span>
              <span v-else class="text-muted-foreground/40">-</span>
            </template>
            <template #card="{ row }">
              <YdCard class="w-full">
                <div class="flex flex-col gap-3">
                  <div class="flex gap-2 items-center justify-between">
                    <span class="text-base font-semibold min-w-0 break-words">{{ row.name }}</span>
                    <YdTag :variant="row.enabled ? 'default' : 'secondary'">
                      {{ row.enabled ? '已启用' : '已停用' }}
                    </YdTag>
                  </div>
                  <div class="text-sm flex flex-col gap-1">
                    <div class="flex gap-2">
                      <span class="text-secondary-foreground/60 shrink-0">服务地址</span>
                      <span class="break-all">{{ row.baseUrl }}</span>
                    </div>
                    <div v-if="row.updateTime" class="flex gap-2">
                      <span class="text-secondary-foreground/60 shrink-0">更新时间</span>
                      <span>{{ dateText(row.updateTime) }}</span>
                    </div>
                  </div>
                  <div class="pt-3 border-t flex flex-wrap gap-2">
                    <YdButton size="sm" variant="outline" @click="openEdit(row)">
                      编辑
                    </YdButton>
                    <YdButton size="sm" variant="outline" @click="test(row)">
                      测试
                    </YdButton>
                    <YdButton v-if="row.protocol === 'official'" size="sm" variant="outline" @click="openMentionBind(row)">
                      回填@
                    </YdButton>
                    <YdButton size="sm" variant="outline" @click="toggle(row)">
                      {{ row.enabled ? '停用' : '启用' }}
                    </YdButton>
                    <YdButton size="sm" variant="ghost" :disabled="!row.enabled" @click="openChat(row)">
                      WebQQ
                    </YdButton>
                  </div>
                </div>
              </YdCard>
            </template>
          </YdResponsiveEnhancedTable>
        </YdLoadingOverlay>
      </template>

      <template #footer>
        <YdPagination
          v-model:page="pagination.page"
          v-model:size="pagination.size"
          :total="pagination.total"
          @page-change="onPageChange"
          @size-change="onSizeChange"
        />
      </template>
    </YdTablePageLayout>

    <YdModal v-model="formVisible" :title="editing ? '编辑 QQ 连接' : '新增 QQ 连接'" show-cancel-button @confirm="save">
      <YdForm :model="form" layout="vertical">
        <YdFormItem label="名称" required>
          <YdInput v-model="form.name" />
        </YdFormItem>
        <YdFormItem label="协议" required>
          <YdSelect
            v-model="form.protocol"
            :disabled="!!editing"
            :options="[
              { label: 'Milky（非官方协议）', value: 'milky' },
              { label: '官方 QQ 机器人 OpenAPI', value: 'official' },
            ]"
            @update:model-value="onProtocolChange"
          />
        </YdFormItem>
        <template v-if="form.protocol === 'official'">
          <YdFormItem label="AppID" required>
            <YdInput v-model="form.appId" placeholder="QQ 开放平台 AppID" />
          </YdFormItem>
          <YdFormItem :label="editing ? 'AppSecret（留空不修改）' : 'AppSecret'" :required="!editing">
            <YdInput v-model="form.appSecret" type="password" />
          </YdFormItem>
          <YdFormItem label="沙箱环境">
            <YdSwitch v-model="form.sandbox" @update:model-value="onSandboxChange" />
          </YdFormItem>
          <YdFormItem label="API 地址">
            <YdInput v-model="form.baseUrl" placeholder="https://api.bot.qq.com" />
          </YdFormItem>
          <YdFormItem label="订阅事件">
            <div class="intent-picker">
              <div class="intent-picker-meta">
                <span>已选 {{ officialIntentCount }} / {{ officialIntentTotal }} 类</span>
                <div class="intent-picker-actions">
                  <YdButton size="sm" variant="ghost" @click="selectRecommendedOfficialIntents">
                    推荐
                  </YdButton>
                  <YdButton size="sm" variant="ghost" @click="selectAllOfficialIntents">
                    全选
                  </YdButton>
                </div>
              </div>
              <div v-for="group in OFFICIAL_QQ_BOT_INTENT_GROUPS" :key="group.label" class="intent-group">
                <YdCheckbox
                  :model-value="groupIntentCheckedState(group.intents.map(intent => intent.bit))"
                  class="intent-group-title"
                  @update:model-value="checked => toggleOfficialIntentGroupBits(group.intents.map(intent => intent.bit), checked)"
                >
                  {{ group.label }}
                </YdCheckbox>
                <div class="intent-group-items">
                  <YdCheckbox
                    v-for="intent in group.intents"
                    :key="intent.code"
                    :model-value="isOfficialIntentChecked(intent.bit)"
                    @update:model-value="checked => toggleOfficialIntentBit(intent.bit, checked)"
                  >
                    <span>
                      <span class="intent-label">{{ intent.label }}</span>
                      <span class="intent-desc">{{ intent.description }}</span>
                    </span>
                  </YdCheckbox>
                </div>
              </div>
            </div>
          </YdFormItem>
        </template>
        <template v-else>
          <YdFormItem label="Milky HTTP 地址" required>
            <YdInput v-model="form.baseUrl" placeholder="http://127.0.0.1:3010" />
          </YdFormItem>
          <YdFormItem :label="editing ? 'Access Token（留空不修改）' : 'Access Token'" :required="!editing">
            <YdInput v-model="form.token" type="password" />
          </YdFormItem>
        </template>
        <YdFormItem label="指令菜单图片格式">
          <YdSelect
            v-model="form.commandMenuImageMode"
            :options="[
              { label: 'Base64（开发环境推荐）', value: 'base64' },
              { label: '公开链接（公网部署）', value: 'url' },
            ]"
          />
        </YdFormItem>
        <YdFormItem v-if="form.commandMenuImageMode === 'url'" label="公开访问基础地址">
          <YdInput v-model="form.commandMenuPublicBaseUrl" placeholder="https://admin.example.com" />
        </YdFormItem>
      </YdForm>
    </YdModal>

    <YdModal v-model="chatVisible" :title="`${chatConnection?.name || 'QQ'} WebQQ`" :show-cancel-button="false" class="sm:max-w-6xl">
      <MilkyChatWorkspace v-if="chatConnection" :connection-id="chatConnection.id" :protocol="chatConnection.protocol || 'milky'" />
    </YdModal>

    <YdModal v-model="mentionBindVisible" title="回填机器人 @ 身份" :show-cancel-button="false">
      <div class="text-sm flex flex-col gap-3">
        <p>开通「接收所有消息」后，群里所有消息都以普通消息推送，机器人无法自动得知自己在群内被 @ 时的身份。按以下步骤一次性回填：</p>
        <ol class="pl-5 list-decimal flex flex-col gap-1">
          <li>在目标群里 @ 机器人发送指令：<code class="font-mono">/绑定机器人 {{ mentionCode?.code }}</code></li>
          <li>机器人回复「已记录机器人提及身份」即完成，之后 @ 机器人即可触发对话；多个群各回填一次。</li>
        </ol>
        <div v-if="mentionCode" class="p-3 border border-[var(--color-border-2)] rounded-lg bg-[var(--color-bg-2)] flex flex-col gap-1">
          <span>回填码：<span class="text-base tracking-widest font-mono font-semibold">{{ mentionCode.code }}</span>（15 分钟内有效、一次性）</span>
          <span class="text-secondary-foreground/60">连接：{{ mentionBindConnection?.name }}</span>
        </div>
        <div v-if="mentionBindConnection?.mentionOpenIds?.length" class="text-secondary-foreground/60">
          已登记身份：{{ mentionBindConnection.mentionOpenIds.join('、') }}
        </div>
      </div>
    </YdModal>
  </div>
</template>

<style scoped>
.intent-picker {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 12px;
  background: var(--color-bg-2);
  border: 1px solid var(--color-border-2);
  border-radius: 8px;
}

.intent-picker-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  color: var(--color-text-2);
  font-size: 12px;
}

.intent-picker-actions {
  display: flex;
  gap: 4px;
}

.intent-group {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.intent-group-title {
  color: var(--color-text-1);
  font-weight: 600;
}

.intent-group-items {
  display: grid;
  grid-template-columns: 1fr;
  gap: 8px;
  padding-left: 22px;
}

.intent-label {
  display: block;
  color: var(--color-text-1);
}

.intent-desc {
  display: block;
  color: var(--color-text-3);
  font-size: 12px;
  font-weight: 400;
  line-height: 1.4;
}
</style>
