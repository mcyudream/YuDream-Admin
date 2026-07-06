<template>
  <section class="wallet-page">
    <section class="wallet-hero">
      <div>
        <span>钱包充值</span>
        <h2>选择支付渠道</h2>
        <p>仅支持已开启充值的货币类币种，实际到账金额按钱包设置中的充值比例计算。</p>
      </div>
      <FaButton :loading="model.loading" variant="outline" @click="model.load">
        <FaIcon name="i-ri:refresh-line" />
        刷新
      </FaButton>
    </section>

    <WalletPanel v-if="model.hasRecharge" title="创建充值订单" eyebrow="Recharge">
      <form class="wallet-form" @submit.prevent="model.submitRecharge">
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
            <span>支付产品</span>
            <select v-model="model.rechargeForm.productType">
              <option v-for="type in selectedChannelTypes" :key="type" :value="type">
                {{ productTypeLabel(type) }}
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
            创建订单
          </FaButton>
        </div>
      </form>
    </WalletPanel>

    <WalletPanel v-else title="充值暂不可用" eyebrow="Recharge">
      <div class="wallet-empty">
        暂无可用支付渠道
      </div>
    </WalletPanel>

    <WalletPanel v-if="model.rechargeResult" title="支付信息" eyebrow="Payment">
      <div class="wallet-asset-list">
        <div class="wallet-asset-row">
          <div>
            <strong>{{ model.rechargeResult.channelName }} · {{ model.rechargeResult.outTradeNo }}</strong>
            <span>
              支付 {{ model.rechargeResult.payAmount }}，到账
              {{ model.assetSymbol(model.rechargeResult.assetCode) }}{{ model.formatAmount(model.rechargeResult.walletAmount, model.rechargeResult.assetCode) }}
            </span>
          </div>
          <span class="wallet-tag">{{ payloadTypeLabel(model.rechargeResult.payloadType) }}</span>
        </div>
      </div>
      <div class="wallet-payload-box">
        <code>{{ model.rechargeResult.payPayload }}</code>
      </div>
      <div class="wallet-actions">
        <FaButton v-if="model.rechargeResult.payloadType === 'HTML_FORM'" type="button" @click="openPayPayload">
          <FaIcon name="i-ri:external-link-line" />
          打开支付页
        </FaButton>
        <FaButton v-else-if="model.rechargeResult.payloadType === 'QRCODE'" variant="outline" type="button" @click="copyPayload">
          复制二维码链接
        </FaButton>
      </div>
    </WalletPanel>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { FaButton, FaIcon, useFaToast } from '@fantastic-admin/components'
import WalletPanel from '../components/WalletPanel.vue'
import type { WalletPluginModel } from '../composables/useWalletPlugin'

const props = defineProps<{
  model: WalletPluginModel
}>()

const toast = useFaToast()
const selectedChannelTypes = computed(() => {
  const channel = props.model.paymentChannels.find(item => item.code === props.model.rechargeForm.channelCode)
  return channel?.productTypes?.length ? channel.productTypes : ['PAGE']
})

function productTypeLabel(type: string) {
  if (type === 'PAGE') {
    return '电脑网页支付'
  }
  if (type === 'WAP') {
    return '手机网页支付'
  }
  if (type === 'FACE_TO_FACE') {
    return '当面付二维码'
  }
  if (type === 'APP') {
    return 'App 支付'
  }
  return type
}

function payloadTypeLabel(type: string) {
  if (type === 'HTML_FORM') {
    return '支付表单'
  }
  if (type === 'QRCODE') {
    return '二维码链接'
  }
  if (type === 'ORDER_STRING') {
    return 'App 订单串'
  }
  return type
}

function openPayPayload() {
  const payload = props.model.rechargeResult?.payPayload
  if (!payload) {
    return
  }
  const win = window.open('', '_blank')
  if (!win) {
    return
  }
  win.document.open()
  win.document.write(payload)
  win.document.close()
}

async function copyPayload() {
  const payload = props.model.rechargeResult?.payPayload
  if (!payload) {
    return
  }
  await navigator.clipboard.writeText(payload)
  toast.success('已复制')
}
</script>
