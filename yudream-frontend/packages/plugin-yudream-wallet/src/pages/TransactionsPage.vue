<template>
  <section class="wallet-page">
    <section class="wallet-hero">
      <div>
        <span>系统流水</span>
        <h2>钱包账务记录</h2>
        <p>按币种、来源、进出账类型和用户筛选系统流水。</p>
      </div>
      <FaButton :loading="model.loading" variant="outline" @click="model.load">
        <FaIcon name="i-ri:refresh-line" />
        刷新
      </FaButton>
    </section>

    <WalletPanel title="筛选" eyebrow="Filter">
      <div class="wallet-filter-bar">
        <label>
          <span>币种</span>
          <select v-model="model.transactionFilters.assetCode">
            <option value="">全部币种</option>
            <option v-for="asset in model.assets" :key="asset.code" :value="asset.code">
              {{ asset.name }}（{{ asset.code }}）
            </option>
          </select>
        </label>
        <label>
          <span>来源</span>
          <select v-model="model.transactionFilters.source">
            <option value="">全部来源</option>
            <option value="ADMIN">管理员</option>
            <option value="TRANSFER">用户转账</option>
            <option value="ALIPAY">支付宝</option>
          </select>
        </label>
        <label>
          <span>类型</span>
          <select v-model="model.transactionFilters.type">
            <option value="">全部类型</option>
            <option value="CREDIT">入账</option>
            <option value="DEBIT">扣账</option>
            <option value="TRANSFER">转账</option>
          </select>
        </label>
        <label>
          <span>用户</span>
          <input v-model="model.transactionFilters.user" placeholder="ID / 用户名 / 邮箱">
        </label>
        <div class="wallet-actions">
          <FaButton type="button" @click="model.loadTransactions">
            <FaIcon name="i-ri:filter-3-line" />
            筛选
          </FaButton>
          <FaButton variant="outline" type="button" @click="model.resetTransactionFilters">
            重置
          </FaButton>
        </div>
      </div>
    </WalletPanel>

    <WalletPanel title="流水列表" eyebrow="Transactions">
      <TransactionList :model="model" :items="model.transactions" />
    </WalletPanel>
  </section>
</template>

<script setup lang="ts">
import { FaButton, FaIcon } from '@fantastic-admin/components'
import TransactionList from '../components/TransactionList.vue'
import WalletPanel from '../components/WalletPanel.vue'
import type { WalletPluginModel } from '../composables/useWalletPlugin'

defineProps<{
  model: WalletPluginModel
}>()
</script>
