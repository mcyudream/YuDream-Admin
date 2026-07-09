<template>
  <section class="wallet-page">
    <section class="wallet-hero">
      <div>
        <span>系统流水</span>
        <h2>钱包账务记录</h2>
        <p>按币种、来源、进出账类型和用户筛选系统流水。</p>
      </div>
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
            <option value="MINECRAFT_SEASON">周目继承</option>
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
        <div class="wallet-actions wallet-filter-actions">
          <FaButton variant="outline" type="button" @click="model.resetTransactionFilters">
            重置
          </FaButton>
          <FaButton type="button" @click="model.applyTransactionFilters">
            <FaIcon name="i-ri:filter-3-line" />
            筛选
          </FaButton>
        </div>
      </div>
    </WalletPanel>

    <WalletPanel title="流水列表" eyebrow="Transactions">
      <TransactionList :model="model" :items="model.transactions" />
      <div class="wallet-pagination">
        <FaButton size="sm" variant="outline" :disabled="model.transactionPager.page <= 1" @click="model.prevTransactionPage">
          <FaIcon name="i-ri:arrow-left-s-line" />
          上一页
        </FaButton>
        <span>第 {{ model.transactionPager.page }} 页</span>
        <FaButton size="sm" variant="outline" :disabled="!model.transactionPager.hasNext" @click="model.nextTransactionPage">
          下一页
          <FaIcon name="i-ri:arrow-right-s-line" />
        </FaButton>
      </div>
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
