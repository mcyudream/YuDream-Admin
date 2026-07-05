import type { YuDreamPluginSdk } from '@yudream/plugin-sdk'
import type { AuthlibStatus } from '../types'

export function createAuthlibApi(sdk: YuDreamPluginSdk) {
  return {
    status: () => sdk.http.get<AuthlibStatus>('/status'),
    apiUrl: (path = '/') => sdk.http.url(path),
  }
}
