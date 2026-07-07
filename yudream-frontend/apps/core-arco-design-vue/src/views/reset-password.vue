<script setup lang="ts">
import ResetPassword from '@/components/AppAccountForm/reset-password.vue'

defineOptions({
  name: 'ResetPassword',
})

const route = useRoute()
const router = useRouter()

const token = computed(() => route.query.token?.toString() ?? '')

function goLogin() {
  router.push({ name: 'login' })
}
</script>

<template>
  <div class="h-screen w-screen flex-col-center bg-muted/30 p-4">
    <div class="w-full max-w-500px rounded-lg bg-background shadow-md">
      <ResetPassword
        :token="token"
        @on-login="goLogin"
        @on-reset-password="goLogin"
      />
    </div>
  </div>
</template>
