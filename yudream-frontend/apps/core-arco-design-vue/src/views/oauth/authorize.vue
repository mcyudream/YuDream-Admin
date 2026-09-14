<script setup lang="ts">
import apiSecurity from '@/api/modules/system-security'

defineOptions({
  name: 'OAuthAuthorize',
})

const route = useRoute()
const router = useRouter()

const status = ref<'authorizing' | 'done' | 'error'>('authorizing')
const message = ref('正在完成启动器授权...')

function queryValue(key: string) {
  const value = route.query[key]
  return typeof value === 'string' ? value : ''
}

function isSafeRedirectUrl(url: string) {
  if (!url) {
    return false
  }
  try {
    const parsed = new URL(url)
    return parsed.protocol === 'sjmcl:' || parsed.protocol === 'ymcl:' || parsed.protocol === 'http:' || parsed.protocol === 'https:'
  }
  catch {
    return false
  }
}

async function authorize() {
  const responseType = queryValue('response_type') || 'code'
  const clientId = queryValue('client_id')
  const redirectUri = queryValue('redirect_uri')
  const scope = queryValue('scope')
  const state = queryValue('state')
  if (!clientId || !redirectUri) {
    status.value = 'error'
    message.value = '授权参数不完整'
    return
  }
  status.value = 'authorizing'
  message.value = '正在完成启动器授权...'
  try {
    const res = await apiSecurity.authorizeOAuth({
      response_type: responseType,
      client_id: clientId,
      redirect_uri: redirectUri,
      scope: scope || undefined,
      state: state || undefined,
    })
    const redirectUrl = res.data.redirectUrl
    if (!isSafeRedirectUrl(redirectUrl)) {
      status.value = 'error'
      message.value = '授权回调地址无效'
      return
    }
    status.value = 'done'
    message.value = '授权完成，正在返回启动器...'
    window.location.assign(redirectUrl)
  }
  catch (error) {
    status.value = 'error'
    message.value = error instanceof Error ? error.message : '授权失败'
  }
}

onMounted(async () => {
  await authorize()
})
</script>

<template>
  <main class="oauth-authorize-page">
    <section class="oauth-authorize-card">
      <h1>启动器授权</h1>
      <p>{{ message }}</p>
      <FaButton v-if="status === 'error'" class="mt-4" @click="authorize">
        重试
      </FaButton>
      <FaButton v-if="status === 'error'" variant="outline" class="mt-4 ml-2" @click="router.replace({ name: 'login' })">
        返回登录
      </FaButton>
    </section>
  </main>
</template>

<style scoped>
.oauth-authorize-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: var(--color-fill-1);
}

.oauth-authorize-card {
  width: min(440px, 100%);
  padding: 32px;
  border-radius: 12px;
  background: var(--color-bg-1);
}

.oauth-authorize-card h1 {
  margin: 0 0 12px;
  font-size: 22px;
  color: var(--color-text-1);
}

.oauth-authorize-card p {
  margin: 0;
  color: var(--color-text-2);
  line-height: 1.6;
}
</style>
