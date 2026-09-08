<script setup lang="ts">
import type { Component } from 'vue'
import { toTypedSchema } from '@vee-validate/zod'
import { useForm } from 'vee-validate'
import * as z from 'zod'
import apiUser from '@/api/modules/user'
import type { VerificationMethod } from '@/api/modules/user'
import { FormControl, FormDescription, FormField, FormItem, FormMessage } from '@/ui/shadcn/ui/form'
import { acquirePluginRemoteModuleByCode, type PluginRemoteModuleLease } from '@/plugins/remote-loader'
import { createPluginSdk } from '@/plugins/sdk'
import apiPlugin from '@/api/modules/platform-plugin'

defineOptions({
  name: 'RegisterForm',
})

const props = defineProps<{
  account?: string
  bindingToken?: string
}>()

const emits = defineEmits<{
  onLogin: [account?: string]
  onRegister: [account?: string]
}>()

const appAccountStore = useAppAccountStore()
const router = useRouter()
const route = useRoute()
const loading = ref(false)
const verificationMethods = ref<VerificationMethod[]>([])
const verifyOpen = ref(false)
const verifyLoading = ref(false)
const verifyError = ref('')
const verifyComponent = shallowRef<Component | null>(null)
const verifyLease = shallowRef<PluginRemoteModuleLease | null>(null)
const activeMethod = ref<VerificationMethod | null>(null)
const verifyEmail = ref('')
const verifySdk = computed(() => createPluginSdk(activeMethod.value?.code || ''))
const methodStates = ref<Record<string, { status: 'NONE' | 'PASSED' | 'PENDING', checking: boolean }>>({})
let loadSequence = 0
let statusSequence = 0
let emailCheckTimer: ReturnType<typeof setTimeout> | null = null

function methodState(code: string) {
  return methodStates.value[code] || { status: 'NONE', checking: false }
}

onMounted(async () => {
  try {
    const res = await apiUser.verificationMethods()
    verificationMethods.value = Array.isArray(res.data) ? res.data : []
  }
  catch {
    verificationMethods.value = []
  }
  void refreshMethodStates()
})

onBeforeUnmount(() => {
  loadSequence += 1
  statusSequence += 1
  if (emailCheckTimer) {
    clearTimeout(emailCheckTimer)
    emailCheckTimer = null
  }
  void releaseVerifyModule()
})

async function releaseVerifyModule() {
  const lease = verifyLease.value
  verifyLease.value = null
  verifyComponent.value = null
  await lease?.release()
}

function resolveVerifyComponent(module: Record<string, any>, methodCode: string): Component | null {
  const names = [
    `${methodCode}/RegisterForm`,
    'RegisterForm',
    `${methodCode}/Public`,
    'Public',
  ]
  for (const name of names) {
    if (module.routes?.[name]) {
      return module.routes[name]
    }
    if (module[name]) {
      return module[name]
    }
  }
  if (module.default && typeof module.default === 'object' && 'routes' in module.default) {
    return resolveVerifyComponent(module.default, methodCode)
  }
  return null
}

async function openVerification(method: VerificationMethod) {
  const email = String(form.values.email || '').trim()
  activeMethod.value = method
  verifyEmail.value = email
  verifyError.value = ''
  verifyOpen.value = true
  const sequence = ++loadSequence
  verifyLoading.value = true
  await releaseVerifyModule()
  try {
    const lease = await acquirePluginRemoteModuleByCode(method.code)
    if (sequence !== loadSequence) {
      await lease.release()
      return
    }
    const component = resolveVerifyComponent(lease.module, method.code)
    if (!component) {
      await lease.release()
      throw new Error('插件未导出注册核验表单')
    }
    verifyLease.value = lease
    verifyComponent.value = component
  }
  catch (error: any) {
    if (sequence === loadSequence) {
      verifyError.value = error?.message || '加载核验表单失败'
    }
  }
  finally {
    if (sequence === loadSequence) {
      verifyLoading.value = false
    }
  }
}

function closeVerification() {
  verifyOpen.value = false
  loadSequence += 1
  void releaseVerifyModule()
}

function onVerifyDone(payload?: { email?: string, status?: string }) {
  const email = String(payload?.email || verifyEmail.value || '').trim()
  if (email) {
    form.setFieldValue('email', email)
    verifyEmail.value = email
  }
  const code = activeMethod.value?.code
  if (code && (payload?.status === 'PASSED' || payload?.status === 'PENDING')) {
    methodStates.value = {
      ...methodStates.value,
      [code]: { status: payload.status, checking: false },
    }
  }
  if (payload?.status === 'PASSED') {
    useFaToast().success('身份核验已通过，可以继续注册')
    closeVerification()
  }
}

async function refreshMethodStates() {
  const email = String(form.values.email || '').trim()
  const methods = verificationMethods.value
  if (!methods.length) {
    return
  }
  const sequence = ++statusSequence
  const next: Record<string, { status: 'NONE' | 'PASSED' | 'PENDING', checking: boolean }> = {}
  for (const method of methods) {
    next[method.code] = { status: 'NONE', checking: Boolean(email) }
  }
  methodStates.value = next
  if (!email || !email.includes('@')) {
    const idle: Record<string, { status: 'NONE' | 'PASSED' | 'PENDING', checking: boolean }> = {}
    for (const method of methods) {
      idle[method.code] = { status: 'NONE', checking: false }
    }
    methodStates.value = idle
    return
  }
  await Promise.all(methods.map(async (method) => {
    try {
      const res = await apiPlugin.request<{ passed?: boolean, eduDomain?: boolean, pending?: boolean, records?: Array<{ status?: string }> }>(
        method.code,
        `/public/status?email=${encodeURIComponent(email)}`,
      )
      if (sequence !== statusSequence) {
        return
      }
      const records = Array.isArray(res.data?.records) ? res.data.records : []
      const passed = Boolean(res.data?.passed) || Boolean(res.data?.eduDomain) || records.some(item => item.status === 'PASSED')
      const pending = !passed && (Boolean(res.data?.pending) || records.some(item => item.status === 'PENDING'))
      methodStates.value = {
        ...methodStates.value,
        [method.code]: { status: passed ? 'PASSED' : pending ? 'PENDING' : 'NONE', checking: false },
      }
    }
    catch {
      if (sequence !== statusSequence) {
        return
      }
      methodStates.value = {
        ...methodStates.value,
        [method.code]: { status: 'NONE', checking: false },
      }
    }
  }))
}

function fallbackToPage() {
  const method = activeMethod.value
  const email = String(form.values.email || verifyEmail.value || '').trim()
  const query = email ? { email } : undefined
  closeVerification()
  if (!method) {
    return
  }
  router.push({ path: `/${method.code}`, query })
}

const form = useForm({
  validationSchema: toTypedSchema(
    z.object({
      account: z.string().min(1, '请输入用户名').min(3, '用户名至少3位'),
      email: z.string().min(1, '请输入邮箱').email('邮箱格式不正确'),
      nickname: z.string().optional(),
      password: z.string().min(1, '请输入密码').min(6, '密码长度为6到18位').max(18, '密码长度为6到18位'),
      checkPassword: z.string().min(1, '请再次输入密码'),
    }).refine(data => data.password === data.checkPassword, {
      message: '两次输入的密码不一致',
      path: ['checkPassword'],
    }),
  ),
  initialValues: {
    account: props.account ?? '',
    email: typeof route.query.email === 'string' ? route.query.email : '',
    nickname: '',
    password: '',
    checkPassword: '',
  },
})

watch(() => form.values.email, () => {
  if (emailCheckTimer) {
    clearTimeout(emailCheckTimer)
  }
  emailCheckTimer = setTimeout(() => {
    emailCheckTimer = null
    void refreshMethodStates()
  }, 400)
})

const onSubmit = form.handleSubmit((values) => {
  loading.value = true
  appAccountStore.register({
    username: values.account,
    email: values.email,
    nickname: values.nickname,
    password: values.password,
    bindingToken: props.bindingToken,
  }).then((data) => {
    const verified = data?.emailVerified === true
    useFaToast().success('注册成功', {
      description: verified
        ? (props.bindingToken ? '身份核验已通过，注册后将绑定该第三方账号' : '身份核验已通过，可直接登录')
        : (props.bindingToken ? '验证邮件已发送，验证后将绑定该第三方账号' : '验证邮件已发送，请前往邮箱查收并验证后再登录'),
    })
    emits('onRegister', values.account)
  }).finally(() => {
    loading.value = false
  })
})
</script>

<template>
  <div class="p-12 flex-col-stretch-center min-h-500px w-full">
    <form @submit="onSubmit">
      <div class="mb-8 space-y-2">
        <h3 class="text-4xl font-bold">
          探索从这里开始 🚀
        </h3>
        <p class="text-sm text-muted-foreground lg:text-base">
          {{ props.bindingToken ? '注册后将绑定该第三方账号' : '注册后即可开启旅程' }}
        </p>
      </div>
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
      <FormField v-slot="{ componentField, errors }" name="email">
        <FormItem class="pb-6 relative space-y-0">
          <FormControl>
            <FaInput type="text" placeholder="邮箱" class="w-full" :class="{ 'border-destructive': errors.length }" v-bind="componentField">
              <template #start>
                <FaIcon name="i-lucide:mail" />
              </template>
            </FaInput>
          </FormControl>
          <Transition enter-active-class="transition-opacity" enter-from-class="opacity-0" leave-active-class="transition-opacity" leave-to-class="opacity-0">
            <FormMessage class="text-xs m-0 bottom-1 absolute" />
          </Transition>
        </FormItem>
      </FormField>
      <FormField v-slot="{ componentField, errors }" name="nickname">
        <FormItem class="pb-6 relative space-y-0">
          <FormControl>
            <FaInput type="text" placeholder="昵称（选填）" class="w-full" :class="{ 'border-destructive': errors.length }" v-bind="componentField">
              <template #start>
                <FaIcon name="i-lucide:smile" />
              </template>
            </FaInput>
          </FormControl>
          <Transition enter-active-class="transition-opacity" enter-from-class="opacity-0" leave-active-class="transition-opacity" leave-to-class="opacity-0">
            <FormMessage class="text-xs m-0 bottom-1 absolute" />
          </Transition>
        </FormItem>
      </FormField>
      <FormField v-slot="{ componentField, value, errors }" name="password">
        <FormItem class="pb-6 relative space-y-0">
          <FormControl>
            <FaInput type="password" placeholder="密码" class="w-full" :class="{ 'border-destructive': errors.length }" v-bind="componentField">
              <template #start>
                <FaIcon name="i-lucide:lock" />
              </template>
            </FaInput>
          </FormControl>
          <FormDescription class="m-0">
            <FaPasswordStrength :password="value" />
          </FormDescription>
          <Transition enter-active-class="transition-opacity" enter-from-class="opacity-0" leave-active-class="transition-opacity" leave-to-class="opacity-0">
            <FormMessage class="text-xs m-0 bottom-1 absolute" />
          </Transition>
        </FormItem>
      </FormField>
      <FormField v-slot="{ componentField, errors }" name="checkPassword">
        <FormItem class="pb-6 relative space-y-0">
          <FormControl>
            <FaInput type="password" placeholder="确认密码" class="w-full" :class="{ 'border-destructive': errors.length }" v-bind="componentField">
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
      <div v-if="verificationMethods.length" class="mb-4 rounded-md border p-3 text-sm">
        <p class="mb-2 text-secondary-foreground">
          注册前请先完成身份核验。人工审核通过前无法注册。
        </p>
        <div class="flex flex-col gap-2">
          <div v-for="method in verificationMethods" :key="method.code" class="flex items-start justify-between gap-3">
            <div class="min-w-0">
              <div class="font-medium">
                {{ method.displayName }}
              </div>
              <div v-if="method.description" class="text-xs text-secondary-foreground/80">
                {{ method.description }}
              </div>
            </div>
            <FaButton
              v-if="methodState(method.code).status === 'PASSED'"
              type="button"
              variant="outline"
              size="sm"
              disabled
            >
              已核验
            </FaButton>
            <FaButton
              v-else-if="methodState(method.code).status === 'PENDING'"
              type="button"
              variant="outline"
              size="sm"
              :loading="methodState(method.code).checking"
              @click="openVerification(method)"
            >
              审核中
            </FaButton>
            <FaButton
              v-else
              type="button"
              variant="outline"
              size="sm"
              :loading="methodState(method.code).checking"
              @click="openVerification(method)"
            >
              去核验
            </FaButton>
          </div>
        </div>
      </div>
      <FaButton :loading="loading" size="lg" class="mt-4 w-full" type="submit">
        注册
      </FaButton>
      <div class="text-sm mt-4 flex-center gap-2">
        <span class="text-secondary-foreground op-50">已经有帐号?</span>
        <FaButton variant="link" class="p-0 h-auto" @click="emits('onLogin', form.values.account)">
          去登录
        </FaButton>
      </div>
    </form>
    <FaModal
      v-model="verifyOpen"
      :title="activeMethod?.displayName || '身份核验'"
      :footer="false"
      class="sm:max-w-3xl"
      @close="closeVerification"
    >
      <div class="register-verify-modal">
        <p v-if="verifyLoading" class="text-sm text-muted-foreground">正在加载核验表单…</p>
        <p v-else-if="verifyError" class="text-sm text-destructive">
          {{ verifyError }}
          <FaButton type="button" variant="link" class="p-0 h-auto" @click="fallbackToPage">
            打开独立页面
          </FaButton>
        </p>
        <component
          :is="verifyComponent"
          v-else-if="verifyComponent"
          :sdk="verifySdk"
          :initial-email="verifyEmail"
          @done="onVerifyDone"
          @update:email="(value: unknown) => verifyEmail = String(value || '')"
        />
      </div>
    </FaModal>
  </div>
</template>

<style scoped>
.register-verify-modal {
  display: grid;
  gap: 12px;
  max-height: min(72vh, 720px);
  overflow: auto;
}
</style>
