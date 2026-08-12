<script setup lang="ts">
import externalLogin from '@/api/modules/external-login'
import router from '@/router'

const route = useRoute()
const accountStore = useAppAccountStore()
const message = ref('正在完成第三方登录...')

onMounted(async () => {
  const type = String(route.query.type || '')
  const code = String(route.query.code || '')
  const state = String(route.query.state || '')
  if (!type || !code || !state) {
    message.value = '第三方登录回调参数不完整'
    return
  }

  try {
    const result = (await externalLogin.callback('wwoyun', type, { code, state })).data
    switch (result.outcome) {
      case 'LOGIN':
        await accountStore.initializeSession(result.session)
        await router.replace('/')
        return
      case 'BOUND':
        message.value = '第三方账号已绑定'
        await router.replace('/')
        break
      case 'BIND_REQUIRED':
        await router.replace({
          name: 'login',
          query: {
            externalLoginBindingToken: result.bindingToken,
            externalLoginProvider: result.providerCode,
            externalLoginType: result.type,
          },
        })
        break
    }
  }
  catch (error: unknown) {
    message.value = error instanceof Error ? error.message : '第三方登录失败'
  }
})
</script>

<template>
  <div class="flex min-h-screen items-center justify-center bg-[var(--color-fill-1)]">
    <div class="rounded-lg bg-[var(--color-bg-1)] px-8 py-6 text-[var(--color-text-2)] shadow-sm">
      {{ message }}
    </div>
  </div>
</template>
