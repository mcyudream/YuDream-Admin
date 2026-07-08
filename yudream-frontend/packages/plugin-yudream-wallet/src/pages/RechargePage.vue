<template>
  <section class="wallet-page">
    <section class="wallet-hero">
      <div>
        <span>钱包充值</span>
        <h2>选择支付渠道</h2>
        <p>当前仅支持电脑网页支付，实际到账金额按钱包设置中的充值比例计算。</p>
      </div>
    </section>

    <WalletPanel v-if="model.hasRecharge" title="创建充值订单" eyebrow="Recharge">
      <form class="wallet-form" @submit.prevent="submitRecharge">
        <div class="wallet-form-grid">
          <label>
            <span>支付渠道</span>
            <select v-model="model.rechargeForm.channelCode">
              <option v-for="channel in model.paymentChannels" :key="channel.code" :value="channel.code">
                {{ channel.name }}
              </option>
            </select>
          </label>
          <label>
            <span>到账币种</span>
            <select v-model="model.rechargeForm.assetCode">
              <option v-for="asset in model.rechargeableAssets" :key="asset.code" :value="asset.code">
                {{ asset.name }}（{{ asset.code }}）
              </option>
            </select>
          </label>
          <label>
            <span>支付金额</span>
            <input v-model="model.rechargeForm.payAmount" inputmode="decimal" placeholder="0.00">
          </label>
        </div>
        <div class="wallet-recharge-summary">
          <span>预计到账</span>
          <strong>{{ model.assetSymbol(model.rechargeForm.assetCode) }}{{ model.formatAmount(model.estimatedWalletAmount, model.rechargeForm.assetCode) }}</strong>
        </div>
        <label>
          <span>备注</span>
          <textarea v-model="model.rechargeForm.remark" rows="3" placeholder="可选" />
        </label>
        <div class="wallet-actions">
          <FaButton :loading="model.saving" type="submit">
            <FaIcon name="i-ri:bank-card-line" />
            创建订单并支付
          </FaButton>
        </div>
      </form>
    </WalletPanel>

    <WalletPanel v-else title="充值暂不可用" eyebrow="Recharge">
      <div class="wallet-empty">
        暂无可用支付渠道
      </div>
    </WalletPanel>
  </section>
</template>

<script setup lang="ts">
import { FaButton, FaIcon, useFaToast } from '@fantastic-admin/components'
import WalletPanel from '../components/WalletPanel.vue'
import type { WalletPluginModel } from '../composables/useWalletPlugin'

const props = defineProps<{
  model: WalletPluginModel
}>()
const toast = useFaToast()

async function submitRecharge() {
  const result = await props.model.submitRecharge()
  if (!result) {
    return
  }
  if (result.payloadType !== 'HTML_FORM') {
    toast.error('当前仅支持电脑网页支付')
    return
  }
  submitPayForm(result.payPayload)
}

function submitPayForm(payload: string) {
  const container = document.createElement('div')
  container.hidden = true
  container.innerHTML = payload
  const form = container.querySelector('form')
  if (!form) {
    throw new Error('支付表单生成失败，请稍后重试')
  }
  form.setAttribute('target', '_self')
  document.body.appendChild(container)
  form.submit()
}
</script>
