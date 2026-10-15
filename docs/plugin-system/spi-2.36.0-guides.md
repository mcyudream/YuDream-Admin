# SPI 2.36.0：插件新手引导

本版本新增声明式新手引导贡献点。版本须完成 Nexus 发布与验签后，独立插件仓才能升级依赖；发布前插件仓不得引用 2.36.0。

## 服务契约

`PluginContext` 新增（default 方法，保持第三方实现二进制兼容）：

```java
void registerGuide(PluginGuide guide);
```

`online.yudream.base.plugin.spi.guide.PluginGuide` 为 record：`code`（插件内唯一）、`title`、`routePath`（引导自动播放的页面路径，必须以 `/` 开头）、`permission`（为空表示登录即可见）、`sort`、`steps`。`PluginGuideStep` 为 record：`target`（`[data-tour="..."]` 形式选择器，为空表示该步骤居中展示）、`title`、`content`（必填，自含完整语义）、`placement`（top/bottom/left/right，默认 bottom）。

注解等价形式：`@PluginGuide`（可重复，容器 `@PluginGuides`）+ `@PluginGuideStep[]`，由注解注册器转成 `registerGuide` 调用。

宿主校验（fail-fast，注册即抛 `BizException`）：编码/标题/路径/步骤非空，路径以 `/` 开头，`target` 仅允许 `[data-tour="标识"]`（标识 `a-z0-9:._-`，最长 80），`placement` 限定枚举词表。**禁止任意 CSS 选择器进入宿主 DOM。**

## 生命周期与下发

注册随插件 disable/unload/reload 自动移除，无需手工注销；重复编码在插件内报重复。已启用插件的引导经 `PluginRuntimeGateway.guides()` 汇总（按 sort、pluginCode、code 排序），随 `GET /api/platform/plugins/frontend-manifest` 的 `guides[]` 字段下发；匿名访客（公开路由清单）不下发。宿主按登录用户在浏览器侧记录完成态（`localStorage` 按用户隔离），插件不感知、不存储完成状态。

## 宿主播放行为

- 用户进入 `routePath` 匹配页面且该引导未完成时，宿主自动播放该页第一个未完成引导；目标元素等待超时（约 8 秒）则放弃本次且不写完成态，下次进入重试。
- 全局 AI 前端工具 `ui.tour` 可按引导编码触发播放（工具参数限定为当前页面可用引导，偏好开关默认关闭）；引导只做展示与定位，**不自动点击、不提交表单**。
- 步骤 `target` 缺失或不可见时该步骤自动降级为居中展示，不中断流程。

## 插件接入要求

1. 页面元素以 `data-tour="稳定标识"` 登记；标识随页面重构保持稳定，宿主与 AI 依赖它定位。
2. 每步 `content` 必须自含完整说明，不依赖被高亮元素的 tooltip 或外部文档。
3. 引导讲述的操作必须对拥有 `permission` 的用户真实可用；不要用引导替代帮助文档或错误提示。
4. 渲染统一由宿主 `YdTour`（components 公开入口）承载，插件不得自带 tour 引擎或在引导外注入全局遮罩。
