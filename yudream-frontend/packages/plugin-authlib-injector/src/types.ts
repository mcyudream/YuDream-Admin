export interface AuthlibStatus {
  apiRoot?: string
  textureBaseUrl?: string
  serverName?: string
  publicKey?: string
  enabled?: boolean
  dependencies?: Record<string, unknown>
  [key: string]: unknown
}

export interface AuthlibEndpoint {
  method: 'GET' | 'POST' | 'PUT' | 'DELETE'
  path: string
  note: string
}
