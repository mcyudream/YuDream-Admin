<script setup lang="ts">
import apiUser from '@/api/modules/user'

defineOptions({
  name: 'VerifyEmail',
})

const route = useRoute()
const router = useRouter()
const appAccountStore = useAppAccountStore()

const loading = ref(true)
const success = ref(false)
const message = ref('正在验证邮箱...')

onMounted(() => {
  const token = route.query.token?.toString() ?? ''
  if (!token) {
    loading.value = false
    success.value = false
    message.value = '验证链接无效，缺少 token'
    return
  }
  apiUser.verifyEmail(token).then(() => {
    loading.value = false
    success.value = true
    appAccountStore.setEmailVerified(true)
    message.value = '邮箱验证成功，即将跳转到登录页'
    setTimeout(() => {
      router.push({ name: 'login' })
    }, 2000)
  }).catch(() => {
    loading.value = false
    success.value = false
    message.value = '邮箱验证失败，链接可能已过期或无效'
  })
})
</script>

<template>
  <div class="h-screen w-screen flex-col-center">
    <div class="w-100 rounded-lg bg-background p-8 shadow-md">
      <div class="mb-6 text-center">
        <FaIcon :name="success ? 'i-lucide:circle-check' : loading ? 'i-lucide:loader' : 'i-lucide:circle-x'" class="size-12" :class="success ? 'text-green-500' : loading ? 'text-primary animate-spin' : 'text-destructive'" />
      </div>
      <h3 class="mb-2 text-center text-xl font-bold">
        {{ success ? '验证成功' : loading ? '验证中' : '验证失败' }}
      </h3>
      <p class="text-center text-muted-foreground">
        {{ message }}
      </p>
      <div v-if="!loading" class="mt-6 flex justify-center">
        <FaButton @click="router.push({ name: 'login' })">
          去登录
        </FaButton>
      </div>
    </div>
  </div>
</template>
