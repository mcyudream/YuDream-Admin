<template>
  <section class="alipay-page">
    <section class="alipay-hero">
      <div>
        <span>支付宝收款</span>
        <h2>官方 API 配置</h2>
        <p>配置 AppId、应用私钥、支付宝公钥和回调地址，启用后会作为钱包充值渠道出现。</p>
      </div>
    </section>

    <AlipayPanel title="接口配置" eyebrow="Config">
      <form class="alipay-form" @submit.prevent="save">
        <div class="alipay-form-grid">
          <label>
            <span>启用状态</span>
            <select v-model="form.enabled">
              <option :value="true">启用</option>
              <option :value="false">停用</option>
            </select>
          </label>
          <label>
            <span>AppId</span>
            <input v-model="form.appId" placeholder="支付宝开放平台应用 AppId">
          </label>
          <label>
            <span>签名类型</span>
            <select v-model="form.signType">
              <option value="RSA2">RSA2</option>
              <option value="RSA">RSA</option>
            </select>
          </label>
          <label>
            <span>字符集</span>
            <input v-model="form.charset" placeholder="UTF-8">
          </label>
        </div>
        <label>
          <span>网关地址</span>
          <input v-model="form.gatewayUrl" placeholder="https://openapi.alipay.com/gateway.do">
        </label>
        <div class="alipay-form-grid">
          <label>
            <span>异步通知地址</span>
            <input v-model="form.notifyUrl" placeholder="https://example.com/api/plugins/yudream-alipay/notify">
          </label>
          <label>
            <span>同步返回地址</span>
            <input v-model="form.returnUrl" placeholder="https://example.com/pay/result">
          </label>
        </div>
        <label>
          <span>应用私钥</span>
          <textarea v-model="form.privateKey" rows="5" placeholder="请填写支付宝应用私钥，保存后会脱敏显示" />
        </label>
        <label>
          <span>支付宝公钥</span>
          <textarea v-model="form.alipayPublicKey" rows="5" placeholder="请填写支付宝公钥，保存后会脱敏显示" />
        </label>
        <div class="alipay-actions">
          <FaButton :loading="saving" type="submit">
            <FaIcon name="i-ri:save-3-line" />
            保存配置
          </FaButton>
        </div>
      </form>
    </AlipayPanel>

    <AlipayPanel title="最近订单" eyebrow="Orders">
      <div class="alipay-table-wrap">
        <table class="alipay-table">
          <thead>
            <tr>
              <th>订单号</th>
              <th>用户</th>
              <th>币种</th>
              <th>支付金额</th>
              <th>到账金额</th>
              <th>状态</th>
              <th>创建时间</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="order in orders" :key="order.outTradeNo">
              <td><code class="alipay-code">{{ order.outTradeNo }}</code></td>
              <td>{{ order.userId }}</td>
              <td>{{ order.assetCode }}</td>
              <td>{{ order.amount }}</td>
              <td>{{ order.walletAmount }}</td>
              <td>{{ statusLabel(order.status) }}</td>
              <td>{{ formatTime(order.createdAt) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </AlipayPanel>
  </section>
</template>

<script setup lang="ts">
import type { YuDreamPluginSdk } from '@yudream/plugin-sdk'
import type { AlipayConfig, AlipayOrder } from '../types'
import { onMounted, reactive, ref } from 'vue'
import { FaButton, FaIcon, useFaToast } from '@fantastic-admin/components'
import { createAlipayApi } from '../api/alipay-api'
import AlipayPanel from '../components/AlipayPanel.vue'

const props = defineProps<{
  sdk: YuDreamPluginSdk
}>()

const api = createAlipayApi(props.sdk)
const toast = useFaToast()
const loading = ref(false)
const saving = ref(false)
const orders = ref<AlipayOrder[]>([])
const DEFAULT_GATEWAY_URL = 'https://openapi.alipay.com/gateway.do'

const form = reactive<AlipayConfig>(withDefaultConfig({
  appId: '',
  privateKey: '',
  alipayPublicKey: '',
  gatewayUrl: DEFAULT_GATEWAY_URL,
  notifyUrl: '',
  returnUrl: '',
  signType: 'RSA2',
  charset: 'UTF-8',
  enabled: false,
}))

async function load() {
  loading.value = true
  try {
    const [config, nextOrders] = await Promise.all([api.config(), api.orders()])
    Object.assign(form, withDefaultConfig(config))
    orders.value = nextOrders
  }
  finally {
    loading.value = false
  }
}

async function save() {
  saving.value = true
  try {
    const payload = withDefaultConfig({ ...form })
    Object.assign(form, payload)
    const saved = await api.saveConfig(payload)
    Object.assign(form, withDefaultConfig(saved))
    toast.success('支付宝配置已保存')
  }
  finally {
    saving.value = false
  }
}

function withDefaultConfig(config: Partial<AlipayConfig>): AlipayConfig {
  return {
    appId: config.appId || '',
    privateKey: config.privateKey || '',
    alipayPublicKey: config.alipayPublicKey || '',
    gatewayUrl: config.gatewayUrl || DEFAULT_GATEWAY_URL,
    notifyUrl: config.notifyUrl || '',
    returnUrl: config.returnUrl || '',
    signType: config.signType || 'RSA2',
    charset: config.charset || 'UTF-8',
    enabled: config.enabled ?? false,
  }
}

function statusLabel(status: string) {
  if (status === 'PAID') {
    return '已入账'
  }
  if (status === 'PAYING') {
    return '待支付'
  }
  if (status === 'CLOSED') {
    return '已关闭'
  }
  return status || '-'
}

function formatTime(value?: number) {
  if (!value) {
    return '-'
  }
  return new Date(value).toLocaleString('zh-CN', { hour12: false })
}

onMounted(load)
</script>
