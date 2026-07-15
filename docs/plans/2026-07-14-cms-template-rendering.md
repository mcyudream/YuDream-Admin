# CMS 模板运行时渲染 Implementation Plan

> **For implementer:** Use TDD throughout. Write the failing test first, confirm it fails, then implement the minimum code.

**Goal:** 为 CMS 公共站点提供运行时最新的已发布页面和公开知识库模板上下文。

**Architecture:** application 层新增模板上下文查询服务，编排 CMS/Wiki repository；interface 层提供只读公开 API；前端公共站点加载上下文并复用现有变量、循环和清洗机制，GrapesJS 提供模板编辑提示。

**Tech Stack:** Java 21、Spring MVC、DDD 分层、Mongo repository、Vue 3、TypeScript、Node `node:test`。

---

### Task 1: 应用层模板上下文

**Files:**
- Create: `yudream-application/src/main/java/online/yudream/base/application/platform/cms/dto/CmsTemplateContextDTO.java`
- Create: `yudream-application/src/main/java/online/yudream/base/application/platform/cms/dto/CmsTemplateItemDTO.java`
- Create: `yudream-application/src/main/java/online/yudream/base/application/platform/cms/service/CmsTemplateContextAppService.java`
- Test: `yudream-bootstrap/src/test/java/online/yudream/base/application/platform/cms/CmsTemplateContextAppServiceTest.java`

**TDD:** 先写测试，验证只选 `PUBLISHED` CMS 页面、只选公开 Wiki 空间、只读取节点当前 `publishedVersionId` 对应版本；运行测试确认因类不存在而失败。实现后重跑定向 Maven 测试并确认通过。

**Implementation:** application service 检查 CMS 能力，读取已发布 CMS 页面；Wiki 能力关闭时返回空集合，开启时过滤公开空间、已发布节点和当前发布版本，生成公开 URL、层级路径和有限长度内容。不得泄漏 DO 或 HTTP res。

### Task 2: 公开接口

**Files:**
- Create: `yudream-interfaces/src/main/java/online/yudream/base/interfaces/platform/cms/res/CmsTemplateContextRes.java`
- Create: `yudream-interfaces/src/main/java/online/yudream/base/interfaces/platform/cms/assembler/CmsTemplateContextWebAssembler.java`
- Modify: `yudream-interfaces/src/main/java/online/yudream/base/interfaces/platform/cms/controller/PublicCmsController.java`
- Test: `yudream-bootstrap/src/test/java/online/yudream/base/interfaces/platform/cms/PublicCmsControllerTest.java`

**TDD:** 先测试 `GET /api/public/cms/template-context` 调用 application service 并返回统一响应，控制器不执行过滤和业务转换；确认路由不存在时失败。

**Implementation:** controller 只路由和包装 Result，assembler 只做 application DTO 到 res 的边界转换。

### Task 3: 前端运行时渲染

**Files:**
- Modify: `yudream-frontend/apps/core-arco-design-vue/src/api/modules/platform-cms.ts`
- Modify: `yudream-frontend/apps/core-arco-design-vue/src/views/site/index.vue`
- Create: `yudream-frontend/apps/core-arco-design-vue/src/utils/cms-template-render.test.ts`

**TDD:** 先测试 `knowledge.latest` 和 `cms.pages.latest` 循环、普通变量 escape、内容插槽清洗；确认新键不支持时失败。

**Implementation:** 新增 API，站点加载统一 context 并合并 `renderContext`；循环键改为路径解析；普通变量继续 escape，内容字段经过清洗或 Markdown 预览，保留现有脚本安全规则和长度限制。

### Task 4: 编辑器和 AI 模板提示

**Files:**
- Modify: `yudream-frontend/apps/core-arco-design-vue/src/views/platform/cms/components/CmsGrapesEditor.vue`
- Modify: `yudream-frontend/apps/core-arco-design-vue/src/views/platform/cms/index.vue`
- Modify: `yudream-application/src/main/java/online/yudream/base/application/platform/ai/service/AiAppService.java`

**TDD:** 扩展现有测试，断言变量面板和 AI 模板约束包含 `knowledge.pages`、`knowledge.latest`、`cms.pages.latest`，且没有发布动作。

**Implementation:** 加入变量/循环说明和 HTML 片段；AI prompt 明确模板运行时填充、使用 `data-yb-repeat`、只生成 HTML/CSS；Header/Footer 继续只允许样式操作。

### Task 5: 全量检查

执行后端定向测试和 `mvn -pl yudream-bootstrap -am -DskipTests compile`；执行前端 Node 模板测试、既有 chrome 测试和 `vue-tsc`；执行 DDD 边界扫描，确认 controller 不构造 Cmd/Res，DO 不泄漏到 application/interface，公开接口不绕过 application service；最后运行 `git diff --check`。
