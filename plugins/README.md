# 外部插件目录

这个目录是核心运行时默认扫描的插件 JAR 落点。

约定：

1. 生产环境与本地运行默认从这里加载外部插件
2. 核心仓不再默认扫描 `yudream-plugins/*/target`
3. 独立插件仓产出的 JAR 应复制或挂载到这里

示例：

```text
plugins/
  yudream-plugin-wallet-1.0.0.jar
  yudream-plugin-project-progress-1.0.0.jar
```
