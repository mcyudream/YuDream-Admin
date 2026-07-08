import type { YuDreamPluginSdk } from '@yudream/plugin-sdk'
import type { WalletAsset, WalletBalance, WalletRechargeOptions, WalletRechargeResult, WalletRechargeSettings, WalletSummary, WalletTransaction } from '../types'

export function createWalletApi(sdk: YuDreamPluginSdk) {
  function query(params: Record<string, string | number | undefined>) {
    const search = new URLSearchParams()
    Object.entries(params).forEach(([key, value]) => {
      if (value !== undefined && value !== '') {
        search.set(key, String(value))
      }
    })
    const value = search.toString()
    return value ? `?${value}` : ''
  }

  return {
    status: () => sdk.http.get<WalletSummary>('/status'),
    assets: () => sdk.http.get<WalletAsset[]>('/assets?page=1&size=200'),
    balances: () => sdk.http.get<WalletBalance[]>('/balances'),
    adminBalances: (assetCode?: string, page = 1, size = 20) => sdk.http.get<WalletBalance[]>(`/admin/balances${query({ page, size, assetCode })}`),
    userBalances: (userId: string) => sdk.http.get<WalletBalance[]>(`/users/${encodeURIComponent(userId)}/balances`),
    transactions: (filters?: Record<string, string>, page = 1, size = 20) => sdk.http.get<WalletTransaction[]>(`/transactions${query({ page, size, ...filters })}`),
    myTransactions: (filters?: Record<string, string>, page = 1, size = 20) => sdk.http.get<WalletTransaction[]>(`/my/transactions${query({ page, size, ...filters })}`),
    saveAsset: (data: Record<string, unknown>) => sdk.http.post<WalletAsset>('/assets', data),
    deleteAsset: (assetCode: string) => sdk.http.request(`/assets/${encodeURIComponent(assetCode)}`, { method: 'DELETE' }),
    rechargeOptions: () => sdk.http.get<WalletRechargeOptions>('/recharge/options'),
    rechargeSettings: () => sdk.http.get<WalletRechargeSettings>('/recharge/settings'),
    saveRechargeSettings: (data: Record<string, unknown>) => sdk.http.request<WalletRechargeSettings>('/recharge/settings', { method: 'PUT', data }),
    createRecharge: (data: Record<string, unknown>) => sdk.http.post<WalletRechargeResult>('/recharges', data),
    credit: (data: Record<string, unknown>) => sdk.http.post<WalletTransaction>('/balances/credit', data),
    debit: (data: Record<string, unknown>) => sdk.http.post<WalletTransaction>('/balances/debit', data),
    transfer: (data: Record<string, unknown>) => sdk.http.post<WalletTransaction>('/balances/transfer', data),
  }
}
