<script setup lang="ts">
import type { UserProfilePayload } from '@/api/modules/profile'
import EditPassword from '@/components/AppAccountForm/edit-password.vue'
import apiProfile from '@/api/modules/profile'
import { toBackendAssetUrl } from '@/utils/backend-url'

const toast = useFaToast()
const appAccountStore = useAppAccountStore()

const active = ref(0)
const loading = ref(false)
const saving = ref(false)
const avatarInput = ref<HTMLInputElement>()
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
    description: '定期修改密码',
  },
])

onMounted(loadProfile)

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
      <EditPassword v-if="active === 1" />
    </div>
  </div>
</template>
