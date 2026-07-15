# CMS GrapesJS Workbench Design

## Goal

Turn the current bare GrapesJS Core integration into a complete CMS workbench without adopting Studio SDK or requiring a commercial license.

## Architecture

Keep GrapesJS Core as the canvas engine and the existing Vue component as the host. Add official open-source GrapesJS plugins through one registry module, then rebuild the host chrome around stable editor commands. CMS remains authoritative for pages, assets, publish state, Header/Footer structure, and AI tools.

The editor has four surfaces:

1. A compact command bar for history, viewport, zoom, preview, save, and close.
2. A collapsible left sidebar with Blocks and Media workspaces.
3. The GrapesJS canvas with a persistent status bar.
4. A collapsible right inspector with Layers, Traits, Styles, Source, and AI.

## Plugin Scope

Use official/community GrapesJS plugins that add real editor capability without replacing CMS ownership:

- `grapesjs-blocks-basic`
- `grapesjs-plugin-forms`
- `grapesjs-navbar`
- `grapesjs-custom-code`
- `grapesjs-style-bg`

Plugins register components, blocks, and style properties. Plugin-created panels are disabled so the Vue workbench remains the single command surface.

## State And Data Flow

- CMS HTML/CSS/JS and project JSON continue to initialize GrapesJS.
- GrapesJS commands drive undo, redo, preview, device changes, and selection.
- CMS media API continues to own uploaded assets.
- Save emits the same payload contract used by the current CMS page and homepage flows.
- Header/Footer locking and template preview remain unchanged.
- Editor dirty state is updated by canvas mutations and cleared after save.

## Performance

- Register blocks and plugins before one consolidated block render.
- Load backend blocks after the first canvas paint.
- Mutation observers must ignore workbench decorations.
- Inactive heavy panels, especially AI, are not rendered.
- Sidebar collapse only changes layout; it does not destroy GrapesJS manager containers.

## Error Handling

- A plugin failure must identify the plugin and allow the core editor to continue where possible.
- Unsupported commands stay disabled instead of failing silently.
- Preview mode always has an explicit exit control.
- Protected Header/Footer structures retain their existing delete/source restrictions.

## Verification

- Type-check the Vue application.
- Test the plugin registry/configuration as a pure module.
- Run existing CMS canvas validation tests.
- Verify desktop layout, sidebar collapse, category collapse, block drag, viewport switching, preview, and save in a browser.

