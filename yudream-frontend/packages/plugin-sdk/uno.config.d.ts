import type { UserConfig } from 'unocss'
import type { Plugin } from 'vite'

export interface YuDreamPluginUnoCssOptions {
  extraContent?: Array<string | RegExp>
}

export declare function yuDreamPluginUnoConfig(options?: YuDreamPluginUnoCssOptions): UserConfig
export declare function yuDreamPluginUnoCss(options?: YuDreamPluginUnoCssOptions): Plugin[]
