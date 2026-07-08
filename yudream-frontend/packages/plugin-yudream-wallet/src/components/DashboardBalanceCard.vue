<script setup lang="ts">
import type { YuDreamPluginSdk } from '@yudream/plugin-sdk'
import type { WalletAsset, WalletBalance } from '../types'
import { computed, onMounted, ref } from 'vue'
import { FaButton, FaIcon } from '@fantastic-admin/components'
import { createWalletApi } from '../api/wallet-api'

interface DashboardCardLike {
  actionPath?: string
}

const props = defineProps<{
  sdk: YuDreamPluginSdk
  card?: DashboardCardLike
  onOpen?: (card?: DashboardCardLike) => void
}>()

const api = createWalletApi(props.sdk)
const loading = ref(false)
const error = ref('')
const assets = ref<WalletAsset[]>([])
const balances = ref<WalletBalance[]>([])

const primaryAsset = computed(() => assets.value.find(asset => asset.code === 'CNY') || assets.value[0])
const primaryBalance = computed(() => balanceOf(primaryAsset.value?.code))
const otherBalances = computed(() => balances.value
  .filter(balance => balance.assetCode !== primaryAsset.value?.code)
  .slice(0, 3))

onMounted(load)

async function load() {
  loading.value = true
  error.value = ''
  try {
    const [assetList, balanceList] = await Promise.all([
      api.assets(),
      api.balances(),
    ])
    assets.value = assetList
    balances.value = balanceList
  }
  catch (cause: any) {
    error.value = cause?.message || '钱包余额加载失败'
  }
  finally {
    loading.value = false
  }
}

function assetByCode(assetCode?: string) {
  return assets.value.find(asset => asset.code === assetCode)
}

function balanceOf(assetCode?: string) {
  if (!assetCode) {
    return '0'
  }
  return balances.value.find(balance => balance.assetCode === assetCode)?.balance ?? '0'
}

function formatAmount(value?: number | string, assetCode?: string) {
  const asset = assetByCode(assetCode)
  const scale = asset?.scale ?? 2
  const amount = Number(value ?? 0)
  if (!Number.isFinite(amount)) {
    return String(value ?? '0')
  }
  return amount.toLocaleString('zh-CN', {
    minimumFractionDigits: scale,
    maximumFractionDigits: scale,
  })
}

function openCard() {
  props.onOpen?.(props.card)
}
</script>

<template>
  <div class="dashboard-card__content wallet-dashboard-card">
    <div v-if="loading" class="wallet-dashboard-card__state">
      <FaIcon name="i-ri:loader-4-line" class="animate-spin" />
      正在读取余额
    </div>
    <div v-else-if="error" class="wallet-dashboard-card__state error">
      <FaIcon name="i-ri:error-warning-line" />
      {{ error }}
    </div>
    <template v-else>
      <div class="wallet-dashboard-card__body">
        <div class="wallet-dashboard-card__main">
          <span>{{ primaryAsset?.name || '钱包余额' }}</span>
          <strong>
            <small>{{ primaryAsset?.symbol }}</small>
            {{ formatAmount(primaryBalance, primaryAsset?.code) }}
          </strong>
        </div>

        <div class="wallet-dashboard-card__assets">
          <div v-for="balance in otherBalances" :key="balance.assetCode">
            <span>{{ assetByCode(balance.assetCode)?.name || balance.assetCode }}</span>
            <strong>{{ assetByCode(balance.assetCode)?.symbol }}{{ formatAmount(balance.balance, balance.assetCode) }}</strong>
          </div>
          <div v-if="!otherBalances.length">
            <span>其他资产</span>
            <strong>暂无余额</strong>
          </div>
        </div>
      </div>

      <div class="wallet-dashboard-card__actions">
        <FaButton v-if="card?.actionPath" size="sm" @click="openCard">
          <FaIcon name="i-ri:wallet-3-line" />
          打开钱包
        </FaButton>
      </div>
    </template>
  </div>
</template>
