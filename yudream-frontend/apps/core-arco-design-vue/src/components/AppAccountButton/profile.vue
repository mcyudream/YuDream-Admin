<script setup lang="ts">
import type { ApiKeyCredential, ApiKeyCreatePayload, CredentialStatus, PasskeyCredential, PasskeyStatus, UserProfilePayload } from '@/api/modules/profile'
import type { PermissionItem } from '@/api/modules/system-role'
import EditPassword from '@/components/AppAccountForm/edit-password.vue'
import apiProfile from '@/api/modules/profile'
import apiRole from '@/api/modules/system-role'
import { toBackendAssetUrl } from '@/utils/backend-url'
import { createPasskeyRegistrationResponse } from '@/utils/webauthn'

type ProfileTab = 'profile' | 'security' | 'apiKey'

const modal = useFaModal()
const toast = useFaToast()
const appAccountStore = useAppAccountStore()

const active = ref<ProfileTab>('profile')
const loading = ref(false)
const saving = ref(false)
const loadingPasskeys = ref(false)
const bindingPasskey = ref(false)
const loadingApiKeys = ref(false)
const loadingPermissions = ref(false)
const creatingApiKey = ref(false)
const avatarInput = ref<HTMLInputElement>()
const passkeys = ref<PasskeyCredential[]>([])
const apiKeys = ref<ApiKeyCredential[]>([])
const availablePermissions = ref<PermissionItem[]>([])
const apiKeySearch = reactive({ keyword: '', page: 1, size: 20, total: 0 })
const createdApiKeyPlaintext = ref('')

const form = reactive<UserProfilePayload>({
  nickname: '',
  email: '',
  phone: '',
  qq: '',
})
const profile = ref({
  username: '',
  avatar: '',
  createTime: '',
})
const apiKeyForm = reactive<ApiKeyCreatePayload>({
  name: '',
  permissions: [],
  expireTime: '',
})

const tabs: { key: ProfileTab, title: string, description: string, icon: string }[] = [
  { key: 'profile', title: '个人资料', description: '头像、昵称和联系方式', icon: 'i-ri:user-3-line' },
  { key: 'security', title: '登录安全', description: '密码和 Passkey 凭据', icon: 'i-ri:shield-keyhole-line' },
  { key: 'apiKey', title: 'API Key', description: '用户级访问密钥', icon: 'i-ri:key-2-line' },
]

const permissionGroups = computed(() => {
  const ownedPermissions = appAccountStore.permissions
  const isSuperAdmin = ownedPermissions.includes('*')
  const groups: Record<string, PermissionItem[]> = {}
  availablePermissions.value
    .filter(item => item.status === 'ACTIVE')
    .filter(item => isSuperAdmin || ownedPermissions.includes(item.code))
    .forEach((item) => {
      const key = item.module || '其他'
      groups[key] ||= []
      groups[key].push(item)
    })
  return groups
})

const selectablePermissionCodes = computed(() => Object.values(permissionGroups.value).flat().map(item => item.code))

const permissionSelectOptions = computed(() => Object.entries(permissionGroups.value).map(([module, items]) => ({
  label: module,
  options: items.map(item => ({
    label: `${item.name} (${item.code})`,
    value: item.code,
  })),
})))

const permissionNameMap = computed(() => new Map(availablePermissions.value.map(item => [item.code, item.name])))

onMounted(async () => {
  await loadProfile()
  await loadPasskeys()
})

watch(active, async (tab) => {
  if (tab === 'apiKey') {
    await Promise.allSettled([
      apiKeys.value.length ? Promise.resolve() : loadApiKeys(),
      availablePermissions.value.length ? Promise.resolve() : loadPermissions(),
    ])
  }
  if (tab === 'security' && !passkeys.value.length) {
    await loadPasskeys()
  }
})

async function loadProfile() {
  loading.value = true
  try {
    const res = await apiProfile.get()
    const data = res.data
    profile.value = {
      username: data.username,
      avatar: toBackendAssetUrl(data.avatar),
      createTime: data.createTime || '',
    }
    Object.assign(form, {
      nickname: data.nickname || '',
      email: data.email || '',
      phone: data.phone || '',
      qq: data.qq || '',
    })
    appAccountStore.setAvatar(data.avatar)
  }
  finally {
    loading.value = false
  }
}

async function loadPasskeys() {
  loadingPasskeys.value = true
  try {
    const res = await apiProfile.passkeys()
    passkeys.value = res.data
  }
  finally {
    loadingPasskeys.value = false
  }
}

async function loadApiKeys() {
  loadingApiKeys.value = true
  try {
    const res = await apiProfile.apiKeys({
      page: apiKeySearch.page,
      size: apiKeySearch.size,
      keyword: apiKeySearch.keyword || undefined,
    })
    apiKeys.value = res.data.records
    apiKeySearch.total = res.data.total
  }
  finally {
    loadingApiKeys.value = false
  }
}

async function loadPermissions() {
  loadingPermissions.value = true
  try {
    if (!appAccountStore.permissions.length) {
      await appAccountStore.getPermissions()
    }
    const res = await apiRole.permissions()
    availablePermissions.value = res.data
  }
  finally {
    loadingPermissions.value = false
  }
}

async function saveProfile() {
  saving.value = true
  try {
    const res = await apiProfile.update(form)
    Object.assign(form, {
      nickname: res.data.nickname || '',
      email: res.data.email || '',
      phone: res.data.phone || '',
      qq: res.data.qq || '',
    })
    toast.success('资料已保存')
  }
  finally {
    saving.value = false
  }
}

function pickAvatar() {
  avatarInput.value?.click()
}

async function uploadAvatar(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) {
    return
  }
  const data = new FormData()
  data.append('file', file)
  const res = await apiProfile.uploadAvatar(data)
  profile.value.avatar = toBackendAssetUrl(res.data.avatar)
  appAccountStore.setAvatar(res.data.avatar)
  toast.success('头像已更新')
}

async function bindPasskey() {
  bindingPasskey.value = true
  try {
    const deviceNameInput = window.prompt('请输入设备名称', `${profile.value.username} 的 Passkey`)
    if (deviceNameInput === null) {
      return
    }
    const deviceName = deviceNameInput || undefined
    const options = await apiProfile.startPasskeyRegistration()
    const responseJson = await createPasskeyRegistrationResponse(options.data.publicKeyJson)
    await apiProfile.finishPasskeyRegistration({
      deviceName,
      requestJson: options.data.requestJson,
      responseJson,
    })
    toast.success('Passkey 已绑定')
    await loadPasskeys()
  }
  finally {
    bindingPasskey.value = false
  }
}

function confirmRevokePasskey(row: PasskeyCredential) {
  modal.confirm({
    title: '吊销 Passkey',
    content: `确认吊销「${row.deviceName || shortCredential(row.credentialId)}」吗？吊销后该设备将不能继续用于登录。`,
    onConfirm: async () => {
      await apiProfile.revokePasskey(row.id)
      toast.success('Passkey 已吊销')
      await loadPasskeys()
    },
  })
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
  creatingApiKey.value = true
  try {
    const res = await apiProfile.createApiKey({
      name: apiKeyForm.name.trim(),
      permissions: selectedPermissions,
      expireTime: apiKeyForm.expireTime || undefined,
    })
    createdApiKeyPlaintext.value = res.data.plaintext
    Object.assign(apiKeyForm, { name: '', permissions: [], expireTime: '' })
    toast.success('API Key 已创建')
    await loadApiKeys()
  }
  finally {
    creatingApiKey.value = false
  }
}

function selectedApiKeyPermissions() {
  const selectableCodes = new Set(selectablePermissionCodes.value)
  return apiKeyForm.permissions.filter(permission => selectableCodes.has(permission))
}

function permissionText(code: string) {
  return permissionNameMap.value.get(code) || code
}

function confirmRevokeApiKey(row: ApiKeyCredential) {
  modal.confirm({
    title: '吊销 API Key',
    content: `确认吊销「${row.name}」吗？已吊销的密钥不能恢复。`,
    onConfirm: async () => {
      await apiProfile.revokeApiKey(row.id)
      toast.success('API Key 已吊销')
      await loadApiKeys()
    },
  })
}

async function copyPlaintext() {
  if (!createdApiKeyPlaintext.value) {
    return
  }
  await navigator.clipboard.writeText(createdApiKeyPlaintext.value)
  toast.success('已复制 API Key')
}

function selectAllApiKeyPermissions() {
  apiKeyForm.permissions = [...selectablePermissionCodes.value]
}

function clearApiKeyPermissions() {
  apiKeyForm.permissions = []
}

function shortCredential(value: string) {
  return value.length > 18 ? `${value.slice(0, 10)}...${value.slice(-6)}` : value
}

function statusText(status: CredentialStatus | PasskeyStatus) {
  const map: Record<CredentialStatus, string> = {
    ACTIVE: '可用',
    REVOKED: '已吊销',
    EXPIRED: '已过期',
  }
  return map[status]
}

function statusVariant(status: CredentialStatus | PasskeyStatus) {
  return status === 'ACTIVE' ? 'default' : 'secondary'
}

function dateText(value?: string) {
  return value ? value.replace('T', ' ').slice(0, 16) : '-'
}
</script>

<template>
  <div class="profile-shell">
    <aside class="profile-aside">
      <div class="profile-card">
        <div class="avatar-wrap">
          <FaAvatar :src="profile.avatar" :fallback="profile.username.slice(0, 2)" class="size-18" />
          <button type="button" class="avatar-edit" @click="pickAvatar">
            <FaIcon name="i-ri:camera-line" />
          </button>
          <input ref="avatarInput" type="file" accept="image/*" hidden @change="uploadAvatar">
        </div>
        <strong>{{ profile.username || '当前用户' }}</strong>
        <span>{{ form.email || '未设置邮箱' }}</span>
      </div>

      <nav class="profile-nav">
        <button v-for="tab in tabs" :key="tab.key" type="button" :class="{ active: active === tab.key }" @click="active = tab.key">
          <FaIcon :name="tab.icon" />
          <span>
            <strong>{{ tab.title }}</strong>
            <small>{{ tab.description }}</small>
          </span>
        </button>
      </nav>
    </aside>

    <main class="profile-main">
      <section v-if="active === 'profile'" class="profile-section" :class="{ loading }" :aria-busy="loading">
        <div class="section-head">
          <div>
            <h2>个人资料</h2>
            <p>维护你的基础资料、头像和联系方式。</p>
          </div>
          <FaButton :loading="saving" @click="saveProfile">
            <FaIcon name="i-ri:save-3-line" />
            保存资料
          </FaButton>
        </div>

        <a-form :model="form" layout="vertical" class="form-grid">
          <a-form-item label="昵称">
            <FaInput v-model="form.nickname" placeholder="请输入昵称" />
          </a-form-item>
          <a-form-item label="邮箱">
            <FaInput v-model="form.email" placeholder="请输入邮箱" />
          </a-form-item>
          <a-form-item label="手机号">
            <FaInput v-model="form.phone" placeholder="请输入手机号" />
          </a-form-item>
          <a-form-item label="QQ">
            <FaInput v-model="form.qq" placeholder="请输入 QQ" />
          </a-form-item>
        </a-form>
      </section>

      <section v-else-if="active === 'security'" class="profile-section">
        <div class="section-head">
          <div>
            <h2>登录安全</h2>
            <p>管理密码和无密码登录凭据。</p>
          </div>
        </div>

        <div class="security-layout">
          <EditPassword />
          <section class="inner-panel">
            <div class="panel-head">
              <div>
                <h3>Passkey 凭据</h3>
                <p>管理已经绑定到当前账号的设备凭据。</p>
              </div>
              <div class="panel-actions">
                <FaButton variant="outline" :loading="loadingPasskeys" @click="loadPasskeys">
                  <FaIcon name="i-ri:refresh-line" />
                  刷新
                </FaButton>
                <FaButton :loading="bindingPasskey" @click="bindPasskey">
                  <FaIcon name="i-ri:fingerprint-line" />
                  绑定
                </FaButton>
              </div>
            </div>

            <div class="credential-list" :class="{ loading: loadingPasskeys }" :aria-busy="loadingPasskeys">
              <div v-if="!passkeys.length" class="empty-state">
                暂无 Passkey 凭据
              </div>
              <article v-for="item in passkeys" :key="item.id" class="credential-item">
                <div class="credential-icon">
                  <FaIcon name="i-ri:fingerprint-line" />
                </div>
                <div class="credential-info">
                  <strong>{{ item.deviceName || '未命名设备' }}</strong>
                  <span>{{ shortCredential(item.credentialId) }}</span>
                  <small>创建：{{ dateText(item.createTime) }} · 最后使用：{{ dateText(item.lastUsedTime) }}</small>
                </div>
                <FaTag :variant="statusVariant(item.status)">
                  {{ statusText(item.status) }}
                </FaTag>
                <FaButton variant="destructive" size="sm" :disabled="item.status !== 'ACTIVE'" @click="confirmRevokePasskey(item)">
                  吊销
                </FaButton>
              </article>
            </div>
          </section>
        </div>
      </section>

      <section v-else class="profile-section">
        <div class="section-head">
          <div>
            <h2>API Key</h2>
            <p>创建只属于当前用户的访问密钥，权限不能超过你的账号权限。</p>
          </div>
          <FaButton variant="outline" :loading="loadingApiKeys" @click="loadApiKeys">
            <FaIcon name="i-ri:refresh-line" />
            刷新
          </FaButton>
        </div>

        <div v-if="createdApiKeyPlaintext" class="secret-once">
          <div>
            <strong>请立即保存密钥明文</strong>
            <p>这段明文只显示一次，关闭后无法再次查看。</p>
            <code>{{ createdApiKeyPlaintext }}</code>
          </div>
          <FaButton @click="copyPlaintext">
            <FaIcon name="i-ri:file-copy-line" />
            复制
          </FaButton>
        </div>

        <section class="inner-panel">
          <div class="panel-head">
            <div>
              <h3>创建密钥</h3>
              <p>从你当前拥有的权限中选择 API Key 可访问的范围。</p>
            </div>
          </div>
          <a-form :model="apiKeyForm" layout="vertical" class="api-key-form">
            <a-form-item label="名称">
              <FaInput v-model="apiKeyForm.name" placeholder="例如：文档集成、自动化脚本" />
            </a-form-item>
            <a-form-item label="过期时间">
              <FaInput v-model="apiKeyForm.expireTime" type="datetime-local" />
            </a-form-item>
            <a-form-item label="权限范围" class="md:col-span-2" required>
              <div class="permission-picker" :class="{ loading: loadingPermissions }" :aria-busy="loadingPermissions">
                <div class="permission-toolbar">
                  <span>已选择 {{ apiKeyForm.permissions.length }} 项</span>
                  <div>
                    <FaButton variant="ghost" size="sm" :disabled="!selectablePermissionCodes.length" @click="selectAllApiKeyPermissions">
                      全选可用
                    </FaButton>
                    <FaButton variant="ghost" size="sm" :disabled="!apiKeyForm.permissions.length" @click="clearApiKeyPermissions">
                      清空
                    </FaButton>
                  </div>
                </div>
                <FaSelect
                  v-if="permissionSelectOptions.length"
                  v-model="apiKeyForm.permissions"
                  multiple
                  :options="permissionSelectOptions"
                  class="w-full"
                  placeholder="请选择 API Key 可访问权限"
                />
                <div v-else class="empty-state">
                  暂无可选择权限
                </div>
              </div>
            </a-form-item>
            <div class="md:col-span-2 flex justify-end">
              <FaButton :loading="creatingApiKey" @click="createApiKey">
                <FaIcon name="i-ri:key-2-line" />
                创建 API Key
              </FaButton>
            </div>
          </a-form>
        </section>

        <section class="inner-panel">
          <div class="panel-head">
            <div>
              <h3>我的密钥</h3>
              <p>仅展示由当前用户创建的 API Key。</p>
            </div>
            <FaInput v-model="apiKeySearch.keyword" clearable placeholder="搜索名称 / 前缀" class="w-56" @keydown.enter="loadApiKeys" @clear="loadApiKeys" />
          </div>
          <div class="credential-list" :class="{ loading: loadingApiKeys }" :aria-busy="loadingApiKeys">
            <div v-if="!apiKeys.length" class="empty-state">
              暂无 API Key
            </div>
            <article v-for="item in apiKeys" :key="item.id" class="credential-item">
              <div class="credential-icon">
                <FaIcon name="i-ri:key-2-line" />
              </div>
              <div class="credential-info">
                <strong>{{ item.name }}</strong>
                <span>{{ item.maskedValue || item.keyPrefix }}</span>
                <small>过期：{{ dateText(item.expireTime) }} · 最后使用：{{ dateText(item.lastUsedTime) }}</small>
                <div class="permission-line">
                  <FaTag v-for="permission in item.permissions.slice(0, 4)" :key="permission" variant="secondary">
                    {{ permissionText(permission) }}
                  </FaTag>
                  <span v-if="item.permissions.length > 4">+{{ item.permissions.length - 4 }}</span>
                </div>
              </div>
              <FaTag :variant="statusVariant(item.status)">
                {{ statusText(item.status) }}
              </FaTag>
              <FaButton variant="destructive" size="sm" :disabled="item.status !== 'ACTIVE'" @click="confirmRevokeApiKey(item)">
                吊销
              </FaButton>
            </article>
          </div>
        </section>
      </section>
    </main>
  </div>
</template>

<style scoped>
.profile-shell {
  display: grid;
  grid-template-columns: 240px minmax(0, 1fr);
  width: 100%;
  height: min(760px, calc(100vh - 64px));
  overflow: hidden;
  background: var(--color-bg-1);
}

.profile-aside {
  display: grid;
  grid-template-rows: auto 1fr;
  gap: 14px;
  padding: 18px;
  border-right: 1px solid var(--color-border-2);
  background: var(--color-fill-1);
}

.profile-card {
  display: grid;
  gap: 8px;
  justify-items: center;
  padding: 18px 12px;
  border: 1px solid var(--color-border-2);
  border-radius: 8px;
  background: var(--color-bg-2);
  text-align: center;
}

.profile-card span {
  max-width: 100%;
  overflow: hidden;
  color: var(--color-text-3);
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.avatar-wrap {
  position: relative;
}

.avatar-edit {
  position: absolute;
  right: -4px;
  bottom: -4px;
  display: grid;
  width: 28px;
  height: 28px;
  place-items: center;
  border: 1px solid var(--color-border-2);
  border-radius: 999px;
  background: var(--color-bg-2);
  color: rgb(var(--primary-6));
}

.profile-nav {
  display: grid;
  align-content: start;
  gap: 8px;
}

.profile-nav button {
  display: flex;
  gap: 10px;
  align-items: center;
  width: 100%;
  padding: 12px;
  border: 1px solid transparent;
  border-radius: 8px;
  color: var(--color-text-2);
  text-align: left;
}

.profile-nav button.active,
.profile-nav button:hover {
  border-color: var(--color-border-2);
  background: var(--color-bg-2);
  color: rgb(var(--primary-6));
}

.profile-nav button > span {
  display: grid;
  gap: 3px;
  min-width: 0;
}

.profile-nav small {
  color: var(--color-text-3);
}

.profile-main {
  min-width: 0;
  overflow: auto;
  padding: 22px;
}

.profile-section,
.security-layout {
  display: grid;
  gap: 16px;
}

.loading {
  opacity: 0.68;
  pointer-events: none;
}

.section-head,
.panel-head,
.credential-item {
  display: flex;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
}

.section-head h2,
.section-head p,
.panel-head h3,
.panel-head p {
  margin: 0;
}

.section-head h2 {
  font-size: 22px;
  font-weight: 800;
}

.section-head p,
.panel-head p {
  color: var(--color-text-3);
}

.form-grid,
.api-key-form {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 14px;
}

.inner-panel,
.secret-once {
  display: grid;
  gap: 12px;
  padding: 16px;
  border: 1px solid var(--color-border-2);
  border-radius: 8px;
  background: var(--color-bg-2);
}

.panel-actions {
  display: flex;
  gap: 8px;
}

.credential-list {
  display: grid;
  gap: 10px;
  min-height: 92px;
}

.credential-item {
  padding: 12px;
  border: 1px solid var(--color-border-2);
  border-radius: 8px;
  background: var(--color-bg-1);
}

.credential-icon {
  display: grid;
  width: 38px;
  height: 38px;
  flex: 0 0 auto;
  place-items: center;
  border-radius: 8px;
  background: var(--color-fill-2);
  color: rgb(var(--primary-6));
}

.credential-info {
  display: grid;
  gap: 4px;
  min-width: 0;
  flex: 1 1 auto;
}

.credential-info span,
.credential-info small {
  overflow: hidden;
  color: var(--color-text-3);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.permission-line {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  align-items: center;
}

.permission-picker {
  display: grid;
  gap: 10px;
  max-height: 320px;
  overflow: auto;
  padding: 10px;
  border: 1px solid var(--color-border-2);
  border-radius: 8px;
  background: var(--color-bg-1);
}

.permission-toolbar {
  position: sticky;
  top: 0;
  z-index: 1;
  display: flex;
  gap: 10px;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 8px;
  border-bottom: 1px solid var(--color-border-2);
  background: var(--color-bg-1);
  color: var(--color-text-3);
  font-size: 13px;
}

.permission-toolbar > div {
  display: flex;
  gap: 6px;
  align-items: center;
}

.permission-group {
  display: grid;
  gap: 8px;
  padding: 12px;
  border: 1px solid var(--color-border-2);
  border-radius: 8px;
  background: var(--color-bg-2);
}

.permission-title {
  color: var(--color-text-1);
  font-weight: 700;
}

.permission-label {
  display: inline-grid;
  gap: 2px;
  min-width: 0;
  vertical-align: middle;
}

.permission-label strong {
  color: var(--color-text-1);
  font-weight: 500;
}

.permission-label small {
  max-width: min(360px, 60vw);
  overflow: hidden;
  color: var(--color-text-3);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.empty-state {
  display: grid;
  min-height: 90px;
  place-items: center;
  color: var(--color-text-3);
}

.secret-once {
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  border-color: rgb(var(--primary-6));
  background: rgba(var(--primary-6), 0.08);
}

.secret-once p {
  margin: 4px 0 10px;
  color: var(--color-text-2);
}

.secret-once code {
  display: block;
  overflow: auto;
  padding: 10px;
  border-radius: 6px;
  background: var(--color-bg-1);
  color: var(--color-text-1);
  font-size: 12px;
}

@media (max-width: 860px) {
  .profile-shell {
    grid-template-columns: 1fr;
    width: min(100vw - 24px, 680px);
  }

  .profile-aside {
    border-right: 0;
    border-bottom: 1px solid var(--color-border-2);
  }

  .profile-card {
    grid-template-columns: auto minmax(0, 1fr);
    justify-items: start;
    text-align: left;
  }

  .profile-nav {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .form-grid,
  .api-key-form {
    grid-template-columns: 1fr;
  }

  .section-head,
  .panel-head,
  .credential-item,
  .secret-once {
    align-items: stretch;
    flex-direction: column;
    grid-template-columns: 1fr;
  }

  .permission-toolbar {
    align-items: stretch;
    flex-direction: column;
  }

  .permission-toolbar > div {
    justify-content: flex-end;
  }
}
</style>
