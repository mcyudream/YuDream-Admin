export interface ActivityProofStatus {
  dependencies: ActivityProofDependencies
  settings: ActivityProofSettings
}

export interface ActivityProofDependencies {
  minecraftReady: boolean
  studentInfoReady: boolean
  wordTemplateReady: boolean
}

export interface ActivityProofSettings {
  templateReady: boolean
  templateFilename: string
  templateUpdatedAt: TimeValue
  defaultActivityName: string
  defaultCollege: string
  defaultIssuer: string
  updatedAt: TimeValue
}

export interface ActivityProofServer {
  id: string
  name: string
  enabled: boolean
  currentSeasonName: string
  currentSeasonStartedAt: TimeValue
}

export interface ActivityProofParticipant {
  index: number
  serverId: string
  playerId: string
  playerName: string
  studentName: string
  studentNo: string
  className: string
  college: string
  matched: boolean
  mapped: boolean
  totalOnlineMillis: number
  totalAfkMillis: number
  effectiveOnlineMillis: number
}

export interface ActivityProofMapping {
  id: string
  serverId: string
  playerId: string
  playerName: string
  studentNo: string
  createdAt: TimeValue
  updatedAt: TimeValue
}

export interface ActivityProofExportRecord {
  id: string
  serverId: string
  serverName: string
  activityName: string
  outputFilename: string
  downloadPath: string
  participantCount: number
  unmatchedCount: number
  operatorUserId: string
  generatedAt: TimeValue
}

export interface ExportForm {
  activityName: string
  activityDate: string
  proofNo: string
  college: string
  issuer: string
  issueDate: string
  minOnlineMinutes: number
  includeAfk: boolean
}

export type TimeValue = number | string | number[] | null | undefined
