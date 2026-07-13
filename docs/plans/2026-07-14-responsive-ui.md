# Responsive Admin UI Implementation Plan

> **For implementer:** Use visual TDD throughout. Capture the failing viewport before changing styles, then capture the same viewport after the change.

**Goal:** Make core admin pages and all official plugin pages responsive across desktop, narrow laptop, tablet, and phone widths.

**Architecture:** The host owns generic containment and navigation behavior. Core pages and remote plugins retain ownership of their own layout rules, adding local breakpoints for their grids, filters, forms, inspectors, and table wrappers. Wide tables scroll inside their page region only.

**Tech Stack:** Vue 3, TypeScript, UnoCSS, CSS media/container queries, Arco Design Vue, remote plugin packages, browser screenshots.

---

### Task 1: Establish Responsive Audit Harness

**Files:**
- Modify: `yudream-frontend/apps/core-arco-design-vue/src/layouts/index.vue`
- Create: `yudream-frontend/apps/core-arco-design-vue/src/styles/responsive.css`
- Modify: the host global style import that owns application-wide CSS

**Steps:**
1. Capture baseline screenshots at 1440, 1280, 1024, 768, and 390 pixels for a system table page and remote plugin route.
2. Add only generic containment styles: shrinking grid/flex children, contained table overflow, wrapped action groups, and safe long-token display.
3. Verify no existing desktop page changes its layout at 1440 pixels.
4. Type-check the core frontend.

### Task 2: Fix Skin Plugin Layouts

**Files:**
- Modify: `D:/code/yudream-admin-plugins/yudream-frontend/packages/plugin-yudream-skin/src/styles.css`
- Verify: `D:/code/yudream-admin-plugins/yudream-frontend/packages/plugin-yudream-skin/src/pages/TexturesPage.vue`
- Verify: `D:/code/yudream-admin-plugins/yudream-frontend/packages/plugin-yudream-skin/src/pages/PlayersPage.vue`
- Verify: `D:/code/yudream-admin-plugins/yudream-frontend/packages/plugin-yudream-skin/src/pages/SystemPage.vue`

**Steps:**
1. Capture the texture library at 1280 and 1024 pixels with navigation visible.
2. At the first collision width, stack the library inspector after the texture grid and remove sticky positioning.
3. Reflow dashboard/player layouts and the administrative filter grid at 1280, 1024, and 640 pixels.
4. Keep texture and player table minimum widths inside their existing scroll wrappers.
5. Build/type-check the skin plugin and capture 390-pixel screenshots.

### Task 3: Repair Core Page Families

**Files:**
- Modify only affected pages under `yudream-frontend/apps/core-arco-design-vue/src/views/system/`
- Modify only affected pages under `yudream-frontend/apps/core-arco-design-vue/src/views/platform/`
- Modify only affected pages under `yudream-frontend/apps/core-arco-design-vue/src/views/dashboard/`
- Modify only affected pages under `yudream-frontend/apps/core-arco-design-vue/src/views/forms/`
- Modify only affected pages under `yudream-frontend/apps/core-arco-design-vue/src/views/wiki/`

**Steps:**
1. Classify each page as form, table, dashboard, editor, or split-pane.
2. For each class, add page-local rules that stack nonessential rails below 1180/1024 pixels and wrap filters/actions below 768 pixels.
3. Preserve readable tables through local scroll wrappers.
4. Capture representative pages at all target widths and type-check the host frontend.

### Task 4: Repair Official Plugin Packages

**Files:**
- Modify affected files under `D:/code/yudream-admin-plugins/yudream-frontend/packages/plugin-ai-chatbot/src/`
- Modify affected files under `D:/code/yudream-admin-plugins/yudream-frontend/packages/plugin-authlib-injector/src/`
- Modify affected files under `D:/code/yudream-admin-plugins/yudream-frontend/packages/plugin-minecraft-activity-proof/src/`
- Modify affected files under `D:/code/yudream-admin-plugins/yudream-frontend/packages/plugin-minecraft-server/src/`
- Modify affected files under `D:/code/yudream-admin-plugins/yudream-frontend/packages/plugin-project-progress/src/`
- Modify affected files under `D:/code/yudream-admin-plugins/yudream-frontend/packages/plugin-yudream-alipay/src/`
- Modify affected files under `D:/code/yudream-admin-plugins/yudream-frontend/packages/plugin-yudream-student-info/src/`
- Modify affected files under `D:/code/yudream-admin-plugins/yudream-frontend/packages/plugin-yudream-wallet/src/`

**Steps:**
1. Capture each package's primary route at 1280, 768, and 390 pixels.
2. Add local responsive rules for fixed grid columns, toolbars, filters, tables, and modals.
3. Keep per-plugin API and host-SDK boundaries unchanged.
4. Build/type-check each changed package.

### Task 5: Visual Regression Pass

**Files:**
- Verify all files changed in Tasks 1-4

**Steps:**
1. Use browser screenshots at 1440, 1280, 1024, 768, and 390 pixels for core representative pages and all plugin primary routes.
2. Confirm no non-table region has unintended horizontal overflow.
3. Confirm tabular regions scroll locally and controls remain reachable.
4. Run the core type-check plus all changed plugin builds.
5. Commit each independently verified module without mixing pre-existing worktree changes.
