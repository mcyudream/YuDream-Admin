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
    <div class="bg-banner" aria-hidden="true" />
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
          <span class="banner-eyebrow">
            <FaIcon name="morph:panels-top-left" class="size-4" />
            {{ appSettingsStore.siteName }}
          </span>
          <h1>从这里，<br>开启新的工作。</h1>
          <p>专注当下，让工作井然有序。</p>
        </div>
        <div class="banner-artwork" aria-hidden="true">
          <div class="banner-orbit banner-orbit--outer" />
          <div class="banner-orbit banner-orbit--inner" />
          <img :src="loginBanner" class="banner" alt="">
        </div>
        <div class="banner-caption" aria-hidden="true">
          <span class="banner-caption-line" />
          <FaIcon name="morph:sparkles" class="size-4" />
          <span>每一次开始，都向前一步</span>
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
  align-items: center;
  justify-content: center;
  min-height: 100svh;
  padding: 88px 32px 0;
  background: var(--color-bg-1);
  isolation: isolate;
}

.bg-banner {
  position: absolute;
  inset: 0;
  z-index: -1;
  overflow: hidden;
  pointer-events: none;
  background:
    radial-gradient(ellipse at 12% 18%, var(--color-fill-2), transparent 52%),
    radial-gradient(ellipse at 88% 84%, var(--color-fill-3), transparent 52%);

  &::after {
    position: absolute;
    inset: 0;
    content: "";
    background-image: radial-gradient(var(--color-border-2) 1px, transparent 1px);
    background-size: 24px 24px;
    opacity: 0.45;
    mask-image: linear-gradient(135deg, #000, transparent 65%);
  }
}

.login-toolbar {
  position: absolute;
  top: 24px;
  right: 32px;
  z-index: 1;
  display: flex;
  gap: 4px;
  align-items: center;
  padding: 4px;
  background: var(--color-bg-2);
  border: 1px solid var(--color-border-2);
  border-radius: 14px;
  box-shadow: 0 4px 16px color-mix(in srgb, var(--color-text-1) 4%, transparent);
}

.login-box {
  position: relative;
  display: flex;
  width: min(1080px, 100%);
  min-height: 620px;
  overflow: hidden;
  background: var(--color-bg-2);
  border: 1px solid var(--color-border-2);
  border-radius: var(--yd-surface-radius, 24px);
  box-shadow: var(--yd-surface-shadow, 0 20px 64px color-mix(in srgb, var(--color-text-1) 8%, transparent));
  animation: login-reveal var(--yd-motion-duration-slow, 420ms) var(--yd-motion-ease, ease-out) both;

  &.left {
    flex-direction: row-reverse;
  }

  &.left,
  &.right {
    width: min(1440px, 100%);
    min-height: max(620px, calc(100svh - 164px));
  }
}

.login-banner {
  position: relative;
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
  padding: 40px;
  overflow: hidden;
  background: linear-gradient(155deg, var(--color-fill-1), var(--color-fill-3));
}

.banner-heading {
  position: relative;
  z-index: 1;
  animation: login-reveal var(--yd-motion-duration-slow, 420ms) var(--yd-motion-ease, ease-out) var(--yd-motion-stagger, 70ms) both;

  h1 {
    margin: 28px 0 16px;
    font-size: clamp(30px, 3vw, 42px);
    font-weight: 650;
    line-height: 1.4;
    color: var(--color-text-1);
    letter-spacing: -0.04em;
  }

  p {
    margin: 0;
    font-size: 14px;
    line-height: 1.8;
    color: var(--color-text-3);
  }
}

.banner-eyebrow {
  display: inline-flex;
  gap: 8px;
  align-items: center;
  max-width: 100%;
  font-size: 13px;
  font-weight: 500;
  line-height: 1.6;
  color: var(--color-text-2);
  overflow-wrap: anywhere;
}

.banner-artwork {
  position: relative;
  display: grid;
  flex: 1;
  place-items: center;
  min-height: 280px;
  margin: 12px -20px;
  animation: login-reveal var(--yd-motion-duration-slow, 420ms) var(--yd-motion-ease, ease-out) calc(var(--yd-motion-stagger, 70ms) * 2) both;
}

.banner-orbit {
  position: absolute;
  width: 250px;
  aspect-ratio: 1;
  pointer-events: none;
  border: 1px solid var(--color-border-2);
  border-radius: 50%;

  &--outer {
    width: 340px;
    border-style: dashed;
    opacity: 0.65;
  }
}

.banner {
  position: relative;
  width: min(100%, 380px);
  height: 300px;
  object-fit: contain;
  transition: transform var(--yd-motion-duration-slow, 420ms) var(--yd-motion-ease, ease-out);
}

.banner-caption {
  position: relative;
  display: flex;
  gap: 10px;
  align-items: center;
  padding-top: 16px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--color-text-3);
  animation: login-reveal var(--yd-motion-duration-slow, 420ms) var(--yd-motion-ease, ease-out) calc(var(--yd-motion-stagger, 70ms) * 3) both;
}

.banner-caption-line {
  width: 28px;
  height: 1px;
  background: var(--color-border-3);
}

.login-form {
  flex: 0 0 52%;
  min-width: 0;
  max-width: 580px;
  padding: 24px 0;
  animation: login-reveal var(--yd-motion-duration-slow, 420ms) var(--yd-motion-ease, ease-out) calc(var(--yd-motion-stagger, 70ms) * 2) both;

  :deep(input:is(:autofill, :-webkit-autofill)),
  :deep(input:is(:autofill, :-webkit-autofill):hover),
  :deep(input:is(:autofill, :-webkit-autofill):focus) {
    caret-color: oklch(var(--foreground));
    box-shadow: 0 0 0 1000px oklch(var(--background)) inset !important;
    transition: background-color 9999s ease-out, color 9999s ease-out;
    -webkit-text-fill-color: oklch(var(--foreground)) !important;
  }
}

.login-form-content :deep(.p-12) {
  padding: clamp(24px, 4vw, 48px);
}

.login-form-content :deep(h3) {
  font-size: clamp(28px, 3vw, 34px);
  line-height: 1.35;
  letter-spacing: -0.035em;
}

/* 登录辅助操作保持文本可读性，不依赖管理员选择的主色明度。 */
.login-form-content :deep([data-slot="button"].text-primary) {
  color: var(--color-text-2);
}

.binding-notice {
  padding: 12px 16px;
  margin: 20px clamp(24px, 4vw, 48px) 0;
  font-size: 13px;
  line-height: 1.7;
  color: var(--color-text-2);
  background: var(--color-fill-1);
  border: 1px solid var(--color-border-2);
  border-radius: 12px;
}

.copyright {
  position: relative;
  width: 100%;
  padding: 24px 0;
  margin: 0;
}

@media (hover: hover) and (prefers-reduced-motion: no-preference) {
  .banner-artwork:hover .banner {
    transform: translateY(var(--yd-motion-lift, -4px)) rotate(-1deg);
  }

  html[data-admin-motion="reduced"] .banner-artwork:hover .banner {
    transform: none;
  }
}

@media (width <= 768px) {
  .login-page {
    justify-content: flex-start;
    padding: 76px 16px 0;
  }

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
    padding: 24px;
  }

  .banner-heading h1 {
    margin: 12px 0 8px;
    font-size: 26px;

    br {
      display: none;
    }
  }

  .banner-artwork,
  .banner-caption {
    display: none;
  }

  .login-form {
    flex: none;
    width: 100%;
    max-width: none;
    padding: 8px 0;
  }

  .login-form-content :deep(.min-h-500px) {
    min-height: 0;
  }

  .copyright {
    padding: 20px 0;
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
  .login-box,
  .banner-heading,
  .banner-artwork,
  .banner-caption,
  .login-form {
    animation: none;
  }

  .banner {
    transition: none;
  }
}
</style>
