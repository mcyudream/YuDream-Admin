<script setup lang="ts">
import systemClient from '@/api/modules/system-client'
import router from '@/router'

const route = useRoute()
const message = ref('正在完成第三方登录...')

onMounted(async () => {
  const type = String(route.query.type || '')
  const code = String(route.query.code || '')
  const state = String(route.query.state || '')
  if (!type || !code || !state) { message.value = '第三方登录回调参数不完整'; return }
  try {
    const res = await systemClient.get<any, { data: { token: string, refreshToken?: string } }>(`api/external-login/wwoyun/${type}/callback`, { params: { code, state } })
    localStorage.setItem('token', res.data.token)
    if (res.data.refreshToken) localStorage.setItem('refreshToken', res.data.refreshToken)
    await router.replace('/')
    window.location.reload()
  }
  catch (error: any) { message.value = error?.message || '第三方登录失败' }
})
</script>

<template><div class="flex min-h-screen items-center justify-center bg-[var(--color-fill-1)]"><div class="rounded-lg bg-[var(--color-bg-1)] px-8 py-6 text-[var(--color-text-2)] shadow-sm">{{ message }}</div></div></template>
