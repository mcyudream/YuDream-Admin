<script setup lang="ts">
import { toTypedSchema } from '@vee-validate/zod'
import { useForm } from 'vee-validate'
import * as z from 'zod'
import apiUser from '@/api/modules/user'
import { useAppFeatureStore } from '@/store/modules/app/features'
import { createPasskeyAuthenticationResponse } from '@/utils/webauthn'
import { FormControl, FormField, FormItem, FormMessage } from '@/ui/shadcn/ui/form'

defineOptions({
  name: 'LoginForm',
})

/** 插件登录入口的呈现方式，与宿主 SPI PluginExternalLoginPresentation 对应 */
export type ExternalLoginPresentation = 'ICON' | 'TAB'

export interface ExternalLoginEntry {
  providerCode: string
  type: string
  label: string
  icon: string
  presentation?: ExternalLoginPresentation
  /** 插件登录入口的排序位，决定登录 Tab 的顺序 */
  sort?: number
}

const props = defineProps<{
  account?: string
  bindingToken?: string
  externalEntries?: ExternalLoginEntry[]
  externalLoading?: boolean
  /** 站点配置的首选登录方式：password / passkey / external:{providerCode}:{type}；空=账号密码 */
  preferredMethod?: string
}>()

const emits = defineEmits<{
  onLogin: [account?: string]
  onRegister: [account?: string]
  onResetPassword: [account?: string]
  onExternalLogin: [entry: ExternalLoginEntry]
}>()

const appAccountStore = useAppAccountStore()
const appFeatureStore = useAppFeatureStore()
const appSettingsStore = useAppSettingsStore()
const toast = useFaToast()

const logo = computed(() => appSettingsStore.logo || new URL('@/assets/images/logo.png', import.meta.url).href)
const loading = ref(false)
const passkeyLoading = ref(false)
const type = ref<string>('password')
/** 首选登录方式是否已经应用过（只应用一次，之后用户的手动切换不再被覆盖） */
const preferredApplied = ref(false)

function externalTabValue(entry: ExternalLoginEntry) {
  return `external:${entry.providerCode}:${entry.type}`
}

/** 声明为 TAB 的插件登录入口：与 Passkey 并列成登录方式；绑定流程下与 Passkey 一样不参与 */
const externalTabEntries = computed(() => (props.bindingToken ? [] : (props.externalEntries ?? []).filter(entry => entry.presentation === 'TAB')))
/** 未声明 TAB 的插件登录入口：仍按历史行为渲染成表单下方的一排图标按钮 */
const externalIconEntries = computed(() => (props.externalEntries ?? []).filter(entry => entry.presentation !== 'TAB'))
/** 内置登录方式的排序基线：插件 TAB 入口的 sort 与之同列排序，小于 100 即排到账号密码之前 */
const PASSWORD_TAB_SORT = 100
const PASSKEY_TAB_SORT = 200
const activeExternalEntry = computed(() => externalTabEntries.value.find(entry => externalTabValue(entry) === type.value) ?? null)
const activeExternalLabel = computed(() => {
  const label = activeExternalEntry.value?.label ?? ''
  return label.endsWith('登录') ? label : `${label}登录`
})
const activeExternalIcon = computed(() => activeExternalEntry.value?.icon || 'i-ri:links-line')

const loginTabs = computed(() => {
  const tabs: { label: string, value: string, icon?: string, sort: number }[] = [
    { label: '账号密码登录', value: 'password', sort: PASSWORD_TAB_SORT },
    ...(!props.bindingToken && appFeatureStore.passkeyEnabled ? [{ label: 'Passkey 登录', value: 'passkey', sort: PASSKEY_TAB_SORT }] : []),
    ...externalTabEntries.value.map(entry => ({ label: entry.label, value: externalTabValue(entry), icon: entry.icon, sort: entry.sort ?? 0 })),
  ]
  // sort 稳定升序：插件入口的 sort 小于内置基线时排到前面（CAS 等以统一身份认证为主的站点可置顶）
  return tabs.sort((a, b) => a.sort - b.sort).map(tab => ({ label: tab.label, value: tab.value, icon: tab.icon }))
})

onMounted(() => {
  appFeatureStore.load()
})

// 当前选中的登录方式不可用时回落账号密码：Passkey 被关闭、进入绑定流程、插件入口消失
watch(loginTabs, (tabs) => {
  applyPreferredMethod()
  if (!tabs.some(tab => tab.value === type.value)) {
    type.value = 'password'
  }
}, { immediate: true })

// 站点配置的首选登录方式可能晚于 Tab 就绪（Passkey 开关、插件入口异步加载）到达
watch(() => props.preferredMethod, () => applyPreferredMethod())

/**
 * 首选登录方式只在首次可用时应用一次：配置的入口不存在、未启用或不是 Tab 型时
 * 保持账号密码，避免登录页选中一个渲染不出来的登录方式。
 */
function applyPreferredMethod() {
  if (preferredApplied.value) {
    return
  }
  const preferred = props.preferredMethod?.trim()
  if (!preferred) {
    return
  }
  if (loginTabs.value.some(tab => tab.value === preferred)) {
    type.value = preferred
    preferredApplied.value = true
  }
}

const form = useForm({
  validationSchema: toTypedSchema(z.object({
    account: z.string().min(1, '请输入用户名或邮箱'),
    password: z.string().min(1, '请输入密码'),
    remember: z.boolean(),
  })),
  initialValues: {
    account: props.account ?? localStorage.getItem('login_account') ?? '',
    password: '',
    remember: localStorage.getItem('login_account') !== null,
  },
})

const onSubmit = form.handleSubmit(async (values) => {
  loading.value = true
  try {
    await appAccountStore.login({
      ...values,
      bindingToken: props.bindingToken,
    })
    rememberAccount(values.account, values.remember)
    emits('onLogin', values.account)
  }
  finally {
    loading.value = false
  }
}, (ctx) => {
  // 校验失败时给一条明确提示：只靠输入框下方的小字，用户会以为「登录按钮没反应」
  const first = Object.values(ctx.errors ?? {}).find(message => typeof message === 'string' && message)
  toast.error(typeof first === 'string' ? first : '请填写完整的登录信息')
})

async function loginWithPasskey() {
  if (!appFeatureStore.passkeyEnabled) {
    toast.error('Passkey 未启用')
    type.value = 'password'
    return
  }
  const account = form.values.account?.trim()
  if (!account) {
    toast.error('请输入用户名或邮箱')
    return
  }
  passkeyLoading.value = true
  try {
    const options = await apiUser.startPasskeyAuthentication(account)
    const responseJson = await createPasskeyAuthenticationResponse(options.data.publicKeyJson)
    await appAccountStore.passkeyLogin({
      account,
      requestJson: options.data.requestJson,
      responseJson,
    })
    rememberAccount(account, form.values.remember)
    emits('onLogin', account)
  }
  finally {
    passkeyLoading.value = false
  }
}

function loginWithExternalTab() {
  const entry = activeExternalEntry.value
  if (!entry || props.externalLoading) {
    return
  }
  emits('onExternalLogin', entry)
}

function rememberAccount(account: string, remember?: boolean) {
  if (remember) {
    localStorage.setItem('login_account', account)
  }
  else {
    localStorage.removeItem('login_account')
  }
}
</script>

<template>
  <div class="p-12 flex-col-stretch-center min-h-500px w-full">
    <div class="mb-6 space-y-2">
      <div class="flex items-center gap-3">
        <img :src="logo" class="h-10 w-10 object-contain rounded">
        <h3 class="text-4xl font-bold">
          欢迎使用
        </h3>
      </div>
      <p class="text-sm text-muted-foreground lg:text-base">
        {{ appSettingsStore.siteName }}
      </p>
    </div>
    <div class="mb-4">
      <FaTabs
        v-model="type" :list="loginTabs" class="inline-flex"
      />
    </div>
    <form @submit="onSubmit">
      <!-- 账号密码/Passkey 都用这个字段：必须始终挂载（只隐藏），否则切回密码登录时它是重新挂载的空框，
           浏览器自动填充也不会补上，提交会被校验拦下 -->
      <div v-show="!activeExternalEntry">
        <FormField v-slot="{ componentField, errors }" name="account">
          <FormItem class="pb-6 relative space-y-0">
            <FormControl>
              <FaInput type="text" placeholder="用户名或邮箱" class="w-full" :class="{ 'border-destructive': errors.length }" v-bind="componentField">
                <template #start>
                  <FaIcon name="i-lucide:user" />
                </template>
              </FaInput>
            </FormControl>
            <Transition enter-active-class="transition-opacity" enter-from-class="opacity-0" leave-active-class="transition-opacity" leave-to-class="opacity-0">
              <FormMessage class="text-xs m-0 bottom-1 absolute" />
            </Transition>
          </FormItem>
        </FormField>
      </div>
      <div v-show="type === 'password'">
        <FormField v-slot="{ componentField, errors }" name="password">
          <FormItem class="pb-6 relative space-y-0">
            <FormControl>
              <FaInput type="password" placeholder="密码" class="w-full" :class="{ 'border-destructive': errors.length }" v-bind="componentField">
                <template #start>
                  <FaIcon name="i-lucide:lock" />
                </template>
              </FaInput>
            </FormControl>
            <Transition enter-active-class="transition-opacity" enter-from-class="opacity-0" leave-active-class="transition-opacity" leave-to-class="opacity-0">
              <FormMessage class="text-xs m-0 bottom-1 absolute" />
            </Transition>
          </FormItem>
        </FormField>
      </div>
      <div v-show="!activeExternalEntry" class="mb-4 flex-center-between">
        <div class="flex-center-start">
          <FormField v-slot="{ componentField }" type="checkbox" name="remember">
            <FormItem>
              <FormControl>
                <FaCheckbox :model-value="componentField.modelValue" @update:model-value="componentField['onUpdate:modelValue']?.($event)">
                  记住账号
                </FaCheckbox>
              </FormControl>
            </FormItem>
          </FormField>
        </div>
        <FaButton v-if="type === 'password'" variant="link" class="p-0 h-auto" type="button" @click="emits('onResetPassword', form.values.account)">
          忘记密码了？
        </FaButton>
      </div>
      <FaButton v-if="type === 'password'" :loading="loading" size="lg" class="w-full" type="submit">
        登录
      </FaButton>
      <FaButton v-else-if="type === 'passkey'" :loading="passkeyLoading" size="lg" class="w-full" type="button" @click="loginWithPasskey">
        <FaIcon name="i-ri:fingerprint-line" />
        使用 Passkey 登录
      </FaButton>
      <FaButton v-else-if="activeExternalEntry" :loading="props.externalLoading" size="lg" class="w-full" type="button" @click="loginWithExternalTab">
        <FaIcon :name="activeExternalIcon" />
        使用 {{ activeExternalLabel }}
      </FaButton>
      <div class="text-sm mt-4 flex-center gap-2">
        <span class="text-secondary-foreground op-50">还没有账号？</span>
        <FaButton variant="link" class="p-0 h-auto" type="button" @click="emits('onRegister', form.values.account)">
          注册新账号
        </FaButton>
      </div>
      <div v-if="externalIconEntries.length" class="qq-login-entry">
        <FaButton
          v-for="entry in externalIconEntries"
          :key="`${entry.providerCode}:${entry.type}`"
          variant="outline"
          size="icon"
          type="button"
          :title="entry.label"
          :aria-label="entry.label"
          :disabled="props.externalLoading"
          @click="emits('onExternalLogin', entry)"
        >
          <FaIcon :name="entry.icon" />
        </FaButton>
      </div>
    </form>
  </div>
</template>

<style scoped>
.qq-login-entry {
  display: flex;
  gap: 10px;
  align-items: center;
  justify-content: center;
  margin-top: 24px;
}

.qq-login-entry :deep(button) {
  width: 38px;
  height: 38px;
  border-radius: 999px;
}
</style>
