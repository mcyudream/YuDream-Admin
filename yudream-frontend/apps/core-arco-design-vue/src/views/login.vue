<script setup lang="ts">
import type { ExternalLoginEntry, ExternalLoginPresentation } from '@/components/AppAccountForm/login.vue'
import { diffTwoObj } from '@fantastic-admin/settings'
import systemClient from '@/api/modules/system-client'
import apiSecurity from '@/api/modules/system-security'
import Login from '@/components/AppAccountForm/login.vue'
import Register from '@/components/AppAccountForm/register.vue'
import ResetPassword from '@/components/AppAccountForm/reset-password.vue'
import ColorScheme from '@/layouts/components/Topbar/Toolbar/ColorScheme/index.vue'
import settingsDefault from '@/settings'
import { sanitizeRedirect, stashExternalLoginRedirect } from '@/utils/login-redirect'

defineOptions({
  name: 'Login',
})

const route = useRoute()
const router = useRouter()
const appSettingsStore = useAppSettingsStore()

const loginBanner = computed(() => appSettingsStore.loginBanner || new URL('@/assets/images/login-banner.png', import.meta.url).href)
const logo = computed(() => appSettingsStore.logo || new URL('@/assets/images/logo.png', import.meta.url).href)

const redirect = ref(sanitizeRedirect(route.query.redirect, appSettingsStore.settings.app.home.fullPath))

// 布局对齐方式
const layoutAlign = ref<'left' | 'center' | 'right'>('center')
// 表单相关
const account = ref<string>()
const bindingToken = typeof route.query.externalLoginBindingToken === 'string' && route.query.externalLoginBindingToken
  ? route.query.externalLoginBindingToken
  : undefined
const formType = ref<'binding' | 'login' | 'register' | 'resetPassword'>(bindingToken && route.query.form !== 'register' ? 'binding' : route.query.form === 'register' ? 'register' : 'login')
const externalProviders = ref<{ code: string, name?: string, supportedTypes: string, icon?: string, presentation?: string, sort?: number }[]>([])
const externalTypeMeta: Record<string, { label: string, icon: string }> = { qq: { label: 'QQ 登录', icon: 'i-ri:qq-line' }, wx: { label: '微信登录', icon: 'i-ri:wechat-line' }, google: { label: 'Google 登录', icon: 'i-ri:google-line' }, gitee: { label: 'Gitee 登录', icon: 'morph:folder-git-2' }, github: { label: 'GitHub 登录', icon: 'i-ri:github-line' } }
const externalEntries = computed<ExternalLoginEntry[]>(() => externalProviders.value.flatMap((provider) => {
  const types = (provider.supportedTypes || '').split(',').map(item => item.trim().toLowerCase()).filter(Boolean)
  const resolvedTypes = types.length ? types : ['default']
  // 插件可声明以登录 Tab 呈现（SPI PluginExternalLoginPresentation.TAB）；宿主托管提供方只有图标按钮
  const presentation: ExternalLoginPresentation = provider.presentation === 'TAB' ? 'TAB' : 'ICON'
  return resolvedTypes.map(type => ({
    providerCode: provider.code,
    type,
    label: provider.icon ? (provider.name || provider.code) : (externalTypeMeta[type]?.label || provider.name || `${type} 登录`),
    icon: provider.icon || externalTypeMeta[type]?.icon || 'morph:link',
    presentation,
    sort: provider.sort ?? 0,
  }))
}))
const externalLoading = ref(false)

function handleLogin() {
  const data = diffTwoObj(settingsDefault, appSettingsStore.settings)
  router.push(redirect.value).then(() => {
    if (Object.keys(data).length > 0) {
      appSettingsStore.updateSettings(data)
    }
  })
}

async function loginWithExternal(entry: ExternalLoginEntry) {
  if (externalLoading.value) {
    return
  }
  externalLoading.value = true
  try {
    const res = await systemClient.get<any, { data: { authorizationUrl: string } }>(`api/external-login/${entry.providerCode}/${entry.type}/authorize`)
    stashExternalLoginRedirect(redirect.value)
    window.location.assign(res.data.authorizationUrl)
  }
  catch {
    externalLoading.value = false
  }
}

onMounted(async () => {
  try {
    externalProviders.value = (await apiSecurity.publicExternalLoginProviders()).data
  }
  catch {
    externalProviders.value = []
  }
})
</script>

<template>
  <div class="login-page">
    <div v-if="appSettingsStore.mode === 'pc' || appSettingsStore.settings.toolbar.colorScheme" class="login-toolbar">
      <FaDropdown
        v-if="appSettingsStore.mode === 'pc'"
        :items="[[
          { label: '左侧布局', disabled: layoutAlign === 'left', handle: () => { layoutAlign = 'left' } },
          { label: '居中布局', disabled: layoutAlign === 'center', handle: () => { layoutAlign = 'center' } },
          { label: '右侧布局', disabled: layoutAlign === 'right', handle: () => { layoutAlign = 'right' } },
        ]]"
      >
        <FaButton variant="ghost" size="icon-sm" aria-label="切换登录页布局">
          <FaIcon
            :name="{
              left: 'morph:panel-left',
              center: 'morph:square',
              right: 'morph:panel-right',
            }[layoutAlign]" class="size-4"
          />
        </FaButton>
      </FaDropdown>
      <ColorScheme v-if="appSettingsStore.settings.toolbar.colorScheme" />
    </div>
    <div class="login-box" :class="layoutAlign">
      <div class="login-banner">
        <div class="banner-heading">
          <img :src="logo" class="banner-logo" alt="">
          <h1>{{ appSettingsStore.siteName }}</h1>
          <p>{{ appSettingsStore.siteDescription || '你的工作、内容与社区，从这里连接。' }}</p>
        </div>
        <div class="banner-artwork" aria-hidden="true">
          <img :src="loginBanner" class="banner" alt="">
        </div>
      </div>
      <div class="login-form flex-col-center">
        <div class="login-form-content w-full">
          <template v-if="formType === 'binding'">
            <div class="p-12 flex-col-stretch-center min-h-500px w-full">
              <div class="mb-8 space-y-2">
                <h3 class="text-4xl font-bold">
                  绑定第三方账号
                </h3>
                <p class="text-sm text-muted-foreground lg:text-base">
                  请选择要绑定的账号类型
                </p>
              </div>
              <FaButton size="lg" class="w-full" @click="formType = 'login'">
                登录已有账号
              </FaButton>
              <FaButton variant="outline" size="lg" class="mt-4 w-full" @click="formType = 'register'">
                注册新账号
              </FaButton>
            </div>
          </template>
          <div v-if="bindingToken && formType !== 'binding'" class="binding-notice" role="status">
            该第三方账号尚未绑定。请登录已有账号完成绑定，或注册新账号后绑定。
          </div>
          <template v-if="formType === 'login'">
            <Login
              :account
              :binding-token="bindingToken"
              :external-entries="externalEntries"
              :external-loading="externalLoading"
              :preferred-method="bindingToken ? '' : appSettingsStore.loginDefaultMethod"
              @on-login="handleLogin"
              @on-external-login="loginWithExternal"
              @on-register="(val) => { formType = 'register'; account = val }"
              @on-reset-password="(val) => { formType = 'resetPassword'; account = val }"
            />
          </template>
          <Register
            v-if="formType === 'register'"
            :account
            :binding-token="bindingToken"
            @on-register="(val) => { formType = 'login'; account = val }"
            @on-login="formType = 'login'"
          />
          <ResetPassword
            v-if="formType === 'resetPassword'"
            :account
            @on-reset-password="(val) => { formType = 'login'; account = val }"
            @on-login="formType = 'login'"
          />
        </div>
      </div>
    </div>
    <AppCopyright class="copyright" />
  </div>
</template>

<style scoped>
.login-page {
  position: relative;
  display: flex;
  flex-direction: column;
  min-height: 100svh;
  color: var(--color-text-1);
  background: var(--color-bg-1);
  isolation: isolate;
}

.login-toolbar {
  position: absolute;
  top: 20px;
  right: 24px;
  z-index: 2;
  display: flex;
  gap: 4px;
  align-items: center;
  color: var(--color-text-2);
}

.login-box {
  position: relative;
  display: flex;
  flex: 1;
  width: 100%;
  min-height: calc(100svh - 64px);

  &.left {
    flex-direction: row-reverse;
  }
}

.login-banner {
  position: relative;
  display: flex;
  flex: 1;
  flex-direction: column;
  justify-content: center;
  min-width: 0;
  padding: clamp(40px, 6vw, 96px);
  overflow: hidden;
  background: var(--color-fill-1);
}

.banner-heading {
  position: relative;
  z-index: 1;
  width: 100%;
  max-width: 680px;
  margin-inline: auto;
  animation: login-reveal var(--yd-motion-duration-slow, 440ms) var(--yd-motion-ease, ease-out) both;

  h1 {
    margin: 28px 0 20px;
    font-size: clamp(40px, 4.6vw, 76px);
    font-weight: 700;
    line-height: 1.12;
    color: var(--color-text-1);
    letter-spacing: -0.045em;
    text-wrap: balance;
    overflow-wrap: anywhere;
  }

  p {
    max-width: 36em;
    margin: 0;
    font-size: 16px;
    line-height: 1.8;
    color: var(--color-text-2);
    text-wrap: pretty;
  }
}

.banner-logo {
  width: 48px;
  height: 48px;
  object-fit: contain;
}

.banner-artwork {
  display: grid;
  place-items: center;
  margin-top: 32px;
  animation: login-reveal var(--yd-motion-duration-slow, 440ms) var(--yd-motion-ease, ease-out) var(--yd-motion-stagger, 45ms) both;
}

.banner {
  display: block;
  width: min(100%, 480px);
  height: clamp(200px, 32vh, 360px);
  object-fit: contain;
}

.login-form {
  flex: 0 0 43%;
  min-width: 400px;
  padding: 88px 32px 56px;
  background: var(--color-bg-2);
  animation: login-reveal var(--yd-motion-duration-slow, 440ms) var(--yd-motion-ease, ease-out) calc(var(--yd-motion-stagger, 45ms) * 2) both;

  :deep(input:is(:autofill, :-webkit-autofill)),
  :deep(input:is(:autofill, :-webkit-autofill):hover),
  :deep(input:is(:autofill, :-webkit-autofill):focus) {
    caret-color: var(--color-text-1);
    box-shadow: 0 0 0 1000px var(--color-fill-1) inset !important;
    -webkit-text-fill-color: var(--color-text-1) !important;
  }
}

.login-form-content {
  max-width: 400px;
}

.login-form :deep([data-slot="input-group"]) {
  height: 48px;
  overflow: hidden;
  background: var(--color-fill-1);
}

.login-form :deep([data-slot="input-group-control"]) {
  height: 100%;
  padding-block: 0;
  font-family: "Segoe UI", system-ui, "Microsoft YaHei", sans-serif;
  font-size: 16px;
  letter-spacing: 0;
}

.login-form :deep([data-slot="input-group-addon"][data-align^="inline-"]) {
  align-self: stretch;
  padding-block: 0;
}

.login-form :deep([data-slot="input-group-addon"][data-align="inline-end"] button) {
  width: 40px;
  height: 40px;
}

.login-form-content :deep(.p-12) {
  padding: 0;
}

.login-form-content :deep(.min-h-500px) {
  min-height: 0;
}

.login-form-content :deep(:is(h2, h3)) {
  font-size: 30px;
  line-height: 1.35;
  letter-spacing: -0.035em;
}

.login-form-content :deep([data-slot="button"].text-primary) {
  color: var(--color-text-2);
}

.binding-notice {
  padding: 12px 16px;
  margin-bottom: 24px;
  font-size: 13px;
  line-height: 1.7;
  color: var(--color-text-2);
  background: var(--color-fill-1);
  border: 1px solid var(--color-border-2);
  border-radius: var(--yd-control-radius, 12px);
}

.copyright {
  position: relative;
  width: 100%;
  padding: 20px 24px;
  margin: 0;
  background: var(--color-bg-2);
}

@media (width > 768px) {
  .login-page:has(.login-box.center) {
    padding-top: 64px;
  }

  .login-page:has(.login-box.center):not(:has(.copyright)) {
    padding-bottom: 64px;
  }

  .login-box.center {
    flex: 0 0 auto;
    width: min(1080px, calc(100% - 64px));
    min-height: 0;
    margin: auto;
    overflow: hidden;
    border: 1px solid var(--color-border-2);
    border-radius: var(--yd-surface-radius, 18px);
  }

  .login-box.center .login-banner {
    padding: clamp(28px, 4vw, 56px);
  }

  .login-box.center .banner-heading h1 {
    font-size: clamp(36px, 4vw, 56px);
  }

  .login-box.center .banner {
    height: clamp(160px, 24vh, 260px);
  }

  .login-box.center .login-form {
    flex-basis: 50%;
    min-width: 0;
    padding: 48px 32px;
  }
}

@media (width <= 768px) {
  .login-toolbar {
    top: 16px;
    right: 16px;
  }

  .login-box,
  .login-box.left,
  .login-box.right {
    flex-direction: column;
    min-height: 0;
  }

  .login-banner {
    flex: none;
    padding: 56px 24px 28px;
  }

  .banner-heading h1 {
    margin: 16px 0 12px;
    font-size: clamp(32px, 8vw, 44px);
  }

  .banner-heading p {
    font-size: 14px;
  }

  .banner-logo {
    width: 36px;
    height: 36px;
  }

  .banner-artwork {
    display: none;
  }

  .login-form {
    flex: 1;
    justify-content: flex-start;
    width: 100%;
    min-width: 0;
    padding: 32px 24px 40px;
  }

  .login-form-content {
    max-width: 440px;
  }

  .login-form-content :deep(:is(h2, h3)) {
    font-size: 26px;
  }

  .login-form :deep([data-slot="input-group-addon"][data-align="inline-end"] button) {
    width: 44px;
    height: 44px;
  }
}

@keyframes login-reveal {
  from {
    opacity: 0;
    transform: translateY(var(--yd-motion-enter-y, 14px));
  }

  to {
    opacity: 1;
    transform: none;
  }
}

@media (prefers-reduced-motion: reduce) {
  .banner-heading,
  .banner-artwork,
  .login-form {
    animation: none;
  }
}
</style>
