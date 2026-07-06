<template>
  <div class="wallet-balance-grid">
    <button
      v-for="balance in model.sortedBalances"
      :key="balance.assetCode"
      class="wallet-balance-card"
      :class="{ active: model.selectedAssetCode === balance.assetCode }"
      type="button"
      @click="model.selectedAssetCode = balance.assetCode"
    >
      <span class="wallet-card-title">{{ model.assetName(balance.assetCode) }}</span>
      <strong>
        <small>{{ model.assetSymbol(balance.assetCode) }}</small>
        {{ model.formatAmount(balance.balance, balance.assetCode) }}
      </strong>
      <span class="wallet-card-meta">
        {{ balance.assetCode }} · {{ model.formatTime(balance.updatedAt) }}
      </span>
    </button>

    <div v-if="!model.sortedBalances.length" class="wallet-empty">
      暂无余额数据
    </div>
  </div>
</template>

<script setup lang="ts">
import type { WalletPluginModel } from '../composables/useWalletPlugin'

defineProps<{
  model: WalletPluginModel
}>()
</script>
