<script setup lang="ts">
import { toTypedSchema } from '@vee-validate/zod'
import { useForm } from 'vee-validate'
import * as z from 'zod'
import apiUser from '@/api/modules/user'
import { createPasskeyAuthenticationResponse } from '@/utils/webauthn'
import { FormControl, FormField, FormItem, FormMessage } from '@/ui/shadcn/ui/form'

defineOptions({
  name: 'LoginForm',
})

const props = defineProps<{
  account?: string
}>()

const emits = defineEmits<{
  onLogin: [account?: string]
  onRegister: [account?: string]
  onResetPassword: [account?: string]
}>()

const appAccountStore = useAppAccountStore()
const toast = useFaToast()

const title = import.meta.env.VITE_APP_TITLE
const loading = ref(false)
const passkeyLoading = ref(false)
const type = ref<'password' | 'passkey'>('password')

const form = useForm({
  validationSchema: toTypedSchema(z.object({
    account: z.string().min(1, '请输入用户名'),
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
    await appAccountStore.login(values)
    rememberAccount(values.account, values.remember)
    emits('onLogin', values.account)
  }
  finally {
    loading.value = false
  }
})

async function loginWithPasskey() {
  const account = form.values.account?.trim()
  if (!account) {
    toast.error('请输入用户名')
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
      <h3 class="text-4xl font-bold">
        欢迎使用
      </h3>
      <p class="text-sm text-muted-foreground lg:text-base">
        {{ title }}
      </p>
    </div>
    <div class="mb-4">
      <FaTabs
        v-model="type" :list="[
          { label: '账号密码登录', value: 'password' },
          { label: 'Passkey 登录', value: 'passkey' },
        ]" class="inline-flex"
      />
    </div>
    <form @submit="onSubmit">
      <FormField v-slot="{ componentField, errors }" name="account">
        <FormItem class="pb-6 relative space-y-0">
          <FormControl>
            <FaInput type="text" placeholder="用户名" class="w-full" :class="{ 'border-destructive': errors.length }" v-bind="componentField">
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
      <div class="mb-4 flex-center-between">
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
      <FaButton v-else :loading="passkeyLoading" size="lg" class="w-full" type="button" @click="loginWithPasskey">
        <FaIcon name="i-ri:fingerprint-line" />
        使用 Passkey 登录
      </FaButton>
      <div class="text-sm mt-4 flex-center gap-2">
        <span class="text-secondary-foreground op-50">还没有账号？</span>
        <FaButton variant="link" class="p-0 h-auto" type="button" @click="emits('onRegister', form.values.account)">
          注册新账号
        </FaButton>
      </div>
    </form>
  </div>
</template>
