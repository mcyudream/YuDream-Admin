<template>
  <section class="wallet-page">
    <section class="wallet-hero">
      <div>
        <span>我的钱包</span>
        <h2>{{ model.accountName }}</h2>
        <p>查看各币种说明及对应余额，支持使用用户 ID、用户名或邮箱转账。</p>
      </div>
      <div class="wallet-actions">
        <a v-if="model.hasRecharge" class="wallet-nav-button" href="/platform/plugins/yudream-wallet/recharge">
          <FaIcon name="i-ri:bank-card-line" />
          充值
        </a>
        <FaButton :loading="model.loading" variant="outline" @click="model.load">
          <FaIcon name="i-ri:refresh-line" />
          刷新
        </FaButton>
      </div>
    </section>

    <BalanceGrid :model="model" />

    <div class="wallet-layout">
      <WalletPanel v-if="model.transferableAssets.length" title="余额转账" eyebrow="Transfer">
        <form class="wallet-form" @submit.prevent="model.submitTransfer">
          <label>
            <span>转入用户</span>
            <input v-model="model.transferForm.toAccount" placeholder="用户 ID / 用户名 / 邮箱">
          </label>
          <label>
            <span>资产</span>
            <select v-model="model.transferForm.assetCode">
              <option v-for="asset in model.transferableAssets" :key="asset.code" :value="asset.code">
                {{ asset.name }}（{{ asset.code }}）
              </option>
            </select>
          </label>
          <label>
            <span>金额</span>
            <input v-model="model.transferForm.amount" inputmode="decimal" placeholder="0">
          </label>
          <label>
            <span>备注</span>
            <textarea v-model="model.transferForm.remark" rows="3" placeholder="可选" />
          </label>
          <div class="wallet-actions">
            <FaButton :loading="model.saving" type="submit">
              <FaIcon name="i-ri:exchange-dollar-line" />
              提交转账
            </FaButton>
          </div>
        </form>
      </WalletPanel>

      <WalletPanel title="币种说明" eyebrow="Assets">
        <div class="wallet-asset-list">
          <div v-for="asset in model.assets" :key="asset.code" class="wallet-asset-row">
            <div>
              <strong>{{ asset.name }}</strong>
              <span>
                {{ asset.code }} · 精度 {{ asset.scale }} ·
                {{ asset.transferEnabled ? '允许转账' : '不可转账' }}
              </span>
            </div>
            <strong class="wallet-asset-balance">
              {{ model.assetSymbol(asset.code) }}{{ model.formatAmount(model.balanceOf(asset.code), asset.code) }}
            </strong>
          </div>
        </div>
      </WalletPanel>
    </div>

    <WalletPanel v-if="model.canManage" title="最近流水" eyebrow="Transactions">
      <TransactionList :model="model" :items="model.recentTransactions" />
    </WalletPanel>
  </section>
</template>

<script setup lang="ts">
import { FaButton, FaIcon } from '@fantastic-admin/components'
import BalanceGrid from '../components/BalanceGrid.vue'
import TransactionList from '../components/TransactionList.vue'
import WalletPanel from '../components/WalletPanel.vue'
import type { WalletPluginModel } from '../composables/useWalletPlugin'

defineProps<{
  model: WalletPluginModel
}>()
</script>
