# CMS Header/Footer Style Editing

## Goal

让 CMS 在编辑器中预览并渲染固定的 Header/Footer，同时允许用户和 AI 修改样式；菜单列表、层级、Logo、登录入口和 DOM 结构始终由系统固定组件驱动。

## Architecture

Header/Footer 使用现有公共站点的 Vue 组件作为唯一结构来源。CMS Home 的 `settings` 仅保存 `chromeHeaderCss` 和 `chromeFooterCss`，不保存对应 HTML、Project JSON 或脚本。编辑器进入 Header/Footer 目标时加载固定预览结构，只开放 GrapesJS 样式面板和 AI 样式动作；保存时丢弃 HTML、JS 和 Project JSON。公共站点将保存的 CSS 应用到固定结构上，并根据既有导航、站点设置和登录状态渲染数据。

## Boundaries

- 用户可修改颜色、字体、间距、尺寸、边框、布局和响应式 CSS。
- 用户不可通过 Header/Footer 编辑器新增、删除、移动、替换节点，修改菜单 JSON、菜单层级、Logo URL、认证入口或脚本。
- AI 在 Header/Footer 目标下只允许 `set-css`、`append-css`、`set-styles`，其它 HTML、属性、节点和 Project JSON 动作被前端拒绝。
- 既有“导航菜单”管理页继续负责菜单数据管理，与样式编辑器隔离。

## Data Flow

1. CMS Home API 返回 `settings.chromeHeaderCss`、`settings.chromeFooterCss`。
2. CMS 打开 Header/Footer 样式编辑器，加载固定模板和对应 CSS。
3. 用户或 AI 只改变 CSS，保存回对应 settings key。
4. 公共 `/site` 页面在固定 Header/Footer 外层加载 CSS，数据由现有 `navigationJson`、系统设置和登录状态提供。

## Safety

编辑器锁定目标保存时只取 CSS。公共渲染完全忽略任何潜在的 `chromeHeaderHtml`、`chromeFooterHtml` 或 Project JSON 设置，避免历史数据或绕过前端提交改变结构。

## Testing

- 前端单元测试覆盖 Header/Footer CSS key、固定模板和锁定目标动作白名单。
- 前端类型检查通过。
- 后端 bootstrap 编译通过，确认现有 CMS API 契约不回归。
