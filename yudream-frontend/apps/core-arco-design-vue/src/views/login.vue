<script setup lang="ts">
import { diffTwoObj } from '@fantastic-admin/settings'
import Login from '@/components/AppAccountForm/login.vue'
import Register from '@/components/AppAccountForm/register.vue'
import ResetPassword from '@/components/AppAccountForm/reset-password.vue'
import ColorScheme from '@/layouts/components/Topbar/Toolbar/ColorScheme/index.vue'
import settingsDefault from '@/settings'
import systemClient from '@/api/modules/system-client'
import apiSecurity from '@/api/modules/system-security'

defineOptions({
  name: 'Login',
})

const route = useRoute()
const router = useRouter()
const appSettingsStore = useAppSettingsStore()

const loginBanner = computed(() => appSettingsStore.loginBanner || new URL('@/assets/images/login-banner.png', import.meta.url).href)

const redirect = ref(route.query.redirect?.toString() ?? appSettingsStore.settings.app.home.fullPath)

// 布局对齐方式
const layoutAlign = ref<'left' | 'center' | 'right'>('center')
// 表单相关
const account = ref<string>()
const formType = ref<'login' | 'register' | 'resetPassword'>('login')
const externalProviders = ref<{ code: string, supportedTypes: string }[]>([])
const externalTypes = computed(() => [...new Set(externalProviders.value.flatMap(provider => provider.supportedTypes.split(',').map(item => item.trim().toLowerCase())))])
const externalTypeMeta: Record<string, { label: string, icon: string }> = { qq: { label: 'QQ 登录', icon: 'i-ri:qq-line' }, wx: { label: '微信登录', icon: 'i-ri:wechat-line' }, google: { label: 'Google 登录', icon: 'i-ri:google-line' }, gitee: { label: 'Gitee 登录', icon: 'i-ri:git-repository-line' }, github: { label: 'GitHub 登录', icon: 'i-ri:github-line' } }

function handleLogin() {
  const data = diffTwoObj(settingsDefault, appSettingsStore.settings)
  router.push(redirect.value).then(() => {
    if (Object.keys(data).length > 0) {
      appSettingsStore.updateSettings(data)
    }
  })
}

async function loginWithExternal(type: string) {
  const res = await systemClient.get<any, { data: { authorizationUrl: string } }>(`api/external-login/wwoyun/${type}/authorize`)
  window.location.assign(res.data.authorizationUrl)
}

onMounted(async () => { try { externalProviders.value = (await apiSecurity.publicExternalLoginProviders()).data } catch { externalProviders.value = [] } })
</script>

<template>
  <div class="bg-banner" />
  <div class="text-base p-1 border rounded-lg bg-background flex-center right-4 top-4 absolute z-1">
    <FaDropdown
      v-if="appSettingsStore.mode === 'pc'"
      :items="[[
        { label: '左侧布局', disabled: layoutAlign === 'left', handle: () => { layoutAlign = 'left' } },
        { label: '居中布局', disabled: layoutAlign === 'center', handle: () => { layoutAlign = 'center' } },
        { label: '右侧布局', disabled: layoutAlign === 'right', handle: () => { layoutAlign = 'right' } },
      ]]"
    >
      <FaButton variant="ghost" size="icon-sm">
        <FaIcon
          :name="{
            left: 'i-icon-park-outline:left-bar',
            center: 'i-icon-park-outline:square',
            right: 'i-icon-park-outline:right-bar',
          }[layoutAlign]" class="size-4"
        />
      </FaButton>
    </FaDropdown>
    <ColorScheme v-if="appSettingsStore.settings.toolbar.colorScheme" />
  </div>
  <div class="login-box" :class="layoutAlign">
    <div class="login-banner">
      <img :src="loginBanner" class="banner">
      <AppCopyright v-if="appSettingsStore.mode === 'pc' && ['left', 'right'].includes(layoutAlign)" class="w-full bottom-0 absolute" />
    </div>
    <div class="login-form flex-col-center">
      <div class="w-full">
        <template v-if="formType === 'login'">
          <Login
            :account
            @on-login="handleLogin"
            @on-register="(val) => { formType = 'register'; account = val }"
            @on-reset-password="(val) => { formType = 'resetPassword'; account = val }"
          />
        </template>
        <div v-if="formType === 'login' && externalTypes.length" class="qq-login-entry">
          <FaButton v-for="type in externalTypes" :key="type" variant="outline" size="icon" :title="externalTypeMeta[type]?.label || `${type} 登录`" :aria-label="externalTypeMeta[type]?.label || `${type} 登录`" @click="loginWithExternal(type)">
            <FaIcon :name="externalTypeMeta[type]?.icon || 'i-ri:links-line'" />
          </FaButton>
        </div>
        <Register
          v-if="formType === 'register'"
          :account
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
  <AppCopyright v-if="appSettingsStore.mode === 'mobile' || layoutAlign === 'center'" class="copyright" />
</template>

<style scoped>
.bg-banner {
  position: fixed;
  z-index: 0;
  width: 100%;
  height: 100%;
  background:
    radial-gradient(closest-side, oklch(var(--border) / 10%) 30%, oklch(var(--primary) / 20%) 30%, oklch(var(--border) / 30%) 50%) no-repeat,
    radial-gradient(closest-side, oklch(var(--border) / 10%) 30%, oklch(var(--primary) / 20%) 30%, oklch(var(--border) / 30%) 50%) no-repeat;
  background-position: 100% 100%, 0% 0%;
  background-size: 200vw 200vh;
  filter: blur(100px);
}

[data-mode="mobile"] {
  .login-box {
    position: relative;
    flex-direction: column;
    justify-content: start;
    width: 100%;

    .login-banner {
      width: 100%;
      padding: 20px 0;

      .banner {
        position: relative;
        top: inherit;
        right: inherit;
        display: inherit;
        width: 100%;
        max-width: 375px;
        margin: 0 auto;
        transform: translateY(0);
      }
    }

    .login-form {
      width: 100%;
    }
  }

  .copyright {
    position: relative;
  }
}

.login-box {
  position: absolute;
  display: flex;
  overflow: hidden;
  background-color: oklch(var(--background));

  [data-mode="pc"] & {
    &.center {
      --uno: shadow-md rounded-md;

      top: 50%;
      left: 50%;
      transform: translateX(-50%) translateY(-50%);
    }

    &.left,
    &.right {
      width: 100%;
      height: 100%;

      .login-banner {
        flex: 1;

        .banner {
          position: absolute;
          top: 50%;
          left: 50%;
          width: 50%;
          height: 50%;
          object-fit: contain;
          transform: translateX(-50%) translateY(-50%);
        }
      }

      .login-form {
        margin: 0 5vw;
      }
    }

    &.left {
      flex-direction: row-reverse;
    }
  }

  .login-banner {
    --uno: bg-muted dark:bg-muted/30;

    position: relative;
    width: 450px;
    overflow: hidden;

    &::before {
      position: absolute;
      width: 100%;
      height: 100%;
      content: "";
      background:
        radial-gradient(closest-side, oklch(var(--border) / 10%) 30%, oklch(var(--primary) / 20%) 30%, oklch(var(--border) / 30%) 50%) no-repeat,
        radial-gradient(closest-side, oklch(var(--border) / 10%) 30%, oklch(var(--primary) / 20%) 30%, oklch(var(--border) / 30%) 50%) no-repeat;
      background-position: 100% 100%, 0% 0%;
      background-size: 200vw 200vh;
      filter: blur(100px);
    }

    .banner {
      position: absolute;
      top: 50%;
      width: 100%;
      transform: translateY(-50%);
    }
  }

  .login-form {
    width: 500px;
    transition: height 0.15s ease;

    :deep(input:is(:autofill, :-webkit-autofill)),
    :deep(input:is(:autofill, :-webkit-autofill):hover),
    :deep(input:is(:autofill, :-webkit-autofill):focus) {
      box-shadow: 0 0 0 1000px oklch(var(--background)) inset !important;
      caret-color: oklch(var(--foreground));
      -webkit-text-fill-color: oklch(var(--foreground)) !important;
      transition: background-color 9999s ease-out, color 9999s ease-out;
    }
  }
}

.copyright {
  position: absolute;
  bottom: 0;
  width: 100%;
  padding: 20px;
  margin: 0;
}

.qq-login-entry {
  display: flex;
  gap: 10px;
  align-items: center;
  justify-content: center;
  position: static;
  margin-top: 0;
  margin-bottom: 24px;
}

.qq-login-entry :deep(button) {
  width: 38px;
  height: 38px;
  border-radius: 999px;
}

@media (max-width: 767px) {
  .qq-login-entry {
    margin-top: 24px;
  }
}
</style>
