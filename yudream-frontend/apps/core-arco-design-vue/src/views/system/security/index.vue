<script setup lang="ts">
import type { TableColumn } from '@fantastic-admin/components'
import type { ApiKeyCredential, ApiKeyCreatePayload, ApiSecurityPolicy, CredentialStatus } from '@/api/modules/system-security'
import type { PermissionItem } from '@/api/modules/system-role'
import apiSecurity from '@/api/modules/system-security'
import apiRole from '@/api/modules/system-role'
import { clearApiEncryptionCache } from '@/utils/api-encryption'

const modal = useFaModal()
const toast = useFaToast()

const loading = ref(false)
const savingPolicy = ref(false)
const creating = ref(false)
const rows = ref<ApiKeyCredential[]>([])
const permissions = ref<PermissionItem[]>([])
const pagination = reactive({ page: 1, size: 10, total: 0 })
const search = reactive({ keyword: '' })
const createVisible = ref(false)
const secretVisible = ref(false)
const oneTimeSecret = ref('')

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

const form = reactive<ApiKeyCreatePayload>({
  name: '',
  permissions: [],
  expireTime: undefined,
})

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
  permissions.value.forEach((item) => {
    const key = item.module || '其他'
    groups[key] ||= []
    groups[key].push(item)
  })
  return groups
})

const tableColumns = computed<TableColumn<ApiKeyCredential>[]>(() => [
  { accessorKey: 'name', header: '名称', width: 180, fixed: 'left' },
  { accessorKey: 'maskedValue', header: '密钥标识', width: 180 },
  { id: 'permissions', header: '权限', width: 220 },
  { id: 'status', header: '状态', width: 100, align: 'center' },
  { accessorKey: 'lastUsedTime', header: '最后使用', width: 180 },
  { accessorKey: 'expireTime', header: '过期时间', width: 180 },
  { id: 'operation', header: '操作', width: 120, align: 'center', fixed: 'right' },
])

onMounted(async () => {
  await Promise.all([loadPolicy(), loadApiKeys(), loadPermissions()])
})

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
  }
  finally {
    savingPolicy.value = false
  }
}

async function loadApiKeys() {
  loading.value = true
  try {
    const res = await apiSecurity.pageApiKeys({
      page: pagination.page,
      size: pagination.size,
      keyword: search.keyword || undefined,
    })
    rows.value = res.data.records
    pagination.total = res.data.total
  }
  finally {
    loading.value = false
  }
}

async function loadPermissions() {
  const res = await apiRole.permissions()
  permissions.value = res.data
}

function openCreate() {
  Object.assign(form, {
    name: '',
    permissions: [],
    expireTime: undefined,
  })
  createVisible.value = true
}

async function createApiKey() {
  creating.value = true
  try {
    const res = await apiSecurity.createApiKey({
      ...form,
      expireTime: normalizeDateTime(form.expireTime),
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

function statusText(status: CredentialStatus) {
  const map: Record<CredentialStatus, string> = {
    ACTIVE: '启用',
    REVOKED: '已吊销',
    EXPIRED: '已过期',
  }
  return map[status]
}

function statusVariant(status: CredentialStatus) {
  return status === 'ACTIVE' ? 'default' : 'secondary'
}

function normalizeDateTime(value?: string) {
  return value || undefined
}
</script>

<template>
  <div>
    <FaPageHeader title="安全中心" class="mb-0">
      <FaButton v-auth="'system:security:edit'" :loading="savingPolicy" @click="savePolicy">
        <FaIcon name="i-ri:save-3-line" />
        保存策略
      </FaButton>
    </FaPageHeader>

    <FaPageMain>
      <div class="security-layout">
        <section class="policy-panel">
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

        <section class="key-panel">
          <div class="key-toolbar">
            <div class="section-title">
              <FaIcon name="i-ri:key-line" />
              API Key
            </div>
            <div class="key-actions">
              <FaInput v-model="search.keyword" clearable placeholder="名称 / 前缀" class="w-56" @keydown.enter="loadApiKeys" @clear="loadApiKeys" />
              <FaButton variant="outline" @click="resetSearch">
                重置
              </FaButton>
              <FaButton @click="loadApiKeys">
                <FaIcon name="i-ri:search-line" />
                筛选
              </FaButton>
              <FaButton v-auth="'system:security:api-key:create'" @click="openCreate">
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
            :columns="tableColumns"
            :data="rows"
          >
            <template #cell-permissions="{ row }">
              <div class="permission-tags">
                <FaTag v-for="item in row.original.permissions.slice(0, 3)" :key="item" variant="secondary">
                  {{ item }}
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
              <FaButton
                v-auth="'system:security:api-key:revoke'"
                variant="destructive"
                size="sm"
                :disabled="row.original.status !== 'ACTIVE'"
                @click="confirmRevoke(row.original)"
              >
                吊销
              </FaButton>
            </template>
          </FaTable>

          <FaPagination
            v-model:page="pagination.page"
            v-model:size="pagination.size"
            :total="pagination.total"
            class="mt-3"
            @page-change="onPageChange"
            @size-change="onSizeChange"
          />
        </section>
      </div>
    </FaPageMain>

    <FaModal v-model="createVisible" title="创建 API Key" show-cancel-button class="sm:max-w-3xl" :confirm-loading="creating" @confirm="createApiKey">
      <a-form :model="form" layout="vertical">
        <a-form-item label="名称" required>
          <FaInput v-model="form.name" placeholder="例如：外部系统同步" />
        </a-form-item>
        <a-form-item label="过期时间">
          <FaInput v-model="form.expireTime" type="datetime-local" />
        </a-form-item>
        <a-form-item label="权限范围" required>
          <div class="permission-panel">
            <div v-for="(items, module) in permissionGroups" :key="module" class="permission-group">
              <div class="permission-title">
                {{ module }}
              </div>
              <a-checkbox-group v-model="form.permissions">
                <a-space wrap>
                  <a-checkbox v-for="item in items" :key="item.code" :value="item.code">
                    {{ item.name }}
                  </a-checkbox>
                </a-space>
              </a-checkbox-group>
            </div>
          </div>
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
.security-layout {
  display: grid;
  gap: 16px;
}

.policy-panel,
.key-panel {
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

.token-grid {
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

.key-actions {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
}

.permission-tags {
  display: flex;
  gap: 6px;
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
