<template>
  <section class="wallet-page">
    <section class="wallet-hero">
      <div>
        <span>钱包管理</span>
        <h2>资产、余额和流水</h2>
        <p>维护人民币和积分类资产，处理管理员入账、扣账，并查看钱包流水。</p>
      </div>
      <FaButton :loading="model.loading" variant="outline" @click="model.load">
        <FaIcon name="i-ri:refresh-line" />
        刷新
      </FaButton>
    </section>

    <div class="wallet-layout">
      <WalletPanel title="资产配置" eyebrow="Asset">
        <form class="wallet-form" @submit.prevent="model.saveAsset">
          <div class="wallet-form-grid">
            <label>
              <span>编码</span>
              <input v-model="model.assetForm.code" placeholder="POINT">
            </label>
            <label>
              <span>名称</span>
              <input v-model="model.assetForm.name" placeholder="积分">
            </label>
            <label>
              <span>符号</span>
              <input v-model="model.assetForm.symbol" placeholder="￥ / 积分">
            </label>
            <label>
              <span>精度</span>
              <input v-model.number="model.assetForm.scale" type="number" min="0" max="8">
            </label>
          </div>
          <div class="wallet-form-grid">
            <label>
              <span>最低转账</span>
              <input v-model="model.assetForm.minTransferAmount" inputmode="decimal">
            </label>
            <label>
              <span>资产类型</span>
              <select v-model="model.assetForm.money">
                <option :value="true">货币</option>
                <option :value="false">积分</option>
              </select>
            </label>
            <label>
              <span>状态</span>
              <select v-model="model.assetForm.enabled">
                <option :value="true">启用</option>
                <option :value="false">停用</option>
              </select>
            </label>
          </div>
          <div class="wallet-actions">
            <FaButton :loading="model.saving" type="submit">
              <FaIcon name="i-ri:save-3-line" />
              保存资产
            </FaButton>
            <FaButton variant="outline" type="button" @click="model.resetAssetForm">
              重置
            </FaButton>
          </div>
        </form>

        <div class="wallet-asset-list mt">
          <button v-for="asset in model.assets" :key="asset.code" class="wallet-asset-row clickable" type="button" @click="model.editAsset(asset)">
            <div>
              <strong>{{ asset.name }}</strong>
              <span>{{ asset.code }} · {{ asset.symbol || '-' }} · 精度 {{ asset.scale }}</span>
            </div>
            <span class="wallet-tag" :class="{ muted: !asset.enabled }">
              {{ asset.enabled ? '启用' : '停用' }}
            </span>
          </button>
        </div>
      </WalletPanel>

      <WalletPanel title="余额处理" eyebrow="Balance">
        <form class="wallet-form" @submit.prevent>
          <label>
            <span>用户 ID</span>
            <input v-model="model.changeForm.userId" placeholder="系统用户 ID">
          </label>
          <label>
            <span>资产</span>
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

    <WalletPanel title="流水记录" eyebrow="Transactions">
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
