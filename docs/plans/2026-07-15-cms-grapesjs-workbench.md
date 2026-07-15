# CMS GrapesJS Workbench Implementation Plan

> **For implementer:** Use TDD throughout. Write failing test first. Watch it fail. Then implement.

**Goal:** Build a polished, plugin-enabled GrapesJS Core workbench for YuDream CMS while preserving existing CMS storage, AI, and locked homepage structure.

**Architecture:** Extract official plugin configuration into a small registry module and keep `CmsGrapesEditor.vue` responsible for host layout and editor commands. Add workbench state incrementally without changing the save payload or backend publishing model.

**Tech Stack:** Vue 3, TypeScript, GrapesJS Core 0.23, official GrapesJS plugins, Vitest, pnpm.

---

### Task 1: Official plugin registry

**Files:**
- Create: `yudream-frontend/apps/core-arco-design-vue/src/views/platform/cms/config/cms-grapes-plugins.ts`
- Create: `yudream-frontend/apps/core-arco-design-vue/src/views/platform/cms/config/cms-grapes-plugins.test.ts`
- Modify: `yudream-frontend/apps/core-arco-design-vue/package.json`

1. Write a failing test that verifies the registry exposes all required plugins and localized categories.
2. Run the targeted Vitest test and confirm failure.
3. Install and configure the five plugins.
4. Run the targeted test and type-check.

### Task 2: Workbench state and commands

**Files:**
- Modify: `yudream-frontend/apps/core-arco-design-vue/src/views/platform/cms/components/CmsGrapesEditor.vue`

1. Add state for sidebar collapse, left workspace, preview mode, device, and dirty status.
2. Route toolbar actions through named command functions.
3. Keep GrapesJS manager mount points alive when sidebars collapse.
4. Verify undo/redo, preview, viewport, zoom, save, and close.

### Task 3: Studio-like workbench layout

**Files:**
- Modify: `yudream-frontend/apps/core-arco-design-vue/src/views/platform/cms/components/CmsGrapesEditor.vue`

1. Rebuild the command bar into grouped icon controls with tooltips.
2. Add Blocks/Media workspace switching and sidebar collapse controls.
3. Add a canvas status bar and clear active states.
4. Restyle GrapesJS blocks, categories, inspector sectors, inputs, layers, and canvas chrome.
5. Verify responsive constraints and no overlapping labels.

### Task 4: Regression verification

**Files:**
- Test existing CMS frontend and backend files.

1. Run the plugin registry test.
2. Run Vue type-check.
3. Run `CmsCanvasValidateAiToolTest`.
4. Run `git diff --check` on touched files.
5. Start the dev server and verify the workbench in a browser.

