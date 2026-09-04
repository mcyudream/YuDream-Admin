<script setup lang="ts">
import externalLogin from '@/api/modules/external-login'
import router from '@/router'
import { consumeExternalLoginRedirect } from '@/utils/login-redirect'

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

  // 登录前暂存的目标路由（由登录页在跳转授权前写入），登录完成后原路返回
  const redirect = consumeExternalLoginRedirect()
  try {
    const result = (await externalLogin.callback('wwoyun', type, { code, state })).data
    switch (result.outcome) {
      case 'LOGIN':
        await accountStore.initializeSession(result.session)
        await router.replace(redirect ?? '/')
        return
      case 'BOUND':
        message.value = '第三方账号已绑定'
        await router.replace(redirect ?? '/')
        break
      case 'BIND_REQUIRED':
        await router.replace({
          name: 'login',
          query: {
            externalLoginBindingToken: result.bindingToken,
            externalLoginProvider: result.providerCode,
            externalLoginType: result.type,
            ...(redirect && redirect !== '/' && { redirect }),
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
