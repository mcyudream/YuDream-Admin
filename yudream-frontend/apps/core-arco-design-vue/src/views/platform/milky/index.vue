<script setup lang="ts">
import type { TableColumn } from '@yudream/components'
import type { MentionBindingCode, MilkyConnection, MilkyConnectionPayload } from '@/api/modules/platform-milky'
import apiMilky from '@/api/modules/platform-milky'
import {
  ALL_OFFICIAL_INTENTS,
  OFFICIAL_QQ_BOT_INTENTS,
  OFFICIAL_QQ_BOT_INTENT_GROUPS,
  RECOMMENDED_OFFICIAL_INTENTS,
  officialIntentSelected,
  selectedOfficialIntentCount,
  setOfficialIntentGroup,
  toggleOfficialIntent,
} from '@/api/modules/official-qqbot-intent-catalog'
import MilkyChatWorkspace from './components/MilkyChatWorkspace.vue'

const toast = useFaToast()
const loading = ref(false)
const formVisible = ref(false)
const rows = ref<MilkyConnection[]>([])
const editing = ref<MilkyConnection | null>(null)
const chatConnection = ref<MilkyConnection | null>(null)
const chatVisible = ref(false)
const mentionBindVisible = ref(false)
const mentionBindConnection = ref<MilkyConnection | null>(null)
const mentionCode = ref<MentionBindingCode | null>(null)
const page = reactive({ page: 1, size: 20, total: 0 })
const form = reactive<MilkyConnectionPayload>({ name: '', protocol: 'milky', baseUrl: 'http://127.0.0.1:3010', token: '', appId: '', appSecret: '', sandbox: false, intents: RECOMMENDED_OFFICIAL_INTENTS, commandMenuImageMode: 'base64', commandMenuPublicBaseUrl: '' })

const columns: TableColumn<MilkyConnection>[] = [
  { accessorKey: 'name', header: '连接名称', width: 180 },
  { id: 'protocol', header: '协议', width: 120 },
  { accessorKey: 'baseUrl', header: '服务地址', width: 280 },
  { id: 'status', header: '状态', width: 100 },
  { accessorKey: 'updateTime', header: '更新时间', width: 180 },
  { id: 'actions', header: '操作', width: 240, fixed: 'right' },
]

async function load() {
  loading.value = true
  try {
    const result = await apiMilky.page({ page: page.page, size: page.size })
    rows.value = result.data.records
    page.total = result.data.total
  }
  finally {
    loading.value = false
  }
}

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

onMounted(load)
</script>

<template>
  <div>
    <FaPageHeader title="QQ 消息平台">
      <FaButton v-auth="'platform:milky:config'" @click="openCreate">
        <FaIcon name="i-ri:add-line" />
        新增连接
      </FaButton>
    </FaPageHeader>

    <FaPageMain>
      <FaResponsiveTable v-loading="loading" :columns="columns" :data="rows" row-key="id" border stripe>
        <template #cell-protocol="{ row }">
          <FaTag variant="secondary">
            {{ row.original.protocol === 'official' ? '官方机器人' : 'Milky' }}
          </FaTag>
        </template>
        <template #cell-status="{ row }">
          <FaTag :variant="row.original.enabled ? 'default' : 'secondary'">
            {{ row.original.enabled ? '已启用' : '已停用' }}
          </FaTag>
        </template>
        <template #cell-actions="{ row }">
          <div class="flex items-center gap-1">
            <FaButton size="sm" variant="ghost" title="编辑" @click="openEdit(row.original)">
              <FaIcon name="i-ri:edit-line" />
            </FaButton>
            <FaButton size="sm" variant="ghost" title="测试连接" @click="test(row.original)">
              <FaIcon name="i-ri:radar-line" />
            </FaButton>
            <FaButton v-if="row.original.protocol === 'official'" size="sm" variant="ghost" title="回填 @ 身份" @click="openMentionBind(row.original)">
              <FaIcon name="i-ri:at-line" />
            </FaButton>
            <FaButton size="sm" variant="ghost" :title="row.original.enabled ? '停用' : '启用'" @click="toggle(row.original)">
              <FaIcon :name="row.original.enabled ? 'i-ri:pause-circle-line' : 'i-ri:play-circle-line'" />
            </FaButton>
            <FaButton size="sm" variant="ghost" :disabled="!row.original.enabled" title="WebQQ" @click="openChat(row.original)">
              <FaIcon name="i-ri:chat-3-line" />
            </FaButton>
          </div>
        </template>
        <template #card="{ row }">
          <FaCard class="w-full">
            <div class="flex flex-col gap-3">
              <div class="flex items-center justify-between gap-2">
                <span class="min-w-0 break-words text-base font-semibold">{{ row.name }}</span>
                <FaTag :variant="row.enabled ? 'default' : 'secondary'">
                  {{ row.enabled ? '已启用' : '已停用' }}
                </FaTag>
              </div>
              <div class="flex flex-col gap-1 text-sm">
                <div class="flex gap-2">
                  <span class="shrink-0 text-secondary-foreground/60">服务地址</span>
                  <span class="break-all">{{ row.baseUrl }}</span>
                </div>
                <div v-if="row.updateTime" class="flex gap-2">
                  <span class="shrink-0 text-secondary-foreground/60">更新时间</span>
                  <span>{{ row.updateTime }}</span>
                </div>
              </div>
              <div class="flex flex-wrap gap-2 border-t pt-3">
                <FaButton size="sm" variant="outline" @click="openEdit(row)">
                  编辑
                </FaButton>
                <FaButton size="sm" variant="outline" @click="test(row)">
                  测试
                </FaButton>
                <FaButton v-if="row.protocol === 'official'" size="sm" variant="outline" @click="openMentionBind(row)">
                  回填@
                </FaButton>
                <FaButton size="sm" variant="outline" @click="toggle(row)">
                  {{ row.enabled ? '停用' : '启用' }}
                </FaButton>
                <FaButton size="sm" variant="ghost" :disabled="!row.enabled" @click="openChat(row)">
                  WebQQ
                </FaButton>
              </div>
            </div>
          </FaCard>
        </template>
      </FaResponsiveTable>
      <FaPagination v-model:page="page.page" v-model:size="page.size" :total="page.total" class="mt-3" @page-change="load" @size-change="load" />
    </FaPageMain>

    <FaModal v-model="formVisible" :title="editing ? '编辑 QQ 连接' : '新增 QQ 连接'" show-cancel-button @confirm="save">
      <a-form :model="form" layout="vertical">
        <a-form-item label="名称" required>
          <FaInput v-model="form.name" />
        </a-form-item>
        <a-form-item label="协议" required>
          <FaSelect
            v-model="form.protocol"
            :disabled="!!editing"
            :options="[
              { label: 'Milky（非官方协议）', value: 'milky' },
              { label: '官方 QQ 机器人 OpenAPI', value: 'official' },
            ]"
            @update:model-value="onProtocolChange"
          />
        </a-form-item>
        <template v-if="form.protocol === 'official'">
          <a-form-item label="AppID" required>
            <FaInput v-model="form.appId" placeholder="QQ 开放平台 AppID" />
          </a-form-item>
          <a-form-item :label="editing ? 'AppSecret（留空不修改）' : 'AppSecret'" :required="!editing">
            <FaInput v-model="form.appSecret" type="password" />
          </a-form-item>
          <a-form-item label="沙箱环境">
            <FaSwitch v-model="form.sandbox" @update:model-value="onSandboxChange" />
          </a-form-item>
          <a-form-item label="API 地址">
            <FaInput v-model="form.baseUrl" placeholder="https://api.bot.qq.com" />
          </a-form-item>
          <a-form-item label="订阅事件">
            <div class="intent-picker">
              <div class="intent-picker-meta">
                <span>已选 {{ officialIntentCount }} / {{ officialIntentTotal }} 类</span>
                <div class="intent-picker-actions">
                  <FaButton size="sm" variant="ghost" @click="selectRecommendedOfficialIntents">推荐</FaButton>
                  <FaButton size="sm" variant="ghost" @click="selectAllOfficialIntents">全选</FaButton>
                </div>
              </div>
              <div v-for="group in OFFICIAL_QQ_BOT_INTENT_GROUPS" :key="group.label" class="intent-group">
                <FaCheckbox
                  :model-value="groupIntentCheckedState(group.intents.map(intent => intent.bit))"
                  class="intent-group-title"
                  @update:model-value="checked => toggleOfficialIntentGroupBits(group.intents.map(intent => intent.bit), checked)"
                >
                  {{ group.label }}
                </FaCheckbox>
                <div class="intent-group-items">
                  <FaCheckbox
                    v-for="intent in group.intents"
                    :key="intent.code"
                    :model-value="isOfficialIntentChecked(intent.bit)"
                    @update:model-value="checked => toggleOfficialIntentBit(intent.bit, checked)"
                  >
                    <span>
                      <span class="intent-label">{{ intent.label }}</span>
                      <span class="intent-desc">{{ intent.description }}</span>
                    </span>
                  </FaCheckbox>
                </div>
              </div>
            </div>
          </a-form-item>
        </template>
        <template v-else>
          <a-form-item label="Milky HTTP 地址" required>
            <FaInput v-model="form.baseUrl" placeholder="http://127.0.0.1:3010" />
          </a-form-item>
          <a-form-item :label="editing ? 'Access Token（留空不修改）' : 'Access Token'" :required="!editing">
            <FaInput v-model="form.token" type="password" />
          </a-form-item>
        </template>
        <a-form-item label="指令菜单图片格式">
          <FaSelect
            v-model="form.commandMenuImageMode"
            :options="[
              { label: 'Base64（开发环境推荐）', value: 'base64' },
              { label: '公开链接（公网部署）', value: 'url' },
            ]"
          />
        </a-form-item>
        <a-form-item v-if="form.commandMenuImageMode === 'url'" label="公开访问基础地址">
          <FaInput v-model="form.commandMenuPublicBaseUrl" placeholder="https://admin.example.com" />
        </a-form-item>
      </a-form>
    </FaModal>

    <FaModal v-model="chatVisible" :title="`${chatConnection?.name || 'QQ'} WebQQ`" :show-cancel-button="false" class="sm:max-w-6xl">
      <MilkyChatWorkspace v-if="chatConnection" :connection-id="chatConnection.id" :protocol="chatConnection.protocol || 'milky'" />
    </FaModal>

    <FaModal v-model="mentionBindVisible" title="回填机器人 @ 身份" :show-cancel-button="false">
      <div class="flex flex-col gap-3 text-sm">
        <p>开通「接收所有消息」后，群里所有消息都以普通消息推送，机器人无法自动得知自己在群内被 @ 时的身份。按以下步骤一次性回填：</p>
        <ol class="flex flex-col gap-1 pl-5 list-decimal">
          <li>在目标群里 @ 机器人发送指令：<code class="font-mono">/绑定机器人 {{ mentionCode?.code }}</code></li>
          <li>机器人回复「已记录机器人提及身份」即完成，之后 @ 机器人即可触发对话；多个群各回填一次。</li>
        </ol>
        <div v-if="mentionCode" class="flex flex-col gap-1 rounded-lg border border-[var(--color-border-2)] bg-[var(--color-bg-2)] p-3">
          <span>回填码：<span class="font-mono text-base font-semibold tracking-widest">{{ mentionCode.code }}</span>（15 分钟内有效、一次性）</span>
          <span class="text-secondary-foreground/60">连接：{{ mentionBindConnection?.name }}</span>
        </div>
        <div v-if="mentionBindConnection?.mentionOpenIds?.length" class="text-secondary-foreground/60">
          已登记身份：{{ mentionBindConnection.mentionOpenIds.join('、') }}
        </div>
      </div>
    </FaModal>
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
