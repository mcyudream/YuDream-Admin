import type { ApiResponse } from './system-client'
import systemClient from './system-client'

export interface AboutFramework {
  name: string
  version: string
  buildTime?: string
  springBootVersion?: string
  javaVersion?: string
  osName?: string
  osArch?: string
}

export interface AboutSpi {
  version?: string
  artifact?: string
  buildTime?: string
}

export interface AboutOverview {
  framework: AboutFramework
  spi: AboutSpi
  pluginTotal: number
  pluginEnabled: number
  pluginError: number
}

export interface AboutLatestVersion {
  key: string
  name: string
  kind: 'MAVEN' | 'NPM'
  latest?: string
  sourceUrl?: string
  checkedAt?: string
  error?: string
}

export interface AboutLatest {
  enabled: boolean
  entries: AboutLatestVersion[]
}

export type PluginGraphNodeStatus = 'ENABLED' | 'LOADED' | 'INSTALLED' | 'DISABLED' | 'ERROR' | 'MISSING'

export interface PluginGraphNode {
  code: string
  name: string
  version?: string
  status: PluginGraphNodeStatus
  errorMessage?: string
}

export interface PluginGraphEdge {
  source: string
  target: string
  kind: 'HARD' | 'SOFT'
}

export interface PluginGraph {
  nodes: PluginGraphNode[]
  edges: PluginGraphEdge[]
}

export default {
  overview: () => systemClient.get<unknown, ApiResponse<AboutOverview>>('api/system/about'),
  latest: () => systemClient.get<unknown, ApiResponse<AboutLatest>>('api/system/about/latest'),
  pluginGraph: () => systemClient.get<unknown, ApiResponse<PluginGraph>>('api/system/about/plugin-graph'),
}
