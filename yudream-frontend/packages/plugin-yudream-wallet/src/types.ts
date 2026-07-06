export interface WalletAsset {
  code: string
  name: string
  symbol?: string
  scale: number
  money: boolean
  enabled: boolean
  transferEnabled: boolean
  minTransferAmount?: number | string
}

export interface WalletUser {
  id: string
  username?: string
  nickname?: string
  email?: string
  avatar?: string
}

export interface WalletBalance {
  userId: string
  user?: WalletUser
  assetCode: string
  balance: number | string
  updatedAt: number
}

export interface WalletTransaction {
  id: string
  businessNo?: string
  type: 'CREDIT' | 'DEBIT' | 'TRANSFER' | string
  source?: string
  assetCode: string
  fromUserId?: string
  fromUser?: WalletUser
  toUserId?: string
  toUser?: WalletUser
  direction?: 'IN' | 'OUT' | 'TRANSFER' | string
  amount: number | string
  fromBalanceAfter?: number | string
  toBalanceAfter?: number | string
  remark?: string
  createdAt: number
}

export interface WalletSummary {
  assetCount: number
  transactionCount: number
}

export interface WalletPaymentChannel {
  code: string
  name: string
  icon?: string
  description?: string
  enabled: boolean
  productTypes: string[]
}

export interface WalletRechargeRule {
  assetCode: string
  enabled: boolean
  ratio: number | string
  minPayAmount?: number | string
  maxPayAmount?: number | string
}

export interface WalletRechargeSettings {
  enabled: boolean
  defaultProductType: string
  rules: WalletRechargeRule[]
}

export interface WalletRechargeOptions extends WalletRechargeSettings {
  channels: WalletPaymentChannel[]
  assets: WalletAsset[]
}

export interface WalletRechargeResult {
  channelCode: string
  channelName: string
  outTradeNo: string
  assetCode: string
  payAmount: number | string
  walletAmount: number | string
  productType: string
  status: string
  payloadType: string
  payPayload: string
  createdAt: number
}

export interface AssetForm {
  code: string
  name: string
  symbol: string
  scale: number
  money: boolean
  enabled: boolean
  transferEnabled: boolean
  minTransferAmount: string
}

export interface TransferForm {
  toAccount: string
  assetCode: string
  amount: string
  remark: string
}

export interface RechargeForm {
  channelCode: string
  assetCode: string
  payAmount: string
  productType: string
  remark: string
}

export interface RechargeRuleForm {
  assetCode: string
  enabled: boolean
  ratio: string
  minPayAmount: string
  maxPayAmount: string
}

export interface RechargeSettingsForm {
  enabled: boolean
  defaultProductType: string
  rules: RechargeRuleForm[]
}

export interface BalanceChangeForm {
  userId: string
  assetCode: string
  amount: string
  remark: string
}

export interface TransactionFilters {
  assetCode: string
  source: string
  type: string
  user: string
}
