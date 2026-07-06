import { defineYuDreamPlugin } from '@yudream/plugin-sdk'
import walletStyles from './styles.css?inline'
import DashboardBalanceCard from './components/DashboardBalanceCard.vue'
import WalletPlugin from './WalletPlugin.vue'

export const Home = WalletPlugin
export const Recharge = WalletPlugin
export const Settings = WalletPlugin
export const Balances = WalletPlugin
export const Transactions = WalletPlugin
export const YUDREAM_WALLET_BALANCE_CARD = DashboardBalanceCard

export const routes = {
  Home,
  Recharge,
  Settings,
  Balances,
  Transactions,
  DashboardBalanceCard,
  YUDREAM_WALLET_BALANCE_CARD,
  'yudream-wallet/DashboardBalanceCard': DashboardBalanceCard,
  'yudream-wallet/Home': Home,
  'yudream-wallet/Recharge': Recharge,
  'yudream-wallet/Settings': Settings,
  'yudream-wallet/Balances': Balances,
  'yudream-wallet/Transactions': Transactions,
  'yudream-wallet/System': Settings,
}

export function install() {
  if (typeof document === 'undefined') {
    return
  }
  const id = 'yudream-plugin-wallet-style'
  let style = document.getElementById(id) as HTMLStyleElement | null
  if (!style) {
    style = document.createElement('style')
    style.id = id
    document.head.appendChild(style)
  }
  style.id = id
  style.textContent = walletStyles
}

export default defineYuDreamPlugin({
  routes,
  default: Home,
  install,
})
