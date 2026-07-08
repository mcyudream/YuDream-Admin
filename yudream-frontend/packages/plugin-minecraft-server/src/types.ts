export interface MinecraftEndpoint {
  id?: string
  name: string
  host: string
  port: number
  edition: 'JAVA' | 'BEDROCK' | string
  primaryLine: boolean
  enabled: boolean
  sort: number
}

export interface MinecraftSeason {
  id?: string
  name: string
  description?: string
  startedAt?: number
  endedAt?: number
  current: boolean
  sort: number
}

export interface MinecraftEndpointStatus {
  endpointId: string
  status: 'ONLINE' | 'OFFLINE' | string
  onlinePlayers: number
  maxPlayers: number
  versionName?: string
  protocolId?: number
  ping?: number
  motd?: string
  errorMessage?: string
  checkedAt: number
}

export interface MinecraftServerStatus {
  serverId: string
  status: 'ONLINE' | 'OFFLINE' | string
  onlinePlayers: number
  maxPlayers: number
  endpoints: MinecraftEndpointStatus[]
  checkedAt: number
}

export interface MinecraftServer {
  id: string
  name: string
  descriptionMarkdown: string
  enabled: boolean
  sort: number
  endpoints: MinecraftEndpoint[]
  seasons: MinecraftSeason[]
  currentSeason?: MinecraftSeason
  status?: MinecraftServerStatus
  createdAt: number
  updatedAt: number
}

export interface InheritanceRule {
  assetPattern: string
  minAmount: string | number
  maxAmount?: string | number
  inheritRate: string | number
  rangeLabel?: string
}

export interface SeasonAdjustment {
  userId: string
  assetCode: string
  inheritedAmount: string | number
  seasonIncomeAmount: string | number
  seasonTotalAmount: string | number
  realTotalIncomeAmount: string | number
  nextInheritedAmount: string | number
  walletBalanceBefore: string | number
  deltaAmount: string | number
  direction: 'CREDIT' | 'DEBIT' | 'NONE' | string
  ruleLabel: string
  walletTransactionId?: string
  rollbackTransactionId?: string
}

export interface SeasonOperation {
  id: string
  serverId: string
  fromSeasonId?: string
  toSeasonId?: string
  toSeasonName: string
  status: 'PREVIEW' | 'APPLIED' | 'ROLLED_BACK' | string
  rules: InheritanceRule[]
  adjustments: SeasonAdjustment[]
  operatorUserId?: string
  remark?: string
  createdAt: number
  rolledBackAt?: number
}

export interface EconomyRecord {
  id: string
  type: string
  source: string
  status: string
  assetCode: string
  amount: string | number
  businessNo?: string
  remark?: string
  createdAt: number
}

export interface ServerForm {
  id: string
  name: string
  descriptionMarkdown: string
  enabled: boolean
  sort: number
  endpoints: MinecraftEndpoint[]
  seasons: MinecraftSeason[]
}

export interface SeasonForm {
  name: string
  description: string
  startedAtText: string
  remark: string
  rules: InheritanceRule[]
}
