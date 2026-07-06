export interface AlipayConfig {
  appId?: string
  privateKey?: string
  alipayPublicKey?: string
  gatewayUrl?: string
  notifyUrl?: string
  returnUrl?: string
  signType?: string
  charset?: string
  enabled: boolean
}

export interface AlipayOrder {
  outTradeNo: string
  userId: string
  assetCode: string
  amount: number | string
  walletAmount: number | string
  subject: string
  productType: string
  status: string
  tradeNo?: string
  walletTransactionId?: string
  createdAt: number
  updatedAt: number
  paidAt: number
}
