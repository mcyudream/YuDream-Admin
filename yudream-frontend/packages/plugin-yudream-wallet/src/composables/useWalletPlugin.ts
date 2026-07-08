import type { YuDreamPluginSdk } from '@yudream/plugin-sdk'
import type { AssetForm, BalanceChangeForm, RechargeForm, RechargeSettingsForm, TransactionFilters, TransferForm, WalletAsset, WalletBalance, WalletPaymentChannel, WalletRechargeOptions, WalletRechargeResult, WalletRechargeRule, WalletSummary, WalletTransaction, WalletUser } from '../types'
import { useFaToast } from '@fantastic-admin/components'
import { computed, reactive, ref } from 'vue'
import { createWalletApi } from '../api/wallet-api'

const MANAGE_PERMISSION = 'plugin:yudream-wallet:manage'

export function useWalletPlugin(sdk: YuDreamPluginSdk) {
  const api = createWalletApi(sdk)
  const toast = useFaToast()
  const loading = ref(false)
  const saving = ref(false)
  const summary = ref<WalletSummary | null>(null)
  const assets = ref<WalletAsset[]>([])
  const balances = ref<WalletBalance[]>([])
  const adminBalances = ref<WalletBalance[]>([])
  const transactions = ref<WalletTransaction[]>([])
  const rechargeOptions = ref<WalletRechargeOptions | null>(null)
  const rechargeResult = ref<WalletRechargeResult | null>(null)
  const selectedAssetCode = ref('CNY')
  const selectedBalanceAssetCode = ref('CNY')

  const transferForm = reactive<TransferForm>({
    toAccount: '',
    assetCode: 'CNY',
    amount: '',
    remark: '',
  })

  const rechargeForm = reactive<RechargeForm>({
    channelCode: '',
    assetCode: 'CNY',
    payAmount: '',
    productType: '',
    remark: '',
  })

  const rechargeSettingsForm = reactive<RechargeSettingsForm>({
    enabled: true,
    defaultProductType: 'PAGE',
    rules: [],
  })

  const assetForm = reactive<AssetForm>({
    code: '',
    name: '',
    symbol: '',
    scale: 0,
    money: false,
    enabled: true,
    transferEnabled: true,
    minTransferAmount: '0',
  })

  const changeForm = reactive<BalanceChangeForm>({
    userId: '',
    assetCode: 'CNY',
    amount: '',
    remark: '',
  })

  const transactionFilters = reactive<TransactionFilters>({
    assetCode: '',
    source: '',
    type: '',
    user: '',
  })

  const canManage = computed(() => sdk.account.permissions.includes('*') || sdk.account.permissions.includes(MANAGE_PERMISSION))
  const accountName = computed(() => sdk.account.username || `用户 ${sdk.account.userId}`)
  const primaryAsset = computed(() => assets.value.find(item => item.code === selectedAssetCode.value) || assets.value[0])
  const transferableAssets = computed(() => assets.value.filter(asset => asset.enabled && asset.transferEnabled))
  const paymentChannels = computed<WalletPaymentChannel[]>(() => rechargeOptions.value?.channels ?? [])
  const rechargeableAssets = computed<WalletAsset[]>(() => rechargeOptions.value?.assets ?? [])
  const hasRecharge = computed(() => !!rechargeOptions.value?.enabled && paymentChannels.value.length > 0 && rechargeableAssets.value.length > 0)
  const selectedRechargeRule = computed(() => rechargeRule(rechargeForm.assetCode))
  const estimatedWalletAmount = computed(() => {
    const payAmount = Number(rechargeForm.payAmount || 0)
    const ratio = Number(selectedRechargeRule.value?.ratio ?? 1)
    if (!Number.isFinite(payAmount) || !Number.isFinite(ratio) || payAmount <= 0) {
      return '0'
    }
    return String(payAmount * ratio)
  })
  const sortedBalances = computed(() => {
    const order = new Map(assets.value.map((asset, index) => [asset.code, index]))
    return [...balances.value].sort((a, b) => (order.get(a.assetCode) ?? 99) - (order.get(b.assetCode) ?? 99))
  })
  const recentTransactions = computed(() => transactions.value.slice(0, 8))

  function assetName(assetCode?: string) {
    if (!assetCode) {
      return '-'
    }
    return assets.value.find(asset => asset.code === assetCode)?.name || assetCode
  }

  function assetSymbol(assetCode?: string) {
    if (!assetCode) {
      return ''
    }
    return assets.value.find(asset => asset.code === assetCode)?.symbol || ''
  }

  function balanceOf(assetCode?: string) {
    if (!assetCode) {
      return '0'
    }
    return balances.value.find(item => item.assetCode === assetCode)?.balance ?? '0'
  }

  function userLabel(user?: WalletUser, fallback?: string) {
    if (!user) {
      return fallback || '-'
    }
    const name = user.nickname || user.username || user.email || user.id
    return `${name}（${user.id}）`
  }

  function formatAmount(value?: number | string, assetCode?: string) {
    const asset = assets.value.find(item => item.code === assetCode)
    const scale = asset?.scale ?? 2
    const number = Number(value ?? 0)
    if (!Number.isFinite(number)) {
      return String(value ?? '0')
    }
    return number.toLocaleString('zh-CN', {
      minimumFractionDigits: scale,
      maximumFractionDigits: scale,
    })
  }

  function formatTime(value?: number) {
    if (!value) {
      return '-'
    }
    return new Date(value).toLocaleString('zh-CN', { hour12: false })
  }

  function transactionLabel(type?: string) {
    if (type === 'CREDIT') {
      return '入账'
    }
    if (type === 'DEBIT') {
      return '扣账'
    }
    if (type === 'TRANSFER') {
      return '转账'
    }
    return type || '-'
  }

  function sourceLabel(source?: string) {
    if (source === 'ALIPAY') {
      return '支付宝'
    }
    if (source === 'ADMIN') {
      return '管理员'
    }
    if (source === 'TRANSFER') {
      return '用户转账'
    }
    if (source === 'MINECRAFT_SEASON') {
      return '周目继承'
    }
    return source || '-'
  }

  async function load() {
    loading.value = true
    try {
      const [nextSummary, nextAssets, nextBalances, nextRechargeOptions] = await Promise.all([api.status(), api.assets(), api.balances(), api.rechargeOptions()])
      summary.value = nextSummary as WalletSummary
      assets.value = nextAssets as WalletAsset[]
      balances.value = nextBalances as WalletBalance[]
      rechargeOptions.value = nextRechargeOptions as WalletRechargeOptions
      if (!assets.value.some(asset => asset.code === selectedAssetCode.value)) {
        selectedAssetCode.value = assets.value[0]?.code || ''
      }
      if (!assets.value.some(asset => asset.code === selectedBalanceAssetCode.value)) {
        selectedBalanceAssetCode.value = assets.value[0]?.code || ''
      }
      if (!transferableAssets.value.some(asset => asset.code === transferForm.assetCode)) {
        transferForm.assetCode = transferableAssets.value[0]?.code || ''
      }
      if (!assets.value.some(asset => asset.code === changeForm.assetCode)) {
        changeForm.assetCode = selectedAssetCode.value
      }
      syncRechargeForm()
      if (canManage.value) {
        await Promise.all([loadAdminBalances(), loadTransactions(), loadRechargeSettings()])
      }
      else {
        transactions.value = await api.myTransactions()
      }
    }
    finally {
      loading.value = false
    }
  }

  async function submitTransfer() {
    if (!transferableAssets.value.length) {
      toast.warning('当前没有可转账币种')
      return
    }
    if (!transferForm.toAccount.trim() || !transferForm.amount.trim()) {
      toast.warning('请填写转入用户和金额')
      return
    }
    if (!transferForm.assetCode) {
      toast.warning('请选择转账币种')
      return
    }
    saving.value = true
    try {
      await api.transfer({
        fromUserId: sdk.account.userId,
        toAccount: transferForm.toAccount.trim(),
        assetCode: transferForm.assetCode,
        amount: transferForm.amount,
        remark: transferForm.remark.trim() || undefined,
      })
      toast.success('转账已提交')
      transferForm.toAccount = ''
      transferForm.amount = ''
      transferForm.remark = ''
      await load()
    }
    finally {
      saving.value = false
    }
  }

  async function submitRecharge() {
    if (!hasRecharge.value) {
      toast.warning('当前没有可用充值渠道')
      return
    }
    if (!rechargeForm.channelCode || !rechargeForm.assetCode || !rechargeForm.payAmount.trim()) {
      toast.warning('请选择渠道、币种并填写支付金额')
      return
    }
    saving.value = true
    try {
      rechargeResult.value = null
      rechargeResult.value = await api.createRecharge({
        userId: sdk.account.userId,
        channelCode: rechargeForm.channelCode,
        assetCode: rechargeForm.assetCode,
        payAmount: rechargeForm.payAmount,
        productType: 'PAGE',
        remark: rechargeForm.remark.trim() || undefined,
      })
      toast.success('订单已创建，正在跳转支付宝')
      return rechargeResult.value
    }
    finally {
      saving.value = false
    }
  }

  function editAsset(asset: WalletAsset) {
    assetForm.code = asset.code
    assetForm.name = asset.name
    assetForm.symbol = asset.symbol || ''
    assetForm.scale = asset.scale
    assetForm.money = asset.money
    assetForm.enabled = asset.enabled
    assetForm.transferEnabled = asset.transferEnabled
    assetForm.minTransferAmount = String(asset.minTransferAmount ?? '0')
  }

  function resetAssetForm() {
    assetForm.code = ''
    assetForm.name = ''
    assetForm.symbol = ''
    assetForm.scale = 0
    assetForm.money = false
    assetForm.enabled = true
    assetForm.transferEnabled = true
    assetForm.minTransferAmount = '0'
  }

  async function saveAsset() {
    if (!assetForm.code.trim() || !assetForm.name.trim()) {
      toast.warning('请填写资产编码和名称')
      return
    }
    saving.value = true
    try {
      await api.saveAsset({
        code: assetForm.code.trim().toUpperCase(),
        name: assetForm.name.trim(),
        symbol: assetForm.symbol.trim(),
        scale: assetForm.scale,
        money: assetForm.money,
        enabled: assetForm.enabled,
        transferEnabled: assetForm.transferEnabled,
        minTransferAmount: assetForm.minTransferAmount || '0',
      })
      toast.success('资产已保存')
      resetAssetForm()
      await load()
    }
    finally {
      saving.value = false
    }
  }

  async function deleteAsset(asset: WalletAsset) {
    saving.value = true
    try {
      await api.deleteAsset(asset.code)
      toast.success('币种已删除')
      if (assetForm.code === asset.code) {
        resetAssetForm()
      }
      await load()
    }
    finally {
      saving.value = false
    }
  }

  async function loadRechargeSettings() {
    const settings = await api.rechargeSettings()
    rechargeSettingsForm.enabled = settings.enabled
    rechargeSettingsForm.defaultProductType = 'PAGE'
    rechargeSettingsForm.rules = settings.rules.map(rule => ({
      assetCode: rule.assetCode,
      enabled: rule.enabled,
      ratio: String(rule.ratio ?? '1'),
      minPayAmount: String(rule.minPayAmount ?? '0'),
      maxPayAmount: rule.maxPayAmount == null ? '' : String(rule.maxPayAmount),
    }))
  }

  async function saveRechargeSettings() {
    saving.value = true
    try {
      await api.saveRechargeSettings({
        enabled: rechargeSettingsForm.enabled,
        defaultProductType: 'PAGE',
        rules: rechargeSettingsForm.rules.map(rule => ({
          assetCode: rule.assetCode,
          enabled: rule.enabled,
          ratio: rule.ratio || '1',
          minPayAmount: rule.minPayAmount || '0',
          maxPayAmount: rule.maxPayAmount || undefined,
        })),
      })
      toast.success('充值配置已保存')
      await load()
    }
    finally {
      saving.value = false
    }
  }

  async function changeBalance(kind: 'credit' | 'debit') {
    if (!changeForm.userId.trim() || !changeForm.amount.trim()) {
      toast.warning('请填写用户和金额')
      return
    }
    if (!changeForm.assetCode) {
      toast.warning('请选择币种')
      return
    }
    saving.value = true
    try {
      const payload = {
        userId: changeForm.userId.trim(),
        assetCode: changeForm.assetCode,
        amount: changeForm.amount,
        businessNo: `wallet-admin-${kind}-${Date.now()}`,
        remark: changeForm.remark.trim() || (kind === 'credit' ? '管理员入账' : '管理员扣账'),
      }
      if (kind === 'credit') {
        await api.credit(payload)
      }
      else {
        await api.debit(payload)
      }
      toast.success(kind === 'credit' ? '入账成功' : '扣账成功')
      changeForm.amount = ''
      changeForm.remark = ''
      await load()
    }
    finally {
      saving.value = false
    }
  }

  async function loadAdminBalances() {
    adminBalances.value = await api.adminBalances(selectedBalanceAssetCode.value)
  }

  async function loadTransactions() {
    transactions.value = await api.transactions({
      assetCode: transactionFilters.assetCode,
      source: transactionFilters.source,
      type: transactionFilters.type,
      user: transactionFilters.user.trim(),
    })
  }

  async function applyBalanceAsset(assetCode: string) {
    selectedBalanceAssetCode.value = assetCode
    await loadAdminBalances()
  }

  async function resetTransactionFilters() {
    transactionFilters.assetCode = ''
    transactionFilters.source = ''
    transactionFilters.type = ''
    transactionFilters.user = ''
    await loadTransactions()
  }

  function rechargeRule(assetCode?: string): WalletRechargeRule | undefined {
    return rechargeOptions.value?.rules.find(rule => rule.assetCode === assetCode)
  }

  function syncRechargeForm() {
    if (!paymentChannels.value.some(channel => channel.code === rechargeForm.channelCode)) {
      rechargeForm.channelCode = paymentChannels.value[0]?.code || ''
    }
    if (!rechargeableAssets.value.some(asset => asset.code === rechargeForm.assetCode)) {
      rechargeForm.assetCode = rechargeableAssets.value[0]?.code || ''
    }
    const channel = paymentChannels.value.find(item => item.code === rechargeForm.channelCode)
    const defaultProductType = 'PAGE'
    if (!rechargeForm.productType || (channel && !channel.productTypes.includes(rechargeForm.productType))) {
      rechargeForm.productType = channel?.productTypes.includes(defaultProductType) ? defaultProductType : channel?.productTypes[0] || defaultProductType
    }
  }

  return reactive({
    loading,
    saving,
    summary,
    assets,
    balances,
    adminBalances,
    transactions,
    rechargeOptions,
    rechargeResult,
    selectedAssetCode,
    selectedBalanceAssetCode,
    transferForm,
    rechargeForm,
    rechargeSettingsForm,
    assetForm,
    changeForm,
    transactionFilters,
    canManage,
    accountName,
    primaryAsset,
    transferableAssets,
    paymentChannels,
    rechargeableAssets,
    hasRecharge,
    estimatedWalletAmount,
    sortedBalances,
    recentTransactions,
    assetName,
    assetSymbol,
    balanceOf,
    userLabel,
    formatAmount,
    formatTime,
    transactionLabel,
    sourceLabel,
    load,
    loadAdminBalances,
    loadTransactions,
    loadRechargeSettings,
    applyBalanceAsset,
    resetTransactionFilters,
    submitTransfer,
    submitRecharge,
    editAsset,
    resetAssetForm,
    saveAsset,
    deleteAsset,
    saveRechargeSettings,
    changeBalance,
  })
}

export type WalletPluginModel = ReturnType<typeof useWalletPlugin>
