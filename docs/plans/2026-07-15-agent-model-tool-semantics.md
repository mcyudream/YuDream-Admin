# Agent Model Tool Semantics Implementation Plan

> **For implementer:** Use TDD throughout. Write failing test first. Watch it fail. Then implement.

**Goal:** Make chat-model nodes own native tool calling, support system and custom Python tools per node, and split the workflow palette into runtime-distinct model semantics.

**Architecture:** Keep `AgentApplication.toolCodes` as a server-verified authorization union, but move tool selection to each chat-model node. Resolve the node's concrete `AiAgentTool` instances in the application layer and expose them to Spring AI through a scoped domain contract; keep the legacy `tool` handler only for existing workflows.

**Tech Stack:** Java 21, Spring AI, Jackson, Mongo repositories, JUnit 5/AssertJ/Mockito, Vue 3, TypeScript, Vue Flow, Node test runner.

---

### Task 1: Define model tool mode and scoped tool instances

**Files:**
- Create: `yudream-domain/src/main/java/online/yudream/base/domain/platform/ai/enumerate/AiToolMode.java`
- Modify: `yudream-domain/src/main/java/online/yudream/base/domain/platform/ai/valobj/AiGenerationRequest.java`
- Modify: `yudream-domain/src/main/java/online/yudream/base/domain/platform/ai/service/AiAgentToolExecutionScope.java`
- Modify: `yudream-infrastructure/src/main/java/online/yudream/base/infra/platform/ai/service/OpenAiCompatibleGenerationGateway.java`
- Test: `yudream-bootstrap/src/test/java/online/yudream/base/infra/platform/ai/service/OpenAiCompatibleGenerationGatewayTest.java`

**Step 1: Write the failing tests**

Add tests proving that an active `AiAgentToolExecutionScope` supplies the exact tool instances for the current model call and excludes globally registered tools. Add a request test proving `NONE`, `AUTO`, and `REQUIRED` survive `withToolCallingEnabled`/copy operations.

The intended API is:

```java
try (AiAgentToolExecutionScope ignored = AiAgentToolExecutionScope.open(List.of(tool))) {
    assertThat(AiAgentToolExecutionScope.currentTools()).containsExactly(tool);
}
assertThat(AiAgentToolExecutionScope.currentTools()).isNull();
```

**Step 2: Run tests and confirm RED**

```powershell
mvn -pl yudream-bootstrap -am '-Dtest=OpenAiCompatibleGenerationGatewayTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Expected: compilation/test failure because `AiToolMode`, scoped tool instances, and request accessors do not exist.

**Step 3: Implement the minimum contract**

- Add `AiToolMode { NONE, AUTO, REQUIRED }`.
- Extend `AiGenerationRequest` with `toolMode`, retaining source-compatible constructors.
- Replace the name-only ThreadLocal scope with an immutable scoped tool list plus an allowed-name view.
- Make the gateway prefer scoped tool instances and fall back to provider beans only when no scope exists.
- For `REQUIRED`, mark the request and let the handler verify that at least one result was produced; do not emulate provider Tool Choice with prompt text in the gateway.

**Step 4: Run tests and confirm GREEN**

Run the Task 1 command. Expected: PASS.

**Step 5: Commit**

```text
feat: 增加模型节点工具调用作用域
```

### Task 2: Adapt custom Python tools to native model callbacks

**Files:**
- Create: `yudream-application/src/main/java/online/yudream/base/application/platform/agent/workflow/support/AgentToolExecutor.java`
- Create: `yudream-application/src/main/java/online/yudream/base/application/platform/agent/workflow/support/AgentModelToolResolver.java`
- Modify: `yudream-application/src/main/java/online/yudream/base/application/platform/agent/workflow/handler/AgentToolNodeHandler.java`
- Test: `yudream-bootstrap/src/test/java/online/yudream/base/application/platform/agent/workflow/AgentModelToolResolverTest.java`
- Test: `yudream-bootstrap/src/test/java/online/yudream/base/application/platform/agent/workflow/AgentToolNodeHandlerTest.java`

**Step 1: Write the failing tests**

Cover these independent behaviours:

- resolving one system tool returns the original `AiAgentTool`;
- resolving one enabled Python tool returns an `AiAgentTool` with descriptor data parsed from `inputSchemaJson`;
- executing the adapted Python tool invokes `def run(params: dict) -> dict` through `RuntimeExecutor` and returns its dictionary payload;
- disabled, missing, unauthorized, or application-ungranted tools fail before model execution;
- the legacy `tool` node delegates to the same executor and keeps its current result contract.

**Step 2: Run tests and confirm RED**

```powershell
mvn -pl yudream-bootstrap -am '-Dtest=AgentModelToolResolverTest,AgentToolNodeHandlerTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Expected: FAIL because the resolver/executor do not exist.

**Step 3: Implement the minimum components**

`AgentModelToolResolver.resolve(List<String> nodeToolCodes, AgentApplication application, AgentRunCmd command)` returns ordered, de-duplicated concrete tools. `AgentToolExecutor` owns permission validation, Python wrapping, runtime execution, schema parsing, dictionary output validation, and result construction. Refactor the legacy handler to delegate instead of duplicating execution logic.

**Step 4: Run tests and confirm GREEN**

Run the Task 2 command. Expected: PASS.

**Step 5: Commit**

```text
feat: 将Python工具接入模型原生调用
```

### Task 3: Implement distinct chat-model node semantics

**Files:**
- Modify: `yudream-application/src/main/java/online/yudream/base/application/platform/agent/workflow/handler/AgentLlmNodeHandler.java`
- Modify: `yudream-application/src/main/java/online/yudream/base/application/platform/agent/workflow/support/AgentWorkflowRunState.java`
- Modify: `yudream-application/src/main/java/online/yudream/base/application/platform/agent/service/AgentWorkflowRuntimeService.java`
- Test: `yudream-bootstrap/src/test/java/online/yudream/base/application/platform/agent/workflow/AgentLlmNodeHandlerTest.java`
- Test: `yudream-bootstrap/src/test/java/online/yudream/base/application/platform/agent/service/AgentWorkflowRuntimeServiceTest.java`

**Step 1: Write one failing test per semantic**

- `llm` returns streamed text and exposes only its own `toolCodes`.
- `extract` requires valid JSON and optionally validates required fields from `outputSchema`.
- `classify` accepts only a configured value from `classes`.
- `vision` passes image data only to a Vision-capable node and fails when no image is available.
- `toolMode=REQUIRED` fails when the model returns no tool results.
- two model nodes with different tools never see each other's tools.
- legacy `understand` keeps its tolerant `strictJson=false` compatibility.

**Step 2: Run tests and confirm RED**

```powershell
mvn -pl yudream-bootstrap -am '-Dtest=AgentLlmNodeHandlerTest,AgentWorkflowRuntimeServiceTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Expected: FAIL for unsupported kinds and missing node-level tool selection.

**Step 3: Implement minimum runtime semantics**

- Support `llm`, `extract`, `classify`, `vision`, and legacy `understand` in the chat-model handler.
- Resolve node tool codes immediately before each model call and open a scoped tool list.
- Map `NONE/AUTO/REQUIRED` to request behaviour and verify required tool use after generation.
- Keep Embedding/Rerank in `AgentKnowledgeNodeHandler`.
- Trigger the integration capability only when code, legacy tool nodes, or selected Python model tools require it.

**Step 4: Run tests and confirm GREEN**

Run the Task 3 command. Expected: PASS.

**Step 5: Commit**

```text
feat: 拆分Agent模型节点运行语义
```

### Task 4: Make backend validation and authorization union authoritative

**Files:**
- Create: `yudream-application/src/main/java/online/yudream/base/application/platform/agent/workflow/AgentWorkflowToolCodes.java`
- Modify: `yudream-application/src/main/java/online/yudream/base/application/platform/agent/workflow/AgentWorkflowValidator.java`
- Modify: `yudream-application/src/main/java/online/yudream/base/application/platform/agent/service/AgentAppService.java`
- Test: `yudream-bootstrap/src/test/java/online/yudream/base/application/platform/agent/workflow/AgentWorkflowValidatorTest.java`
- Test: `yudream-bootstrap/src/test/java/online/yudream/base/application/platform/agent/service/AgentAppServiceLifecycleTest.java`

**Step 1: Write the failing tests**

Verify:

- tool union is derived only from chat-model node arrays plus legacy tool nodes;
- client-supplied application tool codes cannot grant tools absent from the workflow;
- `AUTO/REQUIRED` requires at least one tool, while `NONE` clears node tools;
- Extract schema must be a JSON object;
- Classify requires at least two unique labels;
- Vision requires a configured chat model with `vision=true`;
- legacy `tool`/`understand` remains valid.

**Step 2: Run tests and confirm RED**

```powershell
mvn -pl yudream-bootstrap -am '-Dtest=AgentWorkflowValidatorTest,AgentAppServiceLifecycleTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Expected: assertion failures because validation understands only `llm/understand/tool` and trusts request tool codes.

**Step 3: Implement authoritative derivation**

Parse `toolCodes` only from JSON arrays, preserve order while de-duplicating, validate every code against the catalog, and pass the derived list into `AgentApplication.update`. Extend model catalog validation with the Vision capability flag without exposing provider secrets.

**Step 4: Run tests and confirm GREEN**

Run the Task 4 command. Expected: PASS.

**Step 5: Commit**

```text
fix: 收紧Agent模型工具授权边界
```

### Task 5: Upgrade built-in Agent workflows

**Files:**
- Modify: `yudream-application/src/main/java/online/yudream/base/application/platform/agent/service/BuiltinAgentInitializerService.java`
- Test: `yudream-bootstrap/src/test/java/online/yudream/base/application/platform/agent/service/BuiltinAgentInitializerServiceTest.java`
- Inspect only unless required: `D:/code/yudream-admin-plugins/**/src/main/resources/**/*.json`

**Step 1: Write the failing tests**

Assert that the CMS builder's build model owns `CMS_TOOLS` with `toolMode=AUTO`, while its plan and clarification models own no mutation tools. Assert that startup upgrades an old built-in workflow missing node-level tool codes without re-enabling a disabled application.

**Step 2: Run tests and confirm RED**

```powershell
mvn -pl yudream-bootstrap -am '-Dtest=BuiltinAgentInitializerServiceTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Expected: FAIL because built-in tool codes exist only at application level.

**Step 3: Implement the workflow upgrade**

Add tool settings only to the actual CMS build model. Extend `requiresWorkflowUpgrade` to detect the old built-in schema. Do not persist or mutate plugin-owned runtime Agent definitions.

**Step 4: Run tests and confirm GREEN**

Run the Task 5 command. Expected: PASS.

**Step 5: Commit**

```text
feat: 升级内置Agent模型工具配置
```

### Task 6: Add frontend model node data and catalog

**Files:**
- Create: `yudream-frontend/apps/core-arco-design-vue/src/views/platform/agent/config/agent-node-catalog.ts`
- Create: `yudream-frontend/apps/core-arco-design-vue/src/views/platform/agent/config/agent-node-catalog.test.ts`
- Modify: `yudream-frontend/apps/core-arco-design-vue/src/views/platform/agent/components/types.ts`
- Modify: `yudream-frontend/apps/core-arco-design-vue/src/views/platform/agent/config/agent-node-data.ts`
- Modify: `yudream-frontend/apps/core-arco-design-vue/src/views/platform/agent/config/agent-node-data.test.ts`

**Step 1: Write failing Node tests**

Test the six palette groups, absence of legacy `tool/understand` templates, chat-model classification, defaults for `toolCodes/toolMode/outputSchema/classes/imageVariable`, and normalization of old `toolCode`/`understand` data.

**Step 2: Run tests and confirm RED**

```powershell
pnpm --config.engine-strict=false --filter @fantastic-admin/core-arco-design-vue exec tsx --test src/views/platform/agent/config/agent-node-catalog.test.ts src/views/platform/agent/config/agent-node-data.test.ts
```

Expected: FAIL because the catalog and semantic fields do not exist.

**Step 3: Implement the minimum catalog/data model**

Move palette definitions out of `editor.vue`, add the new kinds and fields, keep legacy kinds in the TypeScript union for loading, and return fresh arrays from defaults to prevent node instances sharing `toolCodes/classes` references.

**Step 4: Run tests and confirm GREEN**

Run the Task 6 command. Expected: PASS.

**Step 5: Commit**

```text
feat: 细分Agent节点目录与模型数据
```

### Task 7: Add frontend validation, authorization union, and legacy migration

**Files:**
- Modify: `yudream-frontend/apps/core-arco-design-vue/src/views/platform/agent/config/agent-workflow-validation.ts`
- Modify: `yudream-frontend/apps/core-arco-design-vue/src/views/platform/agent/config/agent-workflow-validation.test.ts`
- Create: `yudream-frontend/apps/core-arco-design-vue/src/views/platform/agent/config/agent-workflow-tools.ts`
- Create: `yudream-frontend/apps/core-arco-design-vue/src/views/platform/agent/config/agent-workflow-tools.test.ts`

**Step 1: Write failing tests**

Cover model tool availability, tool-mode requirements, Vision capability, Extract schema, Classify labels, deterministic tool union, and safe migration of a legacy tool node only when it has one direct chat-model predecessor and one successor.

**Step 2: Run tests and confirm RED**

```powershell
pnpm --config.engine-strict=false --filter @fantastic-admin/core-arco-design-vue exec tsx --test src/views/platform/agent/config/agent-workflow-validation.test.ts src/views/platform/agent/config/agent-workflow-tools.test.ts
```

Expected: FAIL because the validators and migration helpers do not exist.

**Step 3: Implement pure helpers**

Keep union calculation and migration independent of Vue components. Return a new node/edge graph from migration and never mutate ambiguous legacy paths.

**Step 4: Run tests and confirm GREEN**

Run the Task 7 command. Expected: PASS.

**Step 5: Commit**

```text
feat: 增加模型工具校验与旧节点迁移
```

### Task 8: Rebuild the workflow editor controls

**Files:**
- Modify: `yudream-frontend/apps/core-arco-design-vue/src/views/platform/agent/editor.vue`
- Modify: `yudream-frontend/apps/core-arco-design-vue/src/views/platform/agent/components/AgentNodeInspector.vue`
- Modify: `yudream-frontend/apps/core-arco-design-vue/src/views/platform/agent/components/AgentWorkflowNode.vue`
- Modify: `yudream-frontend/apps/core-arco-design-vue/src/views/platform/agent/components/AgentApplicationInspector.vue`
- Modify: `yudream-frontend/apps/core-arco-design-vue/src/views/platform/agent/components/AgentNodePalette.vue`

**Step 1: Use existing pure tests as the RED guard**

Temporarily import the new catalog/helpers into `editor.vue`; the TypeScript check must fail until component props and templates are updated.

**Step 2: Confirm RED**

```powershell
pnpm --config.engine-strict=false --filter @fantastic-admin/core-arco-design-vue exec vue-tsc --noEmit --pretty false --skipLibCheck --ignoreDeprecations 6.0
```

Expected: type errors for new node fields and removed application tool toggle events.

**Step 3: Implement the UI**

- Render the six catalog groups and remove standalone tool creation.
- Add model tool mode, searchable grouped tool multi-select, semantic fields, and precise placeholders.
- Show model, tool count/mode, and Vision input on node cards.
- Make application tools a read-only derived summary.
- Save the pure derived union and keep legacy tool nodes visibly marked as compatibility nodes.
- Do not add nested cards or duplicate explanatory banners.

**Step 4: Confirm GREEN**

Run Task 8 typecheck plus all frontend config tests. Expected: PASS.

**Step 5: Commit**

```text
feat: 重构Agent模型节点编排界面
```

### Task 9: Full regression and browser verification

**Files:**
- Modify if a durable rule is learned: `.codex/skills/yudream-ddd-architecture/references/knowledge.json`
- No production files unless verification exposes a defect.

**Step 1: Run backend regression**

```powershell
mvn -pl yudream-bootstrap -am '-Dtest=*Agent*Test,*Ai*Test' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Expected: all tests PASS.

**Step 2: Run frontend regression**

```powershell
pnpm --config.engine-strict=false --filter @fantastic-admin/core-arco-design-vue exec tsx --test src/views/platform/agent/config/*.test.ts
pnpm --config.engine-strict=false --filter @fantastic-admin/core-arco-design-vue exec vue-tsc --noEmit --pretty false --skipLibCheck --ignoreDeprecations 6.0
pnpm --config.engine-strict=false --filter @fantastic-admin/core-arco-design-vue exec vite build
```

Expected: tests, typecheck, and Vite build PASS.

**Step 3: Restart backend and verify in the in-app browser**

Verify at `http://127.0.0.1:5182/platform/agent/editor`:

- palette has six semantic groups and no addable standalone tool node;
- text/extract/classify/vision inspectors show their distinct settings;
- model tools persist after save/reload and application summary matches their union;
- CMS build model shows CMS tools while plan/clarify nodes do not;
- debug stream highlights the model node and emits tool results during native calls;
- missing image, invalid classification, invalid extraction JSON, and unavailable tool show node-level errors.

**Step 4: Run final diff checks**

```powershell
git diff --check
git status --short
```

Expected: no whitespace errors and no unrelated files staged.

**Step 5: Commit verification fixes or knowledge update**

```text
test: 完成Agent模型工具调用回归验证
```
