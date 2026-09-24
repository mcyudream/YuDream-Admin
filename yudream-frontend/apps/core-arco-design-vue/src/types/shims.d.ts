declare interface Window {
  webkitDevicePixelRatio: any
  mozDevicePixelRatio: any
}

declare const __SYSTEM_INFO__: {
  pkg: {
    version: string
    dependencies: Record<string, string>
    devDependencies: Record<string, string>
  }
  lastBuildTime: string
}

/** 构建期注入的共享契约包真实版本（workspace 依赖在 __SYSTEM_INFO__ 中只会是 workspace:*） */
declare const __YUDREAM_PACKAGE_VERSIONS__: {
  'plugin-sdk': string
  'components': string
  'dataviz': string
}

declare module 'virtual:fantastic-admin/turbo-console' {
  export function warnKeepAliveComponentNameMissing(filePath: string): void
}

declare module 'form-create-designer-arco-design' {
  import type { Plugin } from 'vue'
  const plugin: Plugin
  export default plugin
}

declare module '@jboltai/tokui/css'
