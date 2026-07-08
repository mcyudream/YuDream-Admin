<template>
  <section class="wallet-page">
    <section class="wallet-hero">
      <div>
        <span>余额管理</span>
        <h2>按币种查看用户余额</h2>
        <p>选择一个币种后查看用户余额，并可进行管理员入账或扣账。</p>
      </div>
      <FaButton :loading="model.loading" variant="outline" @click="model.load">
        <FaIcon name="i-ri:refresh-line" />
        刷新
      </FaButton>
    </section>

    <WalletPanel title="筛选" eyebrow="Filter">
      <div class="wallet-filter-bar">
        <label>
          <span>统计币种</span>
          <select v-model="model.selectedBalanceAssetCode" @change="model.applyBalanceAsset(model.selectedBalanceAssetCode)">
            <option v-for="asset in model.assets" :key="asset.code" :value="asset.code">
              {{ asset.name }}（{{ asset.code }}）
            </option>
          </select>
        </label>
      </div>
    </WalletPanel>

    <div class="wallet-layout">
      <WalletPanel title="余额表" eyebrow="Balances">
        <div class="wallet-table-wrap">
          <table class="wallet-table">
            <thead>
              <tr>
                <th>用户</th>
                <th>邮箱</th>
                <th>币种</th>
                <th>余额</th>
                <th>历史总额</th>
                <th>更新时间</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in model.adminBalances" :key="`${item.userId}:${item.assetCode}`">
                <td class="wrap-cell">{{ model.userLabel(item.user, item.userId) }}</td>
                <td class="wrap-cell">{{ item.user?.email || '-' }}</td>
                <td>{{ model.assetName(item.assetCode) }}</td>
                <td class="amount-cell">{{ model.assetSymbol(item.assetCode) }}{{ model.formatAmount(item.balance, item.assetCode) }}</td>
                <td class="amount-cell muted">{{ model.assetSymbol(item.assetCode) }}{{ model.formatAmount(item.historicalTotalAmount, item.assetCode) }}</td>
                <td>{{ model.formatTime(item.updatedAt) }}</td>
              </tr>
              <tr v-if="!model.adminBalances.length">
                <td colspan="6">
                  <div class="wallet-empty compact">暂无余额记录</div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <div class="wallet-pagination">
          <FaButton size="sm" variant="outline" :disabled="model.balancePager.page <= 1" @click="model.prevBalancePage">
            <FaIcon name="i-ri:arrow-left-s-line" />
            上一页
          </FaButton>
          <span>第 {{ model.balancePager.page }} 页</span>
          <FaButton size="sm" variant="outline" :disabled="!model.balancePager.hasNext" @click="model.nextBalancePage">
            下一页
            <FaIcon name="i-ri:arrow-right-s-line" />
          </FaButton>
        </div>
      </WalletPanel>

      <WalletPanel title="余额处理" eyebrow="Action">
        <form class="wallet-form" @submit.prevent>
          <label>
            <span>用户 ID</span>
            <input v-model="model.changeForm.userId" placeholder="系统用户 ID">
          </label>
          <label>
            <span>币种</span>
            <select v-model="model.changeForm.assetCode">
              <option v-for="asset in model.assets" :key="asset.code" :value="asset.code">
                {{ asset.name }}（{{ asset.code }}）
              </option>
            </select>
          </label>
          <label>
            <span>金额</span>
            <input v-model="model.changeForm.amount" inputmode="decimal" placeholder="0">
          </label>
          <label>
            <span>备注</span>
            <textarea v-model="model.changeForm.remark" rows="3" placeholder="可选" />
          </label>
          <div class="wallet-actions">
            <FaButton :loading="model.saving" type="button" @click="model.changeBalance('credit')">
              <FaIcon name="i-ri:add-circle-line" />
              入账
            </FaButton>
            <FaButton :loading="model.saving" variant="outline" type="button" @click="model.changeBalance('debit')">
              <FaIcon name="i-ri:subtract-line" />
              扣账
            </FaButton>
          </div>
        </form>
      </WalletPanel>
    </div>
  </section>
</template>

<script setup lang="ts">
import { FaButton, FaIcon } from '@fantastic-admin/components'
import WalletPanel from '../components/WalletPanel.vue'
import type { WalletPluginModel } from '../composables/useWalletPlugin'

defineProps<{
  model: WalletPluginModel
}>()
</script>
