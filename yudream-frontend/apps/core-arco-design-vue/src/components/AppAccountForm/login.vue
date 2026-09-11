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

export interface ExternalLoginEntry {
  providerCode: string
  type: string
  label: string
  icon: string
}

const props = defineProps<{
  account?: string
  bindingToken?: string
  externalEntries?: ExternalLoginEntry[]
  externalLoading?: boolean
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
// 第三方登录入口与账号密码/Passkey 平铺为登录方式 tab；绑定流程下只允许账号密码，不展示第三方 tab
const EXTERNAL_TAB_PREFIX = 'external:'
const loginTabs = computed(() => [
  { label: '账号密码登录', value: 'password' },
  ...(!props.bindingToken && appFeatureStore.passkeyEnabled ? [{ label: 'Passkey 登录', value: 'passkey' }] : []),
  ...(!props.bindingToken ? (props.externalEntries ?? []).map(entry => ({ label: entry.label, value: `${EXTERNAL_TAB_PREFIX}${entry.providerCode}:${entry.type}`, icon: entry.icon })) : []),
])
const currentExternal = computed(() => {
  if (!type.value.startsWith(EXTERNAL_TAB_PREFIX)) {
    return undefined
  }
  const [providerCode, entryType] = type.value.slice(EXTERNAL_TAB_PREFIX.length).split(':')
  return props.externalEntries?.find(entry => entry.providerCode === providerCode && entry.type === entryType)
})

onMounted(() => {
  appFeatureStore.load()
})

watch(() => appFeatureStore.passkeyEnabled, (enabled) => {
  if (!enabled && type.value === 'passkey') {
    type.value = 'password'
  }
}, { immediate: true })

watch(currentExternal, (entry) => {
  if (type.value.startsWith(EXTERNAL_TAB_PREFIX) && !entry) {
    type.value = 'password'
  }
})

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
        v-model="type" :list="loginTabs" class="w-full"
      />
    </div>
    <form @submit="onSubmit">
      <FormField v-show="!currentExternal" v-slot="{ componentField, errors }" name="account">
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
      <div v-show="!currentExternal" class="mb-4 flex-center-between">
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
      <div v-else-if="currentExternal" class="flex-col-stretch-center gap-4">
        <div class="flex-center size-16 border rounded-full bg-primary/5 text-primary">
          <FaIcon :name="currentExternal.icon" class="size-8" />
        </div>
        <p class="text-sm text-muted-foreground text-center">
          点击按钮跳转到「{{ currentExternal.label }}」完成认证，认证成功后自动登录；未绑定本站账号时可登录或注册后完成绑定
        </p>
        <FaButton size="lg" class="w-full" type="button" :loading="props.externalLoading" @click="emits('onExternalLogin', currentExternal)">
          <FaIcon :name="currentExternal.icon" />
          前往{{ currentExternal.label }}
        </FaButton>
      </div>
      <div v-if="!currentExternal" class="text-sm mt-4 flex-center gap-2">
        <span class="text-secondary-foreground op-50">还没有账号？</span>
        <FaButton variant="link" class="p-0 h-auto" type="button" @click="emits('onRegister', form.values.account)">
          注册新账号
        </FaButton>
      </div>
    </form>
  </div>
</template>
