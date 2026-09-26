import type { ApiResponse } from './system-client'
import type { ExcelBlobResponse } from '@/utils/excel'
import systemClient from './system-client'

export type BackupScopeType = 'SYSTEM' | 'PLUGIN'
export type BackupJobType = 'EXPORT' | 'REMOTE_BACKUP' | 'IMPORT' | 'REMOTE_RESTORE'
export type BackupJobStatus = 'QUEUED' | 'RUNNING' | 'SUCCEEDED' | 'FAILED'
export type BackupJobTrigger = 'MANUAL' | 'SCHEDULED'
export type BackupConflictStrategy = 'LOCAL_WINS' | 'ARCHIVE_WINS'
export type RemoteTargetType = 'FTP' | 'FTPS' | 'WEBDAV'

export interface BackupScope {
  tag: string
  type: BackupScopeType
  pluginCode?: string
  scopeCode?: string
  displayName: string
  description?: string
  defaultSchedule?: string
}

export interface BackupJob {
  id: string
  type: BackupJobType
  status: BackupJobStatus
  trigger: BackupJobTrigger
  scopeTags?: string[]
  planCode?: string
  targetCode?: string
  targetName?: string
  strategy?: BackupConflictStrategy
  archiveName?: string
  archiveSize?: number
  phase?: string
  percent?: number
  message?: string
  collectionCount?: number
  documentCount?: number
  objectCount?: number
  pluginFileCount?: number
  insertedCount?: number
  conflictCount?: number
  skippedCount?: number
  startedAt?: string
  finishedAt?: string
  createTime?: string
}

export interface RemoteTarget {
  id: string
  code: string
  name: string
  type: RemoteTargetType
  host: string
  port?: number
  username?: string
  basePath?: string
  passiveMode?: boolean
  enabled: boolean
  passwordSet: boolean
  createTime?: string
}

export interface RemoteTargetPayload {
  code?: string
  name: string
  type: RemoteTargetType
  host: string
  port?: number
  username?: string
  /** 留空表示保持原密码。 */
  password?: string
  basePath?: string
  passiveMode?: boolean
}

export interface BackupPlan {
  id: string
  code: string
  name: string
  cron: string
  scopeTags: string[]
  targetCode: string
  targetName?: string
  retentionCount: number
  enabled: boolean
  lastRunAt?: string
  lastStatus?: BackupJobStatus
  lastJobId?: string
}

export interface BackupPlanPayload {
  code?: string
  name: string
  cron: string
  scopeTags: string[]
  targetCode: string
  retentionCount?: number
}

export interface BackupAnalysis {
  hostVersion?: string
  createdAt?: string
  masterKeyFingerprint?: string
  masterKeyMatch: boolean
  collections: { name: string, archiveCount: number, missingCount: number, conflictCount: number }[]
  objects: { archiveCount: number, missingCount: number, conflictCount: number, totalBytes: number }
  pluginScopes: { pluginCode: string, scopeCode: string, displayName?: string, fileCount: number, available: boolean }[]
  warnings: string[]
}

export interface RemoteArchive {
  name: string
  size: number
  modifiedAtMillis?: number
}

export default {
  scopes: () => systemClient.get<unknown, ApiResponse<BackupScope[]>>('api/system/backup/scopes'),
  export: (scopeTags: string[]) => systemClient.post<unknown, ApiResponse<BackupJob>>('api/system/backup/export', { scopeTags }),
  analyzeImport: (data: FormData) => systemClient.post<unknown, ApiResponse<BackupAnalysis>>('api/system/backup/import/analyze', data, { timeout: 0 }),
  import: (data: FormData) => systemClient.post<unknown, ApiResponse<BackupJob>>('api/system/backup/import', data, { timeout: 0 }),
  // 分片导入（超大归档）：顺序分片上传，服务端合并落盘后复用分析/导入
  beginChunkUpload: (data: { name: string, size: number }) =>
    systemClient.post<unknown, ApiResponse<string>>('api/system/backup/import/upload/begin', data),
  uploadChunk: (uploadId: string, offset: number, blob: Blob) =>
    systemClient.post<unknown, ApiResponse<number>>(
      `api/system/backup/import/upload/chunk?uploadId=${encodeURIComponent(uploadId)}&offset=${offset}`,
      blob,
      { headers: { 'Content-Type': 'application/octet-stream' }, timeout: 0 },
    ),
  finishChunkUpload: (data: { uploadId: string, size: number }) =>
    systemClient.post<unknown, ApiResponse<void>>('api/system/backup/import/upload/finish', data, { timeout: 0 }),
  abortChunkUpload: (uploadId: string) =>
    systemClient.post<unknown, ApiResponse<void>>('api/system/backup/import/upload/abort', { uploadId }),
  analyzeStaged: (uploadId: string) =>
    systemClient.post<unknown, ApiResponse<BackupAnalysis>>(
      `api/system/backup/import/analyze/staged?uploadId=${encodeURIComponent(uploadId)}`, null, { timeout: 0 },
    ),
  importStaged: (uploadId: string, strategy: BackupConflictStrategy) =>
    systemClient.post<unknown, ApiResponse<BackupJob>>('api/system/backup/import/staged', { uploadId, strategy }, { timeout: 0 }),
  jobs: (limit = 50) => systemClient.get<unknown, ApiResponse<BackupJob[]>>('api/system/backup/jobs', { params: { limit } }),
  removeJob: (id: string) => systemClient.delete<unknown, ApiResponse<void>>(`api/system/backup/jobs/${id}`),
  downloadArchive: (id: string) => systemClient.get<unknown, ExcelBlobResponse>(`api/system/backup/jobs/${id}/archive`, { responseType: 'blob' }),

  targets: () => systemClient.get<unknown, ApiResponse<RemoteTarget[]>>('api/system/backup/remote-targets'),
  createTarget: (data: RemoteTargetPayload) => systemClient.post<unknown, ApiResponse<RemoteTarget>>('api/system/backup/remote-targets', data),
  updateTarget: (id: string, data: RemoteTargetPayload) => systemClient.put<unknown, ApiResponse<RemoteTarget>>(`api/system/backup/remote-targets/${id}`, data),
  removeTarget: (id: string) => systemClient.delete<unknown, ApiResponse<void>>(`api/system/backup/remote-targets/${id}`),
  enableTarget: (id: string) => systemClient.post<unknown, ApiResponse<void>>(`api/system/backup/remote-targets/${id}/enable`),
  disableTarget: (id: string) => systemClient.post<unknown, ApiResponse<void>>(`api/system/backup/remote-targets/${id}/disable`),
  testTarget: (id: string) => systemClient.post<unknown, ApiResponse<void>>(`api/system/backup/remote-targets/${id}/test`),
  targetArchives: (id: string) => systemClient.get<unknown, ApiResponse<RemoteArchive[]>>(`api/system/backup/remote-targets/${id}/archives`),
  restoreFromTarget: (id: string, data: { archiveName: string, strategy: BackupConflictStrategy }) =>
    systemClient.post<unknown, ApiResponse<void>>(`api/system/backup/remote-targets/${id}/restore`, data),

  plans: () => systemClient.get<unknown, ApiResponse<BackupPlan[]>>('api/system/backup/plans'),
  createPlan: (data: BackupPlanPayload) => systemClient.post<unknown, ApiResponse<BackupPlan>>('api/system/backup/plans', data),
  updatePlan: (id: string, data: BackupPlanPayload) => systemClient.put<unknown, ApiResponse<BackupPlan>>(`api/system/backup/plans/${id}`, data),
  removePlan: (id: string) => systemClient.delete<unknown, ApiResponse<void>>(`api/system/backup/plans/${id}`),
  enablePlan: (id: string) => systemClient.post<unknown, ApiResponse<void>>(`api/system/backup/plans/${id}/enable`),
  disablePlan: (id: string) => systemClient.post<unknown, ApiResponse<void>>(`api/system/backup/plans/${id}/disable`),
  runPlan: (id: string) => systemClient.post<unknown, ApiResponse<void>>(`api/system/backup/plans/${id}/run`),
}
