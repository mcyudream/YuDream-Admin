import { defineYuDreamPlugin } from '@yudream/plugin-sdk'
import skinStyles from './styles.css?inline'
import DashboardCurrentPlayerCard from './components/DashboardCurrentPlayerCard.vue'
import DashboardLegacyActionCard from './components/DashboardLegacyActionCard.vue'
import DashboardSkinPreviewCard from './components/DashboardSkinPreviewCard.vue'
import DashboardStatsCard from './components/DashboardStatsCard.vue'
import SkinPlugin from './SkinPlugin.vue'

export const Dashboard = SkinPlugin
export const Closet = SkinPlugin
export const Settings = SkinPlugin
export const Players = SkinPlugin
export const Textures = SkinPlugin
export const PlayerManagement = SkinPlugin
export const TextureManagement = SkinPlugin
export const ACTION_CARD = DashboardLegacyActionCard

export const routes = {
  Dashboard,
  Closet,
  Settings,
  Players,
  Textures,
  PlayerManagement,
  TextureManagement,
  ACTION_CARD,
  DashboardCurrentPlayerCard,
  DashboardSkinPreviewCard,
  DashboardStatsCard,
  'yudream-skin/DashboardCurrentPlayerCard': DashboardCurrentPlayerCard,
  'yudream-skin/DashboardSkinPreviewCard': DashboardSkinPreviewCard,
  'yudream-skin/DashboardStatsCard': DashboardStatsCard,
  'yudream-skin/Home': Dashboard,
  'yudream-skin/Dashboard': Dashboard,
  'yudream-skin/Closet': Closet,
  'yudream-skin/Settings': Settings,
  'yudream-skin/Players': Players,
  'yudream-skin/Textures': Textures,
  'yudream-skin/PlayerManagement': PlayerManagement,
  'yudream-skin/TextureManagement': TextureManagement,
  'yudream-skin/System': Settings,
  'blessing-skin/Home': Dashboard,
  'blessing-skin/Dashboard': Dashboard,
  'blessing-skin/Players': Players,
  'blessing-skin/Textures': Textures,
  'blessing-skin/Closet': Closet,
  'blessing-skin/System': Settings,
}

export function install() {
  if (typeof document === 'undefined') {
    return
  }
  const id = 'yudream-plugin-yudream-skin-style'
  let style = document.getElementById(id) as HTMLStyleElement | null
  if (!style) {
    style = document.createElement('style')
    style.id = id
    document.head.appendChild(style)
  }
  style.id = id
  style.textContent = skinStyles
}

export default defineYuDreamPlugin({
  routes,
  default: Dashboard,
  install,
})
