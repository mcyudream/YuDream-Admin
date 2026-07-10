# Satori 平台能力实施计划

> **For implementer:** Use TDD throughout. Write failing test first. Watch it fail. Then implement.

**Goal:** 在 YuDream Admin 中实现完整的 Satori v1 平台能力、无界面消息渲染服务，以及向插件开放的全协议调用和消息交互注册能力。

**Architecture:** 核心后端按 Domain/Application/Infrastructure/Interfaces 四层实现 `satori` 与 `message-render` 两个可选能力。Satori 标准接口采用强类型网关，平台专属接口保留受控 raw/internal 入口；Headless Chromium 运行在独立 Render Server 中；插件仅通过稳定 SPI 和可释放交互注册表访问能力。

**Tech Stack:** Java 21、Spring Boot 3.5、Reactor Netty、MongoDB、JUnit 5、Node.js 22、TypeScript、Fastify、Playwright Chromium、Markdown-it、Vitest、Vue 3、Arco Design Vue、pnpm、Docker Compose。

---

## 通用执行约束

- 每个任务先新增失败测试，执行并确认失败原因，再写最小实现。
- 每个任务验证通过后只提交本任务文件，提交信息使用中文。
- 所有 Controller 转换放入 interface assembler；应用服务不得返回 `res` 或接收 `request`。
- 所有平台 ID、插件 ID 与前端可见的事件序列号使用字符串。
- `CapabilityProvider.enable(...)` 不建立连接，连接只在显式业务调用时延迟创建。
- 不修改或提交当前工作树中与本计划无关的现有改动。

## Task 1：固化协议契约与资源模型

**Files:**

- Create: `docs/satori/protocol-v1-contract.md`
- Create: `yudream-domain/src/main/java/online/yudream/base/domain/platform/satori/model/SatoriModels.java`
- Create: `yudream-domain/src/main/java/online/yudream/base/domain/platform/satori/model/SatoriPage.java`
- Create: `yudream-domain/src/main/java/online/yudream/base/domain/platform/satori/model/SatoriBidiPage.java`
- Create: `yudream-domain/src/main/java/online/yudream/base/domain/platform/satori/enumerate/SatoriChannelType.java`
- Create: `yudream-domain/src/main/java/online/yudream/base/domain/platform/satori/enumerate/SatoriLoginStatus.java`
- Create: `yudream-domain/src/main/java/online/yudream/base/domain/platform/satori/enumerate/SatoriOpcode.java`
- Test: `yudream-bootstrap/src/test/java/online/yudream/base/domain/platform/satori/SatoriProtocolModelTest.java`

**Step 1: Write the failing test**

测试 JSON fixture 能完整反序列化 Login、Event、Message、Channel、Guild、GuildMember、GuildRole、Friend、Emoji、User、Argv、Button、Meta、List 和 BidiList；明确断言可选字段缺失与显式 `null` 均可接受，`sn` 以字符串边界输出。

```java
@Test
void shouldKeepPlatformIdsAndSequenceAsStringsAtBoundary() {
    SatoriEvent event = fixture("satori/event-message-created.json", SatoriEvent.class);
    assertThat(event.sn()).isEqualTo("9007199254740993");
    assertThat(event.login().user().id()).isEqualTo("9223372036854775806");
    assertThat(event.message().content()).contains("<at id=\"42\"/>");
}
```

**Step 2: Run test and confirm failure**

```powershell
$env:JAVA_HOME='C:/Users/SiberianHusky/.jdks/ms-21.0.10'; mvn -pl yudream-bootstrap -Dtest=SatoriProtocolModelTest test
```

Expected: FAIL，Satori 模型不存在。

**Step 3: Implement the model**

使用不可变 record/值对象表达协议资源。`docs/satori/protocol-v1-contract.md` 列出文档中的全部 API、请求参数、响应类型、事件和实验性标记，作为后续契约测试清单。

**Step 4: Run test and confirm pass**

Expected: PASS，所有 fixture 可解析且字符串边界无精度损失。

**Step 5: Commit**

```powershell
git add docs/satori yudream-domain yudream-bootstrap/src/test/java/online/yudream/base/domain/platform/satori
git commit -m "feat: 建立 Satori 协议模型与契约清单"
```

## Task 2：实现安全消息元素树与编码器

**Files:**

- Create: `yudream-domain/src/main/java/online/yudream/base/domain/platform/satori/message/SatoriElement.java`
- Create: `yudream-domain/src/main/java/online/yudream/base/domain/platform/satori/message/SatoriMessage.java`
- Create: `yudream-domain/src/main/java/online/yudream/base/domain/platform/satori/message/SatoriMessageBuilder.java`
- Create: `yudream-domain/src/main/java/online/yudream/base/domain/platform/satori/service/SatoriMessageEncoder.java`
- Test: `yudream-bootstrap/src/test/java/online/yudream/base/domain/platform/satori/SatoriMessageEncoderTest.java`

**Step 1: Write the failing tests**

覆盖文本与属性转义、全部标准元素、嵌套 message/quote/author、button、未知适配器命名空间元素、非法标签名和属性名拒绝。

```java
@Test
void shouldEncodeStandardAndNamespacedElementsSafely() {
    SatoriMessage message = SatoriMessageBuilder.create()
            .text("a < b & c")
            .at("42", "Alice")
            .element("kook:card", Map.of("size", "lg"), List.of())
            .build();
    assertThat(encoder.encode(message))
            .isEqualTo("a &lt; b &amp; c<at id=\"42\" name=\"Alice\"/><kook:card size=\"lg\"/>");
}
```

**Step 2: Run and confirm failure**

```powershell
mvn -pl yudream-bootstrap -Dtest=SatoriMessageEncoderTest test
```

Expected: FAIL，编码器不存在。

**Step 3: Implement minimal element AST and encoder**

不使用字符串拼接暴露给调用方；编码器集中校验名称并转义字符。为 `at/sharp/emoji/a/img/audio/video/file/strong/em/ins/del/spl/code/sup/sub/br/p/message/quote/author/button` 提供 builder 方法。

**Step 4: Run test and confirm pass**

**Step 5: Commit**

```powershell
git commit -am "feat: 实现 Satori 消息元素编码器"
```

## Task 3：建立连接、登录、事件游标与仓储

**Files:**

- Create: `yudream-domain/src/main/java/online/yudream/base/domain/platform/satori/aggregate/SatoriConnection.java`
- Create: `yudream-domain/src/main/java/online/yudream/base/domain/platform/satori/aggregate/SatoriLogin.java`
- Create: `yudream-domain/src/main/java/online/yudream/base/domain/platform/satori/aggregate/SatoriEventRecord.java`
- Create: `yudream-domain/src/main/java/online/yudream/base/domain/platform/satori/repo/SatoriConnectionRepo.java`
- Create: `yudream-domain/src/main/java/online/yudream/base/domain/platform/satori/repo/SatoriLoginRepo.java`
- Create: `yudream-domain/src/main/java/online/yudream/base/domain/platform/satori/repo/SatoriEventRepo.java`
- Create: `yudream-infrastructure/src/main/java/online/yudream/base/infra/platform/satori/dataobj/*.java`
- Create: `yudream-infrastructure/src/main/java/online/yudream/base/infra/platform/satori/mapper/SatoriInfraMapper.java`
- Create: `yudream-infrastructure/src/main/java/online/yudream/base/infra/platform/satori/impl/*RepoImpl.java`
- Test: `yudream-bootstrap/src/test/java/online/yudream/base/domain/platform/satori/SatoriConnectionTest.java`
- Test: `yudream-bootstrap/src/test/java/online/yudream/base/infra/platform/satori/SatoriPersistenceMappingTest.java`

**Step 1: Write failing tests**

断言 Base URL 规范化、连接启停状态转换、Token 不出现在 `toString`、Login 唯一键、`connectionId + sn` 幂等键、事件 TTL 字段和 DO/domain 往返映射。

**Step 2: Run and confirm failure**

```powershell
mvn -pl yudream-bootstrap -Dtest=SatoriConnectionTest,SatoriPersistenceMappingTest test
```

**Step 3: Implement aggregates, repos, DOs and mapper**

凭证通过独立 `SatoriCredentialCipher` 端口加密；密钥来自环境变量，不把明文 Token 保存到 Mongo。事件记录使用 TTL 索引字段 `expireAt`，列表查询强制分页。

**Step 4: Run and confirm pass**

**Step 5: Commit**

```powershell
git add yudream-domain/src/main/java/online/yudream/base/domain/platform/satori yudream-infrastructure/src/main/java/online/yudream/base/infra/platform/satori yudream-bootstrap/src/test/java/online/yudream/base
git commit -m "feat: 建立 Satori 连接与事件持久化"
```

## Task 4：实现完整 HTTP API、上传与内部接口网关

**Files:**

- Create: `yudream-domain/src/main/java/online/yudream/base/domain/platform/satori/service/SatoriApiGateway.java`
- Create: `yudream-domain/src/main/java/online/yudream/base/domain/platform/satori/service/SatoriInternalGateway.java`
- Create: `yudream-infrastructure/src/main/java/online/yudream/base/infra/platform/satori/service/ReactorSatoriApiGateway.java`
- Create: `yudream-infrastructure/src/main/java/online/yudream/base/infra/platform/satori/service/SatoriHttpErrorMapper.java`
- Test: `yudream-bootstrap/src/test/java/online/yudream/base/infra/platform/satori/ReactorSatoriApiGatewayTest.java`

**Step 1: Write failing MockWebServer tests**

参数化覆盖契约清单中的全部标准方法：

```java
@ParameterizedTest
@MethodSource("standardApiCases")
void shouldCallEveryStandardApi(ApiCase api) {
    mock.enqueue(api.response());
    Object result = api.invoke(gateway);
    RecordedRequest request = takeRequest();
    assertThat(request.getPath()).isEqualTo("/v1/" + api.path());
    assertThat(request.getHeader("Satori-Platform")).isEqualTo("discord");
    assertThat(request.getHeader("Satori-User-ID")).isEqualTo("bot-1");
    api.assertResult(result);
}
```

另测 multipart 多文件上传、Meta 无账号请求头、proxy URL 编码、raw/internal 方法与路径限制、400/401/403/404/405/501/5xx 映射。

**Step 2: Run and confirm failure**

```powershell
mvn -pl yudream-bootstrap -Dtest=ReactorSatoriApiGatewayTest test
```

**Step 3: Implement typed gateway**

接口必须显式声明全部方法，而不是让标准 API 退化成 `Map<String,Object>`。仅 raw/internal 使用受控通用请求对象。消息创建默认不进行网络级自动重试。

**Step 4: Run and confirm pass**

**Step 5: Commit**

```powershell
git commit -am "feat: 封装完整 Satori HTTP API"
```

## Task 5：实现连接应用服务、能力 Provider 与 HTTP 边界

**Files:**

- Create: `yudream-application/src/main/java/online/yudream/base/application/platform/satori/{cmd,query,dto,assembler,service}/*.java`
- Create: `yudream-infrastructure/src/main/java/online/yudream/base/infra/platform/satori/service/SatoriCapabilityProvider.java`
- Modify: `yudream-domain/src/main/java/online/yudream/base/domain/platform/capability/enumerate/CapabilityType.java`
- Create: `yudream-interfaces/src/main/java/online/yudream/base/interfaces/platform/satori/{request,res,assembler,controller}/*.java`
- Test: `yudream-bootstrap/src/test/java/online/yudream/base/application/platform/satori/SatoriConnectionAppServiceTest.java`
- Test: `yudream-bootstrap/src/test/java/online/yudream/base/interfaces/platform/satori/SatoriConnectionControllerTest.java`

**Step 1: Write failing tests**

断言项目门控关闭时无 Provider/Controller；应用门控关闭时用例拒绝；创建、更新、测试、启停、列表和脱敏 Token 正确；Provider `enable` 不发起网络连接。

**Step 2: Run and confirm failure**

```powershell
mvn -pl yudream-bootstrap -Dtest=SatoriConnectionAppServiceTest,SatoriConnectionControllerTest test
```

**Step 3: Implement four-layer use cases**

Controller 禁止直接 `new Cmd/Res`。权限至少包含 `platform:satori:view/config/connect/send/event/internal`。

**Step 4: Run architecture scans and tests**

```powershell
rg -n "private .*to[A-Z]|new .*Cmd|new .*Res|\.builder\(\)" yudream-interfaces/src/main/java/online/yudream/base/interfaces/platform/satori -g "*Controller.java"
mvn -pl yudream-bootstrap -Dtest='*SatoriConnection*' test
```

Expected: scan 无结果；tests PASS。

**Step 5: Commit**

```powershell
git commit -am "feat: 完成 Satori 连接管理用例"
```

## Task 6：实现 WebSocket、WebHook、会话恢复与事件管道

**Files:**

- Create: `yudream-domain/src/main/java/online/yudream/base/domain/platform/satori/service/SatoriEventPublisher.java`
- Create: `yudream-application/src/main/java/online/yudream/base/application/platform/satori/service/SatoriEventAppService.java`
- Create: `yudream-infrastructure/src/main/java/online/yudream/base/infra/platform/satori/service/ReactorSatoriEventGateway.java`
- Create: `yudream-infrastructure/src/main/java/online/yudream/base/infra/platform/satori/service/SatoriConnectionRuntime.java`
- Create: `yudream-interfaces/src/main/java/online/yudream/base/interfaces/platform/satori/controller/SatoriWebhookController.java`
- Test: `yudream-bootstrap/src/test/java/online/yudream/base/infra/platform/satori/SatoriWebSocketGatewayTest.java`
- Test: `yudream-bootstrap/src/test/java/online/yudream/base/application/platform/satori/SatoriEventAppServiceTest.java`
- Test: `yudream-bootstrap/src/test/java/online/yudream/base/interfaces/platform/satori/SatoriWebhookControllerTest.java`

**Step 1: Write failing tests**

用本地 WebSocket server 验证 IDENTIFY 在 10 秒内发出、READY 同步 Logins、10 秒 PING/PONG、META 更新代理 URL、EVENT 顺序处理、重复 `connectionId + sn` 幂等、断线携带最后成功 `sn` 重连。WebHook 测试 Bearer 反向鉴权和 Opcode。

**Step 2: Run and confirm failure**

```powershell
mvn -pl yudream-bootstrap -Dtest=SatoriWebSocketGatewayTest,SatoriEventAppServiceTest,SatoriWebhookControllerTest test
```

**Step 3: Implement event runtime**

每个连接拥有可关闭 runtime；禁用能力或连接时立即关闭 socket、心跳与重连任务。只有持久化和内部发布完成后推进游标。插件处理失败不阻止游标，失败另行记录。

**Step 4: Run and confirm pass**

**Step 5: Commit**

```powershell
git commit -am "feat: 实现 Satori 事件连接与会话恢复"
```

## Task 7：实现 Headless Render Server

**Files:**

- Create: `yudream-render-server/package.json`
- Create: `yudream-render-server/tsconfig.json`
- Create: `yudream-render-server/src/server.ts`
- Create: `yudream-render-server/src/browser-pool.ts`
- Create: `yudream-render-server/src/render-service.ts`
- Create: `yudream-render-server/src/security.ts`
- Create: `yudream-render-server/src/themes/default.css`
- Create: `yudream-render-server/test/render.test.ts`
- Create: `yudream-render-server/test/security.test.ts`
- Create: `yudream-render-server/Dockerfile`

**Step 1: Write failing Vitest tests**

断言 `/health`、HTML/Markdown PNG 非空、中文字体、代码块、表格、透明背景、输出格式、最大高度、超时、队列满、禁用脚本、拒绝 `file:`/localhost/私网 SSRF。

```ts
it('renders markdown into a nonblank png', async () => {
  const response = await app.inject({ method: 'POST', url: '/v1/render/markdown', payload: { markdown: '# 标题\n```java\nclass A {}\n```' } })
  expect(response.statusCode).toBe(200)
  expect(Buffer.from(response.json().data, 'base64').length).toBeGreaterThan(1000)
})
```

**Step 2: Run and confirm failure**

```powershell
pnpm --dir yudream-render-server install
pnpm --dir yudream-render-server test
```

Expected: FAIL，server 未实现。

**Step 3: Implement Fastify + Playwright service**

Chromium 始终 `headless: true`。BrowserContext 按任务隔离；路由拦截默认拒绝外部网络，仅允许显式资源白名单。Markdown-it 输出进入与 HTML 相同的清洗和截图管道。

**Step 4: Run tests and image pixel check**

```powershell
pnpm --dir yudream-render-server test
pnpm --dir yudream-render-server build
```

Expected: PASS；生成图片非透明空图。

**Step 5: Commit**

```powershell
git add yudream-render-server
git commit -m "feat: 实现无界面消息渲染服务"
```

## Task 8：接入 Java 渲染能力

**Files:**

- Create: `yudream-domain/src/main/java/online/yudream/base/domain/platform/render/model/*.java`
- Create: `yudream-domain/src/main/java/online/yudream/base/domain/platform/render/service/MessageRenderGateway.java`
- Create: `yudream-application/src/main/java/online/yudream/base/application/platform/render/{cmd,dto,assembler,service}/*.java`
- Create: `yudream-infrastructure/src/main/java/online/yudream/base/infra/platform/render/service/HttpMessageRenderGateway.java`
- Create: `yudream-infrastructure/src/main/java/online/yudream/base/infra/platform/render/service/MessageRenderCapabilityProvider.java`
- Create: `yudream-interfaces/src/main/java/online/yudream/base/interfaces/platform/render/{request,res,assembler,controller}/*.java`
- Test: `yudream-bootstrap/src/test/java/online/yudream/base/application/platform/render/MessageRenderAppServiceTest.java`

**Step 1: Write failing tests**

验证 `message-render` 应用门控、HTML/Markdown/URL 三类请求、二进制结果、健康状态、超时映射和降级标记。验证 Provider enable 不请求 Render Server。

**Step 2: Run and confirm failure**

```powershell
mvn -pl yudream-bootstrap -Dtest=MessageRenderAppServiceTest test
```

**Step 3: Implement gateway and use cases**

响应优先以内存字节返回应用层；HTTP Controller 返回 blob，不在 JSON 中传大体积 base64。配置仅保存 Render Server URL、超时和认证信息。

**Step 4: Run and confirm pass**

**Step 5: Commit**

```powershell
git commit -am "feat: 接入消息渲染平台能力"
```

## Task 9：实现统一消息投递、Markdown 与平台降级

**Files:**

- Create: `yudream-domain/src/main/java/online/yudream/base/domain/platform/satori/message/SatoriMessageContent.java`
- Create: `yudream-domain/src/main/java/online/yudream/base/domain/platform/satori/service/PlatformCapabilityProfileFactory.java`
- Create: `yudream-application/src/main/java/online/yudream/base/application/platform/satori/service/MessageDeliveryAppService.java`
- Create: `yudream-application/src/main/java/online/yudream/base/application/platform/satori/service/MarkdownToSatoriConverter.java`
- Create: `yudream-application/src/main/java/online/yudream/base/application/platform/satori/assembler/SatoriMessageAssembler.java`
- Test: `yudream-bootstrap/src/test/java/online/yudream/base/application/platform/satori/MessageDeliveryAppServiceTest.java`

**Step 1: Write failing tests**

覆盖 TEXT、SATORI、MARKDOWN、HTML、IMAGE、AUDIO、VIDEO、FILE、COMPOSITE；Markdown 标准元素转换与转图；上传后 `<img>`；图文不可混发时拆分；不支持富文本时纯文本/图片降级；`referrer` 原样传递；15 个已知 adapter profile 与未知 adapter 默认策略。

**Step 2: Run and confirm failure**

```powershell
mvn -pl yudream-bootstrap -Dtest=MessageDeliveryAppServiceTest test
```

**Step 3: Implement orchestration**

优先信任 `Login.features`，adapter profile 只能补充，不能覆盖明确的 feature 事实。消息创建不自动重试。返回所有实际发送的 Message，保留拆分关系。

**Step 4: Run and confirm pass**

**Step 5: Commit**

```powershell
git commit -am "feat: 实现跨平台消息投递与渲染降级"
```

## Task 10：扩展插件 SPI 的全部消息与渲染能力

**Files:**

- Create: `yudream-plugins/yudream-plugin-spi/src/main/java/online/yudream/base/plugin/spi/system/messaging/*.java`
- Create: `yudream-plugins/yudream-plugin-spi/src/main/java/online/yudream/base/plugin/spi/system/render/*.java`
- Modify: `yudream-plugins/yudream-plugin-spi/src/main/java/online/yudream/base/plugin/spi/system/FrameworkServices.java`
- Test: `yudream-bootstrap/src/test/java/online/yudream/base/plugin/spi/SatoriPluginSpiContractTest.java`

**Step 1: Write failing API contract test**

反射断言 `PluginMessagingService` 覆盖标准资源读写、统一发送、事件回复、上传、资源下载与 Meta；`PluginSatoriRawService` 覆盖 generic RPC/internal；`PluginRenderService` 覆盖 html/markdown/url；全部 ID/sn 类型为 String，签名不引用宿主内部包。

**Step 2: Run and confirm failure**

```powershell
mvn -pl yudream-bootstrap -Dtest=SatoriPluginSpiContractTest test
```

**Step 3: Implement stable SPI DTOs and interfaces**

接口返回同步 DTO 或 `CompletionStage`，不向插件暴露 Reactor、Spring 或 Jackson 类型。更新 SPI 版本属性和契约变更说明，但不在此任务发布 Nexus。

**Step 4: Run and confirm pass**

```powershell
mvn -pl yudream-plugins/yudream-plugin-spi,yudream-bootstrap -am -Dtest=SatoriPluginSpiContractTest test
```

**Step 5: Commit**

```powershell
git commit -am "feat: 向插件开放 Satori 与渲染能力"
```

## Task 11：实现插件消息交互注册表与生命周期隔离

**Files:**

- Create: `yudream-plugins/yudream-plugin-spi/src/main/java/online/yudream/base/plugin/spi/system/messaging/PluginMessageInteractionRegistry.java`
- Create: `yudream-plugins/yudream-plugin-spi/src/main/java/online/yudream/base/plugin/spi/system/messaging/PluginMessageHandler.java`
- Create: `yudream-plugins/yudream-plugin-spi/src/main/java/online/yudream/base/plugin/spi/system/messaging/PluginInteractionFilter.java`
- Create: `yudream-infrastructure/src/main/java/online/yudream/base/infra/platform/plugin/service/PluginMessageInteractionRegistryImpl.java`
- Create: `yudream-infrastructure/src/main/java/online/yudream/base/infra/platform/plugin/service/PluginMessagingFrameworkService.java`
- Create: `yudream-infrastructure/src/main/java/online/yudream/base/infra/platform/plugin/service/PluginRenderFrameworkService.java`
- Modify: `yudream-infrastructure/src/main/java/online/yudream/base/infra/platform/plugin/service/DefaultFrameworkServices.java`
- Modify: `yudream-infrastructure/src/main/java/online/yudream/base/infra/platform/plugin/service/PluginContextImpl.java`
- Test: `yudream-bootstrap/src/test/java/online/yudream/base/infra/platform/plugin/PluginMessageInteractionLifecycleTest.java`

**Step 1: Write failing lifecycle and isolation tests**

验证标准事件、原生事件、内容过滤、斜线指令、button、发送前/后观察器；返回 Markdown/HTML 自动携带 referrer 回复；插件 A 超时不阻塞插件 B；卸载插件后注册数量归零且不再接收事件；raw/internal 缺少高权限时拒绝。

```java
@Test
void shouldDisposeEveryHandlerWhenPluginUnloads() {
    context.interactions().onMessage(filter, handler);
    context.interactions().onButton("approve", buttonHandler);
    assertThat(registry.count("demo")).isEqualTo(2);
    context.close();
    assertThat(registry.count("demo")).isZero();
}
```

**Step 2: Run and confirm failure**

```powershell
mvn -pl yudream-bootstrap -Dtest=PluginMessageInteractionLifecycleTest test
```

**Step 3: Implement registry and framework adapters**

按 pluginCode 保存可释放句柄和隔离执行器；限制并发、超时和队列；失败写入事件处理记录。所有调用运行时检查插件状态、权限、连接范围和能力门控。

**Step 4: Run and confirm pass**

**Step 5: Commit**

```powershell
git commit -am "feat: 实现插件消息交互注册与卸载清理"
```

## Task 12：实现管理端、菜单与权限

**Files:**

- Modify: `yudream-infrastructure/src/main/java/online/yudream/base/infra/platform/menu/enumerate/PlatformMenuModule.java`
- Create: `yudream-frontend/apps/core-arco-design-vue/src/api/modules/platform-satori.ts`
- Create: `yudream-frontend/apps/core-arco-design-vue/src/views/platform/satori/index.vue`
- Create: `yudream-frontend/apps/core-arco-design-vue/src/views/platform/satori/components/SatoriConnectionPanel.vue`
- Create: `yudream-frontend/apps/core-arco-design-vue/src/views/platform/satori/components/SatoriLoginTable.vue`
- Create: `yudream-frontend/apps/core-arco-design-vue/src/views/platform/satori/components/SatoriMessageComposer.vue`
- Create: `yudream-frontend/apps/core-arco-design-vue/src/views/platform/satori/components/SatoriEventMonitor.vue`
- Create: `yudream-frontend/apps/core-arco-design-vue/src/views/platform/satori/components/SatoriRenderPreview.vue`
- Create: `yudream-frontend/apps/core-arco-design-vue/src/views/platform/satori/composables/useSatoriConsole.ts`
- Test: `yudream-bootstrap/src/test/java/online/yudream/base/infra/platform/menu/SatoriMenuRegistrationTest.java`

**Step 1: Write failing menu and type tests**

后端断言项目门控打开时菜单/权限存在、关闭时路由隐藏。前端先定义 API 类型和 composable 调用，运行 typecheck 确认缺少实现失败。

**Step 2: Run and confirm failure**

```powershell
mvn -pl yudream-bootstrap -Dtest=SatoriMenuRegistrationTest test
pnpm --dir yudream-frontend --config.engine-strict=false --filter @fantastic-admin/core-arco-design-vue exec vue-tsc --noEmit --pretty false --skipLibCheck --ignoreDeprecations 6.0
```

**Step 3: Implement UI**

使用紧凑工作台布局，不嵌套卡片；连接、账号、消息、事件和渲染使用独立视图/组件。Token 只支持替换不回显；ID 全部为 string；事件表格分页；按钮使用已有图标库和 tooltip。

**Step 4: Verify typecheck and browser UI**

启动前后端后，使用内置浏览器验证桌面与移动视口、发送模式切换、渲染预览、事件分页、无重叠和控制台无错误。

**Step 5: Commit**

```powershell
git add yudream-infrastructure/src/main/java/online/yudream/base/infra/platform/menu yudream-frontend/apps/core-arco-design-vue/src
git commit -m "feat: 完成 Satori 管理与消息调试界面"
```

## Task 13：完成 Docker、Server 部署与端到端验证

**Files:**

- Modify: `docker-compose.platform.yml`
- Modify: `.env.example`
- Create: `docker/render-server-entrypoint.sh`
- Create: `docs/platform/satori.md`
- Create: `docs/platform/message-render-server.md`
- Create: `ci/satori-e2e/docker-compose.yml`
- Create: `ci/satori-e2e/mock-satori-server.ts`
- Create: `ci/satori-e2e/run.ps1`

**Step 1: Write failing E2E script**

脚本启动 Mock Satori、Render Server 和 Java 服务，创建连接、接收 READY/EVENT、渲染 Markdown、上传 PNG、发送消息、触发插件 button 处理器、断线恢复，并验证能力禁用后连接和处理器释放。

**Step 2: Run and confirm failure**

```powershell
pwsh ci/satori-e2e/run.ps1
```

Expected: FAIL，Compose 服务与文档尚未配置。

**Step 3: Implement deployment**

Compose 为 Render Server 配置健康检查、只读文件系统、tmpfs、内存/CPU 限制和内部网络。文档提供 Docker Compose、Linux Node/systemd、Windows Server 三种无图形界面部署方式。

**Step 4: Run full verification**

```powershell
pnpm --dir yudream-render-server test
$env:JAVA_HOME='C:/Users/SiberianHusky/.jdks/ms-21.0.10'; mvn -pl yudream-bootstrap -am test
pnpm --dir yudream-frontend --config.engine-strict=false --filter @fantastic-admin/core-arco-design-vue exec vue-tsc --noEmit --pretty false --skipLibCheck --ignoreDeprecations 6.0
pwsh ci/satori-e2e/run.ps1
rg -n "private .*to[A-Z]|new .*Cmd|new .*ExcelRow|\.builder\(\)" yudream-interfaces/src/main/java/online/yudream/base/interfaces -g "*Controller.java"
```

Expected: 全部 PASS；Controller 扫描不新增违规；Docker E2E 完成消息渲染、发送、事件和插件交互闭环。

**Step 5: Commit**

```powershell
git add docker-compose.platform.yml .env.example docker docs/platform ci/satori-e2e
git commit -m "feat: 完成 Satori Docker 部署与端到端验证"
```

## 完成分支

全部任务结束后：

1. 再次运行 Task 13 的完整验证命令。
2. 检查 `git status --short`，确保未提交用户原有改动。
3. 进行全量代码审查，重点检查安全边界、发送幂等、事件游标、插件卸载和 SSRF。
4. 向用户提供合并、推送 PR、保留分支或丢弃分支四种选择。
