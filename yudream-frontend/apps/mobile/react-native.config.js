// Re.Pack 5 通过 community CLI 命令接管 start/bundle；MF 宿主打包走 Rspack。
const path = require('path');

module.exports = {
  commands: require('@callstack/repack/commands/rspack'),
  // pnpm monorepo：react-native 经符号链接解析，避免把宿主机路径写死。
  reactNativePath: path.dirname(require.resolve('react-native/package.json', { paths: [__dirname] })),
  project: {
    android: { sourceDir: './android' },
  },
};
