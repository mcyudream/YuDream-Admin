# CMS Header/Footer Style Editing Implementation Plan

> **For implementer:** Use TDD throughout. Write the failing test first, confirm it fails, then implement the smallest change.

**Goal:** 将 Header/Footer 纳入 CMS 样式编辑和公共渲染，同时锁定结构和系统数据绑定。

**Architecture:** 复用 Home `settings` 存储 `chromeHeaderCss` 与 `chromeFooterCss`。前端使用固定 Chrome 模板和锁定模式 GrapesJS 编辑器，公共站点继续渲染固定 Vue 节点；AI 在锁定目标下只执行 CSS 类动作。

**Tech Stack:** Vue 3、TypeScript、GrapesJS、Vitest、Spring Boot、现有 CMS Home API。

---

### Task 1: 固定 Chrome 模板与 CSS 配置工具

**Files:**
- Create: `yudream-frontend/apps/core-arco-design-vue/src/utils/cms-chrome.ts`
- Test: `yudream-frontend/apps/core-arco-design-vue/src/utils/cms-chrome.test.ts`

**Step 1: Write failing tests**

覆盖 `chromeHeaderCss`/`chromeFooterCss` 读写、固定模板包含 Logo/导航/认证/Footer 节点、锁定目标只允许 CSS 动作。

**Step 2: Run tests and confirm RED**

Command: `pnpm exec vitest run src/utils/cms-chrome.test.ts`

Expected: FAIL because the utility does not exist.

**Step 3: Implement minimal utility**

提供 `CmsChromeZone`、`chromeCssKey`、`chromeTemplate`、`chromeStyleValue`、`setChromeStyleValue` 和 `isChromeStyleAction`。模板只作为编辑器预览，公共页面仍使用 Vue 固定结构。

**Step 4: Run tests and confirm GREEN**

Command: `pnpm exec vitest run src/utils/cms-chrome.test.ts`

Expected: PASS.

### Task 2: CMS 编辑器增加 Header/Footer 锁定目标

**Files:**
- Modify: `yudream-frontend/apps/core-arco-design-vue/src/views/platform/cms/index.vue`
- Modify: `yudream-frontend/apps/core-arco-design-vue/src/views/platform/cms/components/CmsGrapesEditor.vue`
- Modify: `yudream-frontend/apps/core-arco-design-vue/src/utils/cms-ai-chat-history.ts`

**Step 1: Write failing tests**

扩展 Chrome utility 测试，验证锁定目标不接受 HTML、JS、Project JSON，仅接受 CSS；验证编辑目标 ID 可区分 Header/Footer 会话。

**Step 2: Run tests and confirm RED**

Command: `pnpm exec vitest run src/utils/cms-chrome.test.ts`

Expected: FAIL for the new lock behavior.

**Step 3: Implement**

增加 `header`/`footer` editor target；锁定模式隐藏区块、层级、属性和源码编辑入口，只保留样式面板与 AI。保存时固定返回模板 HTML、当前 CSS，清空 JS 和 Project JSON。CMS 首页/导航工作区增加 Header/Footer 样式编辑入口，并把样式写入 Home settings。

**Step 4: Run tests and typecheck**

Commands:
- `pnpm exec vitest run src/utils/cms-chrome.test.ts`
- `pnpm --config.engine-strict=false --filter @fantastic-admin/core-arco-design-vue exec vue-tsc --noEmit --pretty false --skipLibCheck --ignoreDeprecations 6.0`

Expected: PASS.

### Task 3: 公共站点应用固定 Chrome CSS

**Files:**
- Modify: `yudream-frontend/apps/core-arco-design-vue/src/views/site/index.vue`
- Modify: `yudream-frontend/apps/core-arco-design-vue/src/api/modules/platform-cms.ts`

**Step 1: Write failing test**

扩展 Chrome utility 测试，验证固定 Header/Footer 的 CSS key 可分别读取，且不存在可渲染的 Chrome HTML 配置入口。

**Step 2: Run test and confirm RED**

Command: `pnpm exec vitest run src/utils/cms-chrome.test.ts`

Expected: FAIL for the new public-style contract.

**Step 3: Implement**

在固定 Header/Footer 节点上加载对应 CSS；继续使用现有导航树、站点 Logo、账户和 Footer 数据。不要从 settings 读取 Header/Footer HTML 或 Project JSON。

**Step 4: Run frontend checks**

Command: `pnpm --config.engine-strict=false --filter @fantastic-admin/core-arco-design-vue exec vue-tsc --noEmit --pretty false --skipLibCheck --ignoreDeprecations 6.0`

Expected: PASS.

### Task 4: 限制 Header/Footer 的 AI 修改动作

**Files:**
- Modify: `yudream-frontend/apps/core-arco-design-vue/src/views/platform/cms/components/CmsGrapesEditor.vue`
- Modify: `yudream-frontend/apps/core-arco-design-vue/src/api/modules/platform-ai.ts`
- Modify: `yudream-application/src/main/java/online/yudream/base/application/platform/ai/service/CmsCanvasAiTool.java`

**Step 1: Write failing tests**

增加动作白名单测试：`set-css`、`append-css`、`set-styles` 通过；`set-html`、`remove-selected`、`set-attributes`、`load-project` 被锁定目标拒绝。

**Step 2: Run test and confirm RED**

Command: `pnpm exec vitest run src/utils/cms-chrome.test.ts`

Expected: FAIL for locked action validation.

**Step 3: Implement**

AI 请求携带 `target: header|footer` 和结构锁定提示；编辑器应用 AI 返回结果前校验动作，后端工具描述同步说明锁定目标只允许 CSS，避免模型生成结构修改意图。

**Step 4: Run tests**

Commands:
- `pnpm exec vitest run src/utils/cms-chrome.test.ts`
- `$env:JAVA_HOME='C:/Users/SiberianHusky/.jdks/ms-21.0.10'; $env:Path="$env:JAVA_HOME/bin;$env:Path"; mvn -pl yudream-bootstrap -am -DskipTests compile`

Expected: PASS.

### Task 5: Final verification

Run:

- `pnpm exec vitest run src/utils/cms-chrome.test.ts`
- `pnpm --config.engine-strict=false --filter @fantastic-admin/core-arco-design-vue exec vue-tsc --noEmit --pretty false --skipLibCheck --ignoreDeprecations 6.0`
- `$env:JAVA_HOME='C:/Users/SiberianHusky/.jdks/ms-21.0.10'; $env:Path="$env:JAVA_HOME/bin;$env:Path"; mvn -pl yudream-bootstrap -am -DskipTests compile`

Expected: all commands pass. Then manually verify CMS Header/Footer editor, public `/site`, mobile preview, menu hierarchy and AI structural mutation rejection.
