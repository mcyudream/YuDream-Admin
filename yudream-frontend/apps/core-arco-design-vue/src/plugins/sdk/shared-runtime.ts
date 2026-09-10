import * as FantasticAdminComponents from '@yudream/components'
import * as Vue from 'vue'
import * as VueRouter from 'vue-router'

/**
 * 插件共享运行时必须早于任何 remoteEntry 求值就绪：主题运行时模块在应用启动
 * 阶段（bootstrapPluginThemes）即加载 remoteEntry，不能依赖 @/plugins/sdk 的
 * 懒加载求值顺序。remote-loader 顶部副作用导入本模块作硬保证。
 */
declare global {
  interface Window {
    __YUDREAM_PLUGIN_SHARED__?: {
      vue: typeof Vue
      vueRouter: typeof VueRouter
      components: typeof FantasticAdminComponents
    }
  }
}

if (typeof window !== 'undefined') {
  window.__YUDREAM_PLUGIN_SHARED__ = {
    vue: Vue,
    vueRouter: VueRouter,
    components: FantasticAdminComponents,
  }
}
