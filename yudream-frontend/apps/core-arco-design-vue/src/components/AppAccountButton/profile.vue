<script setup lang="ts">
import type { PasskeyCredential, PasskeyStatus, UserProfilePayload } from '@/api/modules/profile'
import EditPassword from '@/components/AppAccountForm/edit-password.vue'
import apiProfile from '@/api/modules/profile'
import { toBackendAssetUrl } from '@/utils/backend-url'
import { createPasskeyRegistrationResponse } from '@/utils/webauthn'

const modal = useFaModal()
const toast = useFaToast()
const appAccountStore = useAppAccountStore()

const active = ref(0)
const loading = ref(false)
const saving = ref(false)
const loadingPasskeys = ref(false)
const bindingPasskey = ref(false)
const avatarInput = ref<HTMLInputElement>()
const passkeys = ref<PasskeyCredential[]>([])
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
const tabs = ref([
  {
    title: '基本设置',
    description: '账号资料与头像',
  },
  {
    title: '安全设置',
    description: '密码与 Passkey',
  },
])

onMounted(async () => {
  await loadProfile()
  await loadPasskeys()
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

function shortCredential(value: string) {
  return value.length > 18 ? `${value.slice(0, 10)}...${value.slice(-6)}` : value
}

function passkeyStatusText(status: PasskeyStatus) {
  const map: Record<PasskeyStatus, string> = {
    ACTIVE: '可用',
    REVOKED: '已吊销',
    EXPIRED: '已过期',
  }
  return map[status]
}
</script>

<template>
  <div class="min-h-full w-full">
    <div class="border-b border-e bg-background flex flex-row right-0 top-0 fixed z-1 overflow-auto md:(flex-col h-full w-40 inset-s-0 bottom-0)">
      <div v-for="(tab, index) in tabs" :key="index" class="px-4 py-3 flex-shrink-0 cursor-pointer transition-background-color space-y-2 hover-bg-accent/50" :class="{ 'bg-accent hover-bg-accent!': active === index }" @click="active = index">
        <div class="text-base text-accent-foreground leading-tight">
          {{ tab.title }}
        </div>
        <div class="text-xs text-accent-foreground/50">
          {{ tab.description }}
        </div>
      </div>
    </div>
    <div class="p-6 pt-20 min-h-full md:(ms-40 pt-6)">
      <div v-if="active === 0" v-loading="loading" class="mx-auto max-w-xl space-y-5">
        <div class="flex items-center gap-4">
          <FaAvatar :src="profile.avatar" :fallback="profile.username.slice(0, 2)" class="size-20" />
          <div class="space-y-2">
            <div class="font-semibold">
              {{ profile.username }}
            </div>
            <FaButton variant="outline" size="sm" @click="pickAvatar">
              <FaIcon name="i-ri:image-edit-line" />
              更换头像
            </FaButton>
            <input ref="avatarInput" type="file" accept="image/*" hidden @change="uploadAvatar">
          </div>
        </div>

        <a-form :model="form" layout="vertical">
          <a-form-item label="昵称">
            <FaInput v-model="form.nickname" class="w-full" />
          </a-form-item>
          <a-form-item label="邮箱">
            <FaInput v-model="form.email" class="w-full" />
          </a-form-item>
          <a-form-item label="手机号">
            <FaInput v-model="form.phone" class="w-full" />
          </a-form-item>
          <a-form-item label="QQ">
            <FaInput v-model="form.qq" class="w-full" />
          </a-form-item>
          <div class="flex justify-end">
            <FaButton :loading="saving" @click="saveProfile">
              <FaIcon name="i-ri:save-3-line" />
              保存资料
            </FaButton>
          </div>
        </a-form>
      </div>
      <div v-if="active === 1" class="mx-auto max-w-2xl space-y-5">
        <EditPassword />
        <section class="passkey-panel">
          <div class="passkey-title">
            <div>
              <div class="font-semibold">
                Passkey 凭据
              </div>
              <div class="text-sm text-muted-foreground">
                管理已经绑定到当前账号的设备凭据。
              </div>
            </div>
            <div class="flex gap-2">
              <FaButton variant="outline" :loading="loadingPasskeys" @click="loadPasskeys">
                <FaIcon name="i-ri:refresh-line" />
                刷新
              </FaButton>
              <FaButton :loading="bindingPasskey" @click="bindPasskey">
                <FaIcon name="i-ri:fingerprint-line" />
                绑定 Passkey
              </FaButton>
            </div>
          </div>
          <div v-loading="loadingPasskeys" class="passkey-list">
            <div v-if="!passkeys.length" class="passkey-empty">
              暂无 Passkey 凭据
            </div>
            <div v-for="item in passkeys" :key="item.id" class="passkey-item">
              <div class="passkey-icon">
                <FaIcon name="i-ri:fingerprint-line" />
              </div>
              <div class="passkey-info">
                <strong>{{ item.deviceName || '未命名设备' }}</strong>
                <span>{{ shortCredential(item.credentialId) }}</span>
                <small>创建时间：{{ item.createTime || '-' }} · 最后使用：{{ item.lastUsedTime || '-' }}</small>
              </div>
              <FaTag :variant="item.status === 'ACTIVE' ? 'default' : 'secondary'">
                {{ passkeyStatusText(item.status) }}
              </FaTag>
              <FaButton variant="destructive" size="sm" :disabled="item.status !== 'ACTIVE'" @click="confirmRevokePasskey(item)">
                吊销
              </FaButton>
            </div>
          </div>
        </section>
      </div>
    </div>
  </div>
</template>

<style scoped>
.passkey-panel {
  display: grid;
  gap: 12px;
  padding: 16px;
  border: 1px solid var(--color-border-2);
  border-radius: 6px;
}

.passkey-title,
.passkey-item {
  display: flex;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
}

.passkey-list {
  display: grid;
  gap: 10px;
  min-height: 80px;
}

.passkey-empty {
  display: grid;
  place-items: center;
  min-height: 80px;
  color: var(--color-text-3);
}

.passkey-item {
  padding: 12px;
  border: 1px solid var(--color-border-2);
  border-radius: 6px;
}

.passkey-icon {
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  flex: 0 0 auto;
  border-radius: 6px;
  background: var(--color-fill-2);
  color: var(--ui-primary);
}

.passkey-info {
  display: grid;
  gap: 2px;
  min-width: 0;
  flex: 1 1 auto;
}

.passkey-info span,
.passkey-info small {
  overflow: hidden;
  color: var(--color-text-3);
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
