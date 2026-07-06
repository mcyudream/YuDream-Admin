<template>
  <div class="wallet-table-wrap">
    <table class="wallet-table">
      <thead>
        <tr>
          <th>类型</th>
          <th>来源</th>
          <th>资产</th>
          <th>金额</th>
          <th>用户</th>
          <th>业务单号</th>
          <th>时间</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="item in items" :key="item.id">
          <td>
            <span class="wallet-tag">{{ model.transactionLabel(item.type) }}</span>
          </td>
          <td>{{ model.sourceLabel(item.source) }}</td>
          <td>{{ model.assetName(item.assetCode) }}</td>
          <td class="amount-cell">
            {{ model.assetSymbol(item.assetCode) }}{{ model.formatAmount(item.amount, item.assetCode) }}
          </td>
          <td class="wrap-cell">
            <template v-if="item.type === 'TRANSFER'">
              {{ model.userLabel(item.fromUser, item.fromUserId) }} → {{ model.userLabel(item.toUser, item.toUserId) }}
            </template>
            <template v-else>
              {{ model.userLabel(item.toUser || item.fromUser, item.toUserId || item.fromUserId) }}
            </template>
          </td>
          <td class="wrap-cell">
            {{ item.businessNo || '-' }}
          </td>
          <td>{{ model.formatTime(item.createdAt) }}</td>
        </tr>
        <tr v-if="!items.length">
          <td colspan="7">
            <div class="wallet-empty compact">暂无流水</div>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<script setup lang="ts">
import type { WalletPluginModel } from '../composables/useWalletPlugin'
import type { WalletTransaction } from '../types'

defineProps<{
  model: WalletPluginModel
  items: WalletTransaction[]
}>()
</script>
