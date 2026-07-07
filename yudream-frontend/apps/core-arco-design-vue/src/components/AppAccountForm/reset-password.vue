<script setup lang="ts">
import { toTypedSchema } from '@vee-validate/zod'
import { useForm } from 'vee-validate'
import * as z from 'zod'
import apiUser from '@/api/modules/user'
import { FormControl, FormDescription, FormField, FormItem, FormMessage } from '@/ui/shadcn/ui/form'

defineOptions({
  name: 'ResetPasswordForm',
})

const props = defineProps<{
  account?: string
  token?: string
}>()

const emits = defineEmits<{
  onLogin: [account?: string]
  onResetPassword: [account?: string]
}>()

const toast = useFaToast()
const loading = ref(false)
const isResetMode = computed(() => !!props.token?.trim())

const passwordSchema = z.string()
  .min(1, '请输入新密码')
  .min(6, '密码长度为 6 到 18 位')
  .max(18, '密码长度为 6 到 18 位')
  .refine((value) => {
    const categoryCount = [
      /[a-z]/.test(value),
      /[A-Z]/.test(value),
      /\d/.test(value),
    ].filter(Boolean).length
    return categoryCount >= 2
  }, '密码需包含小写字母、大写字母、数字中的至少两类')

const sendForm = useForm({
  validationSchema: toTypedSchema(z.object({
    account: z.string().min(1, '请输入用户名或邮箱'),
  })),
  initialValues: {
    account: props.account ?? '',
  },
})

const resetForm = useForm({
  validationSchema: toTypedSchema(
    z.object({
      password: passwordSchema,
      checkPassword: z.string().min(1, '请再次输入新密码'),
    }).refine(data => data.password === data.checkPassword, {
      message: '两次输入的密码不一致',
      path: ['checkPassword'],
    }),
  ),
  initialValues: {
    password: '',
    checkPassword: '',
  },
})

const onSendSubmit = sendForm.handleSubmit(async (values) => {
  loading.value = true
  try {
    await apiUser.sendPasswordResetEmail({ account: values.account })
    toast.success('重置邮件已发送', {
      description: '如果账户存在，请前往邮箱查收密码重置链接',
    })
    emits('onResetPassword', values.account)
  }
  finally {
    loading.value = false
  }
})

const onResetSubmit = resetForm.handleSubmit(async (values) => {
  loading.value = true
  try {
    await apiUser.resetPassword({
      token: props.token?.trim() ?? '',
      password: values.password,
    })
    toast.success('密码已重置', {
      description: '请使用新密码登录',
    })
    emits('onLogin')
  }
  finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="p-12 flex-col-stretch-center min-h-500px w-full">
    <form v-if="!isResetMode" @submit="onSendSubmit">
      <div class="mb-8 space-y-2">
        <h3 class="text-4xl font-bold">
          找回密码
        </h3>
        <p class="text-sm text-muted-foreground lg:text-base">
          输入用户名或邮箱，系统会发送密码重置链接
        </p>
      </div>
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
      <FaButton :loading="loading" size="lg" class="mt-4 w-full" type="submit">
        发送重置邮件
      </FaButton>
      <div class="text-sm mt-4 flex-center gap-2">
        <FaButton variant="link" class="p-0 h-auto" type="button" @click="emits('onLogin', sendForm.values.account)">
          去登录
        </FaButton>
      </div>
    </form>

    <form v-else @submit="onResetSubmit">
      <div class="mb-8 space-y-2">
        <h3 class="text-4xl font-bold">
          设置新密码
        </h3>
        <p class="text-sm text-muted-foreground lg:text-base">
          新密码保存后即可返回登录
        </p>
      </div>
      <FormField v-slot="{ componentField, value, errors }" name="password">
        <FormItem class="pb-6 relative space-y-0">
          <FormControl>
            <FaInput type="password" placeholder="新密码" class="w-full" :class="{ 'border-destructive': errors.length }" v-bind="componentField">
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
            <FaInput type="password" placeholder="确认新密码" class="w-full" :class="{ 'border-destructive': errors.length }" v-bind="componentField">
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
      <FaButton :loading="loading" size="lg" class="mt-4 w-full" type="submit">
        确认重置
      </FaButton>
      <div class="text-sm mt-4 flex-center gap-2">
        <FaButton variant="link" class="p-0 h-auto" type="button" @click="emits('onLogin')">
          去登录
        </FaButton>
      </div>
    </form>
  </div>
</template>
