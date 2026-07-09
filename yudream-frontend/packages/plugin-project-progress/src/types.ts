export interface ProjectProgressStatus {
  minecraftReady: boolean
  mailReady: boolean
}

export interface ProjectUserOption {
  id: string
  username: string
  nickname?: string
  email?: string
  avatar?: string
  status?: string
  deptIds: string[]
  deptNames: string[]
}

export interface ProjectDeptOption {
  id: string
  name: string
  parentId?: string | null
  status?: string
  children: ProjectDeptOption[]
}

export interface ProjectStatusOption {
  code: string
  label: string
  terminal: boolean
  sort: number
}

export interface ProjectMinecraftPolicy {
  enabled: boolean
  serverId: string
  requiredOnlineMinutes: number
  includeAfk: boolean
  autoCheckInEnabled: boolean
}

export interface ProjectProgressProject {
  id: string
  name: string
  description: string
  managerUserIds: string[]
  memberUserIds: string[]
  statuses: ProjectStatusOption[]
  defaultStatusCode: string
  doneStatusCode: string
  reworkStatusCode: string
  minCheckInIntervalMinutes: number
  allowedCheckInTypes: string[]
  minecraftPolicy: ProjectMinecraftPolicy
  enabled: boolean
  createdAt: number
  updatedAt: number
}

export interface ProjectWorkDetail {
  id: string
  projectId: string
  title: string
  description: string
  statusCode: string
  assignmentMode: 'CLAIM' | 'RANDOM'
  requiredAssigneeCount: number
  candidateUserIds: string[]
  assigneeUserIds: string[]
  acceptorUserIds: string[]
  published: boolean
  dueAt?: number | null
  createdAt: number
  updatedAt: number
}

export interface ProjectCheckIn {
  id: string
  projectId: string
  detailId: string
  userId: string
  type: string
  summary: string
  files: Array<{
    objectKey: string
    filename: string
    contentType: string
    size: number
    image: boolean
  }>
  location?: {
    address: string
    latitude?: number
    longitude?: number
  } | null
  minecraft?: {
    serverId: string
    playerId: string
    playerName: string
    totalOnlineMillis: number
    totalAfkMillis: number
    effectiveOnlineMillis: number
  } | null
  createdAt: number
}

export interface ProjectAcceptanceRecord {
  id: string
  projectId: string
  detailId: string
  operatorUserId: string
  result: string
  fromStatusCode: string
  toStatusCode: string
  reason: string
  createdAt: number
}

export interface ProjectProgressEvent {
  id: string
  projectId: string
  detailId: string
  operatorUserId: string
  type: string
  message: string
  metadata: Record<string, unknown>
  createdAt: number
}

export interface ProjectForm {
  name: string
  description: string
  managerUserIds: string[]
  memberUserIds: string[]
  statusesText: string
  defaultStatusCode: string
  doneStatusCode: string
  reworkStatusCode: string
  minCheckInIntervalMinutes: number
  allowedCheckInTypes: string[]
  minecraftPolicy: ProjectMinecraftPolicy
  enabled: boolean
}

export interface DetailForm {
  title: string
  description: string
  statusCode: string
  assignmentMode: 'CLAIM' | 'RANDOM'
  candidateScope: 'ALL' | 'SELECTED' | 'PROJECT_MEMBERS'
  requiredAssigneeCount: number
  candidateUserIds: string[]
  assigneeUserIds: string[]
  acceptorUserIds: string[]
  dueAt: string
}
