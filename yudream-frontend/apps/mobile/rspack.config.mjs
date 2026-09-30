import fs from 'node:fs';
import path from 'node:path';
import * as Repack from '@callstack/repack';
import { rspack } from '@rspack/core';

const dirname = Repack.getDirname(import.meta.url);

// .env（可选）：YDAM_OFFICIAL_UPDATE_BASE 指定官方更新源站点，默认 www.swustmc.cn。
// 简单 KEY=VALUE 解析即够，不引入 dotenv 依赖。
function loadEnvFile() {
  try {
    const raw = fs.readFileSync(path.join(dirname, '.env'), 'utf8');
    const map = {};
    for (const line of raw.split(/\r?\n/)) {
      const trimmed = line.trim();
      if (!trimmed || trimmed.startsWith('#')) {
        continue;
      }
      const eq = trimmed.indexOf('=');
      if (eq <= 0) {
        continue;
      }
      map[trimmed.slice(0, eq).trim()] = trimmed.slice(eq + 1).trim().replace(/^["']|["']$/g, '');
    }
    return map;
  } catch {
    return {};
  }
}

const envFile = loadEnvFile();
const officialUpdateBase = envFile.YDAM_OFFICIAL_UPDATE_BASE ?? 'https://www.swustmc.cn';

export default (env) => {
  const {
    mode = 'development',
    platform = process.env.PLATFORM ?? 'android',
    devServer = undefined,
  } = env;

  return {
    mode,
    context: dirname,
    entry: './index.js',
    // 生产也产出 sourcemap：gradle 的 hermesc 步骤需要 packager.map 作为输入，
// 不参与 APK 打包体积，仅留在中间目录。
devtool: 'source-map',
    resolve: {
      ...Repack.getResolveOptions(),
      alias: {
        '@': path.resolve(dirname, 'src'),
      },
    },
    output: {
      path: path.join(dirname, 'build/generated', platform),
      uniqueName: 'yudream-mobile-host',
    },
    module: {
      rules: [
        ...Repack.getJsTransformRules(),
        // pnpm autoInstallPeers 会把 @react-navigation 的可选 peer
        // @react-native-masked-view 装上，其源码是 Flow 注解（swc 只认 TS），
        // 需先经 flow-loader 剥注解再交给默认转换链（本规则靠后、先执行）。
        {
          test: /\.[jt]sx?$/,
          include: /node_modules[\\/]+@react-native-masked-view[\\/]/,
          use: [{ loader: '@callstack/repack/flow-loader' }],
        },
        ...Repack.getAssetTransformRules(),
      ],
    },
    devServer: devServer
      ? { host: '0.0.0.0', port: 8081, hmr: true }
      : undefined,
    plugins: [
      new Repack.RepackPlugin(),
      // 字节码交给 gradle 的 hermesEnabled 管线编译；这里再开会导致 hermesc 对着
      // 已编译产物当 JS 源二次编译（Invalid UTF-8 continuation byte）。
      new Repack.plugins.HermesBytecodePlugin({
        enabled: false,
        test: /\.(js)?bundle$/,
      }),
      // Re.Pack 5.x：MF2 插件包装 @module-federation/enhanced，负责把 remoteEntry
      // 的加载接进 ScriptManager（缓存/离线/回滚在 src/core/plugins 里实现）。
      new Repack.plugins.ModuleFederationPluginV2({
        name: 'host',
        dts: false,
        // remotes 为空：插件远程模块在启动期由 manifest 驱动动态注册
        // （见 src/core/plugins/pluginLoader.ts）。
        remotes: {},
        shared: {
          react: { singleton: true, eager: true, requiredVersion: '18.3.1' },
          'react-native': { singleton: true, eager: true },
          // 宿主向插件注入的稳定契约；插件以 shared 方式消费，禁止自打包。
          '@yudream/plugin-sdk-mobile': {
            singleton: true,
            eager: true,
            version: '0.1.0',
          },
        },
      }),
      new rspack.DefinePlugin({
        __PLATFORM__: JSON.stringify(platform),
        __YDAM_OFFICIAL_UPDATE_BASE__: JSON.stringify(officialUpdateBase),
      }),
    ].filter(Boolean),
  };
};
