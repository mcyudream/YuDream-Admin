<script setup lang="ts">
import type { AxiosError, AxiosRequestConfig } from 'axios'
import axios from 'axios'
import { decryptApiResponse, prepareApiEncryption } from '@/utils/api-encryption'

defineOptions({
  name: 'PayResult',
})

interface BackendResult<T> {
  code: number
  message?: string
  data?: T
}

interface AlipayOrder {
  outTradeNo: string
  assetCode: string
  amount: number | string
  walletAmount: number | string
  status: string
  tradeNo?: string
  walletTransactionId?: string
  paidAt?: number
}

const route = useRoute()
const router = useRouter()
const resultClient = axios.create({
  baseURL: (import.meta.env.DEV && import.meta.env.VITE_ENABLE_PROXY) ? '/proxy/' : import.meta.env.VITE_APP_API_BASEURL,
  timeout: 1000 * 20,
})

resultClient.interceptors.request.use(async (request) => {
  request.headers['Accept-Language'] = 'zh-CN'
  const token = localStorage.getItem('token')
  if (token) {
    request.headers.Authorization = token
  }
  const encrypted = await prepareApiEncryption(request.url, request.data)
  if (encrypted) {
    Object.assign(request.headers, encrypted.headers)
    request.data = encrypted.body
    ;(request as AxiosRequestConfig & { apiEncryptionKey?: CryptoKey }).apiEncryptionKey = encrypted.key
  }
  return request
})

resultClient.interceptors.response.use(async (response) => {
  const key = (response.config as AxiosRequestConfig & { apiEncryptionKey?: CryptoKey }).apiEncryptionKey
  response.data = await decryptApiResponse(response.data, key)
  return response
})

const outTradeNo = computed(() => queryText('out_trade_no'))
const tradeNo = computed(() => queryText('trade_no'))
const totalAmount = computed(() => queryText('total_amount'))
const returnedAt = computed(() => queryText('timestamp'))
const loadingOrder = ref(false)
const order = ref<AlipayOrder | null>(null)
const lookupMessage = ref('')

const details = computed(() => [
  { label: '商户订单号', value: outTradeNo.value },
  { label: '支付宝交易号', value: tradeNo.value },
  { label: '支付金额', value: totalAmount.value ? `￥${totalAmount.value}` : '' },
  { label: '返回时间', value: returnedAt.value },
  { label: '订单状态', value: order.value ? statusLabel(order.value.status) : '' },
  { label: '到账金额', value: order.value ? `${order.value.walletAmount} ${order.value.assetCode}` : '' },
].filter(item => item.value))

const hasOrder = computed(() => Boolean(outTradeNo.value))
const paid = computed(() => order.value?.status === 'PAID')
const pageTitle = computed(() => {
  if (!hasOrder.value) {
    return '支付返回参数缺失'
  }
  if (paid.value) {
    return '支付成功，已入账'
  }
  return '支付返回已接收'
})
const pageMessage = computed(() => {
  if (!hasOrder.value) {
    return '未获取到商户订单号，请回到钱包页面核对充值订单。'
  }
  if (paid.value) {
    return '系统已确认订单入账，可以回到钱包查看余额变化。'
  }
  return '同步返回已完成，最终入账结果以支付宝异步通知为准。'
})

onMounted(fetchOrder)

function queryText(key: string) {
  const value = route.query[key]
  if (Array.isArray(value)) {
    return value[0]?.toString() || ''
  }
  return value?.toString() || ''
}

async function fetchOrder() {
  if (!outTradeNo.value) {
    return
  }
  loadingOrder.value = true
  lookupMessage.value = ''
  try {
    const response = await resultClient.get<BackendResult<AlipayOrder>>(
      `api/plugins/yudream-alipay/orders/${encodeURIComponent(outTradeNo.value)}`,
    )
    const result = response.data
    if (result?.code === 200 && result.data) {
      order.value = result.data
      return
    }
    lookupMessage.value = result?.message || '暂未查询到订单状态，请稍后在钱包中核对。'
  }
  catch (error) {
    lookupMessage.value = friendlyLookupMessage(error as AxiosError)
  }
  finally {
    loadingOrder.value = false
  }
}

function friendlyLookupMessage(error: AxiosError) {
  if (error.response?.status === 401) {
    return '当前登录状态不可用，请登录后到钱包页面查看订单。'
  }
  if (error.response?.status === 403) {
    return '当前账号没有查看该订单的权限，请到钱包页面核对。'
  }
  if (error.response?.status === 404) {
    return '暂未查询到订单状态，异步通知可能还在处理中。'
  }
  return '订单状态暂时不可查，请稍后到钱包页面核对。'
}

function statusLabel(status: string) {
  if (status === 'PAID') {
    return '已入账'
  }
  if (status === 'PAYING') {
    return '等待支付确认'
  }
  if (status === 'CLOSED') {
    return '已关闭'
  }
  return status || '-'
}

function goWallet() {
  router.push('/platform/plugins/yudream-wallet')
}

function goHome() {
  router.push('/')
}
</script>

<template>
  <main class="pay-result-page">
    <section class="pay-result-panel">
      <div class="pay-result-icon" :class="{ 'is-warning': !hasOrder || lookupMessage, 'is-paid': paid }">
        <FaIcon :name="paid ? 'i-ri:checkbox-circle-line' : hasOrder ? 'i-ri:time-line' : 'i-ri:error-warning-line'" />
      </div>

      <div class="pay-result-content">
        <span class="pay-result-eyebrow">Alipay Return</span>
        <h1>{{ pageTitle }}</h1>
        <p>
          {{ pageMessage }}
        </p>
      </div>

      <div v-if="hasOrder" class="pay-result-status" :class="{ 'is-muted': lookupMessage }">
        <FaIcon :name="loadingOrder ? 'i-lucide:loader' : order ? 'i-ri:file-list-3-line' : 'i-ri:information-line'" :class="{ 'pay-result-spin': loadingOrder }" />
        <span>{{ loadingOrder ? '正在查询订单状态...' : order ? `订单${statusLabel(order.status)}` : lookupMessage }}</span>
      </div>

      <dl v-if="details.length" class="pay-result-details">
        <template v-for="item in details" :key="item.label">
          <dt>{{ item.label }}</dt>
          <dd>{{ item.value }}</dd>
        </template>
      </dl>

      <div class="pay-result-actions">
        <FaButton @click="goWallet">
          <FaIcon name="i-ri:wallet-3-line" />
          查看钱包
        </FaButton>
        <FaButton variant="outline" @click="goHome">
          <FaIcon name="i-ri:home-4-line" />
          返回首页
        </FaButton>
      </div>
    </section>
  </main>
</template>

<style scoped>
.pay-result-page {
  display: grid;
  min-height: 100vh;
  place-items: center;
  padding: 24px;
  background:
    linear-gradient(135deg, rgba(24, 144, 255, 0.08), transparent 42%),
    linear-gradient(315deg, rgba(34, 197, 94, 0.08), transparent 38%),
    var(--color-bg-1);
}

.pay-result-panel {
  width: min(100%, 560px);
  padding: 34px;
  border: 1px solid var(--color-border-2);
  border-radius: 8px;
  background: var(--color-bg-2);
  box-shadow: 0 18px 50px rgba(15, 23, 42, 0.12);
}

.pay-result-icon {
  display: grid;
  width: 58px;
  height: 58px;
  place-items: center;
  border-radius: 50%;
  background: rgba(34, 197, 94, 0.12);
  color: rgb(22, 163, 74);
  font-size: 32px;
}

.pay-result-icon.is-paid {
  background: rgba(34, 197, 94, 0.12);
  color: rgb(22, 163, 74);
}

.pay-result-icon.is-warning {
  background: rgba(245, 158, 11, 0.14);
  color: rgb(217, 119, 6);
}

.pay-result-content {
  margin-top: 22px;
}

.pay-result-eyebrow {
  color: rgb(var(--primary-6));
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0;
  text-transform: uppercase;
}

.pay-result-content h1 {
  margin: 8px 0 10px;
  color: var(--color-text-1);
  font-size: 28px;
  line-height: 1.25;
}

.pay-result-content p {
  margin: 0;
  color: var(--color-text-2);
  font-size: 15px;
  line-height: 1.8;
}

.pay-result-status {
  display: flex;
  gap: 10px;
  align-items: center;
  margin-top: 22px;
  padding: 12px 14px;
  border-radius: 8px;
  background: rgba(24, 144, 255, 0.1);
  color: rgb(var(--primary-6));
  font-size: 14px;
  font-weight: 600;
}

.pay-result-status.is-muted {
  background: var(--color-fill-2);
  color: var(--color-text-2);
}

.pay-result-spin {
  animation: pay-result-spin 0.9s linear infinite;
}

.pay-result-details {
  display: grid;
  grid-template-columns: 112px minmax(0, 1fr);
  gap: 12px 16px;
  margin: 26px 0 0;
  padding: 18px;
  border-radius: 8px;
  background: var(--color-fill-2);
}

.pay-result-details dt,
.pay-result-details dd {
  margin: 0;
  min-width: 0;
  font-size: 14px;
  line-height: 1.6;
}

.pay-result-details dt {
  color: var(--color-text-3);
}

.pay-result-details dd {
  overflow-wrap: anywhere;
  color: var(--color-text-1);
  font-weight: 600;
}

.pay-result-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 26px;
}

@media (max-width: 560px) {
  .pay-result-page {
    padding: 16px;
  }

  .pay-result-panel {
    padding: 24px;
  }

  .pay-result-details {
    grid-template-columns: 1fr;
    gap: 4px;
  }
}

@keyframes pay-result-spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
