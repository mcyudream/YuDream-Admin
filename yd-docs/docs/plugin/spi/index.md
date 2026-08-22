# SPI 版本说明与升级指引

`yudream-plugin-spi` 是插件唯一编译期契约模块，坐标 `online.yudream.base:yudream-plugin-spi`。**当前版本：2.7.0**。

## 版本化文档结构

SPI 接口文档按版本号组织，每个版本一个完整教程目录：

```text
/plugin/spi/
  index.md                 ← 本页（版本清单 + 升级指引 + Agent 增量生成规范）
  v1/
    core                   生命周期与 PluginContext
    annotations            注解声明
    http                   HTTP 端点
    frontend               前端元数据
    framework-services     框架能力端口
```

每个页面按统一结构编写：**作用说明 → 方法签名表（名称/签名/参数/返回/说明）→ 使用示例 → 注意事项**。这保证任意版本教程可独立阅读，也便于机器比对。

## 版本清单

| 版本 | 状态 | 说明 |
|---|---|---|
| [v1 (2.7.0)](/plugin/spi/v1/core) | ✅ 当前 | 全量 API 教程 |

## 升级指引

- 插件侧升级 SPI 只需修改依赖版本并按下方变更记录适配；宿主与 SPI 版本兼容矩阵见插件商店索引。
- SPI 遵循语义化原则：新增接口/方法为 minor；删除或改变签名为 major，会提前在变更页给出迁移代码对照。
- 未验证发布的版本不得用于下游（发布流水线 verify 通过后才可用）。

## 面向 Coding Agent 的增量更新规范

本目录结构专门支持"编码代理自动复制增量更新教程"。当 SPI 发布新版本时，代理应按以下流程为新版本生成 `vx.y/` 目录：

1. **Diff 源码**：对比 `yudream-plugins/yudream-plugin-spi/src/main/java` 在新旧版本间的变化：
   - 新增类 → 在对应分类页新增章节；
   - 删除类 → 在迁移章节记录替代方案；
   - 签名变化的方法 → 更新签名表并在"注意事项"标注行为差异。
2. **复制上一版目录**作为起点（如 `cp -r docs/plugin/spi/v1 docs/plugin/spi/v2`），只修改有变化的章节。
3. **保持章节骨架不变**：每页的 H2 标题、表格列（成员/类型/说明）、示例格式必须稳定，便于 diff 与检索。
4. **更新本页版本清单**：新增一行，标注状态（当前/已废弃）与破坏性变更摘要。
5. **同步 config.ts 侧边栏**（`.vitepress/config.ts` 中 `pluginSidebar`）加入新版本条目。
6. **校验**：运行 `pnpm build` 确认所有链接有效。

每篇教程中示例代码必须可直接编译（import 路径、泛型、record 构造器与源码一致），参数说明覆盖到每个字段。
