<script setup lang="ts">
import type { RouteLocationNormalizedLoaded } from 'vue-router'
import type { YuDreamPluginSdk } from '@yudream/plugin-sdk'
import { computed, onMounted } from 'vue'
import { useWalletPlugin } from './composables/useWalletPlugin'
import BalancesPage from './pages/BalancesPage.vue'
import HomePage from './pages/HomePage.vue'
import RechargePage from './pages/RechargePage.vue'
import SettingsPage from './pages/SettingsPage.vue'
import TransactionsPage from './pages/TransactionsPage.vue'

const props = defineProps<{
  sdk: YuDreamPluginSdk
  route?: RouteLocationNormalizedLoaded
}>()

const model = useWalletPlugin(props.sdk)
const page = computed(() => {
  const plugin = props.route?.meta?.plugin as { component?: string } | undefined
  if (plugin?.component === 'yudream-wallet/Settings' || plugin?.component === 'yudream-wallet/System') {
    return SettingsPage
  }
  if (plugin?.component === 'yudream-wallet/Balances') {
    return BalancesPage
  }
  if (plugin?.component === 'yudream-wallet/Recharge') {
    return RechargePage
  }
  if (plugin?.component === 'yudream-wallet/Transactions') {
    return TransactionsPage
  }
  return HomePage
})

onMounted(model.load)
</script>

<template>
  <div class="wallet-plugin">
    <component :is="page" :model="model" />
  </div>
</template>
