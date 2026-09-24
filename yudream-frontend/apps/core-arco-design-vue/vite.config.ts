import fs from 'node:fs'
import path from 'node:path'
import process from 'node:process'
import dayjs from 'dayjs'
import { defineConfig, loadEnv } from 'vite'
import { parseLoadedEnv } from 'vite-plugin-env-parse'
import pkg from './package.json'
import createVitePlugins from './vite/plugins'

// workspace 依赖在 __SYSTEM_INFO__ 里只会显示 "workspace:*"，
// 「关于系统」页需要的真实版本号改为构建期直接从各包 package.json 读取注入。
function readWorkspacePackageVersion(packageDir: string): string {
  try {
    const manifest = JSON.parse(fs.readFileSync(path.resolve(__dirname, '../../packages', packageDir, 'package.json'), 'utf-8'))
    return manifest.version ?? '0.0.0'
  }
  catch {
    return '0.0.0'
  }
}

// https://vitejs.dev/config/
export default defineConfig(({ mode, command }) => {
  const env = parseLoadedEnv(loadEnv(mode, process.cwd()))
  // 全局 scss 资源
  const scssResources: string[] = []
  fs.readdirSync('src/assets/styles/resources').forEach((dirname) => {
    if (fs.statSync(`src/assets/styles/resources/${dirname}`).isFile()) {
      scssResources.push(`@use "/src/assets/styles/resources/${dirname}" as *;`)
    }
  })
  // dev 下前端与启动器等外部客户端统一走站点同源 /api，由下面的代理转发；
  // 代理目标用独立变量（dev 后端地址），前端 axios 的 VITE_APP_API_BASEURL 恒为同源 '/'。
  const devApiTarget = env.VITE_DEV_API_TARGET || 'http://localhost:8080'
  return {
    // 开发服务器选项 https://cn.vitejs.dev/config/server-options
    server: {
      open: true,
      host: true,
      port: 9000,
      proxy: {
        // 与生产 nginx 的 ^~ /api/ 反代保持同语义（保留 /api 前缀）：
        // 生产与开发的后端代理统一为 /api，前端同源调用，启动器等
        // 外部客户端也以站点同源地址访问后端。
        // xfwd 透传 X-Forwarded-Proto/Host，插件据此重构出的自报 origin
        // 是站点地址而不是 dev 后端的 8080。
        '/api': {
          target: devApiTarget,
          changeOrigin: command === 'serve' && env.VITE_ENABLE_PROXY,
          ws: true,
          xfwd: true,
        },
        '/v3/api-docs': {
          target: devApiTarget,
          changeOrigin: command === 'serve' && env.VITE_ENABLE_PROXY,
        },
        '/swagger-ui': {
          target: devApiTarget,
          changeOrigin: command === 'serve' && env.VITE_ENABLE_PROXY,
        },
        '/swagger-ui.html': {
          target: devApiTarget,
          changeOrigin: command === 'serve' && env.VITE_ENABLE_PROXY,
        },
      },
    },
    // 构建选项 https://cn.vitejs.dev/config/build-options
    build: {
      outDir: mode === 'production' ? 'dist' : `dist-${mode}`,
      sourcemap: env.VITE_BUILD_SOURCEMAP,
    },
    define: {
      __SYSTEM_INFO__: JSON.stringify({
        pkg: {
          dependencies: pkg.dependencies,
          devDependencies: pkg.devDependencies,
        },
        lastBuildTime: dayjs().format('YYYY-MM-DD HH:mm:ss'),
      }),
      __YUDREAM_PACKAGE_VERSIONS__: JSON.stringify({
        'plugin-sdk': readWorkspacePackageVersion('plugin-sdk'),
        'components': readWorkspacePackageVersion('components'),
        'dataviz': readWorkspacePackageVersion('dataviz'),
      }),
    },
    plugins: createVitePlugins(mode, command === 'build'),
    optimizeDeps: {
      exclude: [
        '@yudream/components',
        '@fantastic-admin/composables',
      ],
    },
    resolve: {
      alias: {
        '@': path.resolve(__dirname, 'src'),
        '#': path.resolve(__dirname, 'src/types'),
        '@yudream/plugin-sdk': path.resolve(__dirname, '../../packages/plugin-sdk/src/index.ts'),
      },
    },
    css: {
      preprocessorOptions: {
        scss: {
          additionalData: scssResources.join(''),
        },
      },
    },
  }
})
