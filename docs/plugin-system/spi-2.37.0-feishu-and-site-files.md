# SPI 2.37.0：飞书云文档端口与站点目录文件端口

本版本新增两个宿主框架能力端口：`system.feishu.PluginFeishuService`（飞书云文档读取 + 块级编辑原语，经宿主「飞书开放平台」能力）与 `system.storage.PluginSiteFileStore`（站点目录文件，SPI 代理）。两者职责完全分离：**飞书端口只服务飞书云文档（读 + 条件可用的块级编辑），站点目录端口只读写本站点目录文件，禁止互相替代或混用。**

版本须完成 Nexus 发布与验签后，独立插件仓才能升级依赖；发布前插件仓不得引用 2.37.0。

## 1. 飞书云文档端口

`PluginContext` 新增（default 方法，保持第三方实现二进制兼容）：

```java
PluginFeishuService feishu();   // 默认委托 framework().feishu()
```

`FrameworkServices` 新增：

```java
default PluginFeishuService feishu();   // 默认实现 enabled() 恒 false，其余方法抛 UnsupportedOperationException
```

### 能力与双闸门

- 能力编码 `PluginFeishuService.CAPABILITY_CODE = "feishu"`（「平台能力 > 飞书开放平台」）。
- 项目闸门：`yudream.platform.capabilities.feishu.enabled`（后端内部配置，默认开启，不提供部署侧环境变量门控），关闭时宿主不装配能力 Provider。
- 运行侧闸门：管理员在能力页启用并维护配置（App ID、App Secret 加密入库、开放平台地址与超时），
  默认每用户每分钟/每日额度可在能力页与「飞书集成」页调整。
- `enabled()` 为 true 要求能力启用 **且** 应用凭据已配置；false 时插件应隐藏入口/给出提示，禁止尝试其他写入通道。

### 方法契约（当前唯一在范围的 API 模块：云文档）

```java
boolean enabled();

PluginFeishuDocPage<PluginFeishuDriveFile> driveFiles(String folderToken, String pageToken, int pageSize);
PluginFeishuDocPage<PluginFeishuWikiSpace> wikiSpaces(String pageToken, int pageSize);
PluginFeishuDocPage<PluginFeishuWikiNode> wikiNodes(String spaceId, String parentNodeToken, String pageToken, int pageSize);
PluginFeishuDocument document(String documentId);
String documentContent(String documentId, Integer lang);
PluginFeishuDocPage<PluginFeishuDocBlock> documentBlocks(String documentId, String pageToken, int pageSize);

PluginFeishuBlockWriteResult createBlockChildren(String documentId, String parentBlockId,
                                                 List<String> childrenJson, Integer index);
void deleteBlockChildren(String documentId, String parentBlockId, int startIndex, int endIndex);
PluginFeishuBlockWriteResult updateBlockElements(String documentId, String blockId,
                                                 List<String> elementsJson, Long documentRevisionId);
```

- 读取覆盖 Drive 云空间文件、Wiki 知识空间与空间节点列表、docx 文档元信息与纯文本内容、文档块分页列表；
  Wiki 节点按 `parentNodeToken` 分层（空串为根级，`pageSize` 上限 50），节点字段原样返回飞书字符串；
  分页由插件携带上一页 `nextPageToken` 再次调用，每次调用各计 1 次额度。
- 编辑只到**块级原语**：追加子块（POST children）、按下标区间删除子块（DELETE children batch_delete）、
  更新文本承载块 elements（PATCH，仅 `update_text_elements` 语义）。**不接收整块 PATCH**——
  飞书更新块的请求体是操作载荷而非块对象，按块对象提交会造成有损语义。
- **写原语条件可用**：能否编辑取决于应用对目标文档的编辑权限——应用自有文档可直接编辑；
  他人创建的文档需要文档 owner 授权并把本应用添加为可访问应用。宿主不提供静态可写开关，
  无权限时写调用按飞书错误（如 403）抛运行时异常；插件必须按每次调用处理失败（对照宿主前端
  「可编辑范围」提示的同一语义：应用无权限的块/文档只读），禁止假设一定可写。
  前端可参考的判定线索是文档块类型（文本块可编辑、图片等只读）。
- 不提供文档/知识库/云空间的创建、删除、移动、权限与分享。
- **保真与可扩展**：`PluginFeishuDocBlock.rawJson()`、`childrenJson`、`elementsJson` 一律是飞书原始
  JSON 原文，宿主不做「块 -> 文本 -> 块」的有损往返；读回的块 `rawJson` 可直接回填到
  `createBlockChildren` 提交，上游新增字段无需升级 SPI。全部标识与时间保持飞书返回字符串
  （长 ID 不转数值），UI/TS 侧仍按字符串处理。

### 额度与身份（只在宿主页面展示，不经 SPI 下发）

- 每次调用按**当前登录用户**在调用前原子预占分钟/日额度，宿主页面（「飞书集成 > 额度与限流」与
  能力页）展示实际用量与上限；配额不是 RBAC 角色档位，插件不需要也不应自建额度模型。
- 飞书在真正执行前拒绝的调用（限频 429 / code 99991400、鉴权/权限 401/403）退还预占；其他错误与
  网络异常保守计数不退还。
- 归属只取会话身份（Sa-Token 登录态 / API Key 上下文，见宿主 `FeishuActorGateway`）；无法解析
  登录主体或额度耗尽时 fail-closed 抛运行时异常，插件按软依赖降级并展示宿主文案。

## 2. 站点目录文件端口

`PluginContext` 新增：

```java
PluginSiteFileStore siteFiles();   // 默认委托 framework().siteFiles(pluginCode())
```

`FrameworkServices` 新增：

```java
default PluginSiteFileStore siteFiles(String pluginCode);  // 默认抛 UnsupportedOperationException
```

### 目录锚定与创建

- 目录固定为 `sites/{siteCode}/plugins/{pluginCode}/`；`siteCode` 由宿主解析
  （`yudream.platform.site.code`，单站点部署默认 `default`），**插件不可指定站点**；
  站点数据根 `yudream.platform.site.root` 默认 `sites`（相对进程工作目录）。
- 宿主在插件启用成功时幂等创建该目录（创建失败只告警不阻断启用），
  `pluginDirectory()` 每次访问都会再确保一次，返回站点数据根下的相对逻辑路径
  `{siteCode}/plugins/{pluginCode}`。

### 方法契约

```java
String pluginDirectory();
String write(String relativePath, InputStream content, long contentLength, String contentType);
List<String> list(String directory);
PluginStoredFile read(String relativePath);
void delete(String relativePath);
Optional<PluginStoredFile> readShared(String ownerPluginCode, String relativePath);
```

- **写隔离**：写/删/列/读一律限制在调用插件自属目录；
  绝对路径、盘符、`..`、`.`、空段、控制字符与越界解析全部拒绝（fail-closed）。
- **祖先链符号链接防护**：相对路径逐级以 `NOFOLLOW_LINKS` 校验——任一层
  （含 `sites/{siteCode}/plugins` 链与插件目录自身）是符号链接或非目录即拒绝，
  防止借预置/并发替换的父级链接（如 `notes -> /outside`）走出锚点；写入目录创建后
  再次复验，且以「真实路径必须仍解析在站点数据根内」做边界兜底；落点复验失败会清理
  并拒绝，读取最终文件以 `NOFOLLOW` 打开（最终段被换成链接时安全失败）。
- 写入先落同目录临时文件再原子替换，单文件上限 `MAX_WRITE_BYTES`（64 MiB）：
  **不信任调用方声明的 `contentLength`**，宿主用有界拷贝边读边计数，实际字节数一旦超过上限
  立即拒绝（超限分片不落盘），声明长度与实际不一致同样拒绝并清理临时文件；临时文件不残留在目录列表。
- `delete` 只删常规文件，目录与符号链接拒绝；文件不存在时静默返回。
- 符号链接一律拒绝，读取只接受常规文件。
- **本机部署约束**：Java NIO 没有 `openat`/`dirfd` 语义，无法把「校验目录」与
  「打开/创建文件」原子绑定；上述逐级校验、创建后复验与真实路径边界只能检测本机并发
  替换并安全失败，不能在所有平台 100% 消除 TOCTOU 窗口。站点数据根必须只对宿主进程账号
  可写（同站点/同插件目录不得授予其他本机用户写权限）。
- 文件系统实现不持久化 `contentType`（读取按扩展名探测，可能为空串），
  需要强内容类型语义的文件（图片/下载）仍应走 `context.files()` 对象存储。

### 跨插件读取授权

- 跨插件读取只经 `readShared(ownerPluginCode, relativePath)`：宿主授权服务裁决，
  当前内置规则为「当前认证主体持有宿主插件管理查看权限 `platform:plugin:view`」；
  无认证主体、无该权限或路径越过目标插件自属目录抛 `SecurityException`。
- **禁止**按插件实例自造权限码（宿主没有 `plugin:{owner}:view` 之类的授权语义）；
  目标文件不存在返回空。读自己的文件不走该判定。

## 3. SDK / components 与本版本的关系

- 本版本**只发布 SPI**（发布须另行授权）：两个端口都是后端插件契约，飞书 UI 与额度展示由宿主
  「平台能力 > 飞书开放平台」与「飞书集成」页面承载，站点目录是插件后端落盘通道；
  插件前端的宿主能力面（`@yudream/plugin-sdk`）不新增导出，`@yudream/components` 不变。
- 插件前端若需展示飞书数据，仍由插件后端经 SPI 读取后经插件自身 API 下发，不要把宿主
  `/api/platform/feishu/**` 当作插件公开面直连。

## 4. 兼容与迁移

- 两个端口在 `FrameworkServices`/`PluginContext` 都是 default 方法：旧宿主不会编译失败；
  旧宿主上飞书端口 `enabled()` 恒 false、读取方法抛 `UnsupportedOperationException`，
  站点目录端口在 `framework().siteFiles(...)` 处抛 `UnsupportedOperationException`，插件按软依赖降级。
- 插件对飞书能力的可用性判定只信 `enabled()`；对站点目录端口以首次调用的异常为降级信号。
- 站点目录端口是**宿主对站点目录的 SPI 代理**：插件写入落在自属目录、读取受站点/用户授权约束；
  它不承担飞书云文档编辑，飞书端口也不承担站点文件落盘。
