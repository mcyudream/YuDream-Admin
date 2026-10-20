# SPI 2.38.0（设计草案）：插件注册网站工作台区块

> **状态**：设计草案，**未实现、未发布**。SPI 版本号在实施时按 `docs/dev/contract-release.md` 定级；完成 Nexus 发布与验签前，插件仓不得引用本契约。
> **目标**：插件能像 WordPress 插件注册区块一样，向网站工作台插入器贡献区块；宿主仍是唯一的内容安全边界。

## 1. 范围与原则

- 本版本只支持**静态区块**：插件提供节点模板（与站点文档 `StudioNode` 同构的 JSON），插入后成为页面草稿的普通节点，与插件不再联动。
- **不允许**插件向宿主画布或公开站注入任意 HTML/JS/Vue 组件；动态区块（运行期数据绑定、交互组件）需要单独的组件契约与信任模型，另立版本。
- 宿主现有区块注册表（`site-studio-blocks`，名称 `{命名空间}/{名称}`）是唯一入口：核心区块 `core/*`、用户模板 `template/*`、插件区块 `{pluginCode}/*` 共用搜索、分类、最近使用与插入位置语义。

## 2. 服务契约

`PluginContext` 新增（default 方法，保持第三方实现二进制兼容）：

```java
void registerStudioBlock(PluginStudioBlock block);
```

`online.yudream.base.plugin.spi.studio.PluginStudioBlock` 为 record：

| 字段 | 约束 |
|---|---|
| `name` | 插件内唯一，`[a-z0-9-]{1,40}`；对外名称为 `{pluginCode}/{name}` |
| `title` | 必填，≤ 20 字 |
| `category` | 宿主分类词表：`text`/`media`/`design`/`layout`/`widgets`；其他值拒绝 |
| `icon` | `morph:` 图标名（lucide 全集），缺省 `morph:puzzle` |
| `description`、`keywords` | 可选；关键词 ≤ 10 个，每个 ≤ 20 字 |
| `nodesJson` | 节点数组 JSON，≤ 256 KiB |
| `permission` | 可选；为空表示拥有 `platform:cms:edit` 即可见 |
| `sort` | 分类内排序 |

注解等价形式：`@PluginStudioBlock`（可重复，容器 `@PluginStudioBlocks`），`nodes` 指向插件 JAR 内资源路径（如 `studio-blocks/hero.json`），由注解注册器读取后转成 `registerStudioBlock` 调用。

## 3. 宿主校验（fail-fast）

注册即校验，失败抛 `BizException` 且该区块不登记：

- `nodesJson` 走 `StudioDocumentValidator.validateNodes`，与站点文档和用户区块模板**完全相同的规则**：安全标签/属性白名单、样式与 URL 校验、`data:` 仅限白名单媒体、ID 唯一、层级 ≤ 32、节点 ≤ 2000。
- 节点里的资源 URL 只允许同源相对路径或 `https:`；插件 JAR 内静态资源须经插件静态资源端点暴露后以相对路径引用。
- 名称冲突（同插件重复 `name`）报错；不同插件同名互不影响（命名空间隔离）。

## 4. 生命周期与下发

- 注册随插件 disable/unload/reload 自动移除，无需手工注销。
- 已启用插件的区块经 `PluginRuntimeGateway.studioBlocks()` 汇总，随 `GET /api/platform/plugins/frontend-manifest` 的 `studioBlocks[]` 下发；只对拥有 `platform:cms:view` 的登录用户下发，`permission` 不满足的区块不下发。
- 前端在网站工作台加载时把清单注册进区块注册表（`source: 'plugin'`），插入时仍经 `instantiateStudioBlock` 二次校验并重建全部节点 ID；插件被停用后清单不再包含其区块，已插入页面的内容不受影响。
- 能力闸门：`cms` 能力未启用时不下发、不注册（与菜单、路由同一规则，见 `docs/dev/backend-architecture.md` §5.5）。

## 5. 插件接入要求

1. 区块内容只用站点主题变量（`var(--accent)` 等）表达配色，不写死品牌色；需要的变量在说明中写明，宿主插入时会提示缺失变量。
2. 文案使用中文、可直接编辑；图片使用占位或插件静态资源相对路径，不引用第三方追踪链接。
3. 一个区块只表达一个可复用结构（如“价格表”“团队成员卡片”），不要把整页塞进一个区块。
4. 不要用区块传递业务数据；需要实时数据的场景等待动态区块契约。

## 6. 实施清单（未开始）

- SPI：`PluginStudioBlock` record + `PluginContext.registerStudioBlock` + 注解与注册器；契约版本升级与发布。
- 宿主：运行期登记与回收、`frontend-manifest.studioBlocks[]`、校验复用 `validateNodes`、能力与权限过滤。
- 前端：清单注册进 `studioBlockRegistry`（`source: 'plugin'`），插入器展示插件来源标记。
- SDK/文档：插件仓示例插件与教程；双仓同步按 `docs/dev/repo-sync.md` 执行。
