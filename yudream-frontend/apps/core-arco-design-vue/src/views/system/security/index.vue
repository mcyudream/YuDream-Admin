<script setup lang="ts">
import type { TableColumn } from '@yudream/components'
import type {
  ApiKeyCredential,
  ApiKeyCreatePayload,
  ApiSecurityPolicy,
  CredentialStatus,
  OAuthClient,
  OAuthClientAuthMethod,
  OAuthClientPayload,
  OAuthGrantType,
  OAuthProvider,
  OAuthProviderPayload,
  OAuthRegistrationStatus,
  PasskeyCredential,
} from '@/api/modules/system-security'
import type { PermissionItem } from '@/api/modules/system-role'
import apiSecurity from '@/api/modules/system-security'
import apiRole from '@/api/modules/system-role'
import { clearApiEncryptionCache } from '@/utils/api-encryption'

type SecurityTab = 'policy' | 'apiKey' | 'oauth' | 'passkey'
type OAuthPane = 'clients' | 'providers'

const modal = useFaModal()
const toast = useFaToast()

const activeTab = ref<SecurityTab>('policy')
const oauthPane = ref<OAuthPane>('clients')
const loading = ref(false)
const savingPolicy = ref(false)
const creating = ref(false)

const apiKeyRows = ref<ApiKeyCredential[]>([])
const oauthClients = ref<OAuthClient[]>([])
const oauthProviders = ref<OAuthProvider[]>([])
const passkeyRows = ref<PasskeyCredential[]>([])
const permissions = ref<PermissionItem[]>([])

const pagination = reactive({ page: 1, size: 10, total: 0 })
const search = reactive({ keyword: '' })
const passkeySearch = reactive({ userId: '' })

const createVisible = ref(false)
const secretVisible = ref(false)
const oneTimeSecret = ref('')
const oauthClientVisible = ref(false)
const oauthProviderVisible = ref(false)
const editingOAuthClient = ref<OAuthClient | null>(null)
const editingOAuthProvider = ref<OAuthProvider | null>(null)

const policy = reactive<ApiSecurityPolicy>({
  apiEncryptionEnabled: false,
  dualTokenEnabled: false,
  apiKeyEnabled: false,
  passkeyEnabled: false,
  oauthServerEnabled: false,
  oauthClientEnabled: false,
  accessTokenTtlSeconds: 7200,
  refreshTokenTtlSeconds: 604800,
  refreshRotationEnabled: true,
})

const apiKeyForm = reactive<ApiKeyCreatePayload>({
  name: '',
  permissions: [],
  expireTime: undefined,
})

const oauthClientForm = reactive<OAuthClientPayload>({
  clientId: '',
  clientName: '',
  authMethod: 'CLIENT_SECRET_BASIC',
  grantTypes: ['AUTHORIZATION_CODE', 'REFRESH_TOKEN'],
  redirectUris: [],
  scopes: ['openid', 'profile', 'email'],
  accessTokenTtlSeconds: 7200,
  refreshTokenTtlSeconds: 604800,
  status: 'ACTIVE',
})

const oauthProviderForm = reactive<OAuthProviderPayload>({
  code: '',
  name: '',
  issuerUri: '',
  authorizationUri: '',
  tokenUri: '',
  userInfoUri: '',
  clientId: '',
  clientSecret: '',
  authMethod: 'CLIENT_SECRET_POST',
  scopes: ['openid', 'profile', 'email'],
  redirectUri: '',
  status: 'ACTIVE',
})

const tabOptions = [
  { key: 'policy', label: '安全策略', icon: 'i-ri:shield-check-line' },
  { key: 'apiKey', label: 'API Key', icon: 'i-ri:key-2-line' },
  { key: 'oauth', label: 'OAuth', icon: 'i-ri:login-circle-line' },
  { key: 'passkey', label: 'Passkey', icon: 'i-ri:fingerprint-line' },
] as const

const oauthEnabled = computed(() => policy.oauthServerEnabled || policy.oauthClientEnabled)

const tabs = computed(() => tabOptions.filter((tab) => {
  if (tab.key === 'apiKey') {
    return policy.apiKeyEnabled
  }
  if (tab.key === 'passkey') {
    return policy.passkeyEnabled
  }
  if (tab.key === 'oauth') {
    return oauthEnabled.value
  }
  return true
}))

const policyCards = computed(() => [
  { key: 'apiEncryptionEnabled', label: '接口加密', desc: '请求体和响应体使用 RSA-OAEP + AES-GCM 加密', icon: 'i-ri:lock-password-line' },
  { key: 'dualTokenEnabled', label: '双 Token', desc: '访问令牌与刷新令牌分离，可配置有效期', icon: 'i-ri:token-swap-line' },
  { key: 'apiKeyEnabled', label: 'API Key', desc: '第三方访问密钥认证，权限不超过创建人', icon: 'i-ri:key-2-line' },
  { key: 'passkeyEnabled', label: 'Passkey', desc: '无密码登录扩展入口', icon: 'i-ri:fingerprint-line' },
  { key: 'oauthServerEnabled', label: 'OAuth 服务端', desc: '向第三方应用签发授权', icon: 'i-ri:server-line' },
  { key: 'oauthClientEnabled', label: 'OAuth 客户端', desc: '接入外部身份提供商', icon: 'i-ri:login-circle-line' },
] as const)

const permissionGroups = computed(() => {
  const groups: Record<string, PermissionItem[]> = {}
  permissions.value.filter(item => item.status === 'ACTIVE').forEach((item) => {
    const key = item.module || '其他'
    groups[key] ||= []
    groups[key].push(item)
  })
  return groups
})

const permissionSelectOptions = computed(() => Object.entries(permissionGroups.value).map(([module, items]) => ({
  label: module,
  options: items.map(item => ({
    label: `${item.name} (${item.code})`,
    value: item.code,
  })),
})))

const selectablePermissionCodes = computed(() => Object.values(permissionGroups.value).flat().map(item => item.code))

const permissionNameMap = computed(() => new Map(permissions.value.map(item => [item.code, item.name])))

const apiKeyColumns = computed<TableColumn<ApiKeyCredential>[]>(() => [
  { accessorKey: 'name', header: '名称', width: 180, fixed: 'left' },
  { accessorKey: 'maskedValue', header: '密钥标识', width: 180 },
  { id: 'permissions', header: '权限', width: 220 },
  { id: 'status', header: '状态', width: 100, align: 'center' },
  { accessorKey: 'lastUsedTime', header: '最后使用', width: 180 },
  { accessorKey: 'expireTime', header: '过期时间', width: 180 },
  { id: 'operation', header: '操作', width: 120, align: 'center', fixed: 'right' },
])

const oauthClientColumns = computed<TableColumn<OAuthClient>[]>(() => [
  { accessorKey: 'clientName', header: '客户端名称', width: 180, fixed: 'left' },
  { accessorKey: 'clientId', header: 'Client ID', width: 180 },
  { id: 'grantTypes', header: '授权模式', width: 220 },
  { id: 'redirectUris', header: '回调地址', width: 260 },
  { id: 'status', header: '状态', width: 100, align: 'center' },
  { id: 'operation', header: '操作', width: 160, align: 'center', fixed: 'right' },
])

const oauthProviderColumns = computed<TableColumn<OAuthProvider>[]>(() => [
  { accessorKey: 'name', header: '提供商', width: 160, fixed: 'left' },
  { accessorKey: 'code', header: '编码', width: 140 },
  { accessorKey: 'clientId', header: 'Client ID', width: 180 },
  { accessorKey: 'authorizationUri', header: '授权地址', width: 260 },
  { id: 'status', header: '状态', width: 100, align: 'center' },
  { id: 'operation', header: '操作', width: 160, align: 'center', fixed: 'right' },
])

const passkeyColumns = computed<TableColumn<PasskeyCredential>[]>(() => [
  { accessorKey: 'deviceName', header: '设备名称', width: 180, fixed: 'left' },
  { accessorKey: 'userId', header: '用户 ID', width: 140 },
  { accessorKey: 'credentialId', header: '凭据 ID', width: 260 },
  { accessorKey: 'signCount', header: '签名次数', width: 100, align: 'right' },
  { id: 'status', header: '状态', width: 100, align: 'center' },
  { accessorKey: 'lastUsedTime', header: '最后使用', width: 180 },
  { id: 'operation', header: '操作', width: 120, align: 'center', fixed: 'right' },
])

const authMethodOptions = [
  { label: 'client_secret_basic', value: 'CLIENT_SECRET_BASIC' },
  { label: 'client_secret_post', value: 'CLIENT_SECRET_POST' },
  { label: 'none', value: 'NONE' },
]

const statusOptions = [
  { label: '启用', value: 'ACTIVE' },
  { label: '停用', value: 'DISABLED' },
]

onMounted(async () => {
  await loadPolicy()
  await loadFeatureData()
})

watch([
  () => policy.apiKeyEnabled,
  () => policy.passkeyEnabled,
  () => policy.oauthServerEnabled,
  () => policy.oauthClientEnabled,
], syncActiveFeatureTabs)

async function loadPolicy() {
  const res = await apiSecurity.policy()
  Object.assign(policy, res.data)
}

async function savePolicy() {
  savingPolicy.value = true
  try {
    const res = await apiSecurity.updatePolicy(policy)
    Object.assign(policy, res.data)
    clearApiEncryptionCache()
    toast.success('安全策略已保存')
    await loadFeatureData()
  }
  finally {
    savingPolicy.value = false
  }
}

function syncActiveFeatureTabs() {
  if (!tabs.value.some(tab => tab.key === activeTab.value)) {
    activeTab.value = 'policy'
  }
  if (oauthPane.value === 'clients' && !policy.oauthServerEnabled && policy.oauthClientEnabled) {
    oauthPane.value = 'providers'
  }
  if (oauthPane.value === 'providers' && !policy.oauthClientEnabled && policy.oauthServerEnabled) {
    oauthPane.value = 'clients'
  }
}

async function loadApiKeys() {
  if (!policy.apiKeyEnabled) {
    apiKeyRows.value = []
    pagination.total = 0
    return
  }
  loading.value = true
  try {
    const res = await apiSecurity.pageApiKeys({
      page: pagination.page,
      size: pagination.size,
      keyword: search.keyword || undefined,
    })
    apiKeyRows.value = res.data.records
    pagination.total = res.data.total
  }
  finally {
    loading.value = false
  }
}

async function loadPermissions() {
  if (!policy.apiKeyEnabled) {
    permissions.value = []
    return
  }
  const res = await apiRole.permissions()
  permissions.value = res.data
}

async function loadOAuth() {
  oauthClients.value = []
  oauthProviders.value = []
  const tasks = []
  if (policy.oauthServerEnabled) {
    tasks.push(apiSecurity.oauthClients().then(res => oauthClients.value = res.data))
  }
  if (policy.oauthClientEnabled) {
    tasks.push(apiSecurity.oauthProviders().then(res => oauthProviders.value = res.data))
  }
  if (!tasks.length) {
    return
  }
  await Promise.all(tasks)
}

async function loadFeatureData() {
  await Promise.all([
    loadApiKeys(),
    loadPermissions(),
    loadOAuth(),
    syncPasskeyState(),
  ])
}

async function syncPasskeyState() {
  if (!policy.passkeyEnabled) {
    passkeyRows.value = []
    return
  }
  await loadPasskeys()
}

function oauthPaneEnabled() {
  return oauthPane.value === 'clients' ? policy.oauthServerEnabled : policy.oauthClientEnabled
}

function oauthDisabledMessage() {
  return oauthPane.value === 'clients'
    ? 'OAuth 服务端未启用，客户端管理与授权码签发入口已关闭。'
    : 'OAuth 客户端未启用，外部身份提供商入口已关闭。'
}

function apiKeyDisabledMessage() {
  return 'API Key 未启用，密钥创建、列表与吊销入口已关闭。'
}

function passkeyDisabledMessage() {
  return 'Passkey 未启用，用户凭据查询、注册和登录入口已关闭。'
}

async function guardedLoadPasskeys() {
  if (!policy.passkeyEnabled) {
    return
  }
  await loadPasskeys()
}

async function guardedLoadApiKeys() {
  if (!policy.apiKeyEnabled) {
    return
  }
  await loadApiKeys()
}

function guardedOpenCreate() {
  if (!policy.apiKeyEnabled) {
    return
  }
  openCreate()
}

function guardedOpenOAuthClient(row?: OAuthClient) {
  if (!policy.oauthServerEnabled) {
    return
  }
  openOAuthClient(row)
}

function guardedOpenOAuthProvider(row?: OAuthProvider) {
  if (!policy.oauthClientEnabled) {
    return
  }
  openOAuthProvider(row)
}

async function loadPasskeys() {
  loading.value = true
  try {
    const userId = passkeySearch.userId.trim()
    const res = await apiSecurity.passkeys(userId || undefined)
    passkeyRows.value = res.data
  }
  finally {
    loading.value = false
  }
}

function openCreate() {
  Object.assign(apiKeyForm, {
    name: '',
    permissions: [],
    expireTime: undefined,
  })
  createVisible.value = true
}

async function createApiKey() {
  const selectedPermissions = selectedApiKeyPermissions()
  if (!apiKeyForm.name.trim()) {
    toast.error('请输入 API Key 名称')
    return
  }
  if (!selectedPermissions.length) {
    toast.error('请选择权限范围')
    return
  }
  creating.value = true
  try {
    const res = await apiSecurity.createApiKey({
      name: apiKeyForm.name.trim(),
      permissions: selectedPermissions,
      expireTime: normalizeDateTime(apiKeyForm.expireTime),
    })
    oneTimeSecret.value = res.data.plaintext
    secretVisible.value = true
    createVisible.value = false
    toast.success('API Key 已创建')
    await loadApiKeys()
  }
  finally {
    creating.value = false
  }
}

function selectedApiKeyPermissions() {
  const selectableCodes = new Set(selectablePermissionCodes.value)
  return apiKeyForm.permissions.filter(permission => selectableCodes.has(permission))
}

function permissionText(code: string) {
  return permissionNameMap.value.get(code) || code
}

function confirmRevoke(row: ApiKeyCredential) {
  modal.confirm({
    title: '确认吊销',
    content: `确认吊销「${row.name}」吗？吊销后无法恢复。`,
    onConfirm: async () => {
      await apiSecurity.revokeApiKey(row.id)
      toast.success('已吊销')
      await loadApiKeys()
    },
  })
}

function openOAuthClient(row?: OAuthClient) {
  editingOAuthClient.value = row || null
  Object.assign(oauthClientForm, row
    ? {
        clientId: row.clientId,
        clientName: row.clientName,
        authMethod: row.authMethod,
        grantTypes: [...(row.grantTypes || [])],
        redirectUris: [...(row.redirectUris || [])],
        scopes: [...(row.scopes || [])],
        accessTokenTtlSeconds: row.accessTokenTtlSeconds,
        refreshTokenTtlSeconds: row.refreshTokenTtlSeconds,
        status: row.status,
      }
    : {
        clientId: '',
        clientName: '',
        authMethod: 'CLIENT_SECRET_BASIC' as OAuthClientAuthMethod,
        grantTypes: ['AUTHORIZATION_CODE', 'REFRESH_TOKEN'] as OAuthGrantType[],
        redirectUris: [],
        scopes: ['openid', 'profile', 'email'],
        accessTokenTtlSeconds: 7200,
        refreshTokenTtlSeconds: 604800,
        status: 'ACTIVE' as OAuthRegistrationStatus,
      })
  oauthClientVisible.value = true
}

async function saveOAuthClient() {
  const payload = normalizeOAuthClientPayload()
  if (editingOAuthClient.value) {
    await apiSecurity.updateOAuthClient(editingOAuthClient.value.id, payload)
    toast.success('OAuth 客户端已保存')
  }
  else {
    const res = await apiSecurity.createOAuthClient(payload)
    oneTimeSecret.value = res.data.clientSecret
    secretVisible.value = true
    toast.success('OAuth 客户端已创建')
  }
  oauthClientVisible.value = false
  await loadOAuth()
}

function openOAuthProvider(row?: OAuthProvider) {
  editingOAuthProvider.value = row || null
  Object.assign(oauthProviderForm, row
    ? {
        code: row.code,
        name: row.name,
        issuerUri: row.issuerUri || '',
        authorizationUri: row.authorizationUri || '',
        tokenUri: row.tokenUri || '',
        userInfoUri: row.userInfoUri || '',
        clientId: row.clientId || '',
        clientSecret: '',
        authMethod: row.authMethod,
        scopes: [...(row.scopes || [])],
        redirectUri: row.redirectUri || '',
        status: row.status,
      }
    : {
        code: '',
        name: '',
        issuerUri: '',
        authorizationUri: '',
        tokenUri: '',
        userInfoUri: '',
        clientId: '',
        clientSecret: '',
        authMethod: 'CLIENT_SECRET_POST' as OAuthClientAuthMethod,
        scopes: ['openid', 'profile', 'email'],
        redirectUri: '',
        status: 'ACTIVE' as OAuthRegistrationStatus,
      })
  oauthProviderVisible.value = true
}

async function saveOAuthProvider() {
  const payload = normalizeOAuthProviderPayload()
  if (editingOAuthProvider.value) {
    await apiSecurity.updateOAuthProvider(editingOAuthProvider.value.id, payload)
  }
  else {
    await apiSecurity.createOAuthProvider(payload)
  }
  oauthProviderVisible.value = false
  toast.success('OAuth 提供商已保存')
  await loadOAuth()
}

function confirmDisableOAuthClient(row: OAuthClient) {
  modal.confirm({
    title: '确认停用',
    content: `确认停用 OAuth 客户端「${row.clientName}」吗？`,
    onConfirm: async () => {
      await apiSecurity.disableOAuthClient(row.id)
      toast.success('已停用')
      await loadOAuth()
    },
  })
}

function confirmEnableOAuthClient(row: OAuthClient) {
  modal.confirm({
    title: '确认启用',
    content: `确认启用 OAuth 客户端「${row.clientName}」吗？`,
    onConfirm: async () => {
      await apiSecurity.enableOAuthClient(row.id)
      toast.success('已启用')
      await loadOAuth()
    },
  })
}

function confirmDisableOAuthProvider(row: OAuthProvider) {
  modal.confirm({
    title: '确认停用',
    content: `确认停用 OAuth 提供商「${row.name}」吗？`,
    onConfirm: async () => {
      await apiSecurity.disableOAuthProvider(row.id)
      toast.success('已停用')
      await loadOAuth()
    },
  })
}

function confirmEnableOAuthProvider(row: OAuthProvider) {
  modal.confirm({
    title: '确认启用',
    content: `确认启用 OAuth 提供商「${row.name}」吗？`,
    onConfirm: async () => {
      await apiSecurity.enableOAuthProvider(row.id)
      toast.success('已启用')
      await loadOAuth()
    },
  })
}

function confirmRevokePasskey(row: PasskeyCredential) {
  modal.confirm({
    title: '确认吊销',
    content: `确认吊销 Passkey「${row.deviceName || row.credentialId}」吗？`,
    onConfirm: async () => {
      await apiSecurity.revokePasskey(row.id)
      toast.success('Passkey 已吊销')
      await loadPasskeys()
    },
  })
}

async function copySecret() {
  await navigator.clipboard.writeText(oneTimeSecret.value)
  toast.success('已复制')
}

function resetSearch() {
  search.keyword = ''
  pagination.page = 1
  loadApiKeys()
}

function onPageChange(page: number) {
  pagination.page = page
  loadApiKeys()
}

function onSizeChange(size: number) {
  pagination.size = size
  pagination.page = 1
  loadApiKeys()
}

function normalizeOAuthClientPayload(): OAuthClientPayload {
  return {
    ...oauthClientForm,
    redirectUris: cleanList(oauthClientForm.redirectUris),
    scopes: cleanList(oauthClientForm.scopes),
  }
}

function normalizeOAuthProviderPayload(): OAuthProviderPayload {
  return {
    ...oauthProviderForm,
    scopes: cleanList(oauthProviderForm.scopes),
    clientSecret: oauthProviderForm.clientSecret || undefined,
  }
}

function cleanList(values?: string[]) {
  return (values || []).map(item => item.trim()).filter(Boolean)
}

function splitTextarea(value?: string[]) {
  return (value || []).join('\n')
}

function updateList(target: string[], value: string | undefined) {
  if (value === undefined) {
    return
  }
  target.splice(0, target.length, ...value.split(/\r?\n|,/).map(item => item.trim()).filter(Boolean))
}

function statusText(status: CredentialStatus | OAuthRegistrationStatus) {
  const map: Record<string, string> = {
    ACTIVE: '启用',
    DISABLED: '停用',
    REVOKED: '已吊销',
    EXPIRED: '已过期',
  }
  return map[status] || status
}

function statusVariant(status: CredentialStatus | OAuthRegistrationStatus) {
  return status === 'ACTIVE' ? 'default' : 'secondary'
}

function normalizeDateTime(value?: string) {
  return value || undefined
}
</script>

<template>
  <div>
    <FaPageHeader title="安全中心" class="mb-0">
      <FaButton v-if="activeTab === 'policy'" v-auth="'system:security:edit'" :loading="savingPolicy" @click="savePolicy">
        <FaIcon name="i-ri:save-3-line" />
        保存策略
      </FaButton>
    </FaPageHeader>

    <FaPageMain>
      <div class="security-tabs">
        <button v-for="tab in tabs" :key="tab.key" type="button" :class="{ active: activeTab === tab.key }" @click="activeTab = tab.key">
          <FaIcon :name="tab.icon" />
          <span>{{ tab.label }}</span>
        </button>
      </div>

      <section v-if="activeTab === 'policy'" class="panel">
        <div class="section-title">
          <FaIcon name="i-ri:shield-check-line" />
          安全策略
        </div>
        <div class="policy-grid">
          <div v-for="item in policyCards" :key="item.key" class="policy-card">
            <div class="policy-icon">
              <FaIcon :name="item.icon" />
            </div>
            <div class="policy-body">
              <strong>{{ item.label }}</strong>
              <span>{{ item.desc }}</span>
            </div>
            <FaSwitch v-model="policy[item.key]" />
          </div>
        </div>
        <div class="token-grid">
          <a-form-item label="访问令牌有效期（秒）">
            <FaInput v-model.number="policy.accessTokenTtlSeconds" type="number" min="1" />
          </a-form-item>
          <a-form-item label="刷新令牌有效期（秒）">
            <FaInput v-model.number="policy.refreshTokenTtlSeconds" type="number" min="1" />
          </a-form-item>
          <a-form-item label="刷新令牌轮换">
            <FaSwitch v-model="policy.refreshRotationEnabled" />
          </a-form-item>
        </div>
      </section>

      <section v-if="activeTab === 'apiKey'" class="panel">
        <FaAlert v-if="!policy.apiKeyEnabled" icon="i-ri:information-line" title="API Key 未启用" :description="apiKeyDisabledMessage()" />
        <div class="key-toolbar">
          <div class="section-title">
            <FaIcon name="i-ri:key-line" />
            API Key
          </div>
          <div class="key-actions">
            <FaButton v-auth="'system:security:api-key:create'" :disabled="!policy.apiKeyEnabled" @click="guardedOpenCreate">
              <FaIcon name="i-ri:key-2-line" />
              创建
            </FaButton>
          </div>
        </div>
        <FaTable
          v-loading="loading"
          row-key="id"
          table-root-class="rounded-lg overflow-hidden"
          table-class="min-w-[980px]"
          column-visibility
          border
          stripe
          :columns="apiKeyColumns"
          :data="apiKeyRows"
        >
          <template #toolbar>
            <FaSearchBar class="w-full">
              <div class="grid grid-cols-1 gap-3 md:grid-cols-[minmax(260px,360px)_auto] md:items-center">
                <FaInput v-model="search.keyword" clearable placeholder="名称 / 前缀" :disabled="!policy.apiKeyEnabled" @keydown.enter="guardedLoadApiKeys" @clear="guardedLoadApiKeys" />
                <div class="flex gap-2 md:justify-end">
                  <FaButton variant="outline" :disabled="!policy.apiKeyEnabled" @click="resetSearch">重置</FaButton>
                  <FaButton :disabled="!policy.apiKeyEnabled" @click="guardedLoadApiKeys">
                    <FaIcon name="i-ri:search-line" />
                    筛选
                  </FaButton>
                </div>
              </div>
            </FaSearchBar>
          </template>
          <template #cell-permissions="{ row }">
            <div class="tag-row">
              <FaTag v-for="item in row.original.permissions.slice(0, 3)" :key="item" variant="secondary">
                {{ permissionText(item) }}
              </FaTag>
              <FaTag v-if="row.original.permissions.length > 3" variant="secondary">
                +{{ row.original.permissions.length - 3 }}
              </FaTag>
            </div>
          </template>
          <template #cell-status="{ row }">
            <FaTag :variant="statusVariant(row.original.status)">
              {{ statusText(row.original.status) }}
            </FaTag>
          </template>
          <template #cell-operation="{ row }">
            <FaButton v-auth="'system:security:api-key:revoke'" variant="destructive" size="sm" :disabled="row.original.status !== 'ACTIVE'" @click="confirmRevoke(row.original)">
              吊销
            </FaButton>
          </template>
        </FaTable>
        <FaPagination v-model:page="pagination.page" v-model:size="pagination.size" :total="pagination.total" class="mt-3" @page-change="onPageChange" @size-change="onSizeChange" />
      </section>

      <section v-if="activeTab === 'oauth'" class="panel">
        <FaAlert v-if="!oauthPaneEnabled()" icon="i-ri:information-line" title="OAuth 能力未启用" :description="oauthDisabledMessage()" />
        <div class="key-toolbar">
          <div class="section-title">
            <FaIcon name="i-ri:login-circle-line" />
            OAuth
          </div>
          <div class="key-actions">
            <FaButton v-if="policy.oauthServerEnabled" :variant="oauthPane === 'clients' ? 'default' : 'outline'" @click="oauthPane = 'clients'">服务端客户端</FaButton>
            <FaButton v-if="policy.oauthClientEnabled" :variant="oauthPane === 'providers' ? 'default' : 'outline'" @click="oauthPane = 'providers'">外部提供商</FaButton>
            <FaButton v-if="oauthPane === 'clients'" v-auth="'system:security:oauth:edit'" :disabled="!policy.oauthServerEnabled" @click="guardedOpenOAuthClient()">
              <FaIcon name="i-ri:add-line" />
              新增客户端
            </FaButton>
            <FaButton v-else v-auth="'system:security:oauth:edit'" :disabled="!policy.oauthClientEnabled" @click="guardedOpenOAuthProvider()">
              <FaIcon name="i-ri:add-line" />
              新增提供商
            </FaButton>
          </div>
        </div>

        <FaTable v-if="oauthPane === 'clients'" row-key="id" table-root-class="rounded-lg overflow-hidden" table-class="min-w-[1080px]" border stripe column-visibility :columns="oauthClientColumns" :data="oauthClients">
          <template #cell-grantTypes="{ row }">
            <div class="tag-row">
              <FaTag v-for="item in row.original.grantTypes" :key="item" variant="secondary">{{ item }}</FaTag>
            </div>
          </template>
          <template #cell-redirectUris="{ row }">
            <span class="line-clamp-1">{{ row.original.redirectUris?.join(', ') || '-' }}</span>
          </template>
          <template #cell-status="{ row }">
            <FaTag :variant="statusVariant(row.original.status)">{{ statusText(row.original.status) }}</FaTag>
          </template>
          <template #cell-operation="{ row }">
            <div class="table-actions">
              <FaButton v-auth="'system:security:oauth:edit'" size="sm" variant="ghost" :disabled="!policy.oauthServerEnabled" @click="guardedOpenOAuthClient(row.original)">编辑</FaButton>
              <FaButton v-if="row.original.status === 'ACTIVE'" v-auth="'system:security:oauth:edit'" size="sm" variant="ghost" @click="confirmDisableOAuthClient(row.original)">停用</FaButton>
              <FaButton v-else v-auth="'system:security:oauth:edit'" size="sm" variant="ghost" :disabled="!policy.oauthServerEnabled" @click="confirmEnableOAuthClient(row.original)">启用</FaButton>
            </div>
          </template>
        </FaTable>

        <FaTable v-else row-key="id" table-root-class="rounded-lg overflow-hidden" table-class="min-w-[1080px]" border stripe column-visibility :columns="oauthProviderColumns" :data="oauthProviders">
          <template #cell-status="{ row }">
            <FaTag :variant="statusVariant(row.original.status)">{{ statusText(row.original.status) }}</FaTag>
          </template>
          <template #cell-operation="{ row }">
            <div class="table-actions">
              <FaButton v-auth="'system:security:oauth:edit'" size="sm" variant="ghost" :disabled="!policy.oauthClientEnabled" @click="guardedOpenOAuthProvider(row.original)">编辑</FaButton>
              <FaButton v-if="row.original.status === 'ACTIVE'" v-auth="'system:security:oauth:edit'" size="sm" variant="ghost" @click="confirmDisableOAuthProvider(row.original)">停用</FaButton>
              <FaButton v-else v-auth="'system:security:oauth:edit'" size="sm" variant="ghost" :disabled="!policy.oauthClientEnabled" @click="confirmEnableOAuthProvider(row.original)">启用</FaButton>
            </div>
          </template>
        </FaTable>
      </section>

      <section v-if="activeTab === 'passkey'" class="panel">
        <FaAlert v-if="!policy.passkeyEnabled" icon="i-ri:information-line" title="Passkey 未启用" :description="passkeyDisabledMessage()" />
        <div class="key-toolbar">
          <div class="section-title">
            <FaIcon name="i-ri:fingerprint-line" />
            Passkey 凭据
          </div>
        </div>
        <FaTable row-key="id" table-root-class="rounded-lg overflow-hidden" table-class="min-w-[1060px]" border stripe column-visibility :columns="passkeyColumns" :data="passkeyRows">
          <template #toolbar>
            <FaSearchBar class="w-full">
              <div class="grid grid-cols-1 gap-3 md:grid-cols-[minmax(260px,360px)_auto] md:items-center">
                <FaInput v-model="passkeySearch.userId" placeholder="用户 ID（留空全部）" :disabled="!policy.passkeyEnabled" @keydown.enter="guardedLoadPasskeys" />
                <div class="flex gap-2 md:justify-end">
                  <FaButton v-auth="'system:security:passkey:view'" :loading="loading" :disabled="!policy.passkeyEnabled" @click="guardedLoadPasskeys">
                    <FaIcon name="i-ri:search-line" />
                    筛选
                  </FaButton>
                </div>
              </div>
            </FaSearchBar>
          </template>
          <template #cell-status="{ row }">
            <FaTag :variant="statusVariant(row.original.status)">{{ statusText(row.original.status) }}</FaTag>
          </template>
          <template #cell-operation="{ row }">
            <FaButton v-auth="'system:security:passkey:revoke'" variant="destructive" size="sm" :disabled="row.original.status !== 'ACTIVE'" @click="confirmRevokePasskey(row.original)">
              吊销
            </FaButton>
          </template>
        </FaTable>
      </section>
    </FaPageMain>

    <FaModal v-model="createVisible" title="创建 API Key" show-cancel-button class="sm:max-w-3xl" :confirm-loading="creating" @confirm="createApiKey">
      <a-form :model="apiKeyForm" layout="vertical">
        <a-form-item label="名称" required>
          <FaInput v-model="apiKeyForm.name" placeholder="例如：外部系统同步" />
        </a-form-item>
        <a-form-item label="过期时间">
          <FaInput v-model="apiKeyForm.expireTime" type="datetime-local" />
        </a-form-item>
        <a-form-item label="权限范围" required>
          <div class="permission-select-panel">
            <FaSelect
              v-model="apiKeyForm.permissions"
              multiple
              :options="permissionSelectOptions"
              :disabled="!permissionSelectOptions.length"
              class="w-full"
              placeholder="请选择 API Key 可访问权限"
            />
            <div class="permission-select-actions">
              <span>已选择 {{ apiKeyForm.permissions.length }} 项</span>
              <div>
                <FaButton variant="ghost" size="sm" :disabled="!selectablePermissionCodes.length" @click="apiKeyForm.permissions = [...selectablePermissionCodes]">
                  全选可用
                </FaButton>
                <FaButton variant="ghost" size="sm" :disabled="!apiKeyForm.permissions.length" @click="apiKeyForm.permissions = []">
                  清空
                </FaButton>
              </div>
            </div>
          </div>
        </a-form-item>
      </a-form>
    </FaModal>

    <FaModal v-model="oauthClientVisible" :title="editingOAuthClient ? '编辑 OAuth 客户端' : '新增 OAuth 客户端'" show-cancel-button class="sm:max-w-3xl" @confirm="saveOAuthClient">
      <a-form :model="oauthClientForm" layout="vertical">
        <div class="form-grid">
          <a-form-item label="Client ID" required>
            <FaInput v-model="oauthClientForm.clientId" :disabled="!!editingOAuthClient" />
          </a-form-item>
          <a-form-item label="客户端名称" required>
            <FaInput v-model="oauthClientForm.clientName" />
          </a-form-item>
          <a-form-item label="认证方式">
            <FaSelect v-model="oauthClientForm.authMethod" :options="authMethodOptions" />
          </a-form-item>
          <a-form-item label="状态">
            <FaSelect v-model="oauthClientForm.status" :options="statusOptions" />
          </a-form-item>
          <a-form-item label="访问令牌有效期">
            <FaInput v-model.number="oauthClientForm.accessTokenTtlSeconds" type="number" min="1" />
          </a-form-item>
          <a-form-item label="刷新令牌有效期">
            <FaInput v-model.number="oauthClientForm.refreshTokenTtlSeconds" type="number" min="1" />
          </a-form-item>
        </div>
        <a-form-item label="授权模式">
          <a-checkbox-group v-model="oauthClientForm.grantTypes">
            <a-space wrap>
              <a-checkbox value="AUTHORIZATION_CODE">authorization_code</a-checkbox>
              <a-checkbox value="REFRESH_TOKEN">refresh_token</a-checkbox>
              <a-checkbox value="CLIENT_CREDENTIALS">client_credentials</a-checkbox>
            </a-space>
          </a-checkbox-group>
        </a-form-item>
        <a-form-item label="回调地址（一行一个）">
          <FaTextarea :model-value="splitTextarea(oauthClientForm.redirectUris || [])" rows="4" @update:model-value="value => updateList(oauthClientForm.redirectUris, value)" />
        </a-form-item>
        <a-form-item label="授权范围（一行或逗号分隔）">
          <FaTextarea :model-value="splitTextarea(oauthClientForm.scopes || [])" rows="3" @update:model-value="value => updateList(oauthClientForm.scopes, value)" />
        </a-form-item>
      </a-form>
    </FaModal>

    <FaModal v-model="oauthProviderVisible" :title="editingOAuthProvider ? '编辑 OAuth 提供商' : '新增 OAuth 提供商'" show-cancel-button class="sm:max-w-3xl" @confirm="saveOAuthProvider">
      <a-form :model="oauthProviderForm" layout="vertical">
        <div class="form-grid">
          <a-form-item label="编码" required>
            <FaInput v-model="oauthProviderForm.code" :disabled="!!editingOAuthProvider" />
          </a-form-item>
          <a-form-item label="名称" required>
            <FaInput v-model="oauthProviderForm.name" />
          </a-form-item>
          <a-form-item label="Client ID">
            <FaInput v-model="oauthProviderForm.clientId" />
          </a-form-item>
          <a-form-item label="Client Secret">
            <FaInput v-model="oauthProviderForm.clientSecret" type="password" :placeholder="editingOAuthProvider ? '留空则不修改' : ''" />
          </a-form-item>
          <a-form-item label="认证方式">
            <FaSelect v-model="oauthProviderForm.authMethod" :options="authMethodOptions" />
          </a-form-item>
          <a-form-item label="状态">
            <FaSelect v-model="oauthProviderForm.status" :options="statusOptions" />
          </a-form-item>
        </div>
        <a-form-item label="Issuer">
          <FaInput v-model="oauthProviderForm.issuerUri" />
        </a-form-item>
        <a-form-item label="授权地址">
          <FaInput v-model="oauthProviderForm.authorizationUri" />
        </a-form-item>
        <a-form-item label="Token 地址">
          <FaInput v-model="oauthProviderForm.tokenUri" />
        </a-form-item>
        <a-form-item label="用户信息地址">
          <FaInput v-model="oauthProviderForm.userInfoUri" />
        </a-form-item>
        <a-form-item label="回调地址">
          <FaInput v-model="oauthProviderForm.redirectUri" />
        </a-form-item>
        <a-form-item label="授权范围（一行或逗号分隔）">
          <FaTextarea :model-value="splitTextarea(oauthProviderForm.scopes || [])" rows="3" @update:model-value="value => updateList(oauthProviderForm.scopes, value)" />
        </a-form-item>
      </a-form>
    </FaModal>

    <FaModal v-model="secretVisible" title="一次性密钥" :show-cancel-button="false">
      <div class="secret-box">
        <code>{{ oneTimeSecret }}</code>
        <FaButton variant="outline" @click="copySecret">
          <FaIcon name="i-ri:file-copy-line" />
          复制
        </FaButton>
      </div>
    </FaModal>
  </div>
</template>

<style scoped>
.security-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 14px;
}

.security-tabs button {
  display: inline-flex;
  gap: 6px;
  align-items: center;
  height: 36px;
  padding: 0 12px;
  border: 1px solid var(--color-border-2);
  border-radius: 6px;
  background: var(--color-bg-2);
  color: var(--color-text-2);
}

.security-tabs button.active,
.security-tabs button:hover {
  border-color: rgb(var(--primary-6));
  color: rgb(var(--primary-6));
}

.panel {
  display: grid;
  gap: 16px;
}

.section-title {
  display: flex;
  gap: 8px;
  align-items: center;
  font-weight: 600;
}

.policy-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
  gap: 12px;
}

.policy-card {
  display: grid;
  grid-template-columns: 38px minmax(0, 1fr) auto;
  gap: 12px;
  align-items: center;
  padding: 14px;
  border: 1px solid var(--color-border-2);
  border-radius: 6px;
}

.policy-icon {
  display: grid;
  place-items: center;
  width: 38px;
  height: 38px;
  border-radius: 6px;
  background: var(--color-fill-2);
  color: var(--ui-primary);
}

.policy-body {
  display: grid;
  gap: 3px;
  min-width: 0;
}

.policy-body span {
  color: var(--color-text-3);
  font-size: 12px;
}

.token-grid,
.form-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 12px;
}

.key-toolbar {
  display: flex;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
}

.key-actions,
.table-actions,
.tag-row {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
}

.permission-panel {
  display: grid;
  gap: 12px;
  max-height: 340px;
  overflow: auto;
}

.permission-group {
  padding: 12px;
  border: 1px solid var(--color-border-2);
  border-radius: 6px;
}

.permission-title {
  margin-bottom: 8px;
  font-weight: 600;
}

.permission-select-panel {
  display: grid;
  gap: 10px;
}

.permission-select-actions {
  display: flex;
  gap: 10px;
  align-items: center;
  justify-content: space-between;
  color: var(--color-text-3);
  font-size: 13px;
}

.permission-select-actions > div {
  display: flex;
  gap: 6px;
  align-items: center;
}

.secret-box {
  display: grid;
  gap: 12px;
}

.secret-box code {
  display: block;
  padding: 12px;
  overflow: auto;
  border: 1px solid var(--color-border-2);
  border-radius: 6px;
  background: var(--color-fill-1);
}
</style>
