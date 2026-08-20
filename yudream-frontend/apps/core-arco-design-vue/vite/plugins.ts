import type { PluginOption } from 'vite'
import { existsSync, readFileSync } from 'node:fs'
import process from 'node:process'
import { fileURLToPath } from 'node:url'
import { ComposablesAutoImports as FantasticAdminComposablesAutoImports } from '@fantastic-admin/composables/resolver'
import { createCopyrightPlugins as createFantasticAdminCopyrightPlugins } from '@fantastic-admin/copyright'
import VitePluginSvgSpritemap from '@spiriit/vite-plugin-svg-spritemap'
import vueLegacy from '@vitejs/plugin-legacy'
import vue from '@vitejs/plugin-vue'
import vueJsx from '@vitejs/plugin-vue-jsx'
import { ComponentsAutoImports as FantasticAdminComponentsAutoImports, ComponentsResolver as FantasticAdminComponentsResolver, ComponentsType as FantasticAdminComponentsType } from '@yudream/components/resolver'
import Unocss from 'unocss/vite'
import autoImport from 'unplugin-auto-import/vite'
import TurboConsole from 'unplugin-turbo-console/vite'
import components from 'unplugin-vue-components/vite'
import { loadEnv } from 'vite'
import AppLoading from 'vite-plugin-app-loading'
import Archiver from 'vite-plugin-archiver'
import { compression } from 'vite-plugin-compression2'
import { envParse, parseLoadedEnv } from 'vite-plugin-env-parse'
import { vitePluginFakeServer } from 'vite-plugin-fake-server'
import VueDevTools from 'vite-plugin-vue-devtools'

export default function createVitePlugins(mode: string, isBuild = false) {
  const viteEnv = parseLoadedEnv(loadEnv(mode, process.cwd()))
  const vitePlugins: (PluginOption | PluginOption[])[] = [
    vue(),
    vueJsx(),
    vueLegacy({
      renderLegacyChunks: false,
      modernPolyfills: [
        'es.array.at',
        'es.array.find-last',
        'es.object.has-own',
      ],
    }),

    // https://github.com/vuejs/devtools
    viteEnv.VITE_ENABLE_VUE_DEVTOOLS && VueDevTools({
      launchEditor: viteEnv.VITE_LAUNCH_EDITOR,
    }),

    // https://github.com/yue1123/vite-plugin-env-parse
    envParse({
      dtsPath: 'src/types/env.d.ts',
    }),

    // https://github.com/unplugin/unplugin-auto-import
    autoImport({
      imports: [
        'vue',
        'vue-router',
        'pinia',
        FantasticAdminComponentsAutoImports,
        FantasticAdminComposablesAutoImports,
      ],
      dts: './src/types/auto-imports.d.ts',
      dirs: [
        './src/store/modules/**/*',
        './src/composables/**/*',
      ],
    }),

    // https://github.com/unplugin/unplugin-vue-components
    components({
      globs: [
        'src/components/*/index.vue',
      ],
      dts: './src/types/components.d.ts',
      resolvers: [
        FantasticAdminComponentsResolver(),
      ],
      types: [
        FantasticAdminComponentsType,
      ],
    }),

    Unocss(),

    // https://github.com/SpiriitLabs/vite-plugin-svg-spritemap
    VitePluginSvgSpritemap('./src/assets/icons/*.svg'),

    // https://github.com/condorheroblog/vite-plugin-fake-server
    vitePluginFakeServer({
      logger: !isBuild,
      include: 'src/api/fake_modules',
      enableProd: isBuild && viteEnv.VITE_BUILD_FAKE,
    }),

    // https://github.com/nonzzz/vite-plugin-compression
    viteEnv.VITE_BUILD_COMPRESS && compression({
      exclude: [/\.(br)$/, /\.(gz)$/],
      algorithms: viteEnv.VITE_BUILD_COMPRESS.split(',').map((item: string) => ({
        gzip: 'gzip',
        brotli: 'brotliCompress',
      }[item])),
    }),

    viteEnv.VITE_BUILD_ARCHIVE && Archiver({
      archiveType: viteEnv.VITE_BUILD_ARCHIVE,
    }),

    AppLoading('loading.html'),

    // https://github.com/unplugin/unplugin-turbo-console
    viteEnv.VITE_ENABLE_TURBO_CONSOLE && TurboConsole({
      launchEditor: {
        specifiedEditor: viteEnv.VITE_LAUNCH_EDITOR,
      },
    }),
    (() => {
      const moduleId = 'virtual:fantastic-admin/turbo-console'
      const resolvedModuleId = `\0${moduleId}`
      return {
        name: 'vite-plugin-turbo-console-bridge',
        resolveId(id) {
          if (id === moduleId) {
            return resolvedModuleId
          }
        },
        load(id) {
          if (id === resolvedModuleId) {
            if (viteEnv.VITE_ENABLE_TURBO_CONSOLE && !isBuild) {
              return String.raw`
import { host, port } from '~console/server-info'

export function warnKeepAliveComponentNameMissing(filePath) {
  // turbo-console-disable-next-line
  console.warn(
    '[YuDream]\n\n该页面组件未设置组件名，会导致保活失效，点击下方链接打开该文件进行检查\n'
    + '%chttp://' + host + ':' + port + '?path=' + filePath,
    'background:#00DC8250;padding:2px 5px;border-radius:0 3px 3px 0;margin:0 0 5px 0;',
  )
}
              `
            }
            return String.raw`
export function warnKeepAliveComponentNameMissing() {}
            `
          }
        },
      } satisfies PluginOption
    })(),

    createFantasticAdminCopyrightPlugins(),

    // 插件开发者调试抽屉「前端审查」页数据源：暴露 pnpm audit:ui 生成的报告，
    // 每次请求实时读文件，重新执行 pnpm audit:ui 后面板刷新即可看到最新结果
    !isBuild && {
      name: 'yudream-devtools-audit-report',
      configureServer(server) {
        server.middlewares.use('/__yudream-devtools/audit.json', (_req, res) => {
          const reportPath = fileURLToPath(new URL('../../../audit-report.json', import.meta.url))
          res.setHeader('Content-Type', 'application/json; charset=utf-8')
          res.setHeader('Cache-Control', 'no-store')
          if (existsSync(reportPath)) {
            res.end(readFileSync(reportPath, 'utf-8'))
          }
          else {
            res.statusCode = 404
            res.end(JSON.stringify({
              error: '审查报告不存在，请先在 yudream-frontend 根目录执行 pnpm audit:ui 生成',
            }))
          }
        })
      },
    } satisfies PluginOption,

    {
      name: 'vite-plugin-debug-plugin',
      enforce: 'pre',
      transform: (code, id) => {
        if (/src\/main.ts$/.test(id)) {
          if (viteEnv.VITE_APP_DEBUG_TOOL === 'eruda') {
            code = `
${code}
import eruda from 'eruda'
eruda.init()
            `
          }
          else if (viteEnv.VITE_APP_DEBUG_TOOL === 'vconsole') {
            code = `
${code}
import VConsole from 'vconsole'
new VConsole()
            `
          }
          return {
            code,
            map: null,
          }
        }
      },
    },
  ]
  return vitePlugins
}
