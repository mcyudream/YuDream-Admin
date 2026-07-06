<script setup lang="ts">
import { toTypedSchema } from '@vee-validate/zod'
import { useForm } from 'vee-validate'
import * as z from 'zod'
import { FormControl, FormField, FormItem, FormMessage } from '@/ui/shadcn/ui/form'
import apiSetup from '@/api/modules/setup'

const router = useRouter()

const loading = ref(false)

const form = useForm({
  validationSchema: toTypedSchema(
    z.object({
      siteName: z.string().min(1, '请输入站点名称'),
      adminUsername: z.string().min(1, '请输入管理员用户名').min(3, '管理员用户名至少3位'),
      adminEmail: z.string().min(1, '请输入管理员邮箱').email('邮箱格式不正确'),
      adminNickname: z.string().optional(),
      adminPassword: z.string().min(1, '请输入管理员密码').min(6, '密码长度为6到18位').max(18, '密码长度为6到18位'),
      adminConfirmPassword: z.string().min(1, '请再次输入密码'),
    }).refine(data => data.adminPassword === data.adminConfirmPassword, {
      message: '两次输入的密码不一致',
      path: ['adminConfirmPassword'],
    }),
  ),
  initialValues: {
    siteName: '',
    adminUsername: '',
    adminEmail: '',
    adminNickname: '',
    adminPassword: '',
    adminConfirmPassword: '',
  },
})

const onSubmit = form.handleSubmit((values) => {
  loading.value = true
  apiSetup.init({
    siteName: values.siteName,
    adminUsername: values.adminUsername,
    adminEmail: values.adminEmail,
    adminNickname: values.adminNickname,
    adminPassword: values.adminPassword,
    adminConfirmPassword: values.adminConfirmPassword,
  }).then(() => {
    localStorage.setItem('setupCompleted', 'true')
    router.push({ name: 'login' })
  }).finally(() => {
    loading.value = false
  })
})
</script>

<template>
  <div class="bg-banner" />
  <div class="setup-box">
    <div class="setup-banner">
      <img src="@/assets/images/logo.png" class="rounded h-8 inset-s-4 inset-t-4 absolute">
      <img src="@/assets/images/login-banner.png" class="banner">
    </div>
    <div class="setup-form flex-col-center">
      <div class="w-full p-12 flex-col-stretch-center min-h-500px">
        <div class="mb-8 space-y-2">
          <h3 class="text-4xl font-bold">
            系统初始化 🚀
          </h3>
          <p class="text-sm text-muted-foreground lg:text-base">
            配置站点信息并创建超级管理员账号
          </p>
        </div>
        <form @submit="onSubmit">
          <FormField v-slot="{ componentField, errors }" name="siteName">
            <FormItem class="pb-6 relative space-y-0">
              <FormControl>
                <FaInput type="text" placeholder="站点名称" class="w-full" :class="{ 'border-destructive': errors.length }" v-bind="componentField">
                  <template #start>
                    <FaIcon name="i-lucide:globe" />
                  </template>
                </FaInput>
              </FormControl>
              <Transition enter-active-class="transition-opacity" enter-from-class="opacity-0" leave-active-class="transition-opacity" leave-to-class="opacity-0">
                <FormMessage class="text-xs m-0 bottom-1 absolute" />
              </Transition>
            </FormItem>
          </FormField>
          <FormField v-slot="{ componentField, errors }" name="adminUsername">
            <FormItem class="pb-6 relative space-y-0">
              <FormControl>
                <FaInput type="text" placeholder="管理员用户名" class="w-full" :class="{ 'border-destructive': errors.length }" v-bind="componentField">
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
          <FormField v-slot="{ componentField, errors }" name="adminEmail">
            <FormItem class="pb-6 relative space-y-0">
              <FormControl>
                <FaInput type="text" placeholder="管理员邮箱" class="w-full" :class="{ 'border-destructive': errors.length }" v-bind="componentField">
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
          <FormField v-slot="{ componentField, errors }" name="adminNickname">
            <FormItem class="pb-6 relative space-y-0">
              <FormControl>
                <FaInput type="text" placeholder="管理员昵称（选填）" class="w-full" :class="{ 'border-destructive': errors.length }" v-bind="componentField">
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
          <FormField v-slot="{ componentField, errors }" name="adminPassword">
            <FormItem class="pb-6 relative space-y-0">
              <FormControl>
                <FaInput type="password" placeholder="管理员密码" class="w-full" :class="{ 'border-destructive': errors.length }" v-bind="componentField">
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
          <FormField v-slot="{ componentField, errors }" name="adminConfirmPassword">
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
          <FaButton :loading="loading" size="lg" class="mt-4 w-full" type="submit">
            完成初始化
          </FaButton>
        </form>
      </div>
    </div>
  </div>
  <AppCopyright class="copyright" />
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

.setup-box {
  position: absolute;
  top: 50%;
  left: 50%;
  display: flex;
  overflow: hidden;
  background-color: oklch(var(--background));
  transform: translateX(-50%) translateY(-50%);
  --uno: shadow-md rounded-md;
}

.setup-banner {
  --uno: bg-muted dark:bg-muted/30;
  position: relative;
  width: 450px;
  overflow: hidden;
}

.setup-banner::before {
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

.setup-banner .banner {
  position: absolute;
  top: 50%;
  width: 100%;
  transform: translateY(-50%);
}

.setup-form {
  width: 500px;
}

.copyright {
  position: absolute;
  bottom: 0;
  width: 100%;
  padding: 20px;
  margin: 0;
}

[data-mode="mobile"] {
  .setup-box {
    position: relative;
    flex-direction: column;
    justify-content: start;
    width: 100%;
    transform: none;
    top: auto;
    left: auto;
  }

  .setup-banner {
    width: 100%;
    padding: 20px 0;
  }

  .setup-banner .banner {
    position: relative;
    top: inherit;
    right: inherit;
    display: inherit;
    width: 100%;
    max-width: 375px;
    margin: 0 auto;
    transform: translateY(0);
  }

  .setup-form {
    width: 100%;
  }

  .copyright {
    position: relative;
  }
}
</style>
